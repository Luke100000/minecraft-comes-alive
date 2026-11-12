package net.conczin.mca.dialogue;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.InteractionCommands;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialoguePendingResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.resources.DialogueEvents;
import net.conczin.mca.server.world.data.DialogueEventHistory;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * Server-owned dialogue selection and transient conversation state.
 *
 * <p>This type is intentionally server-thread confined. Resource reload publication is owned by
 * {@link DialogueEvents}; generation mismatches invalidate the transient state here.</p>
 */
public final class DialogueEngine {
    public static final int MAX_EVENT_OPTIONS = 64;
    public static final long PAUSE_TICKS = 2400L;
    public static final long ASYNC_TIMEOUT_TICKS = 600L;

    private static final Comparator<DialogueEvent> PRIORITY_ORDER =
            Comparator.comparingInt(DialogueEvent::priority).reversed().thenComparing(DialogueEvent::id);
    private static final Comparator<DialogueEvent> CANONICAL_ORDER = Comparator.comparing(DialogueEvent::id);
    private final DialogueEvents events;
    private final Map<UUID, DialogueSession> sessions = new HashMap<>();
    private final Map<UUID, DialogueOptions> offers = new HashMap<>();
    private final Map<UUID, LinkedHashMap<UUID, ResourceLocation>> recentPreviews = new HashMap<>();
    private final List<PendingSlap> pendingSlaps = new ArrayList<>();
    private final DialoguePendingRequests pendingRequests = new DialoguePendingRequests();
    private final RandomSource random;
    private long tokenSequence;
    private long previewGeneration = Long.MIN_VALUE;

    public DialogueEngine(DialogueEvents events) {
        this(events, RandomSource.create());
    }

    DialogueEngine(DialogueEvents events, RandomSource random) {
        this.events = Objects.requireNonNull(events, "events");
        this.random = Objects.requireNonNull(random, "random");
        this.tokenSequence = random.nextLong();
    }

    public DialogueOptions begin(ServerPlayer player, VillagerEntityMCA villager) {
        return begin(player, villager, true);
    }

    public DialogueOptions begin(ServerPlayer player, VillagerEntityMCA villager, boolean showOpening) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(villager, "villager");
        UUID playerId = player.getUUID();
        long generation = events.generation();
        long gameTime = overworldTime(player);

        invalidateReloaded(playerId, generation);
        if (previewGeneration != generation) {
            recentPreviews.clear();
            previewGeneration = generation;
        }
        // Reopening Topics must not bypass a refusal already shown by this interaction.
        DialogueEvent refusal = null;
        DialogueOptions previousOffer = offers.get(playerId);
        if (previousOffer != null && previousOffer.villagerId().equals(villager.getUUID())
                && previousOffer.preview().isPresent()
                && previousOffer.interactionId() != null
                && villager.getInteractions().matchesInteraction(player, previousOffer.interactionId())) {
            refusal = events.get(previousOffer.preview().orElseThrow().eventId())
                    .filter(DialogueEngine::terminalRefusal).orElse(null);
        }
        DialogueSession previousSession = sessions.get(playerId);
        boolean activeRefusal = previousSession != null
                && previousSession.status() == DialogueSession.Status.ACTIVE
                && previousSession.villagerId().equals(villager.getUUID())
                && terminalRefusal(previousSession.event())
                && previousSession.interactionId() != null
                && villager.getInteractions().matchesInteraction(player, previousSession.interactionId());
        if (activeRefusal) {
            refusal = previousSession.event();
        }
        if (refusal != null && canInteract(player, villager)) {
            DialogueContext check = context(player, villager);
            if (eventEligible(refusal, check, check.history(), gameTime)) {
                if (activeRefusal) {
                    invalidatePending(playerId);
                    sessions.remove(playerId);
                }
                DialogueOptions locked = refusalOffer(player, villager, refusal, generation);
                offers.put(playerId, locked);
                return locked;
            }
        }
        if (activeRefusal) {
            // Fatigue may have been disabled since the refusal was offered.
            invalidatePending(playerId);
            sessions.remove(playerId);
        }
        DialogueSession retained = expirePaused(playerId, gameTime);
        if (retained != null && retained.status() == DialogueSession.Status.ACTIVE
                && retained.villagerId().equals(villager.getUUID())) {
            cancelPending(playerId);
            retained = retained.pause(saturatingAdd(gameTime, PAUSE_TICKS));
            sessions.put(playerId, retained);
        }
        DialogueEventHistory history = DialogueEventHistory.get(player.serverLevel());
        DialogueContext context = new DialogueContext(villager, player, history, id -> events.get(id).isPresent());

        ResourceLocation pausedEvent = retained != null
                && retained.status() == DialogueSession.Status.PAUSED
                && retained.villagerId().equals(villager.getUUID())
                ? retained.eventId()
                : null;
        SelectionPlan plan = planSelection(
                events.all(),
                event -> eventEligible(event, context, history, gameTime),
                pausedEvent
        );

        Optional<EventOption> highlighted = plan.highlighted().map(event -> option(event, villager));
        List<EventOption> ask = plan.ask().stream().map(event -> option(event, villager)).toList();
        List<ResourceLocation> ambient = new ArrayList<>(plan.ambient().stream().map(DialogueEvent::id).toList());
        List<DialogueEvent> previewCandidates = !showOpening ? List.of() : events.all().stream()
                .filter(event -> eventEligible(event, context, history, gameTime))
                .filter(event -> pausedEvent == null || !pausedEvent.equals(event.id()))
                .filter(DialogueEngine::previewable)
                .filter(event -> {
                    DialogueEvent.Node first = event.nodes().get(event.start());
                    return first.lines().size() > 1 || first.next().isPresent()
                            || first.choices().isEmpty()
                            || first.choices().orElseThrow().stream().anyMatch(choice ->
                                    choiceEligible(choice, context));
                })
                .toList();
        ResourceLocation last = recentPreviews.getOrDefault(playerId, new LinkedHashMap<>()).get(villager.getUUID());
        Config config = Config.getInstance();
        Optional<DialogueEvent> selectedPreview = pickPreview(previewCandidates, last, random,
                pairFatigue(context), config.interactionChanceFatigue, config.interactionFatigueEnabled);
        boolean refusing = selectedPreview.filter(DialogueEngine::terminalRefusal).isPresent();
        Optional<AmbientPreview> preview = selectedPreview.map(event -> {
            DialogueEvent.Node node = event.nodes().get(event.start());
            List<ChoiceView> choices = node.lines().size() == 1
                    ? node.choices().orElse(List.of()).stream()
                            .filter(choice -> choiceEligible(choice, context))
                            .map(choice -> new ChoiceView(choice.id(), Component.translatable(choice.text())))
                            .toList()
                    : List.of();
            return new AmbientPreview(event.id(),
                    villager.getTranslatable(player, node.lines().getFirst()), node.silent(),
                    previewAdvanceKind(event), choices);
        });
        if (selectedPreview.isPresent()) {
            ResourceLocation picked = selectedPreview.orElseThrow().id();
            LinkedHashMap<UUID, ResourceLocation> previous = recentPreviews.computeIfAbsent(
                    playerId, ignored -> new LinkedHashMap<>());
            previous.remove(villager.getUUID());
            previous.put(villager.getUUID(), picked);
            if (previous.size() > 32) {
                previous.remove(previous.keySet().iterator().next());
            }
            ambient.remove(picked);
            if (ambient.isEmpty()) {
                ambient.addAll(planSelection(events.all(),
                        event -> !event.id().equals(picked) && eventEligible(event, context, history, gameTime),
                        pausedEvent
                ).ambient().stream().map(DialogueEvent::id).toList());
            }
        }
        Optional<Component> continuationPrompt = Optional.empty();
        Optional<UUID> continuationSessionId = Optional.empty();
        if (retained != null
                && retained.canResume(gameTime, generation, villager.getUUID())) {
            continuationPrompt = Optional.of(continuationPrompt(retained.event(), villager.getDisplayName()));
            continuationSessionId = Optional.of(retained.id());
        }

        DialogueOptions options = new DialogueOptions(
                villager.getUUID(),
                generation,
                refusing ? Optional.empty() : highlighted,
                refusing ? List.of() : ask,
                !refusing && !ambient.isEmpty(),
                refusing ? Optional.empty() : continuationPrompt,
                nextToken(),
                refusing ? List.of() : ambient,
                refusing ? Optional.empty() : continuationSessionId,
                preview,
                villager.getInteractions().interactionId()
        );
        offers.put(playerId, options);
        if (selectedPreview.isPresent()) {
            DialogueEvent shown = selectedPreview.orElseThrow();
            DialogueEvent.Node node = shown.nodes().get(shown.start());
            if (shown.history() == DialogueEvent.HistoryPolicy.SCHEDULING
                    && shown.repeat().maxTicks() > 0L
                    && node.lines().size() == 1 && node.choices().isEmpty()
                    && node.next().isEmpty()) {
                history.complete(playerId, villager.getUUID(), shown, Set.of(), gameTime, random);
            }
        }
        return options;
    }

    static boolean previewable(DialogueEvent event) {
        if (event.presentation().mode() != DialogueEvent.PresentationMode.AMBIENT) {
            return false;
        }
        DialogueEvent.Node node = event.nodes().get(event.start());
        if (node.lines().isEmpty() || node.outcomes().isPresent()) {
            return false;
        }
        return node.lines().size() > 1 || node.next().isPresent()
                || node.choices().isPresent()
                || (node.complete() && event.history() == DialogueEvent.HistoryPolicy.SCHEDULING);
    }

    private static boolean terminalRefusal(DialogueEvent event) {
        DialogueEvent.Node start = event.nodes().get(event.start());
        return event.presentation().mode() == DialogueEvent.PresentationMode.AMBIENT
                && start.closeInteraction() && start.complete() && start.lines().size() == 1;
    }

    private DialogueOptions refusalOffer(ServerPlayer player, VillagerEntityMCA villager,
                                         DialogueEvent event, long generation) {
        DialogueEvent.Node start = event.nodes().get(event.start());
        AmbientPreview preview = new AmbientPreview(event.id(),
                villager.getTranslatable(player, start.lines().getFirst()), start.silent(),
                AdvanceKind.BACK_TO_TOPICS, List.of());
        return new DialogueOptions(villager.getUUID(), generation, Optional.empty(), List.of(),
                false, Optional.empty(), nextToken(), List.of(), Optional.empty(),
                Optional.of(preview), villager.getInteractions().interactionId());
    }

    static AdvanceKind previewAdvanceKind(DialogueEvent event) {
        DialogueEvent.Node node = event.nodes().get(event.start());
        if (node.lines().size() > 1 || node.next().isPresent()) {
            return AdvanceKind.NEXT;
        }
        return node.closeInteraction() ? AdvanceKind.BACK_TO_TOPICS : AdvanceKind.NONE;
    }

    static Optional<DialogueEvent> pickPreview(
            List<DialogueEvent> candidates, @Nullable ResourceLocation previous, RandomSource random
    ) {
        return pickPreview(candidates, previous, random, 0, 0.0f, true);
    }

    static Optional<DialogueEvent> pickPreview(
            List<DialogueEvent> candidates, @Nullable ResourceLocation previous, RandomSource random,
            int pairFatigue, float multiplier, boolean fatigueEnabled
    ) {
        int points = legacyFatiguePoints(pairFatigue, multiplier, fatigueEnabled);
        List<DialogueEvent> safe = candidates.stream().filter(DialogueEngine::previewable)
                .filter(event -> effectiveEventWeight(event, points) > 0.0)
                .sorted(CANONICAL_ORDER).toList();
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        int highest = safe.stream().mapToInt(DialogueEvent::priority).max().orElseThrow();
        List<DialogueEvent> tier = safe.stream().filter(event -> event.priority() == highest).toList();
        if (previous != null && tier.size() > 1) {
            tier = tier.stream().filter(event -> !event.id().equals(previous)).toList();
        }
        return weightedPick(tier, event -> effectiveEventWeight(event, points), random);
    }

    public Optional<DialogueNodeView> select(
            ServerPlayer player,
            long offerToken,
            DialogueSelection selection,
            @Nullable ResourceLocation eventId
    ) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(selection, "selection");
        UUID playerId = player.getUUID();
        DialogueOptions offer = offers.get(playerId);
        if (offer == null || !acceptsMenuOffer(offer, offerToken, events.generation())) {
            invalidateReloaded(playerId, events.generation());
            return Optional.empty();
        }

        VillagerEntityMCA villager = resolveBoundVillager(player, offer.villagerId()).orElse(null);
        if (villager == null || !canInteract(player, villager)
                || (offer.interactionId() != null
                    && !villager.getInteractions().matchesInteraction(player, offer.interactionId()))) {
            return Optional.empty();
        }

        DialogueSession current = sessions.get(playerId);
        if (selection != DialogueSelection.RESUME
                && current != null
                && current.status() == DialogueSession.Status.ACTIVE) {
            return Optional.empty();
        }

        long gameTime = overworldTime(player);
        DialogueEventHistory history = DialogueEventHistory.get(player.serverLevel());
        DialogueContext context = new DialogueContext(villager, player, history, id -> events.get(id).isPresent());

        if (selection == DialogueSelection.RESUME) {
            if (eventId != null || offer.continuationSessionId().isEmpty()) {
                return Optional.empty();
            }
            DialogueSession retained = sessions.get(playerId);
            if (retained == null
                    || !retained.id().equals(offer.continuationSessionId().orElseThrow())
                    || !retained.canResume(gameTime, offer.generation(), offer.villagerId())) {
                if (retained != null && retained.pauseDeadline() <= gameTime) {
                    invalidatePending(playerId);
                    sessions.remove(playerId);
                }
                return Optional.empty();
            }
            offer.consume();
            offers.remove(playerId);
            DialogueSession resumed = resolveAutomaticTransitions(
                    retained.resume(nextToken(), offer.token(), offer.interactionId()), context);
            if (resumed == null) {
                invalidatePending(playerId);
                sessions.remove(playerId);
                return Optional.empty();
            }
            resumed = refreshOfferedChoices(resumed, context);
            pendingRequests.resumeResolved(DialoguePendingRequests.Key.from(retained),
                    DialoguePendingRequests.Key.from(resumed));
            sessions.put(playerId, resumed);
            return Optional.of(view(resumed, context));
        }

        DialogueEvent selected;
        if (selection == DialogueSelection.EVENT) {
            if (eventId == null || !offer.containsEvent(eventId)) {
                return Optional.empty();
            }
            selected = events.get(eventId).orElse(null);
            if (selected == null || !eventEligible(selected, context, history, gameTime)) {
                return Optional.empty();
            }
        } else if (selection == DialogueSelection.AMBIENT) {
            if (eventId != null || !offer.ambientAvailable()) {
                return Optional.empty();
            }
            List<DialogueEvent> candidates = offer.ambientCandidates().stream()
                    .map(events::get)
                    .flatMap(Optional::stream)
                    .filter(event -> eventEligible(event, context, history, gameTime))
                    .sorted(CANONICAL_ORDER)
                    .toList();
            Config config = Config.getInstance();
            int points = legacyFatiguePoints(pairFatigue(context),
                    config.interactionChanceFatigue, config.interactionFatigueEnabled);
            selected = weightedPick(candidates, event -> effectiveEventWeight(event, points), random)
                    .orElse(null);
            if (selected == null) {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }

        offer.consume();
        offers.remove(playerId);
        DialogueSession session = DialogueSession.start(
                playerId,
                villager.getUUID(),
                UUID.randomUUID(),
                offer.generation(),
                nextToken(),
                offer.token(),
                selected,
                offer.interactionId()
        );
        session = resolveAutomaticTransitions(session, context);
        if (session == null) {
            invalidatePending(playerId);
            sessions.remove(playerId);
            return Optional.empty();
        }
        session = refreshOfferedChoices(session, context);
        invalidatePending(playerId);
        sessions.put(playerId, session);
        return Optional.of(view(session, context));
    }

    public TransitionResult choose(ServerPlayer player, long offerToken, String choiceId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(choiceId, "choiceId");
        UUID playerId = player.getUUID();
        DialogueSession session = validActiveSession(player, offerToken);
        if (session == null) {
            return engagePreview(player, offerToken, choiceId);
        }
        if (pendingRequests.pendingFor(DialoguePendingRequests.Key.from(session)).isPresent()) {
            return TransitionResult.rejected();
        }

        VillagerEntityMCA villager = resolveBoundVillager(player, session.villagerId()).orElse(null);
        if (villager == null) {
            pauseUnavailable(player, session);
            return TransitionResult.paused(session.id());
        }
        if (!canInteract(player, villager)) {
            pauseUnavailable(player, session);
            return TransitionResult.paused(session.id());
        }

        DialogueContext context = context(player, villager);
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        if (session.lineIndex() != node.lines().size() - 1 || node.choices().isEmpty()
                || !session.offeredChoices().contains(choiceId)) {
            return TransitionResult.rejected(view(session, context));
        }

        DialogueEvent.Choice choice = node.choices().orElseThrow().stream()
                .filter(candidate -> candidate.id().equals(choiceId))
                .findFirst()
                .orElse(null);
        if (choice == null || !choiceEligible(choice, context)) {
            DialogueSession refreshed = refreshOfferedChoices(session, context);
            if (refreshed.offeredChoices().isEmpty()) {
                invalidatePending(playerId);
                sessions.remove(playerId);
                offers.remove(playerId);
                return TransitionResult.ending(session.id());
            }
            sessions.put(playerId, refreshed);
            return TransitionResult.rejected(view(refreshed, context));
        }

        DialogueSession progressed = session.withAcceptedChoice(choice.id());
        String next;
        if (choice.outcomes().isPresent()) {
            List<DialogueEvent.Outcome> eligible = eligibleOutcomes(
                    choice,
                    condition -> matches(condition, context)
            );
            DialogueEvent.Outcome outcome = pickOutcome(eligible, random,
                    context.villager().getVillagerBrain().getMemoriesForPlayer(context.player()).getInteractionFatigue(),
                    Config.getInstance().interactionChanceFatigue,
                    Config.getInstance().interactionFatigueEnabled).orElse(null);
            if (outcome == null) {
                DialogueSession refreshed = refreshOfferedChoices(session, context);
                if (refreshed.offeredChoices().isEmpty()) {
                    invalidatePending(playerId);
                    sessions.remove(playerId);
                    offers.remove(playerId);
                    return TransitionResult.ending(session.id());
                }
                sessions.put(playerId, refreshed);
                return TransitionResult.rejected(view(refreshed, context));
            }
            int outcomeIndex = choice.outcomes().orElseThrow().indexOf(outcome);
            progressed = progressed.withSelectedOutcome(choice.id(), outcomeIndex);
            progressed = progressed.appendPendingEffects(outcome.actions());
            next = outcome.next();
        } else {
            progressed = progressed.appendPendingEffects(choice.actions());
            next = choice.next().orElseThrow();
        }

        progressed = progressed.withProgress(next, 0, List.of(), nextToken());
        progressed = resolveAutomaticTransitions(progressed, context);
        if (progressed == null) {
            invalidatePending(playerId);
            sessions.remove(playerId);
            offers.remove(playerId);
            return TransitionResult.ending(session.id());
        }
        progressed = refreshOfferedChoices(progressed, context);
        DialogueEvent.Node ending = progressed.event().nodes().get(progressed.nodeId());
        if (ending.complete() && ending.lines().size() == 1
                && ending.choices().isEmpty() && ending.next().isEmpty()) {
            List<DialogueAction.Slap> slaps = progressed.pendingEffects().stream()
                    .filter(DialogueAction.Slap.class::isInstance)
                    .map(DialogueAction.Slap.class::cast).toList();
            if (slaps.size() == 1) {
                UUID interactionId = villager.getInteractions().interactionId();
                if (!villager.getInteractions().matchesInteraction(player, interactionId)) {
                    return TransitionResult.rejected(view(session, context));
                }
                invalidatePending(playerId);
                sessions.remove(playerId);
                offers.remove(playerId);
                if (!commit(player, villager, progressed, context, true)) {
                    return TransitionResult.ending(session.id());
                }
                float amount = slaps.getFirst().amount();
                if (villager.getInteractions().matchesInteraction(player, interactionId)) {
                    villager.getInteractions().stopInteracting(player, interactionId);
                }
                // The effects may have closed or replaced the interaction. The client
                // ignores this original-ID close when a newer interaction is visible.
                Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, villager, interactionId), player);
                pendingSlaps.add(new PendingSlap(player, villager, player.serverLevel(),
                        saturatingAdd(overworldTime(player), 3L), amount,
                        villager.getTranslatable(player, ending.lines().getFirst())));
                return TransitionResult.completion(session.id());
            }
        }
        sessions.put(playerId, progressed);
        return TransitionResult.advanced(view(progressed, context));
    }

    public TransitionResult advance(ServerPlayer player, long offerToken) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUUID();
        DialogueSession session = validActiveSession(player, offerToken);
        if (session == null) {
            return engagePreview(player, offerToken, null);
        }
        if (pendingRequests.pendingFor(DialoguePendingRequests.Key.from(session)).isPresent()) {
            return TransitionResult.rejected();
        }

        VillagerEntityMCA villager = resolveBoundVillager(player, session.villagerId()).orElse(null);
        if (villager == null || !canInteract(player, villager)) {
            pauseUnavailable(player, session);
            return TransitionResult.paused(session.id());
        }
        DialogueContext context = context(player, villager);
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());

        if (session.lineIndex() < node.lines().size() - 1) {
            DialogueSession progressed = session.withProgress(
                    session.nodeId(),
                    session.lineIndex() + 1,
                    List.of(),
                    nextToken()
            );
            progressed = refreshOfferedChoices(progressed, context);
            sessions.put(playerId, progressed);
            return TransitionResult.advanced(view(progressed, context));
        }

        if (node.choices().isPresent()) {
            DialogueSession refreshed = refreshOfferedChoices(session, context);
            if (refreshed.offeredChoices().isEmpty()) {
                invalidatePending(playerId);
                sessions.remove(playerId);
                offers.remove(playerId);
                return TransitionResult.ending(session.id());
            }
            sessions.put(playerId, refreshed);
            return TransitionResult.rejected(view(refreshed, context));
        }

        if (node.next().isPresent()) {
            DialogueSession progressed = session.withProgress(node.next().orElseThrow(), 0, List.of(), nextToken());
            progressed = resolveAutomaticTransitions(progressed, context);
            if (progressed == null) {
                invalidatePending(playerId);
                sessions.remove(playerId);
                offers.remove(playerId);
                return TransitionResult.ending(session.id());
            }
            progressed = refreshOfferedChoices(progressed, context);
            sessions.put(playerId, progressed);
            return TransitionResult.advanced(view(progressed, context));
        }

        invalidatePending(playerId);
        sessions.remove(playerId);
        offers.remove(playerId);
        if (!node.complete()) {
            return TransitionResult.ending(session.id());
        }
        UUID boundInteraction = session.interactionId();
        if (node.closeInteraction() && (boundInteraction == null
                || !villager.getInteractions().matchesInteraction(player, boundInteraction))) {
            return TransitionResult.ending(session.id());
        }
        if (!commit(player, villager, session, context)) {
            return TransitionResult.ending(session.id());
        }
        if (node.closeInteraction()) {
            villager.getInteractions().stopInteracting(player, boundInteraction);
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, villager, boundInteraction), player);
        }
        return TransitionResult.completion(session.id());
    }

    public void pause(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        UUID playerId = player.getUUID();
        offers.remove(playerId);
        DialogueSession session = sessions.get(playerId);
        if (session == null || session.status() == DialogueSession.Status.PAUSED) {
            return;
        }
        cancelPending(playerId);
        long deadline = saturatingAdd(overworldTime(player), PAUSE_TICKS);
        sessions.put(playerId, session.pause(deadline));
    }

    /** Revokes only the dialogue state belonging to the released Interact screen. */
    public void onInteractionReleased(ServerPlayer player, UUID villagerId) {
        onInteractionReleased(player.getUUID(), villagerId, overworldTime(player));
    }

    void onInteractionReleased(UUID playerId, UUID villagerId, long gameTime) {
        DialogueOptions offer = offers.get(playerId);
        if (offer != null && offer.villagerId().equals(villagerId)) {
            offers.remove(playerId);
        }
        DialogueSession session = sessions.get(playerId);
        if (session != null && session.status() == DialogueSession.Status.ACTIVE
                && session.villagerId().equals(villagerId)) {
            cancelPending(playerId);
            sessions.put(playerId, session.pause(saturatingAdd(gameTime, PAUSE_TICKS)));
        }
    }

    /** Returns whether a screen-exit packet may also close the villager interaction. */
    public boolean pause(ServerPlayer player, UUID villagerId, UUID sessionId, long offerToken) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(sessionId, "sessionId");
        UUID playerId = player.getUUID();
        DialogueOptions offer = offers.get(playerId);
        DialogueSession session = sessions.get(playerId);
        boolean matchingOffer = offer != null
                && offer.villagerId().equals(villagerId)
                && offer.token() == offerToken;
        // A screen may close before its first (or refreshed) menu token reaches the client.
        // InteractionCloseRequest independently authenticates the concrete screen interaction.
        boolean matchingUnseenMenu = offer != null
                && offer.villagerId().equals(villagerId)
                && sessionId.equals(Util.NIL_UUID)
                && (offerToken == 0L || matchingOffer);
        boolean matchingActiveSession = session != null
                && session.status() == DialogueSession.Status.ACTIVE
                && session.villagerId().equals(villagerId)
                && session.id().equals(sessionId)
                && (session.offerToken() == offerToken
                    || Objects.equals(session.previousOfferToken(), offerToken));
        boolean matchingPendingMenu = session != null
                && session.status() == DialogueSession.Status.ACTIVE
                && session.villagerId().equals(villagerId)
                && sessionId.equals(Util.NIL_UUID)
                && Objects.equals(session.previousOfferToken(), offerToken);
        if ((offer != null && !matchingOffer && !matchingUnseenMenu)
                || (session != null && session.status() == DialogueSession.Status.ACTIVE
                    && !matchingActiveSession && !matchingPendingMenu && !matchingUnseenMenu)) {
            return false;
        }
        if (matchingOffer || matchingUnseenMenu) {
            offers.remove(playerId);
        }
        if (matchingActiveSession || matchingPendingMenu || (matchingUnseenMenu
                && session != null && session.status() == DialogueSession.Status.ACTIVE
                && session.villagerId().equals(villagerId))) {
            cancelPending(playerId);
            sessions.put(playerId, session.pause(saturatingAdd(overworldTime(player), PAUSE_TICKS)));
        }
        return true;
    }

    public void end(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        invalidatePending(player.getUUID());
        sessions.remove(player.getUUID());
        offers.remove(player.getUUID());
    }

    public void onVillagerLeave(VillagerEntityMCA villager) {
        Objects.requireNonNull(villager, "villager");
        Entity.RemovalReason reason = villager.getRemovalReason();
        boolean destroyed = reason != null && reason.shouldDestroy();
        MinecraftServer server = villager.level() instanceof ServerLevel level ? level.getServer() : null;
        long gameTime = server == null ? 0L : server.overworld().getGameTime();
        sessions.replaceAll((playerId, session) -> {
            if (!session.villagerId().equals(villager.getUUID())) {
                return session;
            }
            invalidatePending(playerId);
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
            if (destroyed) {
                if (player != null) {
                    notifyEnded(player, session);
                }
                return session;
            }
            DialogueSession paused = session.status() == DialogueSession.Status.PAUSED
                    ? session
                    : session.pause(saturatingAdd(gameTime, PAUSE_TICKS));
            if (player != null) {
                notifyPaused(player, paused);
            }
            return paused;
        });
        if (destroyed) {
            sessions.entrySet().removeIf(entry -> entry.getValue().villagerId().equals(villager.getUUID()));
        }
        offers.entrySet().removeIf(entry -> entry.getValue().villagerId().equals(villager.getUUID()));
        recentPreviews.values().forEach(previous -> previous.remove(villager.getUUID()));
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        long generation = events.generation();
        long gameTime = server.overworld().getGameTime();
        for (Iterator<PendingSlap> iterator = pendingSlaps.iterator(); iterator.hasNext();) {
            PendingSlap pending = iterator.next();
            if (gameTime < pending.dueTick()) {
                continue;
            }
            iterator.remove();
            if (!pending.player().isAlive() || pending.player().isRemoved() || pending.player().hasDisconnected()
                    || !pending.villager().isAlive() || pending.villager().isRemoved()
                    || pending.player().getServer() != server
                    || pending.player().serverLevel() != pending.level()
                    || pending.villager().level() != pending.level()) {
                continue;
            }
            pending.villager().getInteractions().slap(pending.player(), pending.amount());
            pending.villager().sendChatMessage(pending.reaction(), pending.player());
        }
        if (previewGeneration != generation) {
            recentPreviews.clear();
            previewGeneration = generation;
        }
        if (gameTime % 20L == 0L) {
            DialogueEventHistory.get(server.overworld()).pruneExpiredScheduling(gameTime, 64);
            recentPreviews.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        sessions.entrySet().removeIf(entry -> {
            DialogueSession session = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(session.playerId());
            if (shouldDiscardSession(session, generation, gameTime, player != null)) {
                invalidatePending(session.playerId());
                if (player != null) {
                    notifyEnded(player, session);
                }
                return true;
            }
            if (session.status() == DialogueSession.Status.PAUSED) {
                Entity entity = findLoadedEntity(server, session.villagerId());
                boolean discard = entity != null
                        && (!(entity instanceof VillagerEntityMCA villager) || !villager.isAlive() || villager.isRemoved());
                if (discard) {
                    invalidatePending(session.playerId());
                    notifyEnded(player, session);
                }
                return discard;
            }

            Entity entity = player.serverLevel().getEntity(session.villagerId());
            if (entity == null) {
                DialogueSession paused = session.pause(saturatingAdd(gameTime, PAUSE_TICKS));
                entry.setValue(paused);
                offers.remove(session.playerId());
                notifyPaused(player, paused);
                return false;
            }
            if (!(entity instanceof VillagerEntityMCA villager) || !villager.isAlive() || villager.isRemoved()) {
                invalidatePending(session.playerId());
                notifyEnded(player, session);
                return true;
            }
            if (!canInteract(player, villager)) {
                DialogueSession paused = session.pause(saturatingAdd(gameTime, PAUSE_TICKS));
                entry.setValue(paused);
                offers.remove(session.playerId());
                notifyPaused(player, paused);
            }
            return false;
        });
        for (DialoguePendingRequests.Ticket ticket : pendingRequests.snapshot()) {
            DialogueSession session = sessions.get(ticket.key().playerId());
            if (session == null || session.status() != DialogueSession.Status.ACTIVE
                    || !ticket.key().equals(DialoguePendingRequests.Key.from(session))) {
                invalidatePending(ticket.key().playerId());
            } else if (gameTime >= ticket.deadline() && finishPending(ticket, null, null, server)) {
                cancelOperation(ticket);
            }
        }
        Map<UUID, DialogueOptions> reloadInvalidatedMenus = new HashMap<>();
        offers.entrySet().removeIf(entry -> {
            DialogueOptions offer = entry.getValue();
            if (offer.generation() != generation) {
                reloadInvalidatedMenus.put(entry.getKey(), offer);
            }
            if (shouldDiscardOffer(
                    offer,
                    generation,
                    server.getPlayerList().getPlayer(entry.getKey()) != null
            )) {
                return true;
            }
            if (offer.continuationSessionId().isEmpty()) {
                return false;
            }
            DialogueSession session = sessions.get(entry.getKey());
            return session == null
                    || !session.id().equals(offer.continuationSessionId().orElseThrow())
                    || !session.canResume(gameTime, generation, offer.villagerId());
        });
        for (Map.Entry<UUID, DialogueOptions> stale : reloadInvalidatedMenus.entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(stale.getKey());
            if (player == null) {
                continue;
            }
            VillagerEntityMCA villager = resolveBoundVillager(player, stale.getValue().villagerId()).orElse(null);
            if (villager != null
                    && canInteract(player, villager)
                    && villager.getInteractions().getInteractingPlayer().filter(player::equals).isPresent()) {
                Network.sendToPlayer(InteractionDialogueOptionsResponse.from(begin(player, villager)), player);
            }
        }
    }

    public void clear(MinecraftServer server) {
        for (UUID playerId : sessions.keySet()) {
            invalidatePending(playerId);
        }
        for (DialoguePendingRequests.Ticket ticket : pendingRequests.snapshot()) {
            invalidatePending(ticket.key().playerId());
        }
        sessions.clear();
        offers.clear();
        recentPreviews.clear();
        pendingSlaps.clear();
    }

    private TransitionResult engagePreview(ServerPlayer player, long token, @Nullable String choiceId) {
        UUID playerId = player.getUUID();
        DialogueOptions offer = offers.get(playerId);
        if (offer == null || !acceptsMenuOffer(offer, token, events.generation()) || offer.preview().isEmpty()) {
            return TransitionResult.rejected();
        }
        DialogueSession old = sessions.get(playerId);
        if (old != null && old.status() == DialogueSession.Status.ACTIVE) {
            return TransitionResult.rejected();
        }
        VillagerEntityMCA villager = resolveBoundVillager(player, offer.villagerId()).orElse(null);
        if (villager == null || !canInteract(player, villager)
                || villager.getInteractions().getInteractingPlayer().filter(player::equals).isEmpty()
                || (offer.interactionId() != null
                    && !villager.getInteractions().matchesInteraction(player, offer.interactionId()))) {
            return TransitionResult.rejected();
        }
        DialogueContext context = context(player, villager);
        DialogueEvent event = events.get(offer.preview().orElseThrow().eventId()).orElse(null);
        if (event == null || !eventEligible(event, context, context.history(), overworldTime(player))) {
            return TransitionResult.rejected();
        }
        AmbientPreview preview = offer.preview().orElseThrow();
        if (choiceId == null ? preview.advanceKind() == AdvanceKind.NONE
                : preview.choices().stream().noneMatch(choice -> choice.id().equals(choiceId))) {
            return TransitionResult.rejected();
        }
        DialogueSession started = DialogueSession.start(playerId, villager.getUUID(), UUID.randomUUID(),
                offer.generation(), nextToken(), offer.token(), event, offer.interactionId());
        started = refreshOfferedChoices(started, context);
        if (choiceId != null && !started.offeredChoices().contains(choiceId)) {
            return TransitionResult.rejected();
        }
        invalidatePending(playerId);
        sessions.put(playerId, started);
        offer.consume();
        offers.remove(playerId);
        return choiceId == null ? advance(player, started.offerToken())
                : choose(player, started.offerToken(), choiceId);
    }

    static SelectionPlan planSelection(
            Collection<DialogueEvent> source,
            Predicate<DialogueEvent> eligible,
            ResourceLocation pausedEventId
    ) {
        List<DialogueEvent> candidates = source.stream()
                .filter(event -> event.trigger() == DialogueEvent.Trigger.TALK)
                .filter(event -> pausedEventId == null || !event.id().equals(pausedEventId))
                .filter(eligible)
                .sorted(CANONICAL_ORDER)
                .toList();

        List<DialogueEvent> highlighted = candidates.stream()
                .filter(event -> event.presentation().mode() == DialogueEvent.PresentationMode.HIGHLIGHTED)
                .sorted(PRIORITY_ORDER)
                .toList();
        Optional<DialogueEvent> direct = highlighted.stream().findFirst();

        List<DialogueEvent> ask = new ArrayList<>();
        if (highlighted.size() > 1) {
            ask.addAll(highlighted.subList(1, highlighted.size()));
        }
        candidates.stream()
                .filter(event -> event.presentation().mode() == DialogueEvent.PresentationMode.ASK)
                .forEach(ask::add);
        ask.sort(PRIORITY_ORDER);
        int askLimit = Math.max(0, MAX_EVENT_OPTIONS - (direct.isPresent() ? 1 : 0));
        if (ask.size() > askLimit) {
            ask = new ArrayList<>(ask.subList(0, askLimit));
        }

        List<DialogueEvent> ambient = candidates.stream()
                .filter(event -> event.presentation().mode() == DialogueEvent.PresentationMode.AMBIENT)
                .toList();
        if (!ambient.isEmpty()) {
            int highestPriority = ambient.stream().mapToInt(DialogueEvent::priority).max().orElseThrow();
            ambient = ambient.stream()
                    .filter(event -> event.priority() == highestPriority)
                    .sorted(CANONICAL_ORDER)
                    .toList();
        }
        return new SelectionPlan(direct, ask, ambient);
    }

    static boolean shouldDiscardSession(
            DialogueSession session,
            long currentGeneration,
            long gameTime,
            boolean playerPresent
    ) {
        if (session.generation() != currentGeneration || !playerPresent) {
            return true;
        }
        return session.status() == DialogueSession.Status.PAUSED && gameTime >= session.pauseDeadline();
    }

    static boolean shouldDiscardOffer(DialogueOptions offer, long currentGeneration, boolean playerPresent) {
        return offer.generation() != currentGeneration || !playerPresent;
    }

    static boolean acceptsMenuOffer(DialogueOptions offer, long offerToken, long currentGeneration) {
        return offer.token() == offerToken
                && !offer.consumed()
                && offer.generation() == currentGeneration;
    }

    static Component continuationPrompt(DialogueEvent event, Component villagerName) {
        return Component.translatable(event.presentation().resumePrompt(), villagerName);
    }

    static boolean hasNoValidContinuation(DialogueSession session) {
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        return !node.lines().isEmpty()
                && session.lineIndex() == node.lines().size() - 1
                && node.choices().isPresent()
                && session.offeredChoices().isEmpty();
    }

    static AdvanceKind advanceKind(DialogueSession session) {
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        if (node.lines().isEmpty()) {
            return AdvanceKind.NONE;
        }
        if (session.lineIndex() < node.lines().size() - 1 || node.next().isPresent()) {
            return AdvanceKind.NEXT;
        }
        if (node.complete() || node.end() || hasNoValidContinuation(session)) {
            return AdvanceKind.BACK_TO_TOPICS;
        }
        return AdvanceKind.NONE;
    }

    static boolean canContinue(DialogueSession session) {
        return advanceKind(session) != AdvanceKind.NONE;
    }

    static boolean repeatAvailable(DialogueEvent event, boolean completed, long nextEligibleAt, long gameTime) {
        return switch (event.repeat().type()) {
            case ALWAYS -> true;
            case ONCE -> !completed;
            case COOLDOWN -> gameTime >= nextEligibleAt;
        };
    }

    static boolean choiceEligible(DialogueEvent.Choice choice, Predicate<DialogueCondition> conditionMatches) {
        return choiceEligible(choice, conditionMatches, 0, 0.0f, true);
    }

    static boolean choiceEligible(DialogueEvent.Choice choice, Predicate<DialogueCondition> conditionMatches,
                                  int pairFatigue, float multiplier, boolean fatigueEnabled) {
        if (!choice.requirements().stream().allMatch(conditionMatches)) {
            return false;
        }
        int points = legacyFatiguePoints(pairFatigue, multiplier, fatigueEnabled);
        return choice.outcomes().isEmpty() || eligibleOutcomes(choice, conditionMatches).stream()
                .anyMatch(outcome -> effectiveOutcomeWeight(outcome, points) > 0.0);
    }

    private static boolean choiceEligible(DialogueEvent.Choice choice, DialogueContext context) {
        Config config = Config.getInstance();
        return choiceEligible(choice, condition -> matches(condition, context), pairFatigue(context),
                config.interactionChanceFatigue, config.interactionFatigueEnabled);
    }

    private static int pairFatigue(DialogueContext context) {
        return context.villager().getVillagerBrain()
                .getMemoriesForPlayer(context.player()).getInteractionFatigue();
    }

    static List<DialogueEvent.Outcome> eligibleOutcomes(
            DialogueEvent.Choice choice,
            Predicate<DialogueCondition> conditionMatches
    ) {
        return eligibleOutcomes(choice.outcomes().orElse(List.of()), conditionMatches);
    }

    static List<DialogueEvent.Outcome> eligibleOutcomes(
            List<DialogueEvent.Outcome> outcomes,
            Predicate<DialogueCondition> conditionMatches
    ) {
        return outcomes.stream()
                .filter(outcome -> outcome.requirements().stream().allMatch(conditionMatches))
                .toList();
    }

    static DialogueSession resolveAutomaticTransitions(
            DialogueSession session,
            Predicate<DialogueCondition> conditionMatches,
            RandomSource random,
            LongSupplier nextToken
    ) {
        return resolveAutomaticTransitions(session, conditionMatches, random, nextToken, 0, 0.0f);
    }

    @Nullable
    static DialogueSession resolveAutomaticTransitions(
            DialogueSession session,
            Predicate<DialogueCondition> conditionMatches,
            RandomSource random,
            LongSupplier nextToken,
            int pairFatigue,
            float multiplier
    ) {
        return resolveAutomaticTransitions(session, conditionMatches, random, nextToken,
                pairFatigue, multiplier, true);
    }

    @Nullable
    static DialogueSession resolveAutomaticTransitions(
            DialogueSession session,
            Predicate<DialogueCondition> conditionMatches,
            RandomSource random,
            LongSupplier nextToken,
            int pairFatigue,
            float multiplier,
            boolean fatigueEnabled
    ) {
        DialogueSession current = session;
        boolean routed = false;
        while (true) {
            DialogueEvent.Node node = current.event().nodes().get(current.nodeId());
            if (node.outcomes().isEmpty()) {
                // Hidden routing nodes never produce a client-visible token. Rotate once
                // after the route so an in-flight close can still match the last visible token.
                if (!routed) {
                    return current;
                }
                return new DialogueSession(
                        current.playerId(), current.villagerId(), current.id(), current.generation(),
                        nextToken.getAsLong(), current.status(), current.pauseDeadline(), current.event(),
                        current.nodeId(), current.lineIndex(), current.offeredChoices(), current.acceptedChoices(),
                        current.selectedOutcomes(), current.pendingEffects(),
                        session.previousOfferToken() != null ? session.previousOfferToken() : session.offerToken(),
                        current.interactionId()
                );
            }
            List<DialogueEvent.Outcome> eligible = eligibleOutcomes(
                    node.outcomes().orElseThrow(),
                    conditionMatches
            );
            Optional<DialogueEvent.Outcome> picked = pickOutcome(eligible, random,
                    pairFatigue, multiplier, fatigueEnabled);
            if (picked.isEmpty()) {
                // A datapack may have only fatigue-conditioned paths. Never render a hidden
                // route or commit effects when none has a positive effective weight.
                return null;
            }
            DialogueEvent.Outcome outcome = picked.orElseThrow();
            current = current.appendPendingEffects(outcome.actions())
                    .withProgress(outcome.next(), 0, List.of(), current.offerToken());
            routed = true;
        }
    }

    static Optional<DialogueEvent.Outcome> pickOutcome(List<DialogueEvent.Outcome> eligible, RandomSource random) {
        return pickOutcome(eligible, random, 0, 0.0f);
    }

    static Optional<DialogueEvent.Outcome> pickOutcome(
            List<DialogueEvent.Outcome> eligible, RandomSource random, int pairFatigue, float multiplier
    ) {
        return pickOutcome(eligible, random, pairFatigue, multiplier, true);
    }

    static Optional<DialogueEvent.Outcome> pickOutcome(
            List<DialogueEvent.Outcome> eligible, RandomSource random,
            int pairFatigue, float multiplier, boolean fatigueEnabled
    ) {
        if (eligible.isEmpty()) return Optional.empty();
        int legacyPoints = legacyFatiguePoints(pairFatigue, multiplier, fatigueEnabled);
        ToDoubleFunction<DialogueEvent.Outcome> effectiveWeight = outcome ->
                effectiveOutcomeWeight(outcome, legacyPoints);
        List<DialogueEvent.Outcome> available = eligible.stream()
                .filter(outcome -> effectiveWeight.applyAsDouble(outcome) > 0.0)
                .toList();
        if (available.isEmpty()) {
            return Optional.empty();
        }
        int highest = available.stream().mapToInt(DialogueEvent.Outcome::priority).max().orElseThrow();
        List<DialogueEvent.Outcome> tier = available.stream()
                .filter(outcome -> outcome.priority() == highest)
                .toList();
        return weightedPick(tier, effectiveWeight, random);
    }

    private static int legacyFatiguePoints(int pairFatigue, float multiplier, boolean enabled) {
        float safeMultiplier = Float.isFinite(multiplier) && multiplier > 0.0f ? multiplier : 0.0f;
        return enabled ? (int) (Math.max(0, pairFatigue) * safeMultiplier) : 0;
    }

    private static double effectiveOutcomeWeight(DialogueEvent.Outcome outcome, int legacyPoints) {
        return outcome.weight() + (int) (legacyPoints * outcome.fatigue());
    }

    private static double effectiveEventWeight(DialogueEvent event, int legacyPoints) {
        return event.weight() + (int) (legacyPoints * event.fatigue());
    }

    static <T> Optional<T> weightedPick(List<T> candidates, ToDoubleFunction<T> weight, RandomSource random) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        double total = 0.0;
        for (T candidate : candidates) {
            double value = weight.applyAsDouble(candidate);
            if (!Double.isFinite(value) || value <= 0.0) {
                throw new IllegalArgumentException("Dialogue weight must be finite and positive");
            }
            total += value;
        }
        double roll = random.nextDouble() * total;
        for (T candidate : candidates) {
            roll -= weight.applyAsDouble(candidate);
            if (roll < 0.0) {
                return Optional.of(candidate);
            }
        }
        return Optional.of(candidates.get(candidates.size() - 1));
    }

    private boolean eventEligible(
            DialogueEvent event,
            DialogueContext context,
            DialogueEventHistory history,
            long gameTime
    ) {
        if (!history.writable()) {
            return false;
        }
        if (event.trigger() != DialogueEvent.Trigger.TALK) {
            return false;
        }
        Config config = Config.getInstance();
        int points = legacyFatiguePoints(pairFatigue(context),
                config.interactionChanceFatigue, config.interactionFatigueEnabled);
        if (effectiveEventWeight(event, points) <= 0.0) {
            return false;
        }
        if (!event.requirements().stream().allMatch(condition -> matches(condition, context))) {
            return false;
        }
        return repeatAvailable(
                event,
                history.completed(context.player().getUUID(), context.villager().getUUID(), event.id()),
                history.nextEligibleAt(context.player().getUUID(), context.villager().getUUID(), event.id(), gameTime),
                gameTime
        );
    }

    private DialogueSession refreshOfferedChoices(DialogueSession session, DialogueContext context) {
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        if (node.lines().isEmpty()
                || session.lineIndex() != node.lines().size() - 1
                || node.choices().isEmpty()) {
            return session.withOfferedChoices(List.of());
        }
        List<String> choices = node.choices().orElseThrow().stream()
                .filter(choice -> choiceEligible(choice, context))
                .map(DialogueEvent.Choice::id)
                .toList();
        return session.withOfferedChoices(choices);
    }

    private boolean commit(
            ServerPlayer player,
            VillagerEntityMCA villager,
            DialogueSession session,
            DialogueContext context
    ) {
        return commit(player, villager, session, context, false);
    }

    private boolean commit(
            ServerPlayer player,
            VillagerEntityMCA villager,
            DialogueSession session,
            DialogueContext context,
            boolean deferSlap
    ) {
        if (!context.history().writable()) {
            return false;
        }
        List<DialogueAction.Command> commands = session.pendingEffects().stream()
                .filter(DialogueAction.Command.class::isInstance)
                .map(DialogueAction.Command.class::cast)
                .toList();
        if (commands.size() > 1) {
            return false;
        }
        if (!commands.isEmpty() && !executeCommand(villager, player, session.interactionId(), commands.getFirst())) {
            return false;
        }

        for (DialogueAction action : session.pendingEffects()) {
            if (action instanceof DialogueAction.Command) {
                continue;
            }
            if (action instanceof DialogueAction.Hearts hearts) {
                villager.getVillagerBrain().rewardHearts(player, hearts.amount());
            } else if (action instanceof DialogueAction.Mood mood) {
                villager.getVillagerBrain().modifyMoodValue(mood.amount());
            } else if (action instanceof DialogueAction.Slap slap) {
                if (!deferSlap) {
                    villager.getInteractions().slap(player, slap.amount());
                }
            } else if (action instanceof DialogueAction.Remember remember) {
                String id = remember.playerScoped()
                        ? remember.id() + "." + player.getUUID()
                        : remember.id();
                if (remember.time().isPresent()) {
                    villager.getLongTermMemory().remember(id, remember.time().getAsLong());
                } else {
                    villager.getLongTermMemory().remember(id);
                }
            } else if (action instanceof DialogueAction.Registered<?> registered) {
                registered.execute(context);
            }
        }

        context.history().complete(
                player.getUUID(),
                villager.getUUID(),
                session.event(),
                session.acceptedChoices(),
                overworldTime(player),
                random
        );
        return true;
    }

    private record PendingSlap(ServerPlayer player, VillagerEntityMCA villager, ServerLevel level,
                               long dueTick, float amount, Component reaction) {
    }

    private static boolean executeCommand(
            VillagerEntityMCA villager,
            ServerPlayer player,
            UUID interactionId,
            DialogueAction.Command command
    ) {
        var interactions = villager.getInteractions();
        var result = InteractionCommands.execute(new InteractionCommands.Context(
                player, villager, interactionId, InteractionCommands.Source.DIALOGUE, command.command()));
        if (result.closeScreen()) {
            interactions.closeInteractionScreen(player, interactionId);
        }
        return result.accepted();
    }

    private DialogueNodeView view(DialogueSession session, DialogueContext context) {
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        if (node.lines().isEmpty()) {
            throw new IllegalStateException("Automatic dialogue routing node cannot be rendered: "
                    + session.eventId() + "#" + session.nodeId());
        }
        Component line = pendingRequests.lineFor(DialoguePendingRequests.Key.from(session)).orElseGet(() ->
                context.villager().getTranslatable(context.player(), node.lines().get(session.lineIndex())));
        List<ChoiceView> choices = session.lineIndex() == node.lines().size() - 1
                ? node.choices().orElse(List.of()).stream()
                        .filter(choice -> session.offeredChoices().contains(choice.id()))
                        .map(choice -> new ChoiceView(choice.id(), Component.translatable(choice.text())))
                        .toList()
                : List.of();
        return new DialogueNodeView(
                session.id(),
                session.eventId(),
                session.offerToken(),
                line,
                node.silent(),
                choices,
                advanceKind(session)
        );
    }

    /** Sends a node only when ready; a resolver node first sends an authenticated pending frame. */
    public void sendView(ServerPlayer player, DialogueNodeView view) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || session.status() != DialogueSession.Status.ACTIVE
                || session.generation() != events.generation()
                || !session.id().equals(view.sessionId()) || session.offerToken() != view.offerToken()
                || !session.eventId().equals(view.eventId())) {
            return;
        }
        DialoguePendingRequests.Key key = DialoguePendingRequests.Key.from(session);
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        if (node.resolve().isEmpty()) {
            invalidatePending(player.getUUID());
            Network.sendToPlayer(InteractionDialogueNodeResponse.active(view), player);
            return;
        }
        VillagerEntityMCA villager = resolveBoundVillager(player, session.villagerId()).orElse(null);
        if (villager == null || !canInteract(player, villager)) {
            pauseUnavailable(player, session);
            return;
        }
        DialogueContext context = context(player, villager);
        if (pendingRequests.lineFor(key).isPresent()) {
            Network.sendToPlayer(InteractionDialogueNodeResponse.active(view(session, context)), player);
            return;
        }
        DialogueAsyncResolver resolver = node.resolve().orElseThrow();
        var existing = pendingRequests.pendingFor(key);
        if (existing.isPresent()) {
            Network.sendToPlayer(new InteractionDialoguePendingResponse(session.id(), session.offerToken(),
                    villager.getTranslatable(player, resolver.pendingLine())), player);
            return;
        }
        UUID interactionId = villager.getInteractions().interactionId();
        if (!villager.getInteractions().matchesInteraction(player, interactionId)) {
            pauseUnavailable(player, session);
            return;
        }
        var ticket = new DialoguePendingRequests.Ticket(key, interactionId, UUID.randomUUID(),
                resolver, saturatingAdd(overworldTime(player), ASYNC_TIMEOUT_TICKS));
        pendingRequests.install(ticket).ifPresent(DialogueEngine::cancelOperation);
        // Send before starting provider work: even an already-complete future must produce pending first.
        Network.sendToPlayer(new InteractionDialoguePendingResponse(session.id(), session.offerToken(),
                villager.getTranslatable(player, resolver.pendingLine())), player);
        MinecraftServer server = player.getServer();
        try {
            CompletionStage<DialogueAsyncResolver.Result> stage = Objects.requireNonNull(
                    resolver.resolve(context, ticket.operationId()), "resolver returned null stage");
            stage.whenComplete((result, error) -> server.execute(() -> finishPending(ticket, result, error, server)));
        } catch (RuntimeException error) {
            server.execute(() -> finishPending(ticket, null, error, server));
        }
    }

    private boolean finishPending(DialoguePendingRequests.Ticket ticket,
                               @Nullable DialogueAsyncResolver.Result result,
                               @Nullable Throwable error, MinecraftServer server) {
        DialoguePendingRequests.Key key = ticket.key();
        if (pendingRequests.pendingFor(key).filter(current -> current == ticket).isEmpty()) {
            return false;
        }
        DialogueSession session = sessions.get(key.playerId());
        ServerPlayer player = server.getPlayerList().getPlayer(key.playerId());
        VillagerEntityMCA villager = player == null ? null
                : resolveBoundVillager(player, key.villagerId()).orElse(null);
        if (session == null || !key.equals(DialoguePendingRequests.Key.from(session))
                || session.status() != DialogueSession.Status.ACTIVE
                || session.generation() != events.generation() || player == null || player.hasDisconnected()
                || villager == null || !canInteract(player, villager)
                || !villager.getInteractions().matchesInteraction(player, ticket.interactionId())) {
            invalidatePending(key.playerId());
            return false;
        }
        DialogueEvent.Node node = session.event().nodes().get(session.nodeId());
        String translationKey = result == null || !result.found() || error != null
                ? ticket.resolver().failureLine()
                : result.lineOverride().orElseGet(() -> node.lines().get(session.lineIndex()));
        Component line;
        try {
            Object[] arguments = result != null ? result.arguments().toArray() : new Object[0];
            if (result != null && result.found() && error == null
                    && ticket.resolver() instanceof DialogueAsyncResolver.StructureLocation
                    && arguments.length > 0 && arguments[0] instanceof String coordinates) {
                arguments[0] = styledStructureCoordinates(coordinates);
            }
            line = result != null && result.found() && error == null
                    ? villager.getTranslatable(player, translationKey, arguments)
                    : villager.getTranslatable(player, translationKey);
        } catch (RuntimeException exception) {
            MCA.LOGGER.warn("Failed to localize async dialogue result {}: {}", key, exception.toString());
            line = villager.getTranslatable(player, ticket.resolver().failureLine());
        }
        if (!pendingRequests.finish(ticket, line)) {
            return false;
        }
        Network.sendToPlayer(InteractionDialogueNodeResponse.active(view(session, context(player, villager))), player);
        return true;
    }

    static Component styledStructureCoordinates(String text) {
        return DialogueCoordinates.copyValue(text)
                .<Component>map(copy -> Component.literal(text).setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0x237B80))
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gui.dialogue.copy_coordinates")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy))))
                .orElseGet(() -> Component.literal(text));
    }

    private void invalidatePending(UUID playerId) {
        pendingRequests.invalidate(playerId).ifPresent(DialogueEngine::cancelOperation);
    }

    private void cancelPending(UUID playerId) {
        pendingRequests.cancelPending(playerId).ifPresent(DialogueEngine::cancelOperation);
    }

    private static void cancelOperation(DialoguePendingRequests.Ticket ticket) {
        try {
            ticket.resolver().cancel(ticket.operationId());
        } catch (RuntimeException error) {
            MCA.LOGGER.warn("Async dialogue cancellation failed for {}: {}", ticket.operationId(), error.toString());
        }
    }

    private DialogueSession validActiveSession(ServerPlayer player, long offerToken) {
        DialogueSession session = sessions.get(player.getUUID());
        if (session == null || !session.acceptsOffer(offerToken, events.generation())) {
            invalidateReloaded(player.getUUID(), events.generation());
            return null;
        }
        return session;
    }

    private DialogueContext context(ServerPlayer player, VillagerEntityMCA villager) {
        return new DialogueContext(
                villager,
                player,
                DialogueEventHistory.get(player.serverLevel()),
                id -> events.get(id).isPresent()
        );
    }

    private static boolean matches(DialogueCondition condition, DialogueContext context) {
        return condition.evaluate(context) == DialogueCondition.Evaluation.MATCH;
    }

    private Optional<VillagerEntityMCA> resolveBoundVillager(ServerPlayer player, UUID villagerId) {
        Entity entity = player.serverLevel().getEntity(villagerId);
        return entity instanceof VillagerEntityMCA villager ? Optional.of(villager) : Optional.empty();
    }

    private static boolean canInteract(ServerPlayer player, VillagerEntityMCA villager) {
        return villager.isAlive()
                && !villager.isRemoved()
                && villager.level() == player.level()
                && villager.getInteractions().matchesInteraction(player, villager.getInteractions().interactionId())
                && !villager.isTrading()
                && !villager.getVillagerBrain().isPanicking()
                && player.canInteractWithEntity(villager, 1.0);
    }

    private void pauseUnavailable(ServerPlayer player, DialogueSession session) {
        if (session.status() == DialogueSession.Status.ACTIVE) {
            cancelPending(player.getUUID());
            sessions.put(player.getUUID(), session.pause(saturatingAdd(overworldTime(player), PAUSE_TICKS)));
            offers.remove(player.getUUID());
        }
    }

    private void invalidateReloaded(UUID playerId, long generation) {
        DialogueSession session = sessions.get(playerId);
        if (session != null && session.generation() != generation) {
            invalidatePending(playerId);
            sessions.remove(playerId);
        }
        DialogueOptions offer = offers.get(playerId);
        if (offer != null && offer.generation() != generation) {
            offers.remove(playerId);
        }
    }

    private DialogueSession expirePaused(UUID playerId, long gameTime) {
        DialogueSession session = sessions.get(playerId);
        if (session != null
                && session.status() == DialogueSession.Status.PAUSED
                && gameTime >= session.pauseDeadline()) {
            invalidatePending(playerId);
            sessions.remove(playerId);
            return null;
        }
        return session;
    }

    private static long overworldTime(ServerPlayer player) {
        return Objects.requireNonNull(player.getServer(), "server").overworld().getGameTime();
    }

    private static Entity findLoadedEntity(MinecraftServer server, UUID entityId) {
        for (ServerLevel level : server.getAllLevels()) {
            Entity entity = level.getEntity(entityId);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    private static long saturatingAdd(long value, long delta) {
        if (delta > 0L && value > Long.MAX_VALUE - delta) {
            return Long.MAX_VALUE;
        }
        return value + delta;
    }

    private EventOption option(DialogueEvent event, VillagerEntityMCA villager) {
        String prompt = event.presentation().prompt().orElseThrow();
        String topic = event.presentation().topic()
                .filter(value -> !value.isBlank() && value.length() <= 64)
                .orElse("other");
        return new EventOption(event.id(), Component.translatable(prompt, villager.getDisplayName()), topic);
    }

    private long nextToken() {
        return tokenSequence++;
    }

    static record SelectionPlan(
            Optional<DialogueEvent> highlighted,
            List<DialogueEvent> ask,
            List<DialogueEvent> ambient
    ) {
        SelectionPlan {
            highlighted = Objects.requireNonNull(highlighted, "highlighted");
            ask = List.copyOf(ask);
            ambient = List.copyOf(ambient);
        }
    }

    public record EventOption(ResourceLocation id, Component prompt, String topic) {
        public EventOption {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(prompt, "prompt");
            Objects.requireNonNull(topic, "topic");
        }
    }

    public record AmbientPreview(
            ResourceLocation eventId, Component line, boolean silent,
            AdvanceKind advanceKind, List<ChoiceView> choices
    ) {
        public AmbientPreview {
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(advanceKind, "advanceKind");
            choices = List.copyOf(choices);
        }
    }

    public static final class DialogueOptions {
        private final UUID villagerId;
        private final long generation;
        private final Optional<EventOption> highlighted;
        private final List<EventOption> ask;
        private final boolean ambientAvailable;
        private final Optional<Component> continuationPrompt;
        private final long token;
        private final List<ResourceLocation> ambientCandidates;
        private final Optional<UUID> continuationSessionId;
        private final Optional<AmbientPreview> preview;
        private final UUID interactionId;
        private boolean consumed;

        DialogueOptions(
                UUID villagerId,
                long generation,
                Optional<EventOption> highlighted,
                List<EventOption> ask,
                boolean ambientAvailable,
                Optional<Component> continuationPrompt,
                long token,
                List<ResourceLocation> ambientCandidates,
                Optional<UUID> continuationSessionId
        ) {
            this(villagerId, generation, highlighted, ask, ambientAvailable,
                    continuationPrompt, token, ambientCandidates, continuationSessionId, Optional.empty(), null);
        }

        DialogueOptions(
                UUID villagerId, long generation, Optional<EventOption> highlighted,
                List<EventOption> ask, boolean ambientAvailable,
                Optional<Component> continuationPrompt, long token,
                List<ResourceLocation> ambientCandidates, Optional<UUID> continuationSessionId,
                Optional<AmbientPreview> preview
        ) {
            this(villagerId, generation, highlighted, ask, ambientAvailable, continuationPrompt, token,
                    ambientCandidates, continuationSessionId, preview, null);
        }

        DialogueOptions(
                UUID villagerId, long generation, Optional<EventOption> highlighted,
                List<EventOption> ask, boolean ambientAvailable,
                Optional<Component> continuationPrompt, long token,
                List<ResourceLocation> ambientCandidates, Optional<UUID> continuationSessionId,
                Optional<AmbientPreview> preview, UUID interactionId
        ) {
            this.villagerId = Objects.requireNonNull(villagerId, "villagerId");
            this.generation = generation;
            this.highlighted = Objects.requireNonNull(highlighted, "highlighted");
            this.ask = List.copyOf(ask);
            this.ambientAvailable = ambientAvailable;
            this.continuationPrompt = Objects.requireNonNull(continuationPrompt, "continuationPrompt");
            this.token = token;
            this.ambientCandidates = List.copyOf(ambientCandidates);
            this.continuationSessionId = Objects.requireNonNull(continuationSessionId, "continuationSessionId");
            this.preview = Objects.requireNonNull(preview, "preview");
            this.interactionId = interactionId;
        }

        public UUID villagerId() {
            return villagerId;
        }

        public long generation() {
            return generation;
        }

        public Optional<EventOption> highlighted() {
            return highlighted;
        }

        public List<EventOption> ask() {
            return ask;
        }

        public boolean ambientAvailable() {
            return ambientAvailable;
        }

        public Optional<Component> continuationPrompt() {
            return continuationPrompt;
        }

        public long token() {
            return token;
        }

        public Optional<AmbientPreview> preview() {
            return preview;
        }

        UUID interactionId() {
            return interactionId;
        }

        public boolean consumed() {
            return consumed;
        }

        List<ResourceLocation> ambientCandidates() {
            return ambientCandidates;
        }

        Optional<UUID> continuationSessionId() {
            return continuationSessionId;
        }

        boolean containsEvent(ResourceLocation eventId) {
            return highlighted.map(EventOption::id).filter(eventId::equals).isPresent()
                    || ask.stream().anyMatch(option -> option.id().equals(eventId));
        }

        void consume() {
            consumed = true;
        }
    }

    public record ChoiceView(String id, Component text) {
        public ChoiceView {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(text, "text");
        }
    }

    public record DialogueNodeView(
            UUID sessionId,
            ResourceLocation eventId,
            long offerToken,
            Component line,
            boolean silent,
            List<ChoiceView> choices,
            AdvanceKind advanceKind
    ) {
        public DialogueNodeView {
            Objects.requireNonNull(sessionId, "sessionId");
            Objects.requireNonNull(eventId, "eventId");
            Objects.requireNonNull(line, "line");
            Objects.requireNonNull(advanceKind, "advanceKind");
            choices = List.copyOf(choices);
        }

        public boolean canContinue() {
            return advanceKind != AdvanceKind.NONE;
        }
    }

    private static void notifyPaused(ServerPlayer player, DialogueSession session) {
        Network.sendToPlayer(
                InteractionDialogueNodeResponse.paused(session.id(), session.offerToken()),
                player
        );
    }

    private static void notifyEnded(ServerPlayer player, DialogueSession session) {
        Network.sendToPlayer(
                InteractionDialogueNodeResponse.ended(session.id(), session.offerToken()),
                player
        );
    }

    @Nullable
    private DialogueSession resolveAutomaticTransitions(DialogueSession session, DialogueContext context) {
        return resolveAutomaticTransitions(
                session,
                condition -> matches(condition, context),
                random,
                this::nextToken,
                context.villager().getVillagerBrain().getMemoriesForPlayer(context.player()).getInteractionFatigue(),
                Config.getInstance().interactionChanceFatigue,
                Config.getInstance().interactionFatigueEnabled
        );
    }

    public enum AdvanceKind {
        NONE,
        NEXT,
        BACK_TO_TOPICS
    }

    public enum DialogueSelection {
        RESUME,
        EVENT,
        AMBIENT
    }

    public enum TransitionStatus {
        REJECTED,
        PAUSED,
        ADVANCED,
        COMPLETED,
        ENDED
    }

    public record TransitionResult(
            TransitionStatus status,
            Optional<UUID> sessionId,
            Optional<DialogueNodeView> view
    ) {
        public TransitionResult {
            Objects.requireNonNull(status, "status");
            sessionId = Objects.requireNonNull(sessionId, "sessionId");
            view = Objects.requireNonNull(view, "view");
            if (view.isPresent()
                    && (sessionId.isEmpty() || !sessionId.orElseThrow().equals(view.orElseThrow().sessionId()))) {
                throw new IllegalArgumentException("Node view must match transition session identity");
            }
        }

        public boolean accepted() {
            return status != TransitionStatus.REJECTED && status != TransitionStatus.PAUSED;
        }

        public boolean completed() {
            return status == TransitionStatus.COMPLETED;
        }

        public boolean paused() {
            return status == TransitionStatus.PAUSED;
        }

        public boolean ended() {
            return status == TransitionStatus.ENDED;
        }

        static TransitionResult rejected() {
            return new TransitionResult(TransitionStatus.REJECTED, Optional.empty(), Optional.empty());
        }

        static TransitionResult rejected(DialogueNodeView view) {
            return new TransitionResult(TransitionStatus.REJECTED, Optional.of(view.sessionId()), Optional.of(view));
        }

        static TransitionResult paused(UUID sessionId) {
            return new TransitionResult(TransitionStatus.PAUSED, Optional.of(sessionId), Optional.empty());
        }

        static TransitionResult advanced(DialogueNodeView view) {
            return new TransitionResult(TransitionStatus.ADVANCED, Optional.of(view.sessionId()), Optional.of(view));
        }

        static TransitionResult completion(UUID sessionId) {
            return new TransitionResult(TransitionStatus.COMPLETED, Optional.of(sessionId), Optional.empty());
        }

        static TransitionResult ending(UUID sessionId) {
            return new TransitionResult(TransitionStatus.ENDED, Optional.of(sessionId), Optional.empty());
        }
    }
}
