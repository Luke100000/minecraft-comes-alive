package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class GuardEquipmentGameTests {
    private GuardEquipmentGameTests() {
    }

    @GameTest(batch = "mca_guard_follow_equipment", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 240)
    public static void peacefulFollowingGuardKeepsEquipmentUntilReleased(GameTestHelper helper) {
        checkFollowEquipment(helper, ProfessionsMCA.GUARD, false);
    }

    @GameTest(batch = "mca_archer_follow_equipment", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 240)
    public static void peacefulFollowingLeftHandedArcherKeepsEquipmentUntilReleased(GameTestHelper helper) {
        checkFollowEquipment(helper, ProfessionsMCA.ARCHER, true);
    }

    private static void checkFollowEquipment(GameTestHelper helper, VillagerProfession profession, boolean leftHanded) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start, 6);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(profession)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Peaceful Follow Equipment Probe")
                .spawn(MobSpawnType.STRUCTURE);
        if (leftHanded) {
            villager.getTraits().addTrait(Traits.LEFT_HANDED);
        } else {
            villager.getTraits().removeTrait(Traits.LEFT_HANDED);
        }
        villager.getVillagerBrain().setArmorWear(false);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.WORK).build());
        // Reproduce a command issued to an established villager, beyond the equipment grace period.
        villager.tickCount = 200;
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(villager.getX() + 2.0D, villager.getY(), villager.getZ());
        ItemStack[] equipment = new ItemStack[2];
        int initialEntityTicks = villager.tickCount;
        int[] ticks = {0};

        helper.onEachTick(() -> {
            int tick = ++ticks[0];
            if (tick == 30) {
                helper.assertTrue(villager.tickCount > initialEntityTicks, "fixture villager did not tick");
                equipment[0] = villager.getItemBySlot(villager.getDominantSlot()).copy();
                equipment[1] = villager.getItemBySlot(EquipmentSlot.CHEST).copy();
                helper.assertTrue(!equipment[0].isEmpty() && !equipment[1].isEmpty(),
                        "on-duty fixture did not equip its weapon and chest armor");
                villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
            }
            if (tick > 30 && tick <= 180) {
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING).orElse(null) == player,
                        "fixture lost its follow command");
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()
                                && villager.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).isEmpty(),
                        "combat masked the peaceful follow equipment check");
                helper.assertTrue(ItemStack.matches(equipment[0], villager.getItemBySlot(villager.getDominantSlot())),
                        "peaceful following villager unequipped its weapon at tick " + tick);
                helper.assertTrue(ItemStack.matches(equipment[1], villager.getItemBySlot(EquipmentSlot.CHEST)),
                        "peaceful following villager unequipped its armor at tick " + tick);
                if (tick == 60) {
                    villager.refreshBrain(helper.getLevel());
                }
            }
            if (tick == 180) {
                villager.getVillagerBrain().setMoveState(MoveState.MOVE, player);
                villager.getBrain().setSchedule(Schedule.EMPTY);
            }
            if (tick == 220) {
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isEmpty(),
                        "release did not clear the follow command");
                helper.assertTrue(villager.getMainHandItem().isEmpty() && villager.getOffhandItem().isEmpty()
                                && villager.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                        "released off-duty villager retained escort equipment");
                villager.discard();
                helper.succeed();
            }
        });
    }
}
