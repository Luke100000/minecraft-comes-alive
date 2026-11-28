package net.conczin.mca.server.world.data;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class VillageManagerCooldownTest {
    @Test
    void positiveCooldownsAreAllowed() {
        VillageManager manager = new VillageManager(null);
        assertDoesNotThrow(() -> manager.setBuildingCooldown(1));
        assertDoesNotThrow(() -> manager.setBuildingCooldown(21));
    }

    @Test
    void zeroAndNegativeCooldownsAreRejectedWithoutChangingCurrentValue() throws ReflectiveOperationException {
        VillageManager manager = new VillageManager(null);
        manager.setBuildingCooldown(21);
        Field cooldown = VillageManager.class.getDeclaredField("buildingCooldown");
        cooldown.setAccessible(true);

        for (int invalid : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> manager.setBuildingCooldown(invalid));
            assertEquals(21, cooldown.getInt(manager),
                    "rejecting " + invalid + " must preserve the previously configured cooldown");
        }
        assertDoesNotThrow(() -> manager.setBuildingCooldown(1),
                "rejected values must leave the manager able to accept valid cooldowns");
    }
}
