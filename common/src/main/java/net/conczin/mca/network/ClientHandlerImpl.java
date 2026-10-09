package net.conczin.mca.network;

import net.conczin.mca.Config;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.CivilRegistryBook;
import net.conczin.mca.client.gui.*;
import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.item.BabyItem;
import net.conczin.mca.item.ExtendedWrittenBookItem;
import net.conczin.mca.network.s2c.*;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.UnaryOperator;

public class ClientHandlerImpl implements ClientHandler {
    private final Minecraft client = Minecraft.getInstance();
    private final DialoguePresentation dialoguePresentation = new DialoguePresentation();

    public DialoguePresentation dialoguePresentation() {
        return dialoguePresentation;
    }

    @Override
    public void handleGuiRequest(OpenGuiRequest message) {
        Entity entity;
        assert client.level != null;
        assert Minecraft.getInstance().player != null;
        switch (message.getGui()) {
            case WHISTLE:
                client.setScreen(new WhistleScreen());
                break;
            case BOOK:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (item.getItem() instanceof ExtendedWrittenBookItem bookItem) {
                        Book book = bookItem.getBook(item);
                        client.setScreen(new ExtendedBookScreen(book));
                    }
                }
                break;
            case BLUEPRINT:
                client.setScreen(new BlueprintScreen());
                break;
            case INTERACT:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    boolean isOnBlacklist = Config.getInstance().villagerInteractionItemBlacklist.contains(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
                    if (!isOnBlacklist) {
                        VillagerLike<?> villager = (VillagerLike<?>) client.level.getEntity(message.villager());
                        client.setScreen(new InteractScreen(villager, message.interactionId()));
                    }
                }
                break;
            case VILLAGER_EDITOR:
                entity = client.level.getEntity(message.villager());
                assert entity != null;
                client.setScreen(new VillagerEditorScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                break;
            case LIMITED_VILLAGER_EDITOR:
                entity = client.level.getEntity(message.villager());
                assert entity != null;
                client.setScreen(new LimitedVillagerEditorScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                break;
            case NEEDLE_AND_THREAD:
                entity = client.level.getEntity(message.villager());
                if (entity == null) {
                    client.setScreen(new NeedleScreen(Minecraft.getInstance().player.getUUID()));
                } else {
                    client.setScreen(new NeedleScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                }
                break;
            case COMB:
                entity = client.level.getEntity(message.villager());
                if (entity == null) {
                    client.setScreen(new CombScreen(Minecraft.getInstance().player.getUUID()));
                } else {
                    client.setScreen(new CombScreen(entity.getUUID(), Minecraft.getInstance().player.getUUID()));
                }
                break;
            case BABY_NAME:
                if (client.player != null) {
                    ItemStack item = client.player.getItemInHand(InteractionHand.MAIN_HAND);
                    if (item.getItem() instanceof BabyItem) {
                        client.setScreen(new NameBabyScreen(client.player, item));
                    }
                }
                break;
            case FAMILY_TREE:
                client.setScreen(new FamilyTreeSearchScreen());
                break;
            case VILLAGER_TRACKER:
                client.setScreen(new VillagerTrackerSearchScreen());
                break;
            default:
        }
    }

    @Override
    public void handleChatAIContextResponse(ChatAIContextResponse message) {
        client.setScreen(new ChatAIContextScreen(message));
    }

    @Override
    public void handleFamilyTreeResponse(GetFamilyTreeResponse message) {
        Screen screen = client.screen;
        if (screen instanceof FamilyTreeScreen gui) {
            gui.acceptFamilyData(message);
        }
    }

    @Override
    public void handleInteractDataResponse(GetInteractDataResponse message) {
        Screen screen = client.screen;
        if (screen instanceof InteractScreen gui) {
            gui.setConstraints(message.constraints());
            gui.setParents(message.father().orElse(null), message.mother().orElse(null));
            gui.setSpouse(message.marriageState(), message.spouse().orElse(null));
        }
    }

    @Override
    public void handleVillageDataResponse(GetVillageResponse message) {
        Screen screen = client.screen;
        if (screen instanceof BlueprintScreen gui) {
            BuildingTypes.getInstance().setBuildingTypes(message.buildingTypes());

            Village village = new Village(message.getData(), null);
            gui.setVillage(village);
            gui.setVillageData(message.rank(), message.reputation(), message.isVillage(), message.ids(), message.tasks());
        }
    }

    @Override
    public void handleVillageDataFailedResponse(GetVillageFailedResponse message) {
        Screen screen = client.screen;
        if (screen instanceof BlueprintScreen gui) {
            gui.setVillage(null);
        }
    }

    @Override
    public void handleFamilyDataResponse(GetFamilyResponse message) {
        Screen screen = client.screen;
        if (screen instanceof WhistleScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleVillagerDataResponse(GetVillagerResponse message) {
        Screen screen = client.screen;
        if (screen instanceof VillagerEditorScreen gui) {
            gui.setVillagerData(message.getData());
        }
    }

    @Override
    public void handleDialogueOptionsResponse(InteractionDialogueOptionsResponse message) {
        if (client.screen instanceof InteractScreen gui && gui.isDialogueMode()) {
            Locale locale = Locale.forLanguageTag(client.options.languageCode.replace('_', '-'));
            boolean silent = message.preview().map(InteractionDialogueNodeResponse.Node::silent).orElse(true);
            dialoguePresentation.acceptOptions(message, System.nanoTime() / 1_000_000L,
                    locale, line -> gui.resolveDialogueLine(line, silent));
        }
    }

    public void handleDialogueSelectionRejectedResponse(InteractionDialogueSelectionRejectedResponse message) {
        if (client.screen instanceof InteractScreen gui
                && gui.isDialogueMode()
                && dialoguePresentation.acceptSelectionRejection(message.offerToken())) {
            gui.requestDialogueMenu();
        }
    }

    @Override
    public void handleDialogueNodeResponse(InteractionDialogueNodeResponse message) {
        Locale locale = Locale.forLanguageTag(client.options.languageCode.replace('_', '-'));
        InteractScreen gui = client.screen instanceof InteractScreen interactScreen ? interactScreen : null;
        boolean visibleInteraction = gui != null && gui.isDialogueMode();
        if (!visibleInteraction && message.state() == InteractionDialogueNodeResponse.State.ACTIVE) {
            return;
        }
        UnaryOperator<Component> resolver = UnaryOperator.identity();
        if (visibleInteraction && message.state() == InteractionDialogueNodeResponse.State.ACTIVE) {
            boolean silent = message.node().orElseThrow().silent();
            resolver = line -> gui.resolveDialogueLine(line, silent);
        }

        boolean accepted = dialoguePresentation.acceptNode(message, System.nanoTime() / 1_000_000L, locale, resolver);
        if (!accepted) {
            return;
        }
        if (message.state() == InteractionDialogueNodeResponse.State.ENDED) {
            if (visibleInteraction) {
                gui.requestDialogueMenu();
            }
        } else if (message.state() == InteractionDialogueNodeResponse.State.PAUSED) {
            if (visibleInteraction) {
                gui.leaveDialogueMode(false);
            }
        }
    }

    @Override
    public void handleSkinListResponse(AnalysisResults message) {
        InteractScreen.setAnalysis(message.analysis());
    }

    @Override
    public void handleBabyNameResponse(BabyNameResponse message) {
        Screen screen = client.screen;
        if (screen instanceof NameBabyScreen gui) {
            gui.setBabyName(message.name());
        }
    }

    @Override
    public void handleVillagerNameResponse(VillagerNameResponse message) {
        Screen screen = client.screen;
        if (screen instanceof VillagerEditorScreen gui) {
            gui.setVillagerName(message.name());
        }
    }

    @Override
    public void handleToastMessage(ShowToastRequest message) {
        SystemToast.add(client.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, message.getTitle(), message.getMessage());
    }

    @Override
    public void handleFamilyTreeUUIDResponse(FamilyTreeUUIDResponse response) {
        Screen screen = client.screen;
        if (screen instanceof FamilyTreeScreen gui) {
            gui.setSearchResults(response.requestId(), response.search(), response.list());
        } else if (screen instanceof FamilyTreeSearchScreen gui) {
            gui.setList(response.requestId(), response.search(), response.list());
        }
    }

    @Override
    public void handlePlayerDataMessage(PlayerDataMessage response) {
        assert client.level != null;
        VillagerEntityMCA villager = EntitiesMCA.MALE_VILLAGER.create(client.level);
        assert villager != null;
        villager.readAdditionalSaveData(response.nbt());
        MCAClient.addPlayerData(response.uuid(), villager);
    }

    @Override
    public void handleCustomSkinListResponse(CustomSkinListResponse message) {
        Screen screen = client.screen;
        ClientSkinCatalog.installServerDelta(message.clothing(), message.bodySkins(), message.layeredHair(), message.hairStyles(), message.hair());
        if (screen instanceof SkinListUpdateListener gui) {
            gui.skinListUpdatedCallback();
        }
    }

    @Override
    public void handleDestinyGuiRequest(OpenDestinyGuiRequest message) {
        MCAClient.getDestinyManager().requestOpen(message.allowTeleportation(), message.destinations());
    }

    @Override
    public void handleConfigResponse(ConfigResponse message) {
        Config.setServerConfig(message.getConfig());
        MCAClient.refreshPlayerDataDependentDimensions();
    }

    @Override
    public void handleVillagerMessage(VillagerMessage message) {
        MutableComponent full = message.prefix().copy().append(message.message());
        client.getChatListener().handleSystemMessage(full, false);
        SpeechManager.INSTANCE.onChatMessage(message.message(), message.uuid());
    }

    @Override
    public void handleCustomSkinsChangedMessage(CustomSkinsChangedMessage message) {
        ClientSkinCatalog.markCustomSkinsOutdated();
        if (client.screen instanceof SkinListUpdateListener) {
            ClientSkinCatalog.sync();
        }
    }

    @Override
    public void handleCivilRegistryResponse(CivilRegistryResponse response) {
        Screen screen = client.screen;
        if (screen instanceof ExtendedBookScreen extendedBookScreen && (extendedBookScreen.getBook() instanceof CivilRegistryBook civilRegistryBook)) {
            civilRegistryBook.receive(response.getIndex(), response.getLines());
        }
    }

    @Override
    public void handleBuildingPolymorph(BuildingPolymorphMessage message) {
        client.setScreen(new BuildingPolymorphScreen(
                message.matchingTypes(), message.scanPos(), message.action(),
                message.expectedTargetId(), client.screen));
    }

    /**
     * Bounded client-only presentation state for the server-authoritative dialogue run.
     * It deliberately retains only one menu, one session snapshot and one resolved line.
     */
    public static final class DialoguePresentation {
        public static final long CHARACTER_REVEAL_MILLIS = 40L;

        private InteractionDialogueOptionsResponse options;
        private UUID sessionId;
        private UUID terminatedSessionId;
        private long offerToken;
        private long latestToken;
        private boolean hasLatestToken;
        private InteractionDialogueNodeResponse.Node node;
        private Component resolvedLine = Component.empty();
        private List<Integer> revealCuts = List.of();
        private int revealedCharacters;
        private long nextRevealAt;
        private Component previewLine = Component.empty();
        private List<Integer> previewCuts = List.of();
        private int previewRevealed;
        private long previewNextRevealAt;
        private boolean nodeVisible;
        private boolean menuMode;
        private DialogueEngine.DialogueSelection pendingSelection;

        public boolean acceptOptions(InteractionDialogueOptionsResponse response) {
            return acceptOptions(response, 0L, Locale.ENGLISH, UnaryOperator.identity());
        }

        public boolean acceptOptions(
                InteractionDialogueOptionsResponse response, long nowMillis,
                Locale locale, UnaryOperator<Component> lineResolver
        ) {
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(lineResolver, "lineResolver");
            if ((!menuMode && pendingSelection == null)
                    || nodeVisible || !acceptsNewToken(response.offerToken())) {
                return false;
            }
            options = response;
            response.preview().ifPresent(preview -> {
                // Preserve the paused session's own line/reveal snapshot separately.
                ResolvedLine resolved = resolveOnce(lineResolver.apply(preview.line()));
                previewLine = resolved.component();
                previewCuts = revealCuts(resolved.text(), locale);
                previewRevealed = 0;
                previewNextRevealAt = saturatingAdd(nowMillis, CHARACTER_REVEAL_MILLIS);
            });
            if (response.preview().isEmpty()) {
                previewLine = Component.empty();
                previewCuts = List.of();
                previewRevealed = 0;
            }
            menuMode = true;
            pendingSelection = null;
            rememberToken(response.offerToken());
            return true;
        }

        public boolean acceptSelectionRejection(long rejectedToken) {
            return pendingSelection != null
                    && options != null
                    && !nodeVisible
                    && options.offerToken() == rejectedToken;
        }

        public boolean acceptNode(InteractionDialogueNodeResponse response, long nowMillis, Locale locale) {
            return acceptNode(response, nowMillis, locale, UnaryOperator.identity());
        }

        public boolean acceptNode(
                InteractionDialogueNodeResponse response,
                long nowMillis,
                Locale locale,
                UnaryOperator<Component> lineResolver
        ) {
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(locale, "locale");
            Objects.requireNonNull(lineResolver, "lineResolver");

            if (response.state() != InteractionDialogueNodeResponse.State.ACTIVE) {
                return acceptTerminal(response);
            }
            if (menuMode && pendingSelection == null) {
                return false;
            }
            if (terminatedSessionId != null && terminatedSessionId.equals(response.sessionId())) {
                return false;
            }

            boolean sameSession = sessionId != null && sessionId.equals(response.sessionId());
            boolean sameOffer = sameSession && offerToken == response.offerToken();
            boolean replacingSession = sessionId != null && !sameSession;
            if (replacingSession
                    && (pendingSelection == null || pendingSelection == DialogueEngine.DialogueSelection.RESUME)) {
                return false;
            }
            if (!sameOffer && !acceptsNewToken(response.offerToken())) {
                return false;
            }

            InteractionDialogueNodeResponse.Node incoming = response.node().orElseThrow();
            boolean preserveResume = sameSession
                    && pendingSelection == DialogueEngine.DialogueSelection.RESUME
                    && node != null;
            boolean preserveCurrentLine = sameOffer && node != null;

            sessionId = response.sessionId();
            offerToken = response.offerToken();
            node = incoming;
            options = null;
            nodeVisible = true;
            menuMode = false;
            rememberToken(response.offerToken());

            if (preserveResume) {
                nextRevealAt = saturatingAdd(nowMillis, CHARACTER_REVEAL_MILLIS);
            } else if (!preserveCurrentLine) {
                ResolvedLine resolved = resolveOnce(lineResolver.apply(incoming.line()));
                resolvedLine = resolved.component();
                revealCuts = revealCuts(resolved.text(), locale);
                revealedCharacters = 0;
                nextRevealAt = saturatingAdd(nowMillis, CHARACTER_REVEAL_MILLIS);
            }

            pendingSelection = null;
            return true;
        }

        private boolean acceptTerminal(InteractionDialogueNodeResponse response) {
            if (sessionId == null
                    || !sessionId.equals(response.sessionId())
                    || offerToken != response.offerToken()) {
                return false;
            }

            options = null;
            nodeVisible = false;
            pendingSelection = null;
            if (response.state() == InteractionDialogueNodeResponse.State.ENDED) {
                terminatedSessionId = sessionId;
                dropSnapshot();
            }
            return true;
        }

        public void tick(long nowMillis) {
            if ((!nodeVisible && !hasPreview()) || (nodeVisible ? isFullyRevealed()
                    : previewRevealed >= previewCuts.size())) {
                return;
            }
            if (!nodeVisible) {
                if (nowMillis >= previewNextRevealAt) {
                    previewRevealed++;
                    previewNextRevealAt = saturatingAdd(nowMillis, CHARACTER_REVEAL_MILLIS);
                }
                return;
            }
            if (nowMillis >= nextRevealAt) {
                revealedCharacters++;
                long scheduledNext = saturatingAdd(nextRevealAt, CHARACTER_REVEAL_MILLIS);
                nextRevealAt = nowMillis >= scheduledNext
                        ? saturatingAdd(nowMillis, CHARACTER_REVEAL_MILLIS)
                        : scheduledNext;
            }
        }

        public void pause() {
            options = null;
            nodeVisible = false;
            menuMode = false;
            pendingSelection = null;
        }

        public void beginRequest() {
            options = null;
            nodeVisible = false;
            menuMode = true;
            pendingSelection = null;
        }

        public void dismissOptions() {
            options = null;
            menuMode = false;
            pendingSelection = null;
        }

        public void markSelectionRequested(DialogueEngine.DialogueSelection selection) {
            pendingSelection = Objects.requireNonNull(selection, "selection");
            menuMode = false;
        }

        public Optional<InteractionDialogueOptionsResponse> options() {
            return Optional.ofNullable(options);
        }

        public Optional<UUID> sessionId() {
            // A Talk menu has no active session, even if a resumable snapshot is retained.
            // Only an explicit Resume selection promotes that snapshot back to the active view.
            return menuMode ? Optional.empty() : Optional.ofNullable(sessionId);
        }

        public OptionalLong offerToken() {
            if (menuMode && options == null) {
                return OptionalLong.empty();
            }
            return sessionId != null || options != null
                    ? OptionalLong.of(options != null ? options.offerToken() : offerToken)
                    : OptionalLong.empty();
        }

        public Optional<InteractionDialogueNodeResponse.Node> node() {
            return nodeVisible ? Optional.ofNullable(node) : Optional.empty();
        }

        public boolean nodeVisible() {
            return nodeVisible;
        }

        public boolean hasPreview() {
            return options != null && options.preview().isPresent();
        }

        public boolean canEngagePreview() {
            return hasPreview() && menuMode && pendingSelection == null;
        }

        public Component fullLine() {
            return hasPreview() && !nodeVisible ? previewLine : resolvedLine;
        }

        public Component visibleLine() {
            if (hasPreview() && !nodeVisible) {
                if (previewRevealed == 0 || previewLine.getString().isEmpty()) {
                    return Component.empty();
                }
                return previewRevealed >= previewCuts.size()
                        ? previewLine : head(previewLine, previewCuts.get(previewRevealed - 1));
            }
            if ((!nodeVisible && !hasPreview()) || resolvedLine.getString().isEmpty()) {
                return Component.empty();
            }
            if (revealedCharacters <= 0) {
                return Component.empty();
            }
            if (isFullyRevealed()) {
                return resolvedLine;
            }
            return head(resolvedLine, revealCuts.get(revealedCharacters - 1));
        }

        public boolean canAdvance() {
            return advanceKind().isPresent();
        }

        public Optional<DialogueEngine.AdvanceKind> advanceKind() {
            if (!(hasPreview() && !nodeVisible ? previewRevealed >= previewCuts.size() : isFullyRevealed())) {
                return Optional.empty();
            }
            DialogueEngine.AdvanceKind kind = nodeVisible && node != null
                    ? node.advanceKind()
                    : options == null ? DialogueEngine.AdvanceKind.NONE
                    : options.preview().map(InteractionDialogueNodeResponse.Node::advanceKind)
                            .orElse(DialogueEngine.AdvanceKind.NONE);
            return kind == DialogueEngine.AdvanceKind.NONE ? Optional.empty() : Optional.of(kind);
        }

        public List<InteractionDialogueNodeResponse.Choice> visibleChoices() {
            if (!(hasPreview() && !nodeVisible ? previewRevealed >= previewCuts.size() : isFullyRevealed())) {
                return List.of();
            }
            if (nodeVisible && node != null) {
                return node.choices();
            }
            return options == null ? List.of() : options.preview()
                    .map(InteractionDialogueNodeResponse.Node::choices).orElse(List.of());
        }

        public void clear() {
            options = null;
            previewLine = Component.empty();
            previewCuts = List.of();
            previewRevealed = 0;
            terminatedSessionId = null;
            hasLatestToken = false;
            latestToken = 0L;
            menuMode = false;
            pendingSelection = null;
            dropSnapshot();
        }

        private void dropSnapshot() {
            sessionId = null;
            offerToken = 0L;
            node = null;
            resolvedLine = Component.empty();
            revealCuts = List.of();
            revealedCharacters = 0;
            nextRevealAt = 0L;
            nodeVisible = false;
        }

        private boolean isFullyRevealed() {
            return revealedCharacters >= revealCuts.size();
        }

        private boolean acceptsNewToken(long candidate) {
            return !hasLatestToken || isNewerToken(candidate, latestToken);
        }

        private void rememberToken(long token) {
            latestToken = token;
            hasLatestToken = true;
        }

        private static boolean isNewerToken(long candidate, long current) {
            return candidate != current && candidate - current > 0L;
        }

        private static long saturatingAdd(long value, long delta) {
            if (delta > 0L && value > Long.MAX_VALUE - delta) {
                return Long.MAX_VALUE;
            }
            return value + delta;
        }

        private static ResolvedLine resolveOnce(Component source) {
            MutableComponent resolved = Component.empty();
            StringBuilder plain = new StringBuilder();
            source.visit((style, text) -> {
                if (!text.isEmpty()) {
                    resolved.append(Component.literal(text).setStyle(style));
                    plain.append(text);
                }
                return Optional.empty();
            }, Style.EMPTY);
            return new ResolvedLine(resolved, plain.toString());
        }

        private static List<Integer> revealCuts(String text, Locale locale) {
            if (text.isEmpty()) {
                return List.of();
            }
            BreakIterator iterator = BreakIterator.getCharacterInstance(locale);
            iterator.setText(text);
            List<Integer> cuts = new ArrayList<>();
            iterator.first();
            for (int end = iterator.next(); end != BreakIterator.DONE; end = iterator.next()) {
                cuts.add(end);
            }
            return List.copyOf(cuts);
        }

        private static Component head(Component source, int utf16Length) {
            MutableComponent result = Component.empty();
            int[] remaining = {utf16Length};
            source.visit((style, text) -> {
                if (remaining[0] <= 0) {
                    return Optional.of(Boolean.TRUE);
                }
                int take = Math.min(remaining[0], text.length());
                if (take > 0) {
                    result.append(Component.literal(text.substring(0, take)).setStyle(style));
                    remaining[0] -= take;
                }
                return remaining[0] <= 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
            }, Style.EMPTY);
            return result;
        }

        private record ResolvedLine(Component component, String text) {
        }
    }
}
