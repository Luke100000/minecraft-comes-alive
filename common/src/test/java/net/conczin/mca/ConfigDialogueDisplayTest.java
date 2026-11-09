package net.conczin.mca;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDialogueDisplayTest {
    private static final Gson GSON = new Gson();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void dialoguePositionDefaultsToCornerAndRemembersSavedChoice() {
        Config existing = GSON.fromJson("{\"version\":2}", Config.class);
        assertFalse(JsonParser.parseString(GSON.toJson(existing)).getAsJsonObject()
                .get("dialogueSpeechAboveChoices").getAsBoolean());

        Config switched = GSON.fromJson("{\"version\":2,\"dialogueSpeechAboveChoices\":true}", Config.class);
        String saved = GSON.toJson(switched);
        Config reloaded = GSON.fromJson(saved, Config.class);
        assertTrue(JsonParser.parseString(GSON.toJson(reloaded)).getAsJsonObject()
                .get("dialogueSpeechAboveChoices").getAsBoolean());
    }
}
