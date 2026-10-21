package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.gametest.GameTestBrain;
import net.conczin.mca.entity.interaction.VillagerCommandHandler;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.common.util.FakePlayer;

import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.HashSet;

@PrefixGameTestTemplate(false)
public final class FishingSiteGameTests {
    private FishingSiteGameTests() {}

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void rejectedSiteStaysRejectedWhenPreferenceChanges(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        pond(helper.getLevel(), stance.east(7).below(), 3, true);
        VillagerEntityMCA villager = spawn(helper, stance);
        FishingSiteSearch search = new FishingSiteSearch();
        try {
            FishingSiteSearch.Site first = null;
            for (int tick = 0; tick < 80 && first == null; tick++) first = search.tick(helper.getLevel(), villager);
            helper.assertTrue(first != null, "control fixture did not discover a fishing site");
            search.reject(new FishingSiteSearch.Site(first.standingPos(), first.waterPos(), !first.likelyOpenWater()));
            for (int tick = 0; tick < 450 && !search.exhausted(); tick++) {
                var next = search.tick(helper.getLevel(), villager);
                if (next == null) continue;
                helper.assertTrue(!next.standingPos().equals(first.standingPos()) || !next.waterPos().equals(first.waterPos()),
                        "rejected physical site was selected again after only its preference flag changed");
                search.reject(new FishingSiteSearch.Site(next.standingPos(), next.waterPos(), !next.likelyOpenWater()));
            }
            helper.assertTrue(search.exhausted(), "rejecting sites restarted the bounded search indefinitely");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void rejectedCurrentAimIsNotUsedAsCastFallback(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(5).below();
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var site = new FishingSiteSearch.Site(stance, water, false);
            helper.assertTrue(FishingSiteSearch.isUsable(helper.getLevel(), villager, site), "control cast was not usable");
            FishingSiteSearch search = new FishingSiteSearch();
            search.reject(new FishingSiteSearch.Site(stance, water, true));
            helper.assertTrue(search.chooseCastSite(helper.getLevel(), villager, site, null) == null,
                    "cast fallback revived a rejected aim with different preference metadata");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void repeatedCastsVaryLandingAcrossDistancesAndKeepSameStance(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos stance = prepare(helper);
        pond(helper.getLevel(), stance.east(6).below(), 4, true);
        VillagerEntityMCA villager = spawn(helper, stance);
        villager.getRandom().setSeed(48371L);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        var siteField = FishingTask.class.getDeclaredField("currentSite");
        siteField.setAccessible(true);
        var targets = new HashSet<BlockPos>();
        BlockPos previous = null;
        ItemStack held = null;
        boolean shortCast = false;
        boolean longCast = false;
        try {
            task.start(helper.getLevel(), villager, 0);
            siteField.set(task, new FishingSiteSearch.Site(stance, stance.east(5).below(), false));
            for (int cast = 0; cast < 40; cast++) {
                task.tick(helper.getLevel(), villager, cast * 50L);
                var site = (FishingSiteSearch.Site)siteField.get(task);
                var bobber = villager.getFishingBobber();
                helper.assertTrue(bobber != null, "did not cast from the safe stance");
                helper.assertTrue(!site.waterPos().equals(previous), "repeated previous target despite a broad pond");
                helper.assertTrue(site.standingPos().equals(stance), "changed standing destination for another cast");
                targets.add(site.waterPos());
                double distance = Vec3.atCenterOf(site.waterPos()).subtract(Vec3.atCenterOf(stance)).horizontalDistance();
                helper.assertTrue(distance >= 3 && distance <= 7, "cast outside the supported range: " + site);
                shortCast |= distance <= 4;
                longCast |= distance >= 6;
                if (held == null) held = villager.getItemInHand(villager.getDominantHand());
                helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == held, "changed held rod between casts");
                for (int tick = 0; tick < 40 && !bobber.isBobbing() && !bobber.isRemoved(); tick++) bobber.tick();
                helper.assertTrue(bobber.isBobbing(), "varied cast failed to reach water");
                helper.assertTrue(bobber.position().subtract(Vec3.atCenterOf(site.waterPos())).horizontalDistance() <= 0.8,
                        "bobber entered near water before its selected target: " + site + ", landing=" + bobber.position());
                for (int tick = 0; tick < 30 && !bobber.isRemoved(); tick++) bobber.tick();
                helper.assertTrue(bobber.isBobbing()
                                && bobber.position().subtract(Vec3.atCenterOf(site.waterPos())).horizontalDistance() <= 0.8,
                        "bobber drifted away from its selected target after landing: " + site + ", settled=" + bobber.position());
                helper.assertTrue(villager.position().equals(Vec3.atBottomCenterOf(stance)), "moved to make another cast");
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "cast variation requested movement");
                // Observe actual successful landing, then simulate ordinary hook removal;
                // existing bite/reel tests cover the catch and delivery lifecycle.
                task.tick(helper.getLevel(), villager, cast * 50L + 1);
                previous = site.waterPos();
                bobber.discard();
            }
            helper.assertTrue(targets.size() >= 4, "casts did not explore available water");
            helper.assertTrue(shortCast && longCast, "did not mix short and long casts");
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "recasts searched for paths");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void desiredDistanceSelectsMatchingWaterAcrossWholeRange(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        for (int distance = 3; distance <= 7; distance++) pond(helper.getLevel(), stance.east(distance).below(), 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        var distances = new HashSet<Integer>();
        try {
            var current = new FishingSiteSearch.Site(stance, stance.east(5).below(), false);
            for (int seed = 0; seed < 64; seed++) {
                // Control only the first roll. Target choice must reflect it in
                // the world, regardless of candidate ordering or tie-breaking.
                int desired = 3 + RandomSource.create(seed).nextInt(5);
                villager.getRandom().setSeed(seed);
                var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, current, null);
                helper.assertTrue(chosen != null && chosen.waterPos().equals(stance.east(desired).below()),
                        "ignored desired cast distance " + desired + ": " + chosen);
                distances.add(desired);
            }
            helper.assertTrue(distances.size() == 5, "fixture did not exercise all five desired distances");
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "distance choice requested a path");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void smallPondFallsBackToShortCastWithoutMoving(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos shortWater = stance.east(3).below();
        BlockPos previousWater = stance.east(4).below();
        pond(helper.getLevel(), shortWater, 0, false);
        pond(helper.getLevel(), previousWater, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var current = new FishingSiteSearch.Site(stance, previousWater, false);
            for (int seed = 0; seed < 32; seed++) {
                villager.getRandom().setSeed(seed);
                var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, current, previousWater);
                helper.assertTrue(chosen != null && chosen.waterPos().equals(shortWater) && chosen.standingPos().equals(stance),
                        "small pond did not fall back to its shorter, non-repeated target: " + chosen);
            }
            helper.assertTrue(villager.position().equals(Vec3.atBottomCenterOf(stance)), "fallback moved the villager");
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "small pond fallback requested a path");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void discoversThreeBlockCastFromExistingSafeStance(GameTestHelper helper) {
        assertOnlyStanceDiscovery(helper, 3);
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void discoversTwoBlockFallbackFromOnlySafeStance(GameTestHelper helper) {
        assertOnlyStanceDiscovery(helper, 2);
    }

    private static void assertOnlyStanceDiscovery(GameTestHelper helper, int distance) {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(distance).below();
        // Only the centre of this isolated 3x3 platform has all four supported
        // neighbours. Other cast lengths cannot satisfy discovery by moving.
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(-5, -6, -8), stance.offset(11, -1, 8))) {
            helper.getLevel().setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(-1, -1, -1), stance.offset(1, -1, 1))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var found = find(helper, villager);
            helper.assertTrue(found.standingPos().equals(stance) && found.waterPos().equals(water),
                    "did not discover the available " + distance + "-block cast: " + found);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void twoBlockOnlyPondCastsAndReusesTargetWithoutMoving(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(2).below();
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        var siteField = FishingTask.class.getDeclaredField("currentSite");
        siteField.setAccessible(true);
        ItemStack held = null;
        try {
            task.start(helper.getLevel(), villager, 0);
            siteField.set(task, new FishingSiteSearch.Site(stance, water, false));
            for (int cast = 0; cast < 4; cast++) {
                task.tick(helper.getLevel(), villager, cast * 50L);
                var bobber = villager.getFishingBobber();
                helper.assertTrue(bobber != null, "did not cast into the only two-block water target");
                var site = (FishingSiteSearch.Site)siteField.get(task);
                helper.assertTrue(site.standingPos().equals(stance) && site.waterPos().equals(water), "short cast changed the safe stance or sole target");
                if (held == null) held = villager.getItemInHand(villager.getDominantHand());
                helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == held, "short recast changed the held rod");
                for (int tick = 0; tick < 40 && !bobber.isBobbing() && !bobber.isRemoved(); tick++) bobber.tick();
                helper.assertTrue(bobber.isBobbing(), "two-block cast did not reach water");
                for (int tick = 0; tick < 30 && !bobber.isRemoved(); tick++) bobber.tick();
                helper.assertTrue(bobber.isBobbing() && bobber.position().subtract(Vec3.atCenterOf(water)).horizontalDistance() <= 0.8,
                        "two-block cast missed or drifted away from its sole target: " + bobber.position());
                helper.assertTrue(villager.position().equals(Vec3.atBottomCenterOf(stance)), "short recast moved the villager");
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "short recast requested movement");
                for (BlockPos p : BlockPos.betweenClosed(stance.offset(-1, 0, -1), stance.offset(1, 0, 1))) {
                    helper.assertTrue(helper.getLevel().getFluidState(p).isEmpty() && helper.getLevel().getFluidState(p.below()).isEmpty(),
                            "two-block fallback relaxed the dry standing footprint");
                }
                task.tick(helper.getLevel(), villager, cast * 50L + 1);
                bobber.discard();
            }
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "short recasts requested paths");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void shortCastWaitsForActualSafeStandingBlock(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(2).below();
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        var siteField = FishingTask.class.getDeclaredField("currentSite");
        siteField.setAccessible(true);
        try {
            task.start(helper.getLevel(), villager, 0);
            siteField.set(task, new FishingSiteSearch.Site(stance, water, false));
            // Within the old 0.8 radius, but still on the adjacent shore block.
            villager.setPos(Vec3.atBottomCenterOf(stance).add(0.6, 0, 0));
            task.tick(helper.getLevel(), villager, 0);
            helper.assertTrue(villager.getFishingBobber() == null && villager.getItemInHand(villager.getDominantHand()).isEmpty(),
                    "cast or equipped from the neighbouring block instead of the checked stance");
            var walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(walk != null && walk.getTarget().currentBlockPosition().equals(stance),
                    "did not retain movement to the actual safe stance");
            // This isolates the arrival gate; natural navigation has separate tests.
            villager.setPos(Vec3.atBottomCenterOf(stance));
            task.tick(helper.getLevel(), villager, 1);
            helper.assertTrue(villager.getFishingBobber() != null, "did not cast after arriving on the safe block");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void offCentreCastRejectsPostBlockedAimAndUsesClearAlternative(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos stance = prepare(helper);
        BlockPos blockedWater = stance.east(7).below();
        BlockPos clearWater = stance.west(5).below();
        pond(helper.getLevel(), blockedWater, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        villager.setLeftHanded(false);
        villager.yBodyRot = 0;
        villager.setPos(Vec3.atBottomCenterOf(stance).add(0, 0, 0.49));
        for (int y = 0; y <= 3; y++) {
            helper.getLevel().setBlock(stance.offset(2, y, 1), Blocks.STONE.defaultBlockState(), 2);
        }
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        var siteField = FishingTask.class.getDeclaredField("currentSite");
        siteField.setAccessible(true);
        var site = new FishingSiteSearch.Site(stance, blockedWater, false);
        try {
            helper.assertTrue(FishingSiteSearch.isUsable(helper.getLevel(), villager, site),
                    "centred planning arc should clear the side post");
            // Establish the real obstruction independently of preflight validation.
            villager.setItemInHand(villager.getDominantHand(), new ItemStack(Items.FISHING_ROD));
            var blocked = MCAFishingBobberEntity.cast(helper.getLevel(), villager, blockedWater);
            for (int tick = 0; tick < 40 && !blocked.isRemoved() && !blocked.isBobbing(); tick++) blocked.tick();
            helper.assertTrue(blocked.isRemoved() && !blocked.isBobbing(),
                    "off-centre control cast did not hit the side post");
            villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);

            task.start(helper.getLevel(), villager, 0);
            siteField.set(task, site);
            task.tick(helper.getLevel(), villager, 0);
            helper.assertTrue(villager.getFishingBobber() == null,
                    "fishing launched a cast whose actual origin is blocked by the side post");

            pond(helper.getLevel(), clearWater, 0, false);
            siteField.set(task, site);
            task.tick(helper.getLevel(), villager, 1);
            var chosen = (FishingSiteSearch.Site)siteField.get(task);
            var bobber = villager.getFishingBobber();
            helper.assertTrue(bobber != null && chosen.waterPos().equals(clearWater),
                    "fishing did not fall back to water clear from the actual position");
            for (int tick = 0; tick < 40 && !bobber.isRemoved() && !bobber.isBobbing(); tick++) bobber.tick();
            helper.assertTrue(bobber.isBobbing(), "off-centre alternative did not reach water");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void preferredTargetIsReusedInsteadOfTakingTwoBlockAlternative(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos preferred = stance.west(7).below();
        pond(helper.getLevel(), stance.east(2).below(), 0, false);
        pond(helper.getLevel(), preferred, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var current = new FishingSiteSearch.Site(stance, preferred, false);
            for (int seed = 0; seed < 32; seed++) {
                villager.getRandom().setSeed(seed);
                var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, current, preferred);
                helper.assertTrue(chosen != null && chosen.waterPos().equals(preferred), "avoiding a repeat took priority over a valid preferred-range cast: " + chosen);
            }
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void twoBlockFallbackIsUsedWhenPreferredWaterIsBlocked(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos shortWater = stance.east(2).below();
        pond(helper.getLevel(), shortWater, 0, false);
        pond(helper.getLevel(), stance.west(5).below(), 0, false);
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(-2, 0, -1), stance.offset(-2, 4, 1))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager,
                    new FishingSiteSearch.Site(stance, shortWater, false), shortWater);
            helper.assertTrue(chosen != null && chosen.standingPos().equals(stance) && chosen.waterPos().equals(shortWater),
                    "blocked preferred water prevented a safe short fallback: " + chosen);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void shortFallbackRejectsOneBlockTargetAndWetStandingFootprint(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(1).below();
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var site = new FishingSiteSearch.Site(stance, water, false);
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager, site), "accepted a one-block shore stance");
            helper.assertTrue(new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, site, water) == null,
                    "fallback relaxed safety to reuse a one-block shore cast");
            BlockPos shortWater = stance.east(2).below();
            pond(helper.getLevel(), shortWater, 0, false);
            var shortSite = new FishingSiteSearch.Site(stance, shortWater, false);
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager, shortSite), "short range overrode the wet standing footprint");
            helper.assertTrue(new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, shortSite, shortWater) == null,
                    "short fallback accepted a wet bank");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void blockedDesiredDistanceFallsBackToClearWater(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos blocked = stance.east(3).below();
        BlockPos clear = stance.west(7).below();
        pond(helper.getLevel(), blocked, 0, false);
        pond(helper.getLevel(), clear, 0, false);
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(2, 0, -1), stance.offset(2, 4, 1))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            villager.getRandom().setSeed(0L); // First desired-distance roll is 3.
            var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager,
                    new FishingSiteSearch.Site(stance, clear, false), null);
            helper.assertTrue(chosen != null && chosen.waterPos().equals(clear) && chosen.standingPos().equals(stance),
                    "blocked desired cast did not fall back to clear water: " + chosen);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void oneWaterTargetCanBeReusedWhenNoAlternativeFits(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        BlockPos water = stance.east(3).below();
        pond(helper.getLevel(), water, 0, false);
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var site = new FishingSiteSearch.Site(stance, water, false);
            var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager, site, water);
            helper.assertTrue(chosen != null && chosen.standingPos().equals(stance) && chosen.waterPos().equals(water),
                    "single-target pond was rejected to force variety");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void variedTargetsRejectBlockedCastsWithoutMoving(GameTestHelper helper) {
        BlockPos stance = prepare(helper);
        pond(helper.getLevel(), stance.east(6).below(), 4, true);
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(1, 0, -6), stance.offset(1, 4, 6))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawn(helper, stance);
        try {
            var chosen = new FishingSiteSearch().chooseCastSite(helper.getLevel(), villager,
                    new FishingSiteSearch.Site(stance, stance.east(5).below(), false), null);
            helper.assertTrue(chosen == null, "variation selected a target through the bank wall");
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "variation queried navigation");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingCommandRejectsMissingRodImmediatelyWithoutReplacingOtherJob(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper, prepare(helper));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FishingCommandTest"));
        try {
            villager.getInventory().clearContent();
            villager.getVillagerBrain().assignJob(Chore.CHOP, player);
            boolean handled = new VillagerCommandHandler(villager).handle(player, "fishing");
            helper.assertTrue(handled, "fishing command was not handled");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "accepted rodless fishing before validating the command's tool requirement");
            helper.assertTrue(villager.getFishingBobber() == null, "rodless command spawned a bobber");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingCommandAcceptsInventoryRodWithoutEquippingIt(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper, prepare(helper));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FishingCommandTest"));
        try {
            villager.getVillagerBrain().abandonJob();
            helper.assertTrue(new VillagerCommandHandler(villager).handle(player, "fishing"), "fishing command was not handled");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.FISH, "inventory rod was not accepted");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "command equipped rod before a site was ready");
            helper.assertTrue(villager.getInventory().countItem(Items.FISHING_ROD) == 1, "command moved or consumed rod");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingCommandAcceptsHeldRodOutsideInventory(GameTestHelper helper) {
        VillagerEntityMCA villager = spawn(helper, prepare(helper));
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FishingCommandTest"));
        ItemStack held = new ItemStack(Items.FISHING_ROD);
        try {
            villager.getVillagerBrain().abandonJob();
            villager.getInventory().clearContent();
            villager.setItemInHand(villager.getDominantHand(), held);
            new VillagerCommandHandler(villager).handle(player, "fishing");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.FISH, "held rod was incorrectly rejected");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == held, "command replaced held rod");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/moo_fishing_hillside", timeoutTicks = 1200)
    public static void mooSavedHillsideWalksDownToRiverAndFishes(GameTestHelper helper) {
        // Exact saved terrain from MCA Floor NBT Test. Moo died before an entity
        // snapshot was saved; place a fresh fisher on nearby verified solid ground.
        BlockPos start = helper.absolutePos(new BlockPos(48, 18, 48));
        BlockPos river = helper.absolutePos(new BlockPos(41, 8, 19));
        helper.assertTrue(helper.getLevel().getBlockState(start.below()).is(Blocks.GRASS_BLOCK)
                        || helper.getLevel().getBlockState(start.below()).is(Blocks.DIRT),
                "missing saved hillside support at " + start.below() + ": "
                        + helper.getLevel().getBlockState(start.below()));
        helper.assertTrue(helper.getLevel().getBlockState(river).is(Blocks.WATER),
                "missing saved river source at " + river + ": " + helper.getLevel().getBlockState(river));
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = naturalSession(helper, villager);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            try {
                var bobber = villager.getFishingBobber();
                if (bobber != null && bobber.isBobbing()) {
                    helper.assertTrue(villager.getY() <= start.getY() - 6, "did not walk downhill to the bank");
                    // The saved river surface steps by a block across the bend.
                    helper.assertTrue(Math.abs(bobber.getY() - river.getY()) <= 2.5
                                    && bobber.blockPosition().distSqr(river) <= 16 * 16,
                            "hook did not reach the saved river: bobber=" + bobber.position() + ", reference=" + river);
                    assertDryStandingFootprint(helper, villager);
                    finish(helper, villager, task);
                } else if (++ticks[0] > 1140) {
                    helper.fail("saved hillside fisher never reached river: pos=" + villager.position()
                            + ", target=" + villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                            .map(target -> target.getTarget().currentBlockPosition()).orElse(null));
                }
            } catch (RuntimeException failure) { cleanup(helper, villager, task); throw failure; }
        });
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void reachableRiverFourteenBlocksBelowIsDiscovered(GameTestHelper helper) {
        assertDownhillDiscovery(helper, 13, true);
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void riverOutsideFourteenBlockDiscoveryRemainsBounded(GameTestHelper helper) {
        assertDownhillDiscovery(helper, 14, false);
    }

    private static void assertDownhillDiscovery(GameTestHelper helper, int rise, boolean expected) {
        BlockPos base = prepare(helper);
        for (int x = -3; x <= 34; x++) {
            int height = Math.max(0, rise - Math.max(0, x));
            for (BlockPos p : BlockPos.betweenClosed(base.offset(x, -2, -7), base.offset(x, rise + 4, 7))) {
                helper.getLevel().setBlock(p, p.getY() < base.getY() + height
                        ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
            }
        }
        BlockPos river = base.east(25).below();
        pond(helper.getLevel(), river, 3, true);
        VillagerEntityMCA villager = spawn(helper, base.above(rise));
        FishingSiteSearch search = new FishingSiteSearch();
        FishingSiteSearch.Site found = null;
        try {
            for (int i = 0; i < 400 && !search.exhausted() && found == null; i++) found = search.tick(helper.getLevel(), villager);
            helper.assertTrue((found != null) == expected, "wrong downhill discovery for vertical difference " + (rise + 1));
            if (found != null) {
                helper.assertTrue(found.waterPos().getY() == river.getY(), "discovered unrelated water");
                assertSetback(helper, found);
            } else helper.assertTrue(search.exhausted(), "out-of-range search did not finish");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 1000)
    public static void walksToLakeFortyBlocksAwayAndActuallyFishes(GameTestHelper helper) {
        BlockPos start = distantLake(helper);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = naturalSession(helper, villager);
        int[] ticks = {0};
        boolean[] directedWalk = {false};
        helper.onEachTick(() -> {
            try {
                var target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                if (target != null) {
                    helper.assertTrue(target.getTarget().currentBlockPosition().getX() > start.getX() + 25,
                            "wandered locally instead of walking directly to the distant bank");
                    directedWalk[0] = true;
                    helper.assertTrue(target.getTarget() instanceof PersistentPathTarget,
                            "distant fishing destination lost persistent movement intent");
                }
                var bobber = villager.getFishingBobber();
                if (bobber != null && bobber.isBobbing()) {
                    helper.assertTrue(directedWalk[0], "never published a persistent walk toward the lake");
                    helper.assertTrue(villager.getX() > start.getX() + 30, "cast before walking to the distant bank");
                    helper.assertTrue(bobber.getX() >= start.getX() + 36, "hook did not land in the distant lake");
                    assertDryStandingFootprint(helper, villager);
                    finish(helper, villager, task);
                } else if (++ticks[0] > 940) {
                    helper.fail("did not fish at lake 40 blocks away: pos=" + villager.position()
                            + ", directedWalk=" + directedWalk[0] + ", target="
                            + (target == null ? null : target.getTarget().currentBlockPosition())
                            + ", choreStatus=" + task.getStatus());
                }
            } catch (RuntimeException failure) { cleanup(helper, villager, task); throw failure; }
        });
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void progressingLongApproachKeepsItsPersistentDestination(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos start = distantLake(helper);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        var selectedSite = new FishingSiteSearch.Site(start.east(34), start.east(39).below(), true);
        var siteField = FishingTask.class.getDeclaredField("currentSite");
        siteField.setAccessible(true);
        try {
            helper.assertTrue(FishingSiteSearch.isUsable(helper.getLevel(), villager, selectedSite), "unsafe long-walk fixture");
            task.start(helper.getLevel(), villager, 0);
            siteField.set(task, selectedSite);
            task.tick(helper.getLevel(), villager, 0);
            var original = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            for (int tick = 1; tick <= 200; tick++) {
                // Isolate the chore's journey watchdog; real navigation is covered above.
                villager.setPos(Vec3.atBottomCenterOf(start).add(tick * 0.1, 0, 0));
                task.tick(helper.getLevel(), villager, tick);
                helper.assertTrue(siteField.get(task) == selectedSite, "abandoned progressing approach at tick " + tick);
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == original,
                        "replaced or cleared the persistent destination at tick " + tick);
                helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "equipped before arrival");
            }
            for (int tick = 201; tick <= 260; tick++) task.tick(helper.getLevel(), villager, tick);
            helper.assertTrue(siteField.get(task) == null, "actually stalled approach never released its destination");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(), "stalled approach kept movement intent");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    private static BlockPos distantLake(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        GameTestTerrain.prepareFlatPath(helper, start, start.east(48));
        for (BlockPos p : BlockPos.betweenClosed(start.offset(-4, -2, -12), start.offset(52, 5, 12))) {
            helper.getLevel().setBlock(p, p.getY() < start.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        pond(helper.getLevel(), start.east(40).below(), 3, true);
        return start;
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void flowersDoNotHideSupportedBank(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(7).below(), 3, true);
        for (BlockPos feet : BlockPos.betweenClosed(start.offset(-13, 0, -13), start.offset(13, 0, 13))) {
            if (helper.getLevel().getBlockState(feet.below()).is(Blocks.STONE)) {
                helper.getLevel().setBlock(feet.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 2);
                helper.getLevel().setBlock(feet, ((feet.getX() + feet.getZ()) % 2 == 0
                        ? Blocks.SHORT_GRASS : Blocks.DANDELION).defaultBlockState(), 2);
            }
        }
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = find(helper, villager);
            helper.assertTrue(site.standingPos().getY() == start.getY(), "treated vegetation top as the standing surface");
            assertSetback(helper, site);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void wetAndCoveredSearchRespectFootprintBudget(GameTestHelper helper) throws ReflectiveOperationException {
        BlockPos start = prepare(helper);
        BlockPos water = start.east(7).below();
        pond(helper.getLevel(), water, 5, true);
        VillagerEntityMCA villager = spawn(helper, start);
        var budget = FishingSiteSearch.class.getDeclaredField("geometryBudget");
        budget.setAccessible(true);
        try {
            for (int covered = 0; covered < 2; covered++) {
                if (covered == 1) {
                    for (BlockPos roof : BlockPos.betweenClosed(water.offset(-5, 3, -5), water.offset(5, 3, 5))) {
                        helper.getLevel().setBlock(roof, Blocks.STONE.defaultBlockState(), 2);
                    }
                }
                FishingSiteSearch search = new FishingSiteSearch();
                boolean validated = false;
                for (int tick = 0; tick < 450 && !search.exhausted(); tick++) {
                    var site = search.tick(helper.getLevel(), villager);
                    int remaining = budget.getInt(search);
                    helper.assertTrue(remaining >= 0 && remaining <= 16,
                            "search exceeded full-footprint budget: remaining=" + remaining + ", covered=" + covered + ", tick=" + tick);
                    validated |= remaining < 16;
                    if (site != null) search.reject(site);
                }
                helper.assertTrue(validated, "fixture never exercised full site validation");
                helper.assertTrue(search.exhausted(), "wet search never exhausted its bounded attempt");
            }
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void multipleFishersExhaustDrySearchWithoutPathQueries(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        List<VillagerEntityMCA> villagers = new ArrayList<>();
        List<FishingSiteSearch> searches = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            villagers.add(spawn(helper, start));
            searches.add(new FishingSiteSearch());
        }
        long singleNanos = 0;
        long allNanos = 0;
        int ticks = 0;
        try {
            for (; ticks < 400 && searches.stream().anyMatch(search -> !search.exhausted()); ticks++) {
                for (int i = 0; i < searches.size(); i++) {
                    long before = System.nanoTime();
                    var site = searches.get(i).tick(helper.getLevel(), villagers.get(i));
                    long elapsed = System.nanoTime() - before;
                    if (i == 0) singleNanos += elapsed;
                    allNanos += elapsed;
                    helper.assertTrue(site == null, "dry arena yielded a fishing site");
                }
            }
            helper.assertTrue(searches.stream().allMatch(FishingSiteSearch::exhausted), "many fishers never finished bounded dry search");
            for (VillagerEntityMCA villager : villagers) {
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == 0, "dry search requested paths");
                helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "search equipped a rod");
            }
            System.out.println("[FishingSearchBudget] fishers=12 ticks=" + ticks + " singleSearchNanos=" + singleNanos
                    + " allSearchesNanos=" + allNanos + " pathQueries=0");
        } finally { villagers.forEach(VillagerEntityMCA::discard); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void noWaterNeverEquipsRodAcrossRetries(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        int starts = 0;
        try {
            for (int tick = 0; tick < 900; tick++) {
                villager.tickCount++;
                if (task.getStatus() == Behavior.Status.STOPPED && task.tryStart(helper.getLevel(), villager, tick)) starts++;
                if (task.getStatus() == Behavior.Status.RUNNING) task.tickOrStop(helper.getLevel(), villager, tick);
                helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "no-water attempt flashed a rod at tick " + tick);
                helper.assertTrue(villager.getFishingBobber() == null, "no-water attempt spawned a bobber");
            }
            helper.assertTrue(starts >= 2, "fixture did not exercise a retry");
            helper.assertTrue(villager.getInventory().countItem(Items.FISHING_ROD) == 1, "no-water retry changed rod ownership");
        } finally { task.stop(helper.getLevel(), villager, 901); villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void approachesInlandBeforeEquipping(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(9).below(), 3, true);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = naturalSession(helper, villager);
        boolean[] walked = {false};
        int[] ticks = {0};
        helper.onEachTick(() -> {
            try {
                var target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                if (target != null) {
                    walked[0] = true;
                    helper.assertTrue(target.getCloseEnoughDist() == 0, "fishing uses shoreline arrival tolerance");
                    helper.assertTrue(helper.getLevel().getFluidState(target.getTarget().currentBlockPosition()).isEmpty(), "walking destination is water");
                }
                if (!villager.getItemInHand(villager.getDominantHand()).isEmpty()) {
                    helper.assertTrue(walked[0] && villager.position().subtract(Vec3.atBottomCenterOf(start)).horizontalDistance() > 0.8,
                            "equipped before actually approaching inland site");
                    helper.assertTrue(villager.getFishingBobber() != null, "rod equipped without a cast-ready site");
                    finish(helper, villager, task);
                } else if (++ticks[0] > 230) helper.fail("never reached fishing site: " + villager.position());
            } catch (RuntimeException failure) { cleanup(helper, villager, task); throw failure; }
        });
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 360)
    public static void villagerCastsAtSelectedWaterAndStaysInland(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(9).below(), 3, true);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = naturalSession(helper, villager);
        Vec3[] stance = {null};
        ItemStack[] held = {null};
        int[] stableTicks = {0};
        int[] ticks = {0};
        helper.onEachTick(() -> {
            try {
                MCAFishingBobberEntity bobber = villager.getFishingBobber();
                if (bobber != null && bobber.isBobbing()) {
                    if (stance[0] == null) { stance[0] = villager.position(); held[0] = villager.getItemInHand(villager.getDominantHand()); }
                    assertDryStandingFootprint(helper, villager);
                }
                if (stance[0] != null) {
                    helper.assertTrue(villager.position().subtract(stance[0]).horizontalDistance() <= 0.8, "drifted toward shoreline while fishing");
                    helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == held[0], "active fishing replaced or unequipped rod");
                    for (BlockPos p : BlockPos.betweenClosed(villager.blockPosition().offset(-1, 0, -1), villager.blockPosition().offset(1, 0, 1))) {
                        helper.assertTrue(helper.getLevel().getFluidState(p).isEmpty(), "standing at water edge");
                    }
                    if (++stableTicks[0] >= 60) finish(helper, villager, task);
                }
                if (++ticks[0] > 325) helper.fail("real bobber never settled in water: " + villager.position());
            } catch (RuntimeException failure) { cleanup(helper, villager, task); throw failure; }
        });
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void siteChangeCancelsCastSafely(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(5), 2, true);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = stationarySession(helper, villager);
        try {
            MCAFishingBobberEntity original = villager.getFishingBobber();
            for (BlockPos p : BlockPos.betweenClosed(start.offset(3, -1, -2), start.offset(7, 0, 2))) {
                helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
            }
            for (int i = 0; i < 25; i++) task.tick(helper.getLevel(), villager, i + 100);
            helper.assertTrue(original.isRemoved(), "changed pond retained current cast");
            helper.assertTrue(villager.getFishingBobber() == null, "cast into removed pond");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void failedCastDoesNotSpawnBobberLoop(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(5), 2, true);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = stationarySession(helper, villager);
        int casts = 0;
        try {
            for (int i = 0; i < 260; i++) {
                var bobber = villager.getFishingBobber();
                if (bobber != null) { casts++; bobber.discard(); }
                task.tick(helper.getLevel(), villager, i + 100);
            }
            helper.assertTrue(casts <= 5, "failed flights caused repeated per-tick recasting: " + casts);
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void recoveryFoodKeepsHandOwnership(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        try {
            villager.getInventory().addItem(new ItemStack(Items.BREAD, 2));
            villager.setHealth(villager.getMaxHealth() - 1);
            task.start(helper.getLevel(), villager, 0);
            villager.tickCount = 200;
            villager.aiStep();
            helper.assertTrue(villager.isUsingRecoveryFood(), "recovery fixture never started eating");
            task.tick(helper.getLevel(), villager, 1);
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).is(Items.BREAD), "fishing took hand from recovery food");
        } finally { cleanup(helper, villager, task); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void choreStopLeavesNoBobberOrEquippedRod(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(5), 2, true);
        VillagerEntityMCA villager = spawn(helper, start);
        Session task = stationarySession(helper, villager);
        try {
            task.stop(helper.getLevel(), villager, 100);
            helper.assertTrue(villager.getFishingBobber() == null, "stopped task owns a bobber");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "stopped task retained rod");
            helper.assertTrue(villager.getInventory().countItem(Items.FISHING_ROD) == 1, "cleanup changed inventory rod count");
        } finally { villager.discard(); }
        helper.succeed();
    }

    private static Session stationarySession(GameTestHelper helper, VillagerEntityMCA villager) {
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        task.start(helper.getLevel(), villager, 0);
        for (int i = 0; i < 50 && villager.getFishingBobber() == null; i++) task.tick(helper.getLevel(), villager, i);
        helper.assertTrue(villager.getFishingBobber() != null, "stationary safe-site fixture never cast");
        return task;
    }

    private static Session naturalSession(GameTestHelper helper, VillagerEntityMCA villager) {
        Session task = new Session(helper.makeMockPlayer(GameType.SURVIVAL));
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>)(Brain<?>)villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, ImmutableList.of(Pair.of(0, new WanderOrTeleportToTargetTask()),
                Pair.of(1, new LookAtTargetSink(45, 90)), Pair.of(5, task)), java.util.Set.of(), java.util.Set.of());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        brain.setActiveActivityIfPossible(Activity.IDLE);
        villager.setNoAi(false);
        return task;
    }

    private static void cleanup(GameTestHelper helper, VillagerEntityMCA villager, Session task) {
        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime());
        villager.discard();
    }

    private static void finish(GameTestHelper helper, VillagerEntityMCA villager, Session task) {
        cleanup(helper, villager, task);
        helper.succeed();
    }

    private static final class Session extends FishingTask {
        private final Player assigningPlayer;
        Session(Player assigningPlayer) { this.assigningPlayer = assigningPlayer; }
        @Override Optional<Player> getAssigningPlayer() { return Optional.of(assigningPlayer); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void wallBlockedWaterIsRejected(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(6), 2, false);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = new FishingSiteSearch.Site(start, start.east(6), false);
            helper.assertTrue(FishingSiteSearch.isUsable(helper.getLevel(), villager, site), "clear cast control not usable");
            for (int y = 0; y <= 3; y++) helper.getLevel().setBlock(start.east(2).above(y), Blocks.STONE.defaultBlockState(), 2);
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager, site), "wall-blocked cast accepted");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void unreachableNearShoreFallsBack(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(9), 3, true);
        BlockPos covered = start.west(7);
        pond(helper.getLevel(), covered, 2, false);
        for (BlockPos p : BlockPos.betweenClosed(covered.offset(-2, 3, -2), covered.offset(2, 3, 2))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        for (BlockPos p : BlockPos.betweenClosed(start.offset(1, 0, -21), start.offset(1, 3, 21))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = find(helper, villager);
            helper.assertTrue(site.waterPos().getX() < start.getX(), "unreachable surface pond hid accessible covered pond: " + site);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void candidatePathProbeBudgetIsBounded(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(9), 3, true);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            FishingSiteSearch search = new FishingSiteSearch();
            int before = PathRequestDiagnostics.snapshot(villager).directRequests();
            for (int i = 0; i < 450 && !search.exhausted(); i++) {
                int previous = PathRequestDiagnostics.snapshot(villager).directRequests();
                var site = search.tick(helper.getLevel(), villager);
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() - previous <= 1, "more than one path probe in tick");
                if (site != null) search.reject(site);
            }
            int probes = PathRequestDiagnostics.snapshot(villager).directRequests() - before;
            helper.assertTrue(probes > 0 && probes <= 8, "expected bounded near and far path queries, saw " + probes);
            helper.assertTrue(search.exhausted(), "rejected sites reset search budget forever");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void selectedStandingBlockIsNotWaterEdge(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(9), 3, true);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = find(helper, villager);
            assertSetback(helper, site);
            var path = villager.getNavigation().createPath(site.standingPos(), 0);
            helper.assertTrue(path != null && path.canReach() && path.getEndNode() != null
                    && path.getEndNode().asBlockPos().equals(site.standingPos()), "selection is not an exactly reachable dry endpoint");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void prefersOpenWaterOverNearPuddle(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(4), 0, false);
        pond(helper.getLevel(), start.east(9), 3, true);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = find(helper, villager);
            helper.assertTrue(site.likelyOpenWater(), "near puddle displaced open-water lake: " + site);
            helper.assertTrue(site.waterPos().getX() >= start.getX() + 8, "selected puddle instead of lake");
            assertSetback(helper, site);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void shallowPondIsUsableFallback(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(6), 1, false);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var site = find(helper, villager);
            helper.assertTrue(!site.likelyOpenWater(), "shallow pond incorrectly qualified for open-water preference");
            assertSetback(helper, site);
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void unsafeShoreFootprintsAreRejected(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        pond(helper.getLevel(), start.east(6), 2, false);
        VillagerEntityMCA villager = spawn(helper, start);
        try {
            var safe = new FishingSiteSearch.Site(start, start.east(6), false);
            helper.assertTrue(FishingSiteSearch.isUsable(helper.getLevel(), villager, safe), "control dry site was not usable");
            helper.getLevel().setBlock(start.north(), Blocks.WATER.defaultBlockState(), 2);
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager, safe), "accepted water touching standing footprint");
            helper.getLevel().setBlock(start.north(), Blocks.AIR.defaultBlockState(), 2);
            helper.getLevel().setBlock(start.north().below(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager, safe), "accepted unsupported bank/pier");
        } finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void roofedWaterFallbackFindsSite(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        BlockPos water = start.east(6);
        pond(helper.getLevel(), water, 2, false);
        for (BlockPos p : BlockPos.betweenClosed(water.offset(-2, 3, -2), water.offset(2, 3, 2))) {
            helper.getLevel().setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawn(helper, start);
        try { assertSetback(helper, find(helper, villager)); }
        finally { villager.discard(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void missingChunkIsNotLoaded(GameTestHelper helper) {
        BlockPos start = prepare(helper);
        VillagerEntityMCA villager = spawn(helper, start);
        BlockPos missing = new BlockPos(29000000, start.getY(), 29000000);
        try {
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(missing.getX() >> 4, missing.getZ() >> 4) == null, "missing chunk fixture already loaded");
            helper.assertTrue(!FishingSiteSearch.isUsable(helper.getLevel(), villager,
                    new FishingSiteSearch.Site(missing, missing.east(5), false)), "unloaded site accepted");
            villager.setPos(Vec3.atBottomCenterOf(missing));
            FishingSiteSearch search = new FishingSiteSearch();
            for (int i = 0; i < 400 && !search.exhausted(); i++) search.tick(helper.getLevel(), villager);
            helper.assertTrue(search.exhausted(), "missing-chunk search never exhausted");
            helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(missing.getX() >> 4, missing.getZ() >> 4) == null, "finder loaded missing chunk");
        } finally { villager.discard(); }
        helper.succeed();
    }

    private static BlockPos prepare(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 3, 40));
        for (BlockPos p : BlockPos.betweenClosed(start.offset(-21, -2, -21), start.offset(21, 5, 21))) {
            helper.getLevel().setBlock(p, p.getY() < start.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        return start;
    }

    private static void pond(ServerLevel world, BlockPos center, int radius, boolean deep) {
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, deep ? -2 : -1, -radius), center.offset(radius, deep ? -2 : -1, radius))) {
            world.setBlock(p, Blocks.STONE.defaultBlockState(), 2);
        }
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-radius, deep ? -1 : 0, -radius), center.offset(radius, 0, radius))) {
            world.setBlock(p, Blocks.WATER.defaultBlockState(), 2);
        }
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos start) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start)).withName("Fishing Site Probe").spawn(EntitySpawnReason.STRUCTURE);
        villager.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        villager.getVillagerBrain().assignJob(Chore.FISH, helper.makeMockPlayer(GameType.SURVIVAL));
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

    private static FishingSiteSearch.Site find(GameTestHelper helper, VillagerEntityMCA villager) {
        FishingSiteSearch search = new FishingSiteSearch();
        for (int i = 0; i < 80; i++) {
            var site = search.tick(helper.getLevel(), villager);
            if (site != null) return site;
            if (search.exhausted()) break;
        }
        helper.fail("No safe fishing site discovered");
        return null;
    }

    private static void assertDryStandingFootprint(GameTestHelper helper, VillagerEntityMCA villager) {
        // Cast distance can vary; the actual actor's bank clearance must not.
        BlockPos feet = villager.blockPosition();
        for (BlockPos p : BlockPos.betweenClosed(feet.offset(-1, 0, -1), feet.offset(1, 0, 1))) {
            helper.assertTrue(helper.getLevel().getFluidState(p).isEmpty() && helper.getLevel().getFluidState(p.below()).isEmpty(),
                    "standing footprint touches the water edge: " + feet);
        }
    }

    private static void assertSetback(GameTestHelper helper, FishingSiteSearch.Site site) {
        double distance = Vec3.atCenterOf(site.waterPos()).subtract(Vec3.atCenterOf(site.standingPos())).horizontalDistance();
        helper.assertTrue(distance >= 2 && distance <= 7, "wrong cast distance: " + site);
        for (BlockPos p : BlockPos.betweenClosed(site.standingPos().offset(-1, 0, -1), site.standingPos().offset(1, 0, 1))) {
            helper.assertTrue(helper.getLevel().getFluidState(p).isEmpty(), "selected standing footprint touches water: " + site);
        }
    }
}
