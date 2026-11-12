package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import net.conczin.mca.resources.DialogueEvents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueOwnershipReleaseTest {
    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID OTHER_VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000204");

    @Test
    void losingInteractionPausesBoundDialogueAndRevokesItsOldOffer() throws Exception {
        DialogueEngine engine = new DialogueEngine(new DialogueEvents());
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 0L, 41L, event());
        sessions(engine).put(PLAYER, active);
        offers(engine).put(PLAYER, offer(VILLAGER));

        engine.onInteractionReleased(PLAYER, VILLAGER, 1_000L);

        DialogueSession paused = sessions(engine).get(PLAYER);
        assertEquals(DialogueSession.Status.PAUSED, paused.status());
        assertEquals(3_400L, paused.pauseDeadline());
        assertFalse(paused.acceptsOffer(41L, 0L), "released screen must not retain a usable dialogue token");
        assertFalse(offers(engine).containsKey(PLAYER), "released screen must not retain a menu offer");
    }

    @Test
    void releasingDifferentVillagerDoesNotInterruptCurrentConversation() throws Exception {
        DialogueEngine engine = new DialogueEngine(new DialogueEvents());
        DialogueSession active = DialogueSession.start(PLAYER, VILLAGER, SESSION, 0L, 41L, event());
        sessions(engine).put(PLAYER, active);
        DialogueEngine.DialogueOptions currentOffer = offer(VILLAGER);
        offers(engine).put(PLAYER, currentOffer);

        engine.onInteractionReleased(PLAYER, OTHER_VILLAGER, 1_000L);

        assertSame(active, sessions(engine).get(PLAYER));
        assertTrue(active.acceptsOffer(41L, 0L));
        assertSame(currentOffer, offers(engine).get(PLAYER));
    }

    private static DialogueEngine.DialogueOptions offer(UUID villagerId) {
        return new DialogueEngine.DialogueOptions(villagerId, 0L, Optional.empty(), List.of(),
                false, Optional.empty(), 73L, List.of(), Optional.empty());
    }

    private static DialogueEvent event() {
        return DialogueEvent.decode(ResourceLocation.parse("test:owner_release"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{"mode":"ask","prompt":"test.prompt","resume_prompt":"test.resume"},
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{"start":{"line":"test.line","complete":true}}
                }
                """).getAsJsonObject());
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DialogueSession> sessions(DialogueEngine engine) throws Exception {
        Field field = DialogueEngine.class.getDeclaredField("sessions");
        field.setAccessible(true);
        return (Map<UUID, DialogueSession>) field.get(engine);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, DialogueEngine.DialogueOptions> offers(DialogueEngine engine) throws Exception {
        Field field = DialogueEngine.class.getDeclaredField("offers");
        field.setAccessible(true);
        return (Map<UUID, DialogueEngine.DialogueOptions>) field.get(engine);
    }
}
