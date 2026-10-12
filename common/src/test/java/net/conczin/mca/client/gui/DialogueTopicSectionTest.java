package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DialogueTopicSectionTest {
    @Test
    void eventTopicsMapToPlayerVisibleSections() {
        assertEquals(DialogueTopicSection.CONVERSATIONS, DialogueTopicSection.fromTopic("small_talk"));
        assertEquals(DialogueTopicSection.CONVERSATIONS, DialogueTopicSection.fromTopic("location"));
        assertEquals(DialogueTopicSection.CONVERSATIONS, DialogueTopicSection.fromTopic("game"));
        assertEquals(DialogueTopicSection.RELATIONSHIPS, DialogueTopicSection.fromTopic("relationship"));
        assertEquals(DialogueTopicSection.PERSONAL_STORIES, DialogueTopicSection.fromTopic("personal"));
        assertEquals(DialogueTopicSection.PERSONAL_STORIES, DialogueTopicSection.fromTopic("life_event"));
        assertEquals(DialogueTopicSection.PERSONAL_STORIES, DialogueTopicSection.fromTopic("introduction"));
        assertEquals(DialogueTopicSection.PRACTICAL_MATTERS, DialogueTopicSection.fromTopic("service"));
        assertEquals(DialogueTopicSection.OTHER_TOPICS, DialogueTopicSection.fromTopic("other"));
        assertEquals(DialogueTopicSection.OTHER_TOPICS, DialogueTopicSection.fromTopic("addon_topic"));
    }
}
