package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@PrefixGameTestTemplate(false)
public final class ResidencySetHomeGameTests {
    private static final List<VillagerEntityMCA> REPLACEMENT_VILLAGERS = new ArrayList<>();
    private static long previousReplacementDayTime;

    private ResidencySetHomeGameTests() {
    }

    @BeforeBatch(batch = "mca_replaced_sleeping_home")
    public static void rememberReplacementDayTime(ServerLevel level) {
        previousReplacementDayTime = level.getDayTime();
    }

    @AfterBatch(batch = "mca_replaced_sleeping_home")
    public static void cleanReplacementSleepers(ServerLevel level) {
        for (VillagerEntityMCA villager : REPLACEMENT_VILLAGERS) {
            if (villager.isSleeping()) {
                villager.stopSleeping();
            }
            villager.getResidency().leaveVillage();
            villager.discard();
        }
        REPLACEMENT_VILLAGERS.clear();
        level.setDayTime(previousReplacementDayTime);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeClaimsReachableBed(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        assertBedReachable(helper, villager, head);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected a reachable bed");
        assertHome(helper, villager, head);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reachable HOME POI was not claimed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeClaimsUntaggedBedBlockHomePoi(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = BedPoiCompatibilityGameTests.placeUntaggedBedPoi(helper, foot, Direction.EAST);
        BlockState headState = helper.getLevel().getBlockState(head);
        helper.assertTrue(!headState.is(BlockTags.BEDS),
                "fixture bed unexpectedly belongs to #minecraft:beds");

        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        assertBedReachable(helper, villager, head);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected an untagged BedBlock HOME POI");
        assertHome(helper, villager, head);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeRejectsEnclosedBed(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        blockBedApproaches(helper, head, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));

        helper.assertTrue(!villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home accepted a bed with no reachable approach");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "failed Set Home created a HOME memory");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isEmpty(),
                "failed Set Home created a FORCED_HOME memory");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                "failed Set Home consumed the enclosed bed POI ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeChoosesReachableBedOverCloserBlockedBed(GameTestHelper helper) {
        placeFloor(helper, 1, 11, 3, 9);
        BlockPos blockedFoot = helper.absolutePos(new BlockPos(4, 1, 5));
        BlockPos blockedHead = placeBed(helper, blockedFoot, Direction.EAST);
        blockBedApproaches(helper, blockedHead, Direction.EAST);
        BlockPos reachableFoot = helper.absolutePos(new BlockPos(8, 1, 5));
        BlockPos reachableHead = placeBed(helper, reachableFoot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 8)));
        assertBedReachable(helper, villager, reachableHead);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), blockedFoot),
                "Set Home failed when a farther reachable bed was available");
        assertHome(helper, villager, reachableHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(blockedHead) == 1,
                "Set Home claimed the blocked bed");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(reachableHead) == 0,
                "Set Home did not claim the reachable bed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void failedReassignmentPreservesExistingForcedHome(GameTestHelper helper) {
        placeFloor(helper, 1, 16, 1, 5);
        BlockPos oldFoot = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos oldHead = placeBed(helper, oldFoot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 3)));
        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), oldFoot),
                "fixture could not assign initial HOME");

        BlockPos blockedFoot = helper.absolutePos(new BlockPos(13, 1, 3));
        BlockPos blockedHead = placeBed(helper, blockedFoot, Direction.EAST);
        blockBedApproaches(helper, blockedHead, Direction.EAST);

        helper.assertTrue(!villager.getResidency().trySetHome(helper.getLevel(), blockedFoot),
                "unreachable reassignment unexpectedly succeeded");
        assertHome(helper, villager, oldHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(oldHead) == 0,
                "failed reassignment released the existing HOME ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(blockedHead) == 1,
                "failed reassignment claimed the blocked HOME ticket");
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void reassignmentChoosesClosestReachableBedIncludingCurrentHome(GameTestHelper helper) {
        placeFloor(helper, 1, 11, 3, 7);
        BlockPos oldFoot = helper.absolutePos(new BlockPos(3, 1, 5));
        BlockPos oldHead = placeBed(helper, oldFoot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), oldFoot),
                "fixture could not assign initial HOME");
        BlockPos newFoot = helper.absolutePos(new BlockPos(8, 1, 5));
        BlockPos newHead = placeBed(helper, newFoot, Direction.EAST);
        assertBedReachable(helper, villager, newHead);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), oldFoot),
                "Set Home rejected the closer already-owned bed");
        assertHome(helper, villager, oldHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(oldHead) == 0,
                "reselecting the closer HOME released its ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(newHead) == 1,
                "reselecting the closer HOME claimed the farther bed");

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), newFoot),
                "Set Home rejected a closer reachable replacement bed");
        assertHome(helper, villager, newHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(oldHead) == 1,
                "reassignment did not release the previous HOME ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(newHead) == 0,
                "reassignment did not reserve the replacement HOME ticket");
        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void reselectingCurrentHomeKeepsItsPoiTicket(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "fixture could not assign initial HOME");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected the villager's already-owned bed");
        assertHome(helper, villager, head);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reselecting the current HOME released its POI ticket");
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void reselectingAutomaticHomePinsExistingAssignment(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(helper.getLevel().getPoiManager().take(
                        type -> type.is(PoiTypes.HOME), (type, pos) -> pos.equals(head), head, 1
                ).filter(head::equals).isPresent(),
                "fixture could not reserve the automatic HOME ticket");
        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), head);
        villager.getBrain().setMemory(MemoryModuleType.HOME, home);
        villager.getResidency().onHomeClaimed();
        helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.FORCED_HOME),
                "fixture HOME was already forced");

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected the villager's already-owned bed");
        helper.assertTrue(villager.getBrain().isMemoryValue(MemoryModuleType.HOME, home),
                "reselecting the automatic HOME changed its assignment");
        helper.assertTrue(villager.getBrain().isMemoryValue(MemoryModuleTypeMCA.FORCED_HOME, true),
                "Set Home did not pin the existing automatic HOME assignment");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reselecting the automatic HOME released its POI ticket");
        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void reselectingReplacedCurrentHomeClaimsItsNewPoiTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");
        replaceBed(helper, foot, head);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                    "replacing the bed did not recreate an available HOME ticket");
            assertHome(helper, owner, head);
            helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot),
                    "Set Home rejected the owner's replaced bed");
            assertHome(helper, owner, head);
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "reselecting the replaced HOME left its new POI ticket available");
            VillagerEntityMCA other = spawnVillager(helper, head.north(3));
            helper.assertTrue(!other.getResidency().trySetHome(helper.getLevel(), foot),
                    "another villager claimed the reselected HOME");
            helper.assertTrue(owner.getResidency().getHomeVillage()
                            .filter(village -> village.isResidentHomeCurrent(owner)).isPresent(),
                    "reselected HOME lost its canonical owner");
            owner.discard();
            other.discard();
        }).thenSucceed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void duplicateRememberedHomeCannotPinSomeoneElsesTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "owner failed to claim HOME");

        VillagerEntityMCA duplicate = spawnVillager(helper, head.north(3));
        duplicate.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        helper.assertTrue(!duplicate.getResidency().trySetHome(helper.getLevel(), foot),
                "a remembered HOME was accepted without owning its occupied POI ticket");
        helper.assertTrue(!duplicate.getBrain().hasMemoryValue(MemoryModuleTypeMCA.FORCED_HOME),
                "failed Set Home pinned a duplicate assignment");
        helper.assertTrue(owner.getResidency().getHomeVillage()
                        .filter(village -> village.isResidentHomeCurrent(owner)).isPresent(),
                "failed Set Home displaced the canonical owner");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "failed Set Home released the canonical HOME ticket");

        BlockPos freeFoot = helper.absolutePos(new BlockPos(6, 1, 8));
        BlockPos freeHead = placeBed(helper, freeFoot, Direction.EAST);
        helper.assertTrue(duplicate.getResidency().trySetHome(helper.getLevel(), freeFoot),
                "stale HOME memory prevented assigning a genuinely free bed");
        assertHome(helper, duplicate, freeHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reassigning the duplicate released the canonical owner's HOME ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(freeHead) == 0,
                "reassigning the duplicate failed to claim its new ticket");
        owner.discard();
        duplicate.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void staleHomeGiveUpCannotReleaseCanonicalTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");

        VillagerEntityMCA duplicate = spawnVillager(helper, head.north(3));
        duplicate.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        OneShot<VillagerEntityMCA> walk = ExtendedWalkTowardsTask.createWithFinalTarget(
                MemoryModuleType.HOME, 0.5F, 1, 0, villager -> true, villager -> { },
                (level, villager, home) -> Optional.empty());
        long now = helper.getLevel().getGameTime();
        walk.tryStart(helper.getLevel(), duplicate, now);
        duplicate.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        duplicate.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, now - 200L);
        walk.tryStart(helper.getLevel(), duplicate, now + 1L);

        helper.assertTrue(duplicate.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "unreachable stale HOME was not forgotten");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "abandoning a stale HOME released another resident's POI ticket");
        owner.discard();
        duplicate.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void staleVanillaHomeReleaseCannotFreeCanonicalTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "owner failed to claim HOME");

        VillagerEntityMCA duplicate = spawnVillager(helper, head.north(3));
        duplicate.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        duplicate.releasePoi(MemoryModuleType.HOME);

        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "vanilla HOME release bypassed MCA ownership and freed the owner's ticket");
        owner.discard();
        duplicate.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void duplicateHomeValidationCannotReleaseAwakeOwnersTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "owner did not claim bed");
        helper.assertTrue(!owner.isSleeping(), "canonical owner must remain awake");

        VillagerEntityMCA stale = spawnVillager(helper, head.north(2));
        stale.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        BlockState state = helper.getLevel().getBlockState(head);
        helper.getLevel().setBlock(head, state.setValue(BedBlock.OCCUPIED, true), 3);

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), stale, helper.getLevel().getGameTime()),
                "stale HOME validator did not run");
        helper.assertTrue(stale.getResidency().getHome().isEmpty(), "stale HOME survived validation");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "stale HOME validation released awake owner's ticket");
        stale.discard();
        owner.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void rejectedDuplicateHomeReconciliationRetiresPreviousAssignment(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos firstFoot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos firstHead = placeBed(helper, firstFoot, Direction.EAST);
        BlockPos secondFoot = helper.absolutePos(new BlockPos(7, 1, 8));
        BlockPos secondHead = placeBed(helper, secondFoot, Direction.EAST);
        VillagerEntityMCA first = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        VillagerEntityMCA second = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 8)));
        helper.assertTrue(first.getResidency().trySetHome(helper.getLevel(), firstFoot), "first claim failed");
        helper.assertTrue(second.getResidency().trySetHome(helper.getLevel(), secondFoot), "second claim failed");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(firstHead) == 0,
                "first claim did not reserve original ticket");

        first.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), secondHead));
        first.getResidency().reconcileVillageMembership();

        helper.assertTrue(first.getResidency().getHome().isEmpty(), "conflicting HOME was not rejected");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(firstHead) == 0,
                "rejected assignment released an old ticket whose current holder is unknown");
        helper.assertTrue(first.getResidency().getHomeVillage()
                        .filter(village -> village.isResidentHomeCurrent(first)).isPresent(),
                "rejected resident retained its invalid HOME index");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(secondHead) == 0,
                "conflict freed canonical resident's ticket");
        first.discard();
        second.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void rejectingStaleHomeCannotReleaseNewOccupantOfFormerBed(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos oldFoot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos oldHead = placeBed(helper, oldFoot, Direction.EAST);
        BlockPos otherFoot = helper.absolutePos(new BlockPos(7, 1, 8));
        BlockPos otherHead = placeBed(helper, otherFoot, Direction.EAST);
        VillagerEntityMCA formerOwner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        VillagerEntityMCA otherOwner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 8)));
        helper.assertTrue(formerOwner.getResidency().trySetHome(helper.getLevel(), oldFoot), "original claim failed");
        helper.assertTrue(otherOwner.getResidency().trySetHome(helper.getLevel(), otherFoot), "other claim failed");
        replaceBed(helper, oldFoot, oldHead);

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            PoiManager pois = helper.getLevel().getPoiManager();
            helper.assertTrue(pois.getFreeTickets(oldHead) == 1, "replaced bed has no available ticket");
            helper.assertTrue(pois.take(type -> type.is(PoiTypes.HOME),
                            (type, pos) -> pos.equals(oldHead), oldHead, 1).filter(oldHead::equals).isPresent(),
                    "new resident could not acquire the replacement ticket");

            formerOwner.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), otherHead));
            formerOwner.getResidency().reconcileVillageMembership();
            helper.assertTrue(formerOwner.getResidency().getHome().isEmpty(), "conflicting HOME was not rejected");
            helper.assertTrue(pois.getFreeTickets(oldHead) == 0,
                    "rejected former owner released a replacement ticket held by another claimant");
            helper.assertTrue(pois.getFreeTickets(otherHead) == 0,
                    "rejected former owner released the competing canonical HOME ticket");
            formerOwner.discard();
            otherOwner.discard();
        }).thenSucceed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void canonicalHomeReleaseStillFreesItsOwnTicket(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "owner failed to claim HOME");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");

        owner.releasePoi(MemoryModuleType.HOME);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                "canonical HOME owner could not release its own ticket");
        owner.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void unloadedFormerOwnerReconcilesHomeOnFirstTick(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA formerOwner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(formerOwner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        CompoundTag saved = formerOwner.saveWithoutId(new CompoundTag());
        formerOwner.discard();

        replaceBed(helper, foot, head);
        VillagerEntityMCA[] loaded = new VillagerEntityMCA[2];
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            VillagerEntityMCA claimant = spawnVillager(helper, head.north(3));
            helper.assertTrue(claimant.getResidency().trySetHome(helper.getLevel(), foot),
                    "replacement bed was not claimed");

            VillagerEntityMCA reloaded = VillagerFactory.newVillager(helper.getLevel()).build();
            reloaded.load(saved);
            helper.assertTrue(reloaded.getResidency().getHome().isPresent(),
                    "fixture did not restore the former owner's HOME");
            helper.assertTrue(helper.getLevel().addFreshEntity(reloaded), "former owner could not reload");
            loaded[0] = claimant;
            loaded[1] = reloaded;
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(loaded[1].getResidency().getHome().isEmpty(),
                    "reloaded villager retained a HOME already reassigned while unloaded");
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "reloaded former owner released the new claimant's POI ticket");
            loaded[1].discard();
            loaded[0].discard();
        }).thenSucceed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void duplicateHomeValidationKeepsCanonicalResidentTicket(GameTestHelper helper) {
        placeFloor(helper, 1, 10, 2, 8);
        BlockPos bell = helper.absolutePos(new BlockPos(4, 1, 5));
        helper.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        helper.assertTrue(VillageManager.get(helper.getLevel()).processBuilding(bell) == Building.validationResult.SUCCESS,
                "fixture bell did not create an MCA village");

        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot),
                "canonical owner could not claim the HOME ticket");
        helper.assertTrue(owner.getResidency().getHomeVillage()
                        .filter(village -> village.isResidentHomeCurrent(owner)).isPresent(),
                "claimed HOME was not recorded as the canonical resident assignment");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "canonical owner's HOME ticket was not claimed");

        owner.startSleeping(head);
        helper.assertTrue(owner.isSleeping(), "canonical owner did not start sleeping in its HOME");

        VillagerEntityMCA observer = spawnVillager(helper, head.relative(Direction.NORTH, 2));
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));
        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "duplicate HOME validator did not run");

        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "duplicate observer retained the canonical resident's occupied HOME");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "duplicate observer released the canonical resident's HOME ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void duplicateResidentHomeReconciliationWakesSleepingVillager(GameTestHelper helper) {
        placeFloor(helper, 1, 10, 2, 8);
        BlockPos bell = helper.absolutePos(new BlockPos(4, 1, 5));
        helper.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        helper.assertTrue(VillageManager.get(helper.getLevel()).processBuilding(bell) == Building.validationResult.SUCCESS,
                "fixture bell did not create an MCA village");

        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot),
                "canonical owner could not claim the HOME ticket");

        VillagerEntityMCA duplicate = spawnVillager(helper, head.relative(Direction.NORTH, 2));
        duplicate.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));
        duplicate.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);
        duplicate.startSleeping(head);
        helper.assertTrue(duplicate.isSleeping(), "duplicate villager did not start sleeping");

        duplicate.getResidency().reconcileVillageMembership();

        helper.assertTrue(duplicate.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "duplicate resident retained the canonical owner's HOME");
        helper.assertTrue(!duplicate.isSleeping(),
                "duplicate resident stayed asleep after its HOME was invalidated");
        helper.assertTrue(duplicate.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isEmpty(),
                "duplicate resident retained the forced marker after its HOME was invalidated");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "duplicate reconciliation released the canonical resident's HOME ticket");
        helper.succeed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacementClaimRetractsPreviousOwnersHomeMovement(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        var producer = ExtendedWalkTowardsTask.create(MemoryModuleType.HOME, 0.5F, 0, 1200,
                ignored -> false, ignored -> {});
        helper.assertTrue(producer.tryStart(helper.getLevel(), owner, helper.getLevel().getGameTime()),
                "HOME movement producer did not run");
        helper.assertTrue(owner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "HOME movement producer did not assign movement");
        helper.assertTrue(owner.getNavigation().moveTo(head.getX(), head.getY(), head.getZ(), 0.5D),
                "fixture could not start HOME navigation");
        replaceBed(helper, foot, head);
        VillagerEntityMCA claimant = spawnVillager(helper, head.north(3));

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(claimant.getResidency().trySetHome(helper.getLevel(), foot), "replacement claim failed");
            helper.assertTrue(owner.getResidency().getHome().isEmpty(), "displaced owner retained HOME");
            helper.assertTrue(owner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isEmpty(),
                    "displaced owner retained FORCED_HOME");
            helper.assertTrue(!owner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "displaced owner retained HOME-owned movement");
            helper.assertTrue(owner.getNavigation().isDone(), "displaced owner retained HOME navigation");
            assertHome(helper, claimant, head);
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "displacement released the replacement claimant's ticket");
            claimant.startSleeping(head);
            helper.assertTrue(claimant.isSleeping(), "replacement claimant could not sleep");
            claimant.stopSleeping();
            owner.discard();
            claimant.discard();
        }).thenSucceed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacementClaimWakesPreviousForcedHomeSleeper(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        owner.startSleeping(head);
        replaceBed(helper, foot, head);
        VillagerEntityMCA claimant = spawnVillager(helper, head.north(3));

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(owner.isSleeping(), "fixture lost the previous sleeper before replacement claim");
            helper.assertTrue(claimant.getResidency().trySetHome(helper.getLevel(), foot), "replacement claim failed");
            helper.assertTrue(owner.getResidency().getHome().isEmpty(), "displaced sleeper retained HOME");
            helper.assertTrue(owner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isEmpty(),
                    "displaced sleeper retained FORCED_HOME");
            helper.assertTrue(!owner.isSleeping(), "displaced sleeper stayed asleep after replacement claim");
            assertHome(helper, claimant, head);
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "waking displaced sleeper released the replacement claimant's ticket");
            claimant.startSleeping(head);
            owner.getResidency().reconcileVillageMembership();
            helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                    "later reconciliation cleared the replacement sleeper's occupied bed");
            claimant.stopSleeping();
            owner.discard();
            claimant.discard();
        }).thenSucceed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacementClaimPreservesUnrelatedMovement(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        BlockPos otherDestination = helper.absolutePos(new BlockPos(2, 1, 8));
        WalkTarget otherMovement = new WalkTarget(otherDestination, 0.5F, 0);
        owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, otherMovement);
        helper.assertTrue(owner.getNavigation().moveTo(otherDestination.getX(), otherDestination.getY(),
                otherDestination.getZ(), 0.5D), "fixture could not start unrelated navigation");
        var otherPath = owner.getNavigation().getPath();
        replaceBed(helper, foot, head);
        VillagerEntityMCA claimant = spawnVillager(helper, head.north(3));

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(claimant.getResidency().trySetHome(helper.getLevel(), foot), "replacement claim failed");
            helper.assertTrue(owner.getResidency().getHome().isEmpty(), "displaced owner retained HOME");
            helper.assertTrue(owner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                    .filter(target -> target == otherMovement).isPresent(), "displacement erased unrelated movement");
            helper.assertTrue(owner.getNavigation().getPath() == otherPath, "displacement stopped unrelated navigation");
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "displacement released the replacement claimant's ticket");
            owner.discard();
            claimant.discard();
        }).thenSucceed();
    }

    @GameTest(batch = "mca_home_claim_handoff", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacementClaimDoesNotInvalidateDifferentRememberedHome(GameTestHelper helper) {
        prepareClaimVillage(helper);
        BlockPos foot = helper.absolutePos(new BlockPos(7, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA owner = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(owner.getResidency().trySetHome(helper.getLevel(), foot), "initial claim failed");
        BlockPos otherHead = placeBed(helper, helper.absolutePos(new BlockPos(7, 1, 8)), Direction.EAST);
        helper.assertTrue(helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                (poi, pos) -> pos.equals(otherHead), otherHead, 1).isPresent(), "different HOME ticket was not acquired");
        // Model an index that still remembers the old bed while the entity has already changed HOME.
        owner.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), otherHead));
        owner.startSleeping(otherHead);
        WalkTarget otherMovement = new WalkTarget(otherHead.north(), 0.5F, 0);
        owner.getBrain().setMemory(MemoryModuleType.WALK_TARGET, otherMovement);
        replaceBed(helper, foot, head);
        VillagerEntityMCA claimant = spawnVillager(helper, head.north(3));

        helper.startSequence().thenIdle(2).thenExecute(() -> {
            helper.assertTrue(claimant.getResidency().trySetHome(helper.getLevel(), foot), "replacement claim failed");
            assertHome(helper, owner, otherHead);
            helper.assertTrue(owner.getSleepingPos().filter(otherHead::equals).isPresent(),
                    "stale index displacement woke the owner of a different HOME");
            helper.assertTrue(owner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                    .filter(target -> target == otherMovement).isPresent(), "stale index displacement erased unrelated movement");
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(otherHead) == 0,
                    "stale index displacement released the different HOME ticket");
            helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                    "stale index displacement released the replacement HOME ticket");
            owner.stopSleeping();
            owner.discard();
            claimant.discard();
        }).thenSucceed();
    }

    private static void prepareClaimVillage(GameTestHelper helper) {
        placeFloor(helper, 1, 10, 2, 9);
        BlockPos bell = helper.absolutePos(new BlockPos(4, 1, 5));
        helper.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        helper.assertTrue(VillageManager.get(helper.getLevel()).processBuilding(bell) == Building.validationResult.SUCCESS,
                "fixture bell did not create an MCA village");
    }

    private static void replaceBed(GameTestHelper helper, BlockPos foot, BlockPos head) {
        helper.getLevel().setBlock(head, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
        placeBed(helper, foot, Direction.EAST);
    }

    @GameTest(batch = "mca_replaced_sleeping_home", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 1000)
    public static void replacingClaimedBedDoesNotAllowTwoSleepingOwners(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        GameTestTerrain.prepareFlatArea(helper, center, 12, 3);
        helper.getLevel().setDayTime(13_000L);
        BlockPos bell = center.west(6);
        helper.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        helper.assertTrue(VillageManager.get(helper.getLevel()).processBuilding(bell) == Building.validationResult.SUCCESS,
                "fixture bell did not create an MCA village");

        BlockPos foot = center.east(3);
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        PoiManager poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.getCountInRange(poi -> poi.is(PoiTypes.HOME), center, 48,
                        PoiManager.Occupancy.ANY) == 1,
                "replacement fixture must contain exactly one discoverable HOME POI");

        VillagerEntityMCA owner = spawnNightVillager(helper, head.south(3), "Original Bed Owner");
        REPLACEMENT_VILLAGERS.add(owner);
        VillagerEntityMCA[] incoming = new VillagerEntityMCA[1];
        boolean[] sawReclaim = {false};
        helper.startSequence().thenWaitUntil(() -> {
            helper.assertTrue(owner.isSleeping(), "original villager did not naturally acquire HOME and sleep");
        }).thenExecute(() -> {
            helper.assertTrue(owner.getResidency().getHome()
                            .filter(home -> home.pos().equals(head)).isPresent(),
                    "natural sleeper did not remember the fixture bed as HOME");
            helper.assertTrue(owner.getResidency().getHomeVillage()
                            .filter(village -> village.isResidentHomeCurrent(owner)).isPresent(),
                    "natural sleeper was not registered as the canonical HOME owner");
            helper.assertTrue(poiManager.getFreeTickets(head) == 0, "natural sleeper never claimed the HOME ticket");

            // Remove and replace within one tick, without editing Brain memories or POI tickets.
            helper.getLevel().setBlock(head, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(foot, Blocks.AIR.defaultBlockState(), 3);
            placeBed(helper, foot, Direction.EAST);
        }).thenIdle(2).thenExecute(() -> {
            helper.assertTrue(poiManager.getFreeTickets(head) == 1,
                    "replacing the bed did not recreate an available HOME ticket");
            incoming[0] = spawnNightVillager(helper, head.north(3), "Replacement Bed Claimant");
            REPLACEMENT_VILLAGERS.add(incoming[0]);
        }).thenExecuteFor(400, () -> {
            VillagerEntityMCA claimant = incoming[0];
            boolean reclaimed = claimant.getResidency().getHome()
                    .filter(home -> home.pos().equals(head)).isPresent();
            sawReclaim[0] |= reclaimed;
            boolean bothSleepingInBed = owner.getSleepingPos().filter(head::equals).isPresent()
                    && claimant.getSleepingPos().filter(head::equals).isPresent();
            if (bothSleepingInBed) {
                String failure = "bed replacement allowed two natural sleepers in one bed; originalHome="
                        + owner.getResidency().getHome() + "; claimantHome=" + claimant.getResidency().getHome()
                        + "; originalCurrent=" + owner.getResidency().getHomeVillage()
                        .map(village -> village.isResidentHomeCurrent(owner))
                        + "; claimantCurrent=" + claimant.getResidency().getHomeVillage()
                        .map(village -> village.isResidentHomeCurrent(claimant))
                        + "; originalTicks=" + owner.tickCount + "; freeTickets=" + poiManager.getFreeTickets(head);
                owner.stopSleeping();
                claimant.stopSleeping();
                owner.discard();
                claimant.discard();
                helper.fail(failure);
            }
        }).thenWaitUntil(() -> {
            helper.assertTrue(incoming[0].getSleepingPos().filter(head::equals).isPresent(),
                    "replacement claimant did not naturally sleep in its claimed bed");
        }).thenExecute(() -> {
            helper.assertTrue(sawReclaim[0], "replacement claimant never acquired the recreated HOME ticket");
            helper.assertTrue(poiManager.getFreeTickets(head) == 0, "replacement claimant lost its HOME ticket");
            helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                    "old owner cleared the replacement sleeper's occupied bed");
            owner.stopSleeping();
            incoming[0].stopSleeping();
            owner.discard();
            incoming[0].discard();
        }).thenSucceed();
    }

    private static VillagerEntityMCA spawnNightVillager(GameTestHelper helper, BlockPos feet, String name) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName(name)
                .spawn(MobSpawnType.STRUCTURE);
        villager.setOnGround(true);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().setSchedule(SchedulesMCA.DEFAULT);
        villager.getBrain().updateActivityFromSchedule(helper.getLevel().getDayTime(), helper.getLevel().getGameTime());
        return villager;
    }

    private static void assertHome(GameTestHelper helper, VillagerEntityMCA villager, BlockPos expectedHome) {
        GlobalPos home = villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                .orElseThrow(() -> new AssertionError("villager has no HOME"));
        helper.assertTrue(home.pos().equals(expectedHome), "villager selected the wrong HOME");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isPresent(),
                "Set Home did not mark the HOME as forced");
    }

    private static void assertBedReachable(GameTestHelper helper, VillagerEntityMCA villager, BlockPos head) {
        BedApproachTarget target = BedApproachTarget.create(helper.getLevel(), head)
                .orElseThrow(() -> new AssertionError("fixture did not create a BedApproachTarget"));
        var pathTargets = target.getPathTargets(villager);
        helper.assertTrue(!pathTargets.isEmpty(), "fixture bed had no valid approach targets");
        var path = villager.getNavigation().createPath(pathTargets, 0);
        helper.assertTrue(path != null, "fixture navigation returned no path to the bed approaches");
        helper.assertTrue(path.canReach(), "fixture navigation could not reach the bed approaches");
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet) {
        helper.getLevel().getChunk(feet);
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());
        return villager;
    }

    private static void placeFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feet = helper.absolutePos(new BlockPos(x, 1, z));
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }

    private static void blockBedApproaches(GameTestHelper helper, BlockPos head, Direction facing) {
        BlockPos foot = head.relative(facing.getOpposite());
        helper.getLevel().setBlock(head.relative(facing), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.relative(facing.getClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.relative(facing.getCounterClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getCounterClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
    }
}
