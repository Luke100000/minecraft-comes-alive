package net.conczin.mca.client.gui;

enum DialogueTopicSection {
    CONVERSATIONS("gui.dialogue.section.conversations"),
    RELATIONSHIPS("gui.dialogue.section.relationships"),
    PERSONAL_STORIES("gui.dialogue.section.personal_stories"),
    PRACTICAL_MATTERS("gui.dialogue.section.practical_matters"),
    OTHER_TOPICS("gui.dialogue.section.other_topics");

    final String label;

    DialogueTopicSection(String label) {
        this.label = label;
    }

    static DialogueTopicSection fromTopic(String topic) {
        return switch (topic) {
            case "small_talk", "location", "game" -> CONVERSATIONS;
            case "relationship" -> RELATIONSHIPS;
            case "personal", "life_event", "introduction" -> PERSONAL_STORIES;
            case "service" -> PRACTICAL_MATTERS;
            default -> OTHER_TOPICS;
        };
    }
}
