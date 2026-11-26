package net.conczin.mca.entity;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class VillageDefenseGameTests {
    private VillageDefenseGameTests() {
    }

    @GameTest(batch = "mca_golem_defends_villager", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void golemDefendsMcaVillagerWithBadPlayerReputation(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper, new BlockPos(2, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        IronGolem golem = spawnGolem(helper, new BlockPos(4, 2, 2), false);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "DefenseTest"));
        player.setInvulnerable(false);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.getLevel().addNewPlayer(player);
        try {
            helper.assertTrue(golem.canAttack(player), "test player must be eligible for golem targeting");
            helper.assertTrue(!golem.canAttack(villager), "golem must remain friendly to MCA villagers");
            helper.assertTrue(!new DefendVillageTargetGoal(golem).canUse(), "neutral player triggered village defense");

            // Use the vanilla hurt-reputation entry point to establish an offending player.
            for (int hit = 0; hit < 4; hit++) {
                villager.setLastHurtByMob(player);
            }
            helper.assertTrue(villager.getPlayerReputation(player) <= -100, "test did not establish hostile reputation");

            DefendVillageTargetGoal defense = new DefendVillageTargetGoal(golem);
            helper.assertTrue(defense.canUse(), "golem ignored the MCA villager's hostile player reputation");
            defense.start();
            helper.assertTrue(golem.getTarget() == player, "golem did not select the offending player");
        } finally {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            villager.discard();
            golem.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guards_defend_golem", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void guardsWarnThenRetaliateAgainstPlayerAttackingVillageGolem(GameTestHelper helper) {
        VillagerEntityMCA guard = spawnVillager(helper, new BlockPos(2, 2, 2), ProfessionsMCA.GUARD);
        VillagerEntityMCA archer = spawnVillager(helper, new BlockPos(2, 2, 4), ProfessionsMCA.ARCHER);
        IronGolem golem = spawnGolem(helper, new BlockPos(4, 2, 2), false);
        var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        attacker.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        try {
            guard.getVillagerBrain().getMemoriesForPlayer(attacker).setHearts(0);
            archer.getVillagerBrain().getMemoriesForPlayer(attacker).setHearts(0);

            helper.assertTrue(golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1), "first hit did not damage golem");
            for (VillagerEntityMCA defender : new VillagerEntityMCA[]{guard, archer}) {
                helper.assertTrue(defender.getSmallBounty() == 1, "guard did not record the first attack on a village golem");
                helper.assertTrue(defender.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "guard skipped the first warning");
            }

            golem.invulnerableTime = 0;
            helper.assertTrue(golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1), "second hit did not damage golem");
            for (VillagerEntityMCA defender : new VillagerEntityMCA[]{guard, archer}) {
                helper.assertTrue(defender.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == attacker,
                        "guard did not retaliate against repeated attacks on a village golem");
            }
        } finally {
            guard.discard();
            archer.discard();
            golem.discard();
            attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guards_ignore_player_golem", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void attackingPlayerCreatedGolemDoesNotAlertGuards(GameTestHelper helper) {
        VillagerEntityMCA guard = spawnVillager(helper, new BlockPos(2, 2, 2), ProfessionsMCA.GUARD);
        IronGolem golem = spawnGolem(helper, new BlockPos(4, 2, 2), true);
        var attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        attacker.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        try {
            helper.assertTrue(golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().playerAttack(attacker), 1), "hit did not damage player-created golem");
            helper.assertTrue(guard.getSmallBounty() == 0, "player-created golem triggered a village guard alarm");
        } finally {
            guard.discard();
            golem.discard();
            attacker.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_golem_targets_outlaw", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void golemAcquiresOutlawWithoutAttackingPeacefulVillagers(GameTestHelper helper) {
        IronGolem golem = spawnGolem(helper, new BlockPos(2, 2, 2), false);
        VillagerEntityMCA peaceful = spawnVillager(helper, new BlockPos(3, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        VillagerEntityMCA outlaw = spawnVillager(helper, new BlockPos(4, 2, 2), ProfessionsMCA.OUTLAW);
        try {
            helper.assertTrue(outlaw.isHostile(), "test requires an outlaw");
            for (int attempt = 0; attempt < 100 && golem.getTarget() == null; attempt++) {
                golem.targetSelector.tick();
            }
            helper.assertTrue(golem.getTarget() == outlaw, "golem did not acquire the outlaw through its target goals");
        } finally {
            golem.discard();
            peaceful.discard();
            outlaw.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_golem_damages_outlaw", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void golemDamageRespectsVillagerHostility(GameTestHelper helper) {
        IronGolem golem = spawnGolem(helper, new BlockPos(2, 2, 2), false);
        VillagerEntityMCA peaceful = spawnVillager(helper, new BlockPos(3, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        VillagerEntityMCA outlaw = spawnVillager(helper, new BlockPos(4, 2, 2), ProfessionsMCA.OUTLAW);
        try {
            float peacefulHealth = peaceful.getHealth();
            golem.doHurtTarget(helper.getLevel(), peaceful);
            helper.assertTrue(peaceful.getHealth() == peacefulHealth, "golem damaged a peaceful MCA villager");

            float outlawHealth = outlaw.getHealth();
            golem.doHurtTarget(helper.getLevel(), outlaw);
            helper.assertTrue(outlaw.getHealth() < outlawHealth, "outlaw was immune to the golem's attack");
        } finally {
            golem.discard();
            peaceful.discard();
            outlaw.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guards_defend_against_outlaw", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void guardsRespondToOutlawAttackingVillager(GameTestHelper helper) {
        VillagerEntityMCA guard = spawnVillager(helper, new BlockPos(2, 2, 2), ProfessionsMCA.GUARD);
        VillagerEntityMCA victim = spawnVillager(helper, new BlockPos(3, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        VillagerEntityMCA peaceful = spawnVillager(helper, new BlockPos(4, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        VillagerEntityMCA outlaw = spawnVillager(helper, new BlockPos(4, 2, 4), ProfessionsMCA.OUTLAW);
        try {
            victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(peaceful), 1);
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "peaceful villager triggered a guard alarm");
            victim.invulnerableTime = 0;
            victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(outlaw), 1);
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == outlaw,
                    "guard treated the outlaw attacking a villager as friendly");
        } finally {
            guard.discard();
            victim.discard();
            peaceful.discard();
            outlaw.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_guards_defend_golem_against_outlaw", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void guardsRespondToOutlawAttackingGolem(GameTestHelper helper) {
        VillagerEntityMCA guard = spawnVillager(helper, new BlockPos(2, 2, 2), ProfessionsMCA.GUARD);
        IronGolem golem = spawnGolem(helper, new BlockPos(3, 2, 2), false);
        VillagerEntityMCA peaceful = spawnVillager(helper, new BlockPos(4, 2, 2), BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        VillagerEntityMCA outlaw = spawnVillager(helper, new BlockPos(4, 2, 4), ProfessionsMCA.OUTLAW);
        try {
            golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(peaceful), 1);
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(), "peaceful villager triggered a guard alarm");
            golem.invulnerableTime = 0;
            helper.assertTrue(golem.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(outlaw), 1), "outlaw did not damage golem");
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == outlaw,
                    "guard did not respond to outlaw attacking a village golem");
        } finally {
            guard.discard();
            golem.discard();
            peaceful.discard();
            outlaw.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_golem_ignores_outlaw_reputation", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void golemDoesNotProtectOutlawAgainstPlayer(GameTestHelper helper) {
        VillagerEntityMCA outlaw = spawnVillager(helper, new BlockPos(2, 2, 2), ProfessionsMCA.OUTLAW);
        IronGolem golem = spawnGolem(helper, new BlockPos(4, 2, 2), false);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "OutlawDefenseTest"));
        player.setInvulnerable(false);
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(4, 2, 4))));
        helper.getLevel().addNewPlayer(player);
        try {
            for (int hit = 0; hit < 4; hit++) {
                outlaw.setLastHurtByMob(player);
            }
            helper.assertTrue(outlaw.getPlayerReputation(player) <= -100, "test did not establish hostile outlaw reputation");
            helper.assertTrue(!new DefendVillageTargetGoal(golem).canUse(), "golem treated the outlaw as a villager to protect");
        } finally {
            helper.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            outlaw.discard();
            golem.discard();
        }
        helper.succeed();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos pos, VillagerProfession profession) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(profession)
                .withPosition(Vec3.atBottomCenterOf(helper.absolutePos(pos)))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.setNoGravity(true);
        return villager;
    }

    private static IronGolem spawnGolem(GameTestHelper helper, BlockPos pos, boolean playerCreated) {
        IronGolem golem = Objects.requireNonNull(EntityType.IRON_GOLEM.create(helper.getLevel(), EntitySpawnReason.STRUCTURE));
        golem.setPos(Vec3.atBottomCenterOf(helper.absolutePos(pos)));
        golem.setPlayerCreated(playerCreated);
        golem.setNoAi(true);
        golem.setNoGravity(true);
        helper.getLevel().addFreshEntity(golem);
        return golem;
    }
}
