package net.conczin.mca.client.gui;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCAButtonTest {
    @Test
    void buttonCommandDefaultsToExistingIdentifier() {
        MCAButton original = new MCAButton("gui.button.harvesting", null, 0, 0, 0, 0, 66, 21,
                true, false, "", false, false);
        assertEquals("harvesting", original.serverCommand().orElseThrow());
    }

    @Test
    void addonButtonSendsExplicitCommand() {
        MCAButton addon = new Gson().fromJson("""
                {"identifier":"gui.myaddon.inspect","notifyServer":true,
                 "targetServer":false,"command":"myaddon:inspect.part"}
                """, MCAButton.class);
        assertEquals("myaddon:inspect.part", addon.serverCommand().orElseThrow());
    }

    @Test
    void blankExplicitCommandDoesNotFallBack() {
        MCAButton invalid = new Gson().fromJson("""
                {"identifier":"gui.button.harvesting","notifyServer":true,"command":"  "}
                """, MCAButton.class);
        assertTrue(invalid.serverCommand().isEmpty());
    }
}
