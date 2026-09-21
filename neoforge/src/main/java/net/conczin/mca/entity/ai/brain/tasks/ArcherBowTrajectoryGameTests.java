package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherBowTrajectoryGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private ArcherBowTrajectoryGameTests() {
    }

    @GameTest(batch = "mca_archer_bow_level_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void archerHitsLevelSkeleton(GameTestHelper helper) {
        runSkeletonTrajectoryCase(
                helper,
                new BlockPos(8, 22, 8),
                new BlockPos(18, 22, 8),
                "level"
        );
    }

    @GameTest(batch = "mca_archer_bow_uphill_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void archerHitsSkeletonTwoBlocksHigher(GameTestHelper helper) {
        runSkeletonTrajectoryCase(
                helper,
                new BlockPos(8, 22, 8),
                new BlockPos(18, 24, 8),
                "uphill"
        );
    }

    @GameTest(batch = "mca_archer_bow_downhill_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void archerHitsSkeletonTwoBlocksLower(GameTestHelper helper) {
        runSkeletonTrajectoryCase(
                helper,
                new BlockPos(8, 24, 8),
                new BlockPos(18, 22, 8),
                "downhill"
        );
    }

    private static void runSkeletonTrajectoryCase(
            GameTestHelper helper,
            BlockPos relativeArcherPos,
            BlockPos relativeSkeletonPos,
            String label
    ) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(relativeArcherPos);
        BlockPos skeletonPos = helper.absolutePos(relativeSkeletonPos);
        clearTrajectoryCorridor(helper, archerPos, skeletonPos);
        preparePlatform(helper, archerPos);
        preparePlatform(helper, skeletonPos);

        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(archerPos))
                .withName("Bow Trajectory Archer")
                .spawn(MobSpawnType.STRUCTURE);
        archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
        archer.setOnGround(true);
        archer.refreshBrain(helper.getLevel());
        archer.setNoAi(true);

        Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
        if (skeleton == null) {
            throw new IllegalStateException("failed to create skeleton target");
        }
        skeleton.absMoveTo(skeletonPos.getX() + 0.5D, skeletonPos.getY(), skeletonPos.getZ() + 0.5D);
        skeleton.setNoAi(true);
        helper.getLevel().addFreshEntity(skeleton);

        TEST_ENTITIES.add(archer);
        TEST_ENTITIES.add(skeleton);
        float initialHealth = skeleton.getHealth();
        int[] ticks = {0};
        double[] closestArrowDistanceSquared = {Double.POSITIVE_INFINITY};
        Vec3[] closestArrowPosition = {null};
        AbstractArrow[] arrow = {null};

        helper.onEachTick(() -> {
            ticks[0]++;
            if (ticks[0] == 10) {
                ItemStack bow = archer.getMainHandItem();
                AbstractArrow projectile = ProjectileUtil.getMobArrow(
                        archer,
                        Items.ARROW.getDefaultInstance(),
                        1.0F,
                        bow
                );
                Vec3 shot = RangedWeaponHelper.calculateBowShotVector(
                        projectile.position(),
                        skeleton.position(),
                        skeleton.getBbHeight()
                );
                projectile.shoot(shot.x, shot.y, shot.z, 1.6F, 0.0F);
                helper.getLevel().addFreshEntity(projectile);
                TEST_ENTITIES.add(projectile);
                arrow[0] = projectile;
            }
            if (arrow[0] != null && arrow[0].isAlive()) {
                double distanceSquared = distanceToBoxSquared(arrow[0].position(), skeleton.getBoundingBox());
                if (distanceSquared < closestArrowDistanceSquared[0]) {
                    closestArrowDistanceSquared[0] = distanceSquared;
                    closestArrowPosition[0] = arrow[0].position();
                }
            }
            if (skeleton.getHealth() < initialHealth || !skeleton.isAlive()) {
                helper.succeed();
                return;
            }
            if (ticks[0] >= 300) {
                helper.fail(
                        "archer never hit " + label + " skeleton; health=" + skeleton.getHealth()
                                + ", archerPos=" + archer.position()
                                + ", targetPos=" + skeleton.position()
                                + ", closestArrowDistanceSqr=" + closestArrowDistanceSquared[0]
                                + ", closestArrowPos=" + closestArrowPosition[0]
                                + ", arrowAlive=" + (arrow[0] != null && arrow[0].isAlive())
                                + ", arrowPos=" + (arrow[0] == null ? null : arrow[0].position())
                                + ", arrowMotion=" + (arrow[0] == null ? null : arrow[0].getDeltaMovement())
                                + ", arrowTickCount=" + (arrow[0] == null ? -1 : arrow[0].tickCount)
                                + ", arrowPickable=" + (arrow[0] != null && arrow[0].isPickable())
                                + ", arrowBlock=" + (arrow[0] == null
                                ? null
                                : helper.getLevel().getBlockState(arrow[0].blockPosition()))
                );
            }
        });
    }

    private static double distanceToBoxSquared(Vec3 point, AABB box) {
        double x = Math.max(box.minX - point.x, Math.max(0.0D, point.x - box.maxX));
        double y = Math.max(box.minY - point.y, Math.max(0.0D, point.y - box.maxY));
        double z = Math.max(box.minZ - point.z, Math.max(0.0D, point.z - box.maxZ));
        return x * x + y * y + z * z;
    }

    private static void clearTrajectoryCorridor(GameTestHelper helper, BlockPos first, BlockPos second) {
        int minX = Math.min(first.getX(), second.getX()) - 2;
        int maxX = Math.max(first.getX(), second.getX()) + 2;
        int minY = Math.min(first.getY(), second.getY()) - 1;
        int maxY = Math.max(first.getY(), second.getY()) + 3;
        int minZ = Math.min(first.getZ(), second.getZ()) - 2;
        int maxZ = Math.max(first.getZ(), second.getZ()) + 2;
        ChunkPos minChunk = new ChunkPos(new BlockPos(minX, first.getY(), minZ));
        ChunkPos maxChunk = new ChunkPos(new BlockPos(maxX, first.getY(), maxZ));
        for (int chunkX = minChunk.x; chunkX <= maxChunk.x; chunkX++) {
            for (int chunkZ = minChunk.z; chunkZ <= maxChunk.z; chunkZ++) {
                helper.getLevel().setChunkForced(chunkX, chunkZ, true);
            }
        }

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    helper.getLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void preparePlatform(GameTestHelper helper, BlockPos center) {
        helper.getLevel().getChunk(center);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++) {
                    helper.getLevel().setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.removeIf(entity -> {
            if (entity.isAlive()) {
                entity.discard();
            }
            return true;
        });
    }
}
