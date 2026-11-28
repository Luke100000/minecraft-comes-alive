package net.conczin.mca;

import com.google.gson.Gson;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigPathfindingTest {
    private static final Gson GSON = new Gson();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingPathfindingDistanceUses160Default() {
        Config config = GSON.fromJson("{\"version\":2}", Config.class);

        assertEquals(160, config.getVillagerPathfindingDistance());
        assertEquals(48, config.getVillagerFollowRange());
    }
}
