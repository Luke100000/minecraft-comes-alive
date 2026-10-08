package net.conczin.mca.dialogue;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Transient server-owned dialogue state. It deliberately retains no live entity references. */
public record DialogueSession(
        UUID playerId,
        UUID villagerId,
        UUID id,
        long generation,
        long offerToken,
        Status status,
        long pauseDeadline,
        DialogueEvent event,
        String nodeId,
        int lineIndex,
        List<String> offeredChoices,
        Set<String> acceptedChoices,
        Map<String, Integer> selectedOutcomes,
        List<DialogueAction> pendingEffects,
        Long previousOfferToken
) {
    public enum Status {
        ACTIVE,
        PAUSED
    }

    public DialogueSession {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(villagerId, "villagerId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(nodeId, "nodeId");
        if (lineIndex < 0) {
            throw new IllegalArgumentException("Dialogue line index must be nonnegative");
        }
        offeredChoices = List.copyOf(offeredChoices);
        acceptedChoices = Set.copyOf(acceptedChoices);
        selectedOutcomes = Map.copyOf(selectedOutcomes);
        pendingEffects = List.copyOf(pendingEffects);
    }

    public DialogueSession(
            UUID playerId, UUID villagerId, UUID id, long generation, long offerToken,
            Status status, long pauseDeadline, DialogueEvent event, String nodeId,
            int lineIndex, List<String> offeredChoices, Set<String> acceptedChoices,
            Map<String, Integer> selectedOutcomes, List<DialogueAction> pendingEffects
    ) {
        this(playerId, villagerId, id, generation, offerToken, status, pauseDeadline,
                event, nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, pendingEffects, null);
    }

    public static DialogueSession start(
            UUID playerId,
            UUID villagerId,
            UUID sessionId,
            long generation,
            long offerToken,
            DialogueEvent event
    ) {
        return start(playerId, villagerId, sessionId, generation, offerToken, null, event);
    }

    public static DialogueSession start(
            UUID playerId, UUID villagerId, UUID sessionId, long generation,
            long offerToken, Long previousOfferToken, DialogueEvent event
    ) {
        return new DialogueSession(
                playerId,
                villagerId,
                sessionId,
                generation,
                offerToken,
                Status.ACTIVE,
                0L,
                event,
                event.start(),
                0,
                List.of(),
                Set.of(),
                Map.of(),
                List.of(),
                previousOfferToken
        );
    }

    public net.minecraft.resources.ResourceLocation eventId() {
        return event.id();
    }

    public DialogueSession withProgress(String nodeId, int lineIndex, List<String> offeredChoices, long offerToken) {
        return new DialogueSession(
                playerId, villagerId, id, generation, offerToken, status, pauseDeadline, event,
                nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, pendingEffects,
                this.offerToken == offerToken ? previousOfferToken : Long.valueOf(this.offerToken)
        );
    }

    public DialogueSession withOfferedChoices(List<String> offeredChoices) {
        return new DialogueSession(
                playerId, villagerId, id, generation, offerToken, status, pauseDeadline, event,
                nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, pendingEffects, previousOfferToken
        );
    }

    public DialogueSession withAcceptedChoice(String choiceId) {
        LinkedHashSet<String> choices = new LinkedHashSet<>(acceptedChoices);
        choices.add(Objects.requireNonNull(choiceId, "choiceId"));
        return new DialogueSession(
                playerId, villagerId, id, generation, offerToken, status, pauseDeadline, event,
                nodeId, lineIndex, offeredChoices, choices, selectedOutcomes, pendingEffects, previousOfferToken
        );
    }

    public DialogueSession withSelectedOutcome(String choiceId, int outcomeIndex) {
        if (outcomeIndex < 0) {
            throw new IllegalArgumentException("Dialogue outcome index must be nonnegative");
        }
        LinkedHashMap<String, Integer> outcomes = new LinkedHashMap<>(selectedOutcomes);
        outcomes.put(Objects.requireNonNull(choiceId, "choiceId"), outcomeIndex);
        return new DialogueSession(
                playerId, villagerId, id, generation, offerToken, status, pauseDeadline, event,
                nodeId, lineIndex, offeredChoices, acceptedChoices, outcomes, pendingEffects, previousOfferToken
        );
    }

    public DialogueSession withPendingEffects(List<DialogueAction> effects) {
        return new DialogueSession(
                playerId, villagerId, id, generation, offerToken, status, pauseDeadline, event,
                nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, effects, previousOfferToken
        );
    }

    public DialogueSession appendPendingEffects(List<DialogueAction> effects) {
        if (effects.isEmpty()) {
            return this;
        }
        java.util.ArrayList<DialogueAction> combined = new java.util.ArrayList<>(pendingEffects);
        combined.addAll(effects);
        return withPendingEffects(combined);
    }

    public DialogueSession pause(long deadline) {
        if (status == Status.PAUSED) {
            return this;
        }
        if (deadline < 0L) {
            throw new IllegalArgumentException("Dialogue pause deadline must be nonnegative");
        }
        return new DialogueSession(playerId, villagerId, id, generation, offerToken, Status.PAUSED,
                deadline, event, nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, pendingEffects, previousOfferToken);
    }

    public DialogueSession resume(long freshOfferToken) {
        return resume(freshOfferToken, null);
    }

    public DialogueSession resume(long freshOfferToken, Long previousMenuToken) {
        if (status != Status.PAUSED) {
            throw new IllegalStateException("Only a paused dialogue can resume");
        }
        if (freshOfferToken == offerToken
                || Objects.equals(previousOfferToken, freshOfferToken)
                || Objects.equals(previousMenuToken, freshOfferToken)) {
            throw new IllegalArgumentException("Resumed dialogue must use a fresh offer token");
        }
        return new DialogueSession(playerId, villagerId, id, generation, freshOfferToken, Status.ACTIVE,
                0L, event, nodeId, lineIndex, offeredChoices, acceptedChoices, selectedOutcomes, pendingEffects, previousMenuToken);
    }

    public boolean canResume(long gameTime, long currentGeneration, UUID targetVillager) {
        return status == Status.PAUSED
                && generation == currentGeneration
                && villagerId.equals(targetVillager)
                && gameTime < pauseDeadline;
    }

    public boolean acceptsOffer(long token, long currentGeneration) {
        return status == Status.ACTIVE && generation == currentGeneration && offerToken == token;
    }
}
