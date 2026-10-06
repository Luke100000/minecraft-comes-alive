package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@PrefixGameTestTemplate(false)
public final class VillagerMCABabiesSensorGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private VillagerMCABabiesSensorGameTests() {
    }

    @GameTest(batch = "mca_villager_babies_sensor", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void sensorKeepsVisibleMcaBabiesInNearestOrder(GameTestHelper helper) {
        cleanupTestEntities();
        try {
            BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
            VillagerEntityMCA observer = spawnMcaVillager(helper, origin, Gender.MALE, 0);
            VillagerEntityMCA nearestBaby = spawnMcaVillager(
                    helper, origin.east(2), Gender.FEMALE, AgeState.BABY.toAge());
            VillagerEntityMCA adult = spawnMcaVillager(helper, origin.east(3), Gender.MALE, 0);
            Villager vanillaBaby = spawnVanillaBaby(helper, origin.east(4));
            VillagerEntityMCA fartherBaby = spawnMcaVillager(
                    helper, origin.east(5), Gender.MALE, AgeState.CHILD.toAge());

            observer.refreshBrain(helper.getLevel());
            observer.getBrain().setMemory(
                    MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new NearestVisibleLivingEntities(
                            observer,
                            List.of(nearestBaby, adult, vanillaBaby, fartherBaby)
                    )
            );

            new VillagerMCABabiesSensor().doTick(helper.getLevel(), observer);

            List<LivingEntity> babies = observer.getBrain()
                    .getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES)
                    .orElse(List.of());
            helper.assertTrue(
                    babies.equals(List.of(nearestBaby, fartherBaby)),
                    "baby sensor must keep only visible MCA babies in nearest-first order; actual=" + babies
            );

            observer.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            new VillagerMCABabiesSensor().doTick(helper.getLevel(), observer);
            helper.assertTrue(
                    observer.getBrain().getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES).orElse(List.of()).isEmpty(),
                    "baby sensor must publish an empty list when nearest-visible memory is absent"
            );
            helper.succeed();
        } finally {
            cleanupTestEntities();
        }
    }

    private static VillagerEntityMCA spawnMcaVillager(
            GameTestHelper helper,
            BlockPos position,
            Gender gender,
            int age
    ) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withGender(gender)
                .withAge(age)
                .withPosition(Vec3.atBottomCenterOf(position))
                .spawn(MobSpawnType.STRUCTURE);
        TEST_ENTITIES.add(villager);
        return villager;
    }

    private static Villager spawnVanillaBaby(GameTestHelper helper, BlockPos position) {
        Villager villager = EntityType.VILLAGER.create(helper.getLevel());
        if (villager == null) {
            throw new IllegalStateException("failed to create vanilla villager");
        }
        villager.setAge(-24000);
        villager.absMoveTo(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(villager);
        TEST_ENTITIES.add(villager);
        return villager;
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
    }
}
