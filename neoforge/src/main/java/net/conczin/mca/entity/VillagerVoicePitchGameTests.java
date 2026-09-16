package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerVoicePitchGameTests {
    private VillagerVoicePitchGameTests() {
    }

    @GameTest(batch = "mca_villager_voice_pitch", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void voicePitchTracksGrowthAgeInsteadOfEntityUptime(GameTestHelper helper) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE));
        villager.setPos(Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 1, 1))));
        villager.setAge(-AgeState.getMaxAge());
        helper.getLevel().addFreshEntity(villager);

        villager.tickCount = 0;
        villager.getRandom().setSeed(42L);
        float initialPitch = villager.getVoicePitch();

        villager.tickCount = AgeState.getStageDuration() / 2;
        villager.getRandom().setSeed(42L);
        float laterPitch = villager.getVoicePitch();

        helper.assertTrue(
                Math.abs(initialPitch - laterPitch) < 0.0001F,
                "voice pitch changed with entity uptime even though growth age did not change"
        );
        helper.succeed();
    }
}
