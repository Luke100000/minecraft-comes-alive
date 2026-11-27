package net.conczin.mca.client.tts;

import net.minecraft.client.Minecraft;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SpeechManagerVoiceRequestTest {
    @Test
    void completingManyDifferentSpeakersReleasesOutstandingRequestTokens() throws Exception {
        SpeechManager manager = new SpeechManager();
        for (int i = 0; i < 300; i++) {
            UUID speaker = UUID.randomUUID();
            long request = manager.beginVoiceRequest(speaker);
            finish(manager, speaker, request);
        }

        assertTrue(tokens(manager).isEmpty(), "Completed voice requests must not accumulate by speaker");
    }

    @Test
    void completingObsoleteRequestCannotRetireNewerRequest() throws Exception {
        SpeechManager manager = new SpeechManager();
        UUID speaker = UUID.randomUUID();
        long old = manager.beginVoiceRequest(speaker);
        long newer = manager.beginVoiceRequest(speaker);

        finish(manager, speaker, old);
        assertEquals(newer, tokens(manager).get(speaker));

        finish(manager, speaker, newer);
        assertFalse(tokens(manager).containsKey(speaker));
    }

    @Test
    void disconnectInvalidatesOutstandingRequestsBeforeLateCompletion() throws Exception {
        SpeechManager manager = new SpeechManager();
        UUID speaker = UUID.randomUUID();
        long request = manager.beginVoiceRequest(speaker);

        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Minecraft disconnected = (Minecraft) ((Unsafe) unsafeField.get(null)).allocateInstance(Minecraft.class);
        manager.tick(disconnected);

        assertTrue(tokens(manager).isEmpty(), "No voice request from a previous session may survive disconnect");
        finish(manager, speaker, request);
        assertTrue(tokens(manager).isEmpty());
    }

    private static void finish(SpeechManager manager, UUID speaker, long request) throws Exception {
        Method finish = SpeechManager.class.getDeclaredMethod("finishVoiceRequest", UUID.class, long.class);
        finish.setAccessible(true);
        finish.invoke(manager, speaker, request);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, Long> tokens(SpeechManager manager) throws Exception {
        Field field = SpeechManager.class.getDeclaredField("latestVoiceRequest");
        field.setAccessible(true);
        return (Map<UUID, Long>) field.get(manager);
    }
}
