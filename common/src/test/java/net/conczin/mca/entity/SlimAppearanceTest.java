package net.conczin.mca.entity;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlimAppearanceTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void oldAppearanceDataDefaultsToWideArms() {
        assertFalse(VillagerLike.SLIM.load(new CompoundTag(), RegistryAccess.EMPTY));
    }

    @Test
    void sharedAppearanceDataRoundTripsBothArmTypes() {
        CompoundTag data = new CompoundTag();
        VillagerLike.SLIM.save(data, true, RegistryAccess.EMPTY);
        assertTrue(VillagerLike.SLIM.load(data, RegistryAccess.EMPTY));
        VillagerLike.SLIM.save(data, false, RegistryAccess.EMPTY);
        assertFalse(VillagerLike.SLIM.load(data, RegistryAccess.EMPTY));
    }
}
