package net.mca.entity.ai.chatAI.inworldAIModules.api;


public record Interaction(String name,
                          String[] textList,
                          Emotion emotion,
                          String sessionId,
                          RelationshipUpdate relationshipUpdate,
                          TriggerEvent[] activeTriggers,
                          TriggerEvent[] outgoingTriggers) {

    public record Emotion(SpaffCode behaviour, Strength strength) {
        public enum SpaffCode {
            SPAFF_CODE_UNSPECIFIED,
            NEUTRAL,
            DISGUST,
            CONTEMPT,
            BELLIGERENCE,
            DOMINEERING,
            CRITICISM,
            ANGER,
            TENSION,
            TENSE_HUMOR,
            DEFENSIVENESS,
            WHINING,
            SADNESS,
            STONEWALLING,
            INTEREST,
            VALIDATION,
            AFFECTION,
            HUMOR,
            SURPRISE,
            JOY
        }

        public enum Strength {
            STRENGTH_UNSPECIFIED,
            WEAK,
            STRONG,
            NORMAL
        }
    }

    public record RelationshipUpdate(int trust, int respect, int familiar, int flirtatious, int attraction) {}

}