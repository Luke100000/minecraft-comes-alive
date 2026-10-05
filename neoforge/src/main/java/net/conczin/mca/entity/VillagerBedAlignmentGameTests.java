package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
@PrefixGameTestTemplate(false)
public final class VillagerBedAlignmentGameTests {
    private static final double EPSILON = 1.0E-6D;

    private VillagerBedAlignmentGameTests() {
    }

    @GameTest(batch = "mca_villager_bed_alignment", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sleepingVillagerKeepsBedAnchorThroughMovementTicks(GameTestHelper helper) {
        helper.getLevel().setDayTime(18000L);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos bedHead = placeBed(helper, new BlockPos(3, 1, 3), facing);
            VillagerEntityMCA villager = createAdultMale(helper, bedHead);
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), bedHead));
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            // Navigation ticks before Brain and MoveControl ticks after SleepInBed.
            villager.getMoveControl().setWantedPosition(villager.getX(), villager.getY(), villager.getZ() - 1.0D, 0.5D);
            villager.startSleeping(bedHead);
            Vec3 anchor = villager.position();
            // Movement input can remain from the approach on the tick SleepInBed starts.
            villager.setZza(1.0F);
            for (int tick = 0; tick < 20; tick++) {
                helper.getLevel().tickNonPassenger(villager);
                helper.assertTrue(villager.isSleeping(), "fixture woke during movement ticks");
                helper.assertTrue(villager.position().distanceToSqr(anchor) < EPSILON * EPSILON,
                        "sleeping villager moved off the " + facing + " bed anchor: "
                                + anchor + " -> " + villager.position());
            }
            villager.stopSleeping();
            Vec3 awakePosition = villager.position();
            villager.setDeltaMovement(0.1D, 0.0D, 0.0D);
            villager.travel(Vec3.ZERO);
            helper.assertTrue(villager.position().distanceToSqr(awakePosition) > EPSILON,
                    "villager could not resume movement after waking");
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_villager_bed_alignment", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sleepingTallAdultUsesVisualStandingEyeHeightForRenderAnchor(GameTestHelper helper) {
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

        helper.assertTrue(villager.getVisualVerticalScaleFactor() > villager.getPhysicalVerticalScaleFactor(),
                "test fixture did not exceed the adult physical-height cap");
        float physicalStandingHeight = villager.getDefaultDimensions(Pose.STANDING).height();
        helper.assertTrue(Math.abs(physicalStandingHeight - villager.getPhysicalVerticalScaleFactor() * 2.0F) < EPSILON,
                "tall adult villager did not use physical height for standing dimensions");
        float expectedNameTagY = villager.getVisualVerticalScaleFactor() * VillagerLike.PLAYER_MODEL_NAME_TAG_HEIGHT;
        double nameTagY = villager.getDefaultDimensions(Pose.STANDING)
                .attachments()
                .get(EntityAttachment.NAME_TAG, 0, 0.0F)
                .y;
        helper.assertTrue(Math.abs(nameTagY - expectedNameTagY) < EPSILON,
                "tall adult villager name tag did not use visual standing height");
        float visualModelEyeHeight = villager.getVisualVerticalScaleFactor() * VillagerLike.PLAYER_MODEL_EYE_HEIGHT;
        float physicalStandingEyeHeight = villager.getEyeHeight(Pose.STANDING);
        helper.assertTrue(physicalStandingEyeHeight < visualModelEyeHeight,
                "test fixture did not reproduce the visual-model bed-anchor mismatch");
        helper.succeed();
    }

    @GameTest(batch = "mca_zombie_villager_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void tallAdultZombieUsesCappedPhysicalHeight(GameTestHelper helper) {
        ZombieVillagerEntityMCA zombie = Objects.requireNonNull(EntitiesMCA.MALE_ZOMBIE_VILLAGER.create(helper.getLevel()));
        zombie.setAgeState(AgeState.ADULT);
        zombie.getGenetics().setGene(Genetics.SIZE, 1.0F);

        helper.assertTrue(zombie.getVisualVerticalScaleFactor() > zombie.getPhysicalVerticalScaleFactor(),
                "test fixture did not exceed the adult physical-height cap");
        float physicalHeight = zombie.getDefaultDimensions(Pose.STANDING).height();
        float expectedPhysicalHeight = zombie.getPhysicalVerticalScaleFactor() * 2.0F;
        helper.assertTrue(Math.abs(physicalHeight - expectedPhysicalHeight) < EPSILON,
                "tall adult zombie villager used visual height for physical dimensions");
        double nameTagY = zombie.getDefaultDimensions(Pose.STANDING)
                .attachments()
                .get(EntityAttachment.NAME_TAG, 0, 0.0F)
                .y;
        helper.assertTrue(Math.abs(nameTagY - zombie.getVisualVerticalScaleFactor() * VillagerLike.PLAYER_MODEL_NAME_TAG_HEIGHT) < EPSILON,
                "tall adult zombie villager name tag did not use visual standing height");
        helper.succeed();
    }

    @GameTest(batch = "mca_player_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void tallAdultPlayerHitboxProjectionUsesPhysicalScale(GameTestHelper helper) {
        VillagerEntityMCA villager = createAdultMale(helper, helper.absolutePos(new BlockPos(3, 1, 3)));
        villager.getGenetics().setGene(Genetics.SIZE, 1.0F);
        villager.getGenetics().setGene(Genetics.WIDTH, 1.0F);

        PlayerDimensions.Scale scale = PlayerDimensions.fromVillager(villager);

        helper.assertTrue(villager.getVisualVerticalScaleFactor() > villager.getPhysicalVerticalScaleFactor(),
                "test fixture did not exceed the adult physical-height cap");
        helper.assertTrue(villager.getVisualHorizontalScaleFactor() > villager.getPhysicalHorizontalScaleFactor(),
                "test fixture did not exceed the adult physical-width cap");
        helper.assertTrue(Math.abs(scale.height() - villager.getPhysicalVerticalScaleFactor()) < EPSILON,
                "player hitbox projection used visual height instead of physical height");
        helper.assertTrue(Math.abs(scale.width() - villager.getPhysicalHorizontalScaleFactor()) < EPSILON,
                "player hitbox projection used visual width instead of physical width");
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
