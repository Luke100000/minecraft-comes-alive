package net.conczin.mca;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMourningTest {
    @Test
    void mourningDefaultsEnabledAndAcceptsDisable() {
        assertTrue(Config.SERVER.enableMourning.getDefault());
        assertTrue(Config.SERVER.enableMourning.getSpec().test(false));
    }
}
