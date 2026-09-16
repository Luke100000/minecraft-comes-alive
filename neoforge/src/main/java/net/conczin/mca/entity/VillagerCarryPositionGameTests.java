package net.conczin.mca.entity;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerCarryPositionGameTests {
    private static final double EPSILON = 1.0E-6D;

    private VillagerCarryPositionGameTests() {
    }

    @GameTest(batch = "mca_villager_carry_position", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void carriedBabiesUseBothShouldersAndHeadPosition(GameTestHelper helper) {
        Player player = createPlayer(helper);
        VillagerEntityMCA left = createBaby(helper, player);
        VillagerEntityMCA right = createBaby(helper, player);
        VillagerEntityMCA head = createBaby(helper, player);

        assertCarryOffset(helper, player, left, new Vec3(0.4D, 0.05D, 0.0D), "left shoulder");
        assertCarryOffset(helper, player, right, new Vec3(-0.4D, 0.05D, 0.0D), "right shoulder");
        assertCarryOffset(helper, player, head, new Vec3(0.0D, 0.55D, 0.0D), "head");

        helper.succeed();
    }

    @GameTest(batch = "mca_villager_carry_position", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void carryingUsesSleepingDimensionsAndDismountRestoresBabyDimensions(GameTestHelper helper) {
        Player player = createPlayer(helper);
        VillagerEntityMCA baby = createBabyEntity(helper, player);
        float normalWidth = baby.getBbWidth();
        float normalHeight = baby.getBbHeight();

        helper.assertTrue(baby.startRiding(player, true), "baby could not ride mock player");
        helper.assertTrue(Math.abs(baby.getBbWidth() - 0.2F) < EPSILON, "carried baby width was not sleeping width");
        helper.assertTrue(Math.abs(baby.getBbHeight() - 0.2F) < EPSILON, "carried baby height was not sleeping height");

        baby.stopRiding();

        helper.assertTrue(Math.abs(baby.getBbWidth() - normalWidth) < EPSILON, "dismount did not restore baby width");
        helper.assertTrue(Math.abs(baby.getBbHeight() - normalHeight) < EPSILON, "dismount did not restore baby height");
        helper.succeed();
    }

    private static Player createPlayer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerPos = helper.absolutePos(new BlockPos(2, 1, 2));
        player.absMoveTo(playerPos.getX() + 0.5D, playerPos.getY(), playerPos.getZ() + 0.5D);
        player.yBodyRot = 0.0F;
        return player;
    }

    private static VillagerEntityMCA createBaby(GameTestHelper helper, Player player) {
        VillagerEntityMCA baby = createBabyEntity(helper, player);
        helper.assertTrue(baby.startRiding(player, true), "baby could not ride mock player");
        return baby;
    }

    private static VillagerEntityMCA createBabyEntity(GameTestHelper helper, Player player) {
        VillagerEntityMCA baby = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        baby.setAge(-AgeState.getMaxAge());
        baby.setPos(player.getX(), player.getY(), player.getZ());
        helper.getLevel().addFreshEntity(baby);
        return baby;
    }

    private static void assertCarryOffset(
            GameTestHelper helper,
            Player player,
            VillagerEntityMCA baby,
            Vec3 expectedOffset,
            String position
    ) {
        player.positionRider(baby);
        Vec3 vanillaPassengerPosition = baby.position();

        baby.rideTick();

        Vec3 carryOffset = baby.position().subtract(vanillaPassengerPosition);
        double hitboxYOffset = baby.getBoundingBox().minY - baby.getY();
        MCA.LOGGER.info(
                "[MCA Carry A/B] position={} vanillaPassenger={} carried={} carryOffset={} hitboxMinY={} hitboxYOffset={} dimensions={}x{}",
                position,
                vanillaPassengerPosition,
                baby.position(),
                carryOffset,
                baby.getBoundingBox().minY,
                hitboxYOffset,
                baby.getBbWidth(),
                baby.getBbHeight()
        );

        helper.assertTrue(carryOffset.distanceTo(expectedOffset) < EPSILON, position + " carry offset was " + carryOffset);
        helper.assertTrue(Math.abs(hitboxYOffset) < EPSILON, position + " hitbox minY did not follow entity Y");
        helper.assertTrue(Math.abs(baby.getBbWidth() - 0.2F) < EPSILON, position + " carried width was not sleeping width");
        helper.assertTrue(Math.abs(baby.getBbHeight() - 0.2F) < EPSILON, position + " carried height was not sleeping height");
    }
}
