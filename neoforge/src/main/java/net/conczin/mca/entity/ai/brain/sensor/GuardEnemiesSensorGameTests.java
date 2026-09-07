package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class GuardEnemiesSensorGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private GuardEnemiesSensorGameTests() {
    }

    @GameTest(batch = "mca_guard_enemy_range", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void guardDetectsVisibleEnemyBeyondVanillaSixteenBlockScan(GameTestHelper helper) {
        cleanupTestEntities();
        try {
            BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos targetPos = guardPos.east(24);
            prepareClearLane(helper, guardPos, targetPos);

            VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withProfession(ProfessionsMCA.GUARD)
                    .withPosition(Vec3.atBottomCenterOf(guardPos))
                    .withName("Guard Enemy Sensor Probe")
                    .spawn(MobSpawnType.STRUCTURE);
            guard.refreshBrain(helper.getLevel());
            TEST_ENTITIES.add(guard);

            LivingEntity nearby = EntityType.COW.create(helper.getLevel());
            if (nearby == null) {
                throw new IllegalStateException("failed to create nearby entity");
            }
            BlockPos nearbyPos = guardPos.east(8);
            nearby.absMoveTo(nearbyPos.getX() + 0.5D, nearbyPos.getY(), nearbyPos.getZ() + 0.5D);
            helper.getLevel().addFreshEntity(nearby);
            TEST_ENTITIES.add(nearby);

            Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
            if (target == null) {
                throw new IllegalStateException("failed to create zombie target");
            }
            target.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
            target.setNoAi(true);
            helper.getLevel().addFreshEntity(target);
            TEST_ENTITIES.add(target);

            guard.getBrain().eraseMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
            guard.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            new GuardEnemiesSensor().doTick(helper.getLevel(), guard);
            Entity detected = guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null);
            List<LivingEntity> nearbyEntities = guard.getBrain()
                    .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                    .orElse(List.of());
            helper.assertTrue(
                    detected == target,
                    "guard sensor did not detect a visible enemy 24 blocks away"
            );
            helper.assertTrue(
                    nearbyEntities.contains(nearby),
                    "guard sensor did not preserve vanilla nearby-entity memory inside 16 blocks"
            );
            helper.assertTrue(
                    !nearbyEntities.contains(target),
                    "guard sensor leaked a 24-block entity into vanilla 16-block nearby memory"
            );
            helper.succeed();
        } finally {
            cleanupTestEntities();
        }
    }

    private static void prepareClearLane(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (int x = from.getX(); x <= to.getX(); x++) {
            BlockPos feet = new BlockPos(x, from.getY(), from.getZ());
            helper.getLevel().getChunk(feet);
            helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
    }
}
