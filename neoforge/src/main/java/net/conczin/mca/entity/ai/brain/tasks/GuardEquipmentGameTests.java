package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.MoveState;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class GuardEquipmentGameTests {
    private GuardEquipmentGameTests() {
    }

    @GameTest(batch = "mca_archer_draw_equipment", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void routineArmorRefreshPreservesActiveBowDraw(GameTestHelper helper) {
        VillagerEntityMCA archer = drawEquipmentArcher(helper);
        ItemStack original = new ItemStack(Items.BOW);
        original.setDamageValue(42);
        archer.setItemInHand(archer.getDominantHand(), original);
        archer.startUsingItem(archer.getDominantHand());
        archer.getVillagerBrain().setArmorWear(true);
        EquipmentTask task = new EquipmentTask(v -> true, v -> EquipmentSet.ARCHER_0);
        try {
            helper.assertTrue(task.checkExtraStartConditions(helper.getLevel(), archer), "armour refresh must run");
            task.start(helper.getLevel(), archer, helper.getLevel().getGameTime());
            helper.assertTrue(archer.getItemInHand(archer.getDominantHand()) == original && original.getDamageValue() == 42,
                    "routine armour refresh replaced the suitable active bow stack");
            helper.assertTrue(archer.isUsingItem() && archer.getUseItem() == original,
                    "routine armour refresh invalidated active item use");
            helper.assertTrue(archer.getInventory().countItem(Items.BOW) == 0,
                    "armour refresh stowed or duplicated the active bow");
        } finally {
            archer.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_draw_equipment", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void routineArmorRefreshPreservesOffhandCrossbowCharge(GameTestHelper helper) {
        VillagerEntityMCA archer = drawEquipmentArcher(helper);
        ItemStack original = new ItemStack(Items.CROSSBOW);
        archer.getInventory().setItem(0, original);
        archer.setItemSlot(EquipmentSlot.OFFHAND, original);
        archer.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        EquipmentTask task = new EquipmentTask(v -> true, v -> EquipmentSet.ARCHER_0);
        try {
            task.start(helper.getLevel(), archer, helper.getLevel().getGameTime());
            helper.assertTrue(archer.getOffhandItem() == original && archer.getUseItem() == original,
                    "armour refresh replaced an actively charging offhand crossbow");
            helper.assertTrue(archer.getMainHandItem() != original,
                    "armour refresh aliased the borrowed offhand crossbow into both hands");
            helper.assertTrue(net.conczin.mca.entity.ai.RangedWeaponHelper.getCrossbowHoldingHand(archer)
                            == net.minecraft.world.InteractionHand.OFF_HAND
                            && archer.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND,
                    "armour refresh changed the charging crossbow's selected hand");
            helper.assertTrue(archer.getInventory().countItem(Items.CROSSBOW) == 1,
                    "armour refresh duplicated or lost the borrowed crossbow");
        } finally {
            archer.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_draw_equipment", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void intentionalBowRemovalCancelsDraw(GameTestHelper helper) {
        VillagerEntityMCA archer = drawEquipmentArcher(helper);
        ItemStack original = new ItemStack(Items.BOW);
        archer.getInventory().setItem(0, original);
        archer.setItemInHand(archer.getDominantHand(), original);
        archer.startUsingItem(archer.getDominantHand());
        ItemStack removed = archer.getInventory().removeItemNoUpdate(0);
        archer.onInvChange(archer.getInventory());
        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(archer.getItemInHand(archer.getDominantHand()).isEmpty() && !archer.isUsingItem(),
                        "intentional inventory transfer left a borrowed bow drawing");
                helper.assertTrue(removed == original && removed.getCount() == 1,
                        "intentional removal lost or duplicated the actual bow stack");
            } finally {
                archer.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_draw_equipment", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void recoveryFoodStillOwnsAndReleasesHand(GameTestHelper helper) {
        VillagerEntityMCA archer = drawEquipmentArcher(helper);
        archer.getInventory().addItem(new ItemStack(Items.BREAD, 2));
        archer.setHealth(archer.getMaxHealth() - 1);
        archer.tickCount = 200;
        archer.aiStep();
        try {
            helper.assertTrue(archer.isUsingRecoveryFood(), "fixture did not start recovery food use");
            ItemStack food = archer.getItemInHand(archer.getDominantHand());
            EquipmentTask task = new EquipmentTask(v -> true, v -> EquipmentSet.ARCHER_0);
            task.start(helper.getLevel(), archer, helper.getLevel().getGameTime());
            helper.assertTrue(archer.getItemInHand(archer.getDominantHand()) == food && archer.isUsingRecoveryFood(),
                    "routine equipment stole the hand from recovery food");
            archer.stopUsingItem();
            helper.assertTrue(!archer.isUsingRecoveryFood() && archer.getItemInHand(archer.getDominantHand()).isEmpty()
                            && archer.getInventory().countItem(Items.BREAD) == 2,
                    "stopped recovery retained the hand or lost food");
        } finally {
            archer.discard();
        }
        helper.succeed();
    }

    private static VillagerEntityMCA drawEquipmentArcher(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 8, 5);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withProfession(ProfessionsMCA.ARCHER).withPosition(Vec3.atBottomCenterOf(start))
                .spawn(MobSpawnType.STRUCTURE);
        archer.getTraits().removeTrait(Traits.LEFT_HANDED);
        archer.setNoAi(true);
        archer.setOnGround(true);
        return archer;
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void guardsRespondToDutyScheduleChangesAcrossTicks(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start, 6);
        VillagerEntityMCA first = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                .spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA second = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.east(2)))
                .spawn(MobSpawnType.STRUCTURE);
        first.setNoAi(true);
        second.setNoAi(true);

        Schedule work = new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.WORK).build();
        Schedule rest = new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.REST).build();
        first.getBrain().setSchedule(work);
        second.getBrain().setSchedule(work);

        helper.assertTrue(VillagerTasksMCA.isOnDuty(first) && VillagerTasksMCA.isOnDuty(second),
                "shared WORK schedule did not keep both guards on duty");
        second.getBrain().setSchedule(rest);
        helper.assertTrue(!VillagerTasksMCA.isOnDuty(second),
                "switching to REST did not release the second guard from duty");

        helper.runAfterDelay(2, () -> {
            try {
                helper.assertTrue(VillagerTasksMCA.isOnDuty(first), "WORK was lost on the next tick");
                helper.assertTrue(!VillagerTasksMCA.isOnDuty(second), "REST was lost on the next tick");
                first.getBrain().setSchedule(rest);
                second.getBrain().setSchedule(work);
                helper.assertTrue(!VillagerTasksMCA.isOnDuty(first),
                        "WORK-to-REST schedule switch was ignored on the next tick");
                helper.assertTrue(VillagerTasksMCA.isOnDuty(second),
                        "REST-to-WORK schedule switch was ignored on the next tick");
            } finally {
                first.discard();
                second.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_guard_rest_village_defense", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void restingArcherKeepsBowWhenZombieChasesNearbyVillager(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA victim = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.east(6)))
                .spawn(MobSpawnType.STRUCTURE);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie fixture could not be constructed");
        zombie.moveTo(Vec3.atBottomCenterOf(start.east(12)));
        helper.getLevel().addFreshEntity(zombie);
        archer.setNoAi(true);
        victim.setNoAi(true);
        zombie.setNoAi(true);
        zombie.setTarget(victim);
        archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule())
                .changeActivityAt(0, Activity.REST).build());
        archer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), start));
        archer.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        archer.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, zombie);
        archer.setItemInHand(archer.getDominantHand(), new ItemStack(Items.BOW));
        EquipmentTask task = new EquipmentTask(VillagerTasksMCA::isOnDuty, v -> EquipmentSet.ARCHER_0);

        try {
            helper.assertTrue(VillagerTasksMCA.isOnDuty(archer),
                    "resting archer ignored a zombie actively chasing a nearby villager");
            task.start(helper.getLevel(), archer, helper.getLevel().getGameTime());
            helper.assertTrue(archer.getItemInHand(archer.getDominantHand()).is(Items.BOW),
                    "resting archer put away its bow while a zombie chased a nearby villager");
        } finally {
            archer.discard();
            victim.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guard_rest_sensor_rescue", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 160)
    public static void restingArcherAcquiresZombieChasingNearbyVillagerWithoutSeededEnemyMemory(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        // Factory assigns the profession after constructing the original brain;
        // install the guard CORE tasks before testing natural sensor acquisition.
        archer.refreshBrain(helper.getLevel());
        VillagerEntityMCA victim = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.east(7)))
                .spawn(MobSpawnType.STRUCTURE);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie fixture could not be constructed");
        zombie.moveTo(Vec3.atBottomCenterOf(start.east(12)));
        helper.getLevel().addFreshEntity(zombie);
        victim.setNoAi(true);
        zombie.setNoAi(true);
        zombie.setTarget(victim);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0D);
        zombie.setHealth(200.0F);
        archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule())
                .changeActivityAt(0, Activity.REST).build());
        archer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), start));
        archer.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        archer.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
        archer.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                .setBaseValue(0.0D);

        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == zombie) {
                archer.discard();
                victim.discard();
                zombie.discard();
                helper.succeed();
            } else if (ticks[0] >= 110) {
                try {
                    helper.fail("REST guard never acquired zombie chasing a nearby villager; sensor="
                            + archer.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null)
                            + ", victim=" + zombie.getTarget()
                            + ", onDuty=" + VillagerTasksMCA.isOnDuty(archer)
                            + ", activity=" + archer.getBrain().getActiveNonCoreActivity());
                } finally {
                    archer.discard();
                    victim.discard();
                    zombie.discard();
                }
            }
        });
    }

    @GameTest(batch = "mca_guard_rest_target_continuity", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void restingArcherRetainsZombieAfterItChangesVictimToArcher(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 24);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA victim = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.east(6)))
                .spawn(MobSpawnType.STRUCTURE);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie fixture could not be constructed");
        zombie.moveTo(Vec3.atBottomCenterOf(start.east(20)));
        helper.getLevel().addFreshEntity(zombie);
        archer.setOnGround(true);
        zombie.setNoAi(true);
        zombie.setTarget(victim);
        zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200.0D);
        zombie.setHealth(200.0F);
        archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule())
                .changeActivityAt(0, Activity.REST).build());
        archer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), start));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, zombie);
        archer.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, zombie);
        zombie.setTarget(archer);
        try {
            helper.assertTrue(archer.getBrain().getSchedule().getActivityAt(
                            (int) (helper.getLevel().getDayTime() % 24000L)) == Activity.REST,
                    "fixture did not establish a REST schedule");
            helper.assertTrue(archer.distanceToSqr(zombie) > 16.0D * 16.0D,
                    "fixture did not place the engaged zombie beyond the REST defence radius");
            // Exercise the guard's real invalid-target behavior, not a
            // reimplementation of its predicate in the GameTest.
            VillagerTasksMCA.getGuardCorePackage(archer).get(6).getSecond()
                    .tryStart(helper.getLevel(), archer, helper.getLevel().getGameTime());
            helper.assertTrue(archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == zombie,
                    "guard invalid-target behavior erased an engaged zombie beyond the REST defence radius");
        } finally {
            archer.discard();
            victim.discard();
            zombie.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guard_trading_weapon", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void guardTradingBehaviorsCannotEraseCombatBow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(zombie != null, "zombie fixture could not be constructed");
        zombie.moveTo(Vec3.atBottomCenterOf(start.east(9)));
        helper.getLevel().addFreshEntity(zombie);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(archer.getX() + 2.0D, archer.getY(), archer.getZ());
        archer.setNoAi(true);
        zombie.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, zombie);
        archer.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, player);
        ItemStack combatBow = new ItemStack(Items.BOW);
        combatBow.setDamageValue(37);
        archer.setItemSlot(EquipmentSlot.MAINHAND, combatBow);

        try {
            // Both IDLE and MEET can run while the guard CORE is in combat.
            var idleTrade = VillagerTasksMCA.getIdlePackage(0.5F).get(4).getSecond();
            idleTrade.tryStart(helper.getLevel(), archer, helper.getLevel().getGameTime());
            idleTrade.tickOrStop(helper.getLevel(), archer, helper.getLevel().getGameTime() + 1);
            helper.assertTrue(archer.getMainHandItem().is(Items.BOW)
                            && archer.getMainHandItem().getDamageValue() == 37,
                    "IDLE trade display erased the guard's active bow");

            var meetTrade = VillagerTasksMCA.getMeetPackage(0.5F).get(1).getSecond();
            meetTrade.tryStart(helper.getLevel(), archer, helper.getLevel().getGameTime());
            meetTrade.tickOrStop(helper.getLevel(), archer, helper.getLevel().getGameTime() + 1);
            helper.assertTrue(archer.getMainHandItem().is(Items.BOW)
                            && archer.getMainHandItem().getDamageValue() == 37,
                    "MEET trade display erased the guard's active bow");
        } finally {
            archer.discard();
            zombie.discard();
            player.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guard_night_shifts", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void guardsAndArchersHonorNightOwlSettings(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 8);
        VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start.east(3))).spawn(MobSpawnType.STRUCTURE);
        boolean originalAnyNightOwl = Config.getInstance().allowAnyNightOwl;
        float originalNightOwlChance = Config.getInstance().nightOwlChance;
        try {
            // Guard schedules must continue to honor the civilian night-owl opt-in.
            // Force chance to one to make both branches deterministic.
            Config.getInstance().allowAnyNightOwl = false;
            Config.getInstance().nightOwlChance = 1.0F;
            guard.refreshBrain(helper.getLevel());
            archer.refreshBrain(helper.getLevel());
            for (VillagerEntityMCA defender : new VillagerEntityMCA[]{guard, archer}) {
                helper.assertTrue(defender.getBrain().getSchedule().getActivityAt(16000) == Activity.REST,
                        "guard ignored disabled night owls and received a night shift");
                helper.assertTrue(defender.getBrain().getSchedule().getActivityAt(100) == Activity.WORK,
                        "guard did not work during its scheduled day shift");
            }

            Config.getInstance().allowAnyNightOwl = true;
            guard.refreshBrain(helper.getLevel());
            archer.refreshBrain(helper.getLevel());
            for (VillagerEntityMCA defender : new VillagerEntityMCA[]{guard, archer}) {
                helper.assertTrue(defender.getBrain().getSchedule().getActivityAt(16000) == Activity.WORK,
                        "night-owl guard profession did not receive a night WORK shift");
                helper.assertTrue(defender.getBrain().getSchedule().getActivityAt(100) == Activity.REST,
                        "night-shift guard did not rest during the day");
            }
        } finally {
            Config.getInstance().allowAnyNightOwl = originalAnyNightOwl;
            Config.getInstance().nightOwlChance = originalNightOwlChance;
            guard.discard();
            archer.discard();
        }
        helper.succeed();
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
        // Other GameTest fixtures can leave hostile mobs in the shared level.
        // Keep this peaceful-follow scenario outside the guard's 48-block scan
        // of those ground-level actors while retaining real guard sensing.
        BlockPos start = helper.absolutePos(new BlockPos(4, 82, 4));
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
                        "combat masked the peaceful follow equipment check at tick " + tick
                                + ", attackTarget=" + villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null)
                                + ", guardEnemy=" + villager.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null)
                                + ", position=" + villager.position());
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
