package net.conczin.mca.entity.ai;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ValidateNearbyPoiMixinTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void villagerPoiValidatorsLoadWithFabricMixins() {
        assertNotNull(ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME));
        assertNotNull(ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.MEETING), MemoryModuleType.MEETING_POINT));
    }
}
