package net.conczin.mca.entity;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerRecoveryFoodGameTests {
    private VillagerRecoveryFoodGameTests() {
    }

    @GameTest(batch = "mca_recovery_food_complete", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 160)
    public static void inventoryFoodRestoresEquippedWeaponAfterUse(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnRecoveringVillager(helper);
        float healthBefore = villager.getHealth();
        boolean[] sawRecoveryUse = {false};

        helper.onEachTick(() -> {
            if (villager.isUsingRecoveryFood()) {
                sawRecoveryUse[0] = true;
                return;
            }

            if (sawRecoveryUse[0]) {
                helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                        "recovery food did not restore the equipped sword");
                helper.assertTrue(villager.getHealth() > healthBefore,
                        "completed recovery food did not heal the villager");
                helper.assertTrue(countItem(villager, Items.APPLE) == 0,
                        "completed recovery food did not consume exactly one apple");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_recovery_food_interrupt", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 160)
    public static void dangerInterruptRestoresWeaponWithoutConsumingFood(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnRecoveringVillager(helper);
        Zombie threat = spawnThreat(helper, helper.absolutePos(new BlockPos(10, 2, 6)));
        float healthBefore = villager.getHealth();
        boolean[] injectedDanger = {false};

        helper.onEachTick(() -> {
            if (!injectedDanger[0] && villager.isUsingRecoveryFood()) {
                injectedDanger[0] = true;
                villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);
                return;
            }

            if (injectedDanger[0] && !villager.isUsingRecoveryFood()) {
                helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                        "interrupted recovery food did not restore the equipped sword");
                helper.assertTrue(countItem(villager, Items.APPLE) == 1,
                        "interrupted recovery food consumed or lost the apple");
                helper.assertTrue(villager.getHealth() == healthBefore,
                        "interrupted recovery food healed before completion");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_recovery_food_danger", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void activeGuardThreatPreventsRecoveryFromStarting(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnRecoveringVillager(helper);
        Zombie threat = spawnThreat(helper, helper.absolutePos(new BlockPos(10, 2, 6)));
        villager.setNoAi(true);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, threat);
        villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            helper.assertTrue(!villager.isUsingRecoveryFood(),
                    "recovery food started while an active guard threat was present");
            helper.assertTrue(villager.getMainHandItem().is(Items.IRON_SWORD),
                    "danger recovery check replaced the equipped sword");
            if (ticks[0] >= 10) {
                helper.assertTrue(countItem(villager, Items.APPLE) == 1,
                        "danger recovery check removed the apple from inventory");
                helper.succeed();
            }
        });
    }

    private static VillagerEntityMCA spawnRecoveringVillager(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(6, 2, 6));
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Recovery Food Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setItemSlot(EquipmentSlot.MAINHAND, Items.IRON_SWORD.getDefaultInstance());
        villager.getInventory().addItem(Items.APPLE.getDefaultInstance());
        villager.setHealth(Math.max(1.0F, villager.getMaxHealth() - 8.0F));
        villager.tickCount = 199;
        return villager;
    }

    private static Zombie spawnThreat(GameTestHelper helper, BlockPos pos) {
        Zombie threat = net.minecraft.world.entity.EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        if (threat == null) {
            throw new IllegalStateException("failed to create recovery threat");
        }
        threat.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        threat.setNoAi(true);
        helper.getLevel().addFreshEntity(threat);
        return threat;
    }

    private static int countItem(VillagerEntityMCA villager, net.minecraft.world.item.Item item) {
        int count = 0;
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            ItemStack stack = villager.getInventory().getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }
}
