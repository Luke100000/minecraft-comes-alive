package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerBedAlignmentGameTests {
    private static final double EPSILON = 1.0E-6D;

    private VillagerBedAlignmentGameTests() {
    }

    @GameTest(batch = "mca_villager_bed_alignment", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sleepingTallVillagerUsesModelScaleForRenderAnchor(GameTestHelper helper) {
        BlockPos bedHead = placeBed(helper, new BlockPos(3, 1, 3), Direction.NORTH);
        VillagerEntityMCA villager = createAdultMale(helper, bedHead);
        villager.getGenetics().setGene(Genetics.SIZE, 1.0F);

        villager.startSleeping(bedHead);

        helper.assertTrue(villager.isSleeping(), "villager did not enter the sleeping pose");
        helper.assertTrue(Math.abs(villager.getX() - (bedHead.getX() + 0.5D)) < EPSILON,
                "sleeping villager was not centered on the bed head X");
        helper.assertTrue(Math.abs(villager.getZ() - (bedHead.getZ() + 0.5D)) < EPSILON,
                "sleeping villager was not centered on the bed head Z");
        helper.assertTrue(Math.abs(villager.getBbWidth() - 0.2F) < EPSILON,
                "sleeping villager did not use vanilla sleeping width");
        helper.assertTrue(Math.abs(villager.getBbHeight() - 0.2F) < EPSILON,
                "sleeping villager did not use vanilla sleeping height");

        float expectedRawStandingEyeHeight = EntityDimensions
                .scalable(villager.getRawHorizontalScaleFactor() * 0.6F,
                        villager.getRawVerticalScaleFactor() * 2.0F)
                .scale(villager.getScale())
                .eyeHeight();
        helper.assertTrue(Math.abs(villager.getRawStandingEyeHeight() - expectedRawStandingEyeHeight) < EPSILON,
                "raw-model standing eye height did not match the rendered model scale");

        float collisionEyeHeight = villager.getEyeHeight(Pose.STANDING);
        helper.assertTrue(collisionEyeHeight < expectedRawStandingEyeHeight,
                "tall villager standing collision eye height was no longer capped independently from rendering");
        helper.succeed();
    }

    private static VillagerEntityMCA createAdultMale(GameTestHelper helper, BlockPos bedHead) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAge(0);
        villager.getGenetics().setGender(Gender.MALE);
        villager.setPos(bedHead.getX() + 0.5D, bedHead.getY() + 1.0D, bedHead.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos relativeFoot, Direction facing) {
        BlockPos foot = helper.absolutePos(relativeFoot);
        BlockPos head = foot.relative(facing);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }
}
