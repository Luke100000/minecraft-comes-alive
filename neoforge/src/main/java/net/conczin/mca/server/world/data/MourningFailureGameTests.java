package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.EnterGraveyardTask;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.GrieveTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.BeforeBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.conczin.mca.gametest.GameTestBrain;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class MourningFailureGameTests {
    private static final String BATCH = "mca_mourning_failure_policy";
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final List<Zombie> ZOMBIES = new ArrayList<>();
    private static long previousDayTime;
    private static boolean previousMourning;
    private static boolean previousTeleporting;

    private MourningFailureGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void prepareMourningBatch(ServerLevel level) {
        previousDayTime = getDayTime(level);
        previousMourning = Config.getInstance().enableMourning;
        previousTeleporting = Config.getInstance().allowVillagerTeleporting;
        setDayTime(level, 6_000L);
        Config.getInstance().enableMourning = true;
        Config.getInstance().allowVillagerTeleporting = false;
    }

    @AfterBatch(batch = BATCH)
    public static void cleanUpMourningBatch(ServerLevel level) {
        ZOMBIES.forEach(Zombie::discard);
        ZOMBIES.clear();
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        setDayTime(level, previousDayTime);
        Config.getInstance().enableMourning = previousMourning;
        Config.getInstance().allowVillagerTeleporting = previousTeleporting;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena",
            timeoutTicks = 2_000)
    public static void blockedVillageMourningEndsAmbientAssignmentAndDefersPersonalAssignment(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        encloseGrave(helper.getLevel(), grave);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "Blocked Ambient Mourner");
        VillagerEntityMCA personal = spawnVillager(helper, grave.offset(6, 0, 3), "Blocked Personal Mourner");
        assignAmbient(helper, grave, ambient);
        Mourning.start(personal, grave);
        for (VillagerEntityMCA mourner : List.of(ambient, personal)) {
            helper.assertTrue(new EnterGraveyardTask().tryStart(helper.getLevel(), mourner,
                            helper.getLevel().getGameTime()), "fixture could not select a standing position");
            helper.assertTrue(mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION),
                    "fixture needs a valid standing position behind the walls");
        }
        long began = helper.getLevel().getGameTime();
        boolean[] sawFailure = {false};
        boolean[] finished = {false};
        helper.onEachTick(() -> {
            if (finished[0]) return;
            long now = helper.getLevel().getGameTime();
            sawFailure[0] |= ambient.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            boolean ambientEnded = !ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE);
            Long personalRetry = personal.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).orElse(null);
            if (ambientEnded && personalRetry != null) {
                helper.assertTrue(sawFailure[0], "ambient mourning ended without exercising navigation failure");
                helper.assertTrue(isAssignedTo(personal, grave), "failed personal mourning lost its exact grave");
                helper.assertTrue(personalRetry > now + 4_700L && personalRetry <= now + 4_800L,
                        "personal path failure must defer for four minutes");
                helper.assertTrue(!ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION)
                                && !ambient.getBrain().isActive(ActivitiesMCA.GRIEVE),
                        "finished ambient mourning retained its position or activity");
                finished[0] = true;
                helper.runAfterDelay(100, () -> {
                    helper.assertTrue(!ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE),
                            "ambient mourning restarted its failed assignment");
                    helper.assertTrue(isAssignedTo(personal, grave), "deferred personal mourning lost its assignment");
                    helper.succeed();
                });
            } else if (now - began > 1_700L) {
                helper.fail("unreachable ambient mourning retained its grave instead of ending the assignment"
                        + ", ambientPos=" + ambient.position()
                        + ", ambientDestination=" + ambient.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        + ", ambientWalk=" + ambient.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + ", ambientFailure=" + ambient.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        + ", ambientPath=" + ambient.getNavigation().getPath()
                        + ", personalPos=" + personal.position()
                        + ", personalDestination=" + personal.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        + ", personalWalk=" + personal.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + ", personalFailure=" + personal.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        + ", personalRetry=" + personalRetry);
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignSamePositionMovementSurvivesMourningExit(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "pause", Mourning::pause, true, -1L, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignSamePositionMovementSurvivesUnsafeDeferral(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "unsafe deferral", Mourning::deferUnsafe, true, 200L, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignSamePositionMovementSurvivesMourningRetry(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "retry", Mourning::retry, true, 1_200L, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignSamePositionMovementSurvivesMourningFinish(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "finish", Mourning::finish, false, -1L, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignDifferentPositionMovementSurvivesMourningFinish(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "different-position finish", Mourning::finish,
                false, -1L, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignMovementSurvivesInvalidGraveCancellation(GameTestHelper helper) {
        verifyForeignMovementSurvives(helper, "invalid grave", villager -> {
            helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), villager,
                            helper.getLevel().getGameTime()),
                    "invalid grave unexpectedly started scheduled mourning");
        }, false, -1L, false, true);
    }

    private static void verifyForeignMovementSurvives(GameTestHelper helper, String transition,
                                                       Consumer<VillagerEntityMCA> exit, boolean retainsAssignment,
                                                       long retryDelay, boolean differentPosition, boolean invalidateGrave) {
        BlockPos grave = prepareArena(helper);
        long now = helper.getLevel().getGameTime();
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Foreign Movement " + transition);
        Mourning.start(mourner, grave);
        helper.assertTrue(isAssignedTo(mourner, grave) && mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "fixture did not start mourning");
        GlobalPos stand = GlobalPos.of(helper.getLevel().dimension(), grave.east());
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION, stand);
        if (invalidateGrave) {
            helper.getLevel().setBlock(grave, Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(Mourning.isKnownInvalidSite(mourner), "fixture grave was not invalidated");
        }

        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(),
                differentPosition ? grave.east(3) : stand.pos());
        OneShot<VillagerEntityMCA> foreignProducer = publishHome(helper, mourner, home, now);
        WalkTarget foreignWalk = mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElseThrow();
        helper.assertTrue(foreignWalk.getTarget().currentBlockPosition().equals(home.pos()),
                "foreign producer did not publish the expected physical target");
        Path foreignPath = admitCurrentWalk(helper, mourner, now);
        BlockPosTracker foreignLook = new BlockPosTracker(grave);
        mourner.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, foreignLook);
        long failureSince = now - 25L;
        WalkTargetFailureMemory.record(mourner, home, failureSince);
        assertFailureProjection(helper, mourner, home, failureSince, "fixture");

        exit.accept(mourner);

        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(target -> target == foreignWalk).isPresent(),
                transition + " retracted the foreign walk command");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                        .filter(target -> target == foreignLook).isPresent(),
                transition + " erased the foreign look at the grave");
        assertActivePath(helper, mourner, foreignPath, transition + " stopped foreign navigation");
        assertFailureProjection(helper, mourner, home, failureSince, transition);
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.HOME).filter(home::equals).isPresent(),
                transition + " erased the foreign destination");
        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION)
                        && !mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                transition + " left the mourning position or activity active");
        helper.assertTrue(retainsAssignment ? isAssignedTo(mourner, grave)
                        : !mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE),
                transition + " changed the assignment policy");
        helper.assertTrue(retryDelay < 0L
                        ? !mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        : mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        .filter(deadline -> deadline == now + retryDelay).isPresent(),
                transition + " changed the retry policy");

        assertForeignGateRetained(helper, mourner, foreignProducer, home, failureSince, now, transition);
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void ownedMourningMovementStopsOnPause(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        long now = helper.getLevel().getGameTime();
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Owned Movement Pause");
        Mourning.start(mourner, grave);
        GlobalPos stand = GlobalPos.of(helper.getLevel().dimension(), grave.east());
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION, stand);
        var producer = VillagerTasksMCA.getGrievingPackage().get(2).getSecond();
        helper.assertTrue(producer.tryStart(helper.getLevel(), mourner, now)
                        && ExtendedWalkTowardsTask.ownsWalkTarget(mourner, MemoryModuleTypeMCA.MOURNING_POSITION),
                "fixture did not publish the mourning command");
        admitCurrentWalk(helper, mourner, now);
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                        .filter(target -> target.currentBlockPosition().equals(grave)).isPresent(),
                "fixture did not publish the mourning look");
        WalkTargetFailureMemory.record(mourner, stand, now - 25L);
        assertFailureProjection(helper, mourner, stand, now - 25L, "fixture");

        Mourning.pause(mourner);

        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && !mourner.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                        && mourner.getNavigation().isDone(),
                "pause left owned movement active");
        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "pause left the mourning look active");
        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        && !mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET),
                "pause left the owned failure projection active");
        helper.assertTrue(isAssignedTo(mourner, grave)
                        && !mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION)
                        && !mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "pause did not preserve assignment while ending its approach");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void standSelectionPreservesForeignFailure(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        long now = helper.getLevel().getGameTime();
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Foreign Failure During Stand Selection");
        Mourning.start(mourner, grave);
        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION),
                "fixture already had a standing position");
        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), grave.east(3));
        OneShot<VillagerEntityMCA> foreignProducer = publishHome(helper, mourner, home, now);
        WalkTarget foreignWalk = mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElseThrow();
        Path foreignPath = admitCurrentWalk(helper, mourner, now);
        long failureSince = now - 25L;
        WalkTargetFailureMemory.record(mourner, home, failureSince);
        assertFailureProjection(helper, mourner, home, failureSince, "fixture");

        helper.assertTrue(new EnterGraveyardTask().tryStart(helper.getLevel(), mourner, now),
                "stand selection did not start");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(position -> position.dimension().equals(helper.getLevel().dimension())
                                && !position.pos().equals(grave)
                                && position.pos().distSqr(grave) <= 3.0D).isPresent(),
                "stand selection did not publish a graveside standing position");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(target -> target == foreignWalk).isPresent(),
                "stand selection erased the foreign command");
        assertActivePath(helper, mourner, foreignPath, "stand selection stopped the foreign path");
        assertFailureProjection(helper, mourner, home, failureSince, "stand selection");
        assertForeignGateRetained(helper, mourner, foreignProducer, home, failureSince, now, "stand selection");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void foreignMovementSurvivesGravesideStart(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        long now = helper.getLevel().getGameTime();
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Foreign Graveside Movement");
        Mourning.start(mourner, grave);
        var grieving = VillagerTasksMCA.getGrievingPackage();
        helper.assertTrue(grieving.get(0).getSecond().tryStart(helper.getLevel(), mourner, now),
                "fixture did not run the registered standing-position behavior");
        arriveAtAssignedStand(helper, mourner);

        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), grave.east(6));
        OneShot<VillagerEntityMCA> foreignProducer = publishHome(helper, mourner, home, now);
        WalkTarget foreignWalk = mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElseThrow();
        Path foreignPath = admitCurrentWalk(helper, mourner, now);
        long failureSince = now - 25L;
        WalkTargetFailureMemory.record(mourner, home, failureSince);
        assertFailureProjection(helper, mourner, home, failureSince, "graveside fixture");

        // The graveside behavior may defer starting, but it must not cancel the preempting command.
        grieving.get(1).getSecond().tryStart(helper.getLevel(), mourner, now + 1L);

        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(target -> target == foreignWalk).isPresent(),
                "graveside start retracted foreign movement");
        assertActivePath(helper, mourner, foreignPath, "graveside start stopped foreign navigation");
        helper.assertTrue(((MCAGroundPathNavigation) mourner.getNavigation()).hasAdmittedWalkTarget(foreignWalk),
                "graveside start retired the foreign command's admitted route");
        assertFailureProjection(helper, mourner, home, failureSince, "graveside start");
        helper.assertTrue(isAssignedTo(mourner, grave), "graveside start lost the grave assignment");
        assertForeignGateRetained(helper, mourner, foreignProducer, home, failureSince, now + 1L, "graveside start");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void ownedMourningMovementStopsAtGravesideStart(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        long now = helper.getLevel().getGameTime();
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Owned Graveside Arrival");
        Mourning.start(mourner, grave);
        var grieving = VillagerTasksMCA.getGrievingPackage();
        helper.assertTrue(grieving.get(0).getSecond().tryStart(helper.getLevel(), mourner, now),
                "fixture did not run the registered standing-position behavior");
        helper.assertTrue(grieving.get(2).getSecond().tryStart(helper.getLevel(), mourner, now)
                        && ExtendedWalkTowardsTask.ownsWalkTarget(mourner, MemoryModuleTypeMCA.MOURNING_POSITION),
                "fixture did not publish the registered mourning approach");
        WalkTarget ownedWalk = mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElseThrow();
        Path ownedPath = admitCurrentWalk(helper, mourner, now);

        // Model crossing into the assigned block before the next sink tick retires the approach.
        arriveAtAssignedStand(helper, mourner);
        assertActivePath(helper, mourner, ownedPath, "fixture retired its approach before graveside start");
        helper.assertTrue(((MCAGroundPathNavigation) mourner.getNavigation()).hasAdmittedWalkTarget(ownedWalk)
                        && mourner.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(target -> target == ownedWalk).isPresent(),
                "fixture lost ownership before the graveside arrival handoff");
        helper.assertTrue(grieving.get(1).getSecond().tryStart(helper.getLevel(), mourner, now + 1L),
                "graveside behavior did not start after the owned approach arrived");

        helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && !mourner.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                        && mourner.getNavigation().isDone()
                        && !((MCAGroundPathNavigation) mourner.getNavigation()).hasAdmittedWalkTarget(ownedWalk),
                "graveside start left the owned approach running after arrival");
        helper.assertTrue(isAssignedTo(mourner, grave) && EnterGraveyardTask.isAtMourningSite(mourner),
                "graveside arrival discarded the assigned grave or standing position");
        helper.succeed();
    }

    private static void arriveAtAssignedStand(GameTestHelper helper, VillagerEntityMCA mourner) {
        GlobalPos stand = mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION).orElseThrow();
        Vec3 arrival = Vec3.atBottomCenterOf(stand.pos());
        mourner.setPos(arrival.x, arrival.y, arrival.z);
        helper.assertTrue(mourner.getBrain().isActive(ActivitiesMCA.GRIEVE)
                        && !Mourning.isTemporarilyBlocked(mourner)
                        && !Mourning.isAssignedGraveUnsafe(mourner)
                        && EnterGraveyardTask.isAtMourningSite(mourner),
                "fixture did not meet the graveside behavior's existing start conditions");
    }

    private static OneShot<VillagerEntityMCA> publishHome(GameTestHelper helper, VillagerEntityMCA villager,
                                                        GlobalPos home, long now) {
        villager.getBrain().setMemory(MemoryModuleType.HOME, home);
        OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 1_200, ignored -> true, ignored -> {});
        helper.assertTrue(producer.tryStart(helper.getLevel(), villager, now)
                        && ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME)
                        && !ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleTypeMCA.MOURNING_POSITION),
                "fixture did not publish a distinct HOME command");
        return producer;
    }

    private static Path admitCurrentWalk(GameTestHelper helper, VillagerEntityMCA villager, long now) {
        // Newly spawned actors have not yet received their first on-ground update.
        villager.setOnGround(true);
        WalkTarget command = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElseThrow();
        MCAGroundPathNavigation navigation = (MCAGroundPathNavigation) villager.getNavigation();
        helper.assertTrue(new WanderOrTeleportToTargetTask().tryStart(helper.getLevel(), villager, now),
                "fixture could not start the core movement sink");
        helper.assertTrue(navigation.hasAdmittedWalkTarget(command),
                "fixture movement command was not actually admitted by navigation");
        Path path = navigation.getPath();
        helper.assertTrue(path != null && !path.isDone() && path.getNodeCount() >= 2,
                "fixture did not start a nontrivial physical path");
        assertActivePath(helper, villager, path, "fixture has no admitted active path");
        return path;
    }

    private static void assertActivePath(GameTestHelper helper, VillagerEntityMCA villager, Path path, String message) {
        helper.assertTrue(!villager.getNavigation().isDone() && villager.getNavigation().getPath() == path
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.PATH)
                        .filter(remembered -> remembered == path).isPresent(), message);
    }

    private static void assertFailureProjection(GameTestHelper helper, VillagerEntityMCA villager,
                                                GlobalPos destination, long since, String context) {
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(value -> value == since).isPresent()
                        && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                        .filter(destination::equals).isPresent(),
                context + " lost or changed the failure projection");
    }

    private static void assertForeignGateRetained(GameTestHelper helper, VillagerEntityMCA villager,
                                                  OneShot<VillagerEntityMCA> producer, GlobalPos destination,
                                                  long since, long now, String context) {
        // A retained shared projection alone does not prove that the HOME retry gate survived.
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
        helper.assertTrue(producer.tryStart(helper.getLevel(), villager, now + 1L),
                context + " prevented the foreign producer from resuming");
        assertFailureProjection(helper, villager, destination, since, context + " foreign producer resumed");
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void expiredMourningFailureEndsOwnedMovementEvenWithActiveWalkTarget(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        encloseGrave(helper.getLevel(), grave);
        long now = helper.getLevel().getGameTime();
        GlobalPos standingPosition = GlobalPos.of(helper.getLevel().dimension(), grave.east());
        for (boolean ambient : List.of(true, false)) {
            VillagerEntityMCA mourner = spawnVillager(helper,
                    ambient ? grave.east(6) : grave.offset(6, 0, 3),
                    ambient ? "Expired Ambient Mourner" : "Expired Personal Mourner");
            if (ambient) {
                Mourning.startAmbient(mourner, grave);
            } else {
                Mourning.start(mourner, grave);
            }
            mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION, standingPosition);
            var producer = VillagerTasksMCA.getGrievingPackage().get(2).getSecond();
            helper.assertTrue(producer.tryStart(helper.getLevel(), mourner, now)
                            && ExtendedWalkTowardsTask.ownsWalkTarget(mourner, MemoryModuleTypeMCA.MOURNING_POSITION),
                    "grieving producer did not publish the owned blocked approach");

            mourner.setOnGround(true);
            Path directProbe = MCAGroundPathNavigation.probePath(mourner.getNavigation(), standingPosition.pos(), 0);
            helper.assertTrue(directProbe == null || !directProbe.canReach(),
                    "expiry fixture must have a blocked logical standing position");
            // The sink can admit a reachable fallback prefix without reaching the logical stand.
            admitCurrentWalk(helper, mourner, now);
            WalkTargetFailureMemory.record(mourner, standingPosition, now - 1_202L);
            helper.assertTrue(producer.tryStart(helper.getLevel(), mourner, now + 1L),
                    "grieving producer did not inspect its expired owned path failure");
            helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "expired mourning left its active owned movement running");
            helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleType.PATH)
                            && mourner.getNavigation().isDone(),
                    "expired mourning left its previously admitted path running");
            if (ambient) {
                helper.assertTrue(!mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE),
                        "expired ambient mourning retained its grave assignment");
            } else {
                helper.assertTrue(isAssignedTo(mourner, grave)
                                && mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                                .filter(retryAt -> retryAt == helper.getLevel().getGameTime() + 4_800L).isPresent(),
                        "expired personal mourning did not defer its original grave for four minutes");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void ambientClassificationSurvivesRefreshReloadAndPersonalReassignment(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "Persistent Ambient Mourner");
        assignAmbient(helper, grave, ambient);
        Mourning.pause(ambient);
        ambient.refreshBrain(helper.getLevel());
        helper.assertTrue(isAmbient(ambient) && isAssignedTo(ambient, grave),
                "Brain refresh lost the ambient assignment classification");
        VillagerEntityMCA loaded = reloadVillager(helper, saveVillager(ambient));
        helper.assertTrue(isAmbient(loaded) && isAssignedTo(loaded, grave),
                "save/load lost the ambient assignment classification");

        Mourning.start(loaded, grave);
        helper.assertTrue(!isAmbient(loaded), "personal reassignment retained the ambient marker");
        long now = helper.getLevel().getGameTime();
        Mourning.unreachable(loaded);
        long retryAt = loaded.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).orElseThrow();
        helper.assertTrue(retryAt == now + 4_800L && isAssignedTo(loaded, grave),
                "personal failure did not preserve its grave with a four-minute retry");
        VillagerEntityMCA deferred = reloadVillager(helper, saveVillager(loaded));
        helper.assertTrue(!isAmbient(deferred) && isAssignedTo(deferred, grave)
                        && deferred.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        .filter(deadline -> deadline == retryAt).isPresent(),
                "reload lost the personal retry deadline or changed its classification");
        helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), deferred, now),
                "reloaded personal mourning ignored its retry deadline");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void legacyAssignmentWithoutAmbientMarkerRetainsPersonalIntent(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "Legacy Mourner");
        Mourning.startAmbient(ambient, grave);
        CompoundTag saved = saveVillager(ambient);
        saved.getCompound("Brain").orElseThrow().getCompound("memories").orElseThrow().remove("mca:mourning_ambient");
        VillagerEntityMCA loaded = reloadVillager(helper, saved);
        Mourning.unreachable(loaded);
        helper.assertTrue(!isAmbient(loaded) && isAssignedTo(loaded, grave)
                        && loaded.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_RETRY_AT),
                "a legacy assignment without a classification must preserve personal intent");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void interruptionAndSafetyDeferralPreserveAmbientClassification(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "Interrupted Ambient Mourner");
        Mourning.startAmbient(ambient, grave);
        ambient.getBrain().setMemory(MemoryModuleTypeMCA.STAYING, true);
        Mourning.pause(ambient);
        Mourning.resume(ambient);
        helper.assertTrue(isAssignedTo(ambient, grave) && isAmbient(ambient)
                        && !ambient.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "forced staying lost the assignment or restarted mourning");
        ambient.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);
        Mourning.resume(ambient);
        Mourning.deferUnsafe(ambient);
        helper.assertTrue(isAssignedTo(ambient, grave) && isAmbient(ambient)
                        && ambient.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        .filter(deadline -> deadline == helper.getLevel().getGameTime() + 200L).isPresent(),
                "safety deferral was treated as terminal ambient path failure");
        Mourning.finish(ambient);
        helper.assertTrue(!isAmbient(ambient), "finished mourning retained its ambient marker");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void scheduledRetryPreservesDeadlineUntilEligible(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        GlobalPos site = GlobalPos.of(helper.getLevel().dimension(), grave);
        List<VillagerEntityMCA> mourners = List.of(
                spawnVillager(helper, grave.east(6), "Scheduled Personal Mourner"),
                spawnVillager(helper, grave.offset(6, 0, 3), "Scheduled Ambient Mourner"));
        long deadline = helper.getLevel().getGameTime() + 20L;
        for (int i = 0; i < mourners.size(); i++) {
            VillagerEntityMCA mourner = mourners.get(i);
            mourner.setNoAi(true);
            if (i == 0) Mourning.start(mourner, grave);
            else Mourning.startAmbient(mourner, grave);
            Mourning.pause(mourner);
            mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, deadline);
            helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                    "scheduled mourning ignored a future deadline");
            assertScheduledRetry(helper, mourner, site, i == 1, deadline);
            mourner.getBrain().setMemory(MemoryModuleTypeMCA.STAYING, true);
        }

        helper.runAfterDelay(20, () -> {
            long now = helper.getLevel().getGameTime();
            helper.assertTrue(now >= deadline, "fixture has not reached the retry deadline");
            helper.assertTrue(Mourning.isMournableTombstone(helper.getLevel(), grave)
                            && Mourning.isSafeToMourn(helper.getLevel(), grave),
                    "fixture grave is not safe and occupied");
            for (int i = 0; i < mourners.size(); i++) {
                VillagerEntityMCA mourner = mourners.get(i);
                helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), mourner, now),
                        "scheduled mourning interrupted forced staying at its deadline");
                assertScheduledRetry(helper, mourner, site, i == 1, deadline);
                mourner.getBrain().eraseMemory(MemoryModuleTypeMCA.STAYING);
                helper.assertTrue(!Mourning.isTemporarilyBlocked(mourner), "fixture remained blocked after staying");
                helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), mourner, now),
                        "eligible scheduled mourning did not resume when staying ended");
                assertScheduledResumption(helper, mourner, site, i == 1);
                // Re-arm a due assignment for the independent unsafe-grave branch.
                Mourning.pause(mourner);
                mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, now);
                assertScheduledRetry(helper, mourner, site, i == 1, now);
            }

            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
            if (zombie == null) throw new IllegalStateException("failed to create zombie");
            ZOMBIES.add(zombie);
            BlockPos zombiePos = grave.north(3);
            zombie.snapTo(zombiePos.getX() + 0.5D, zombiePos.getY(), zombiePos.getZ() + 0.5D);
            zombie.setNoAi(true);
            helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "could not admit unsafe-grave fixture zombie");
            helper.assertTrue(!Mourning.isSafeToMourn(helper.getLevel(), grave), "fixture grave is not unsafe");
            try {
                for (int i = 0; i < mourners.size(); i++) {
                    VillagerEntityMCA mourner = mourners.get(i);
                    helper.assertTrue(!Mourning.isTemporarilyBlocked(mourner),
                            "fixture is blocked by panic or attack instead of grave safety");
                    helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), mourner, now),
                            "scheduled mourning started at an unsafe grave");
                    assertScheduledRetry(helper, mourner, site, i == 1, now + 200L);
                }
            } finally {
                zombie.discard();
            }

            helper.runAfterDelay(1, () -> {
                long polledAt = helper.getLevel().getGameTime();
                helper.assertTrue(polledAt < now + 200L, "fixture skipped the unsafe retry window");
                helper.assertTrue(Mourning.isSafeToMourn(helper.getLevel(), grave), "fixture grave remained unsafe");
                for (int i = 0; i < mourners.size(); i++) {
                    VillagerEntityMCA mourner = mourners.get(i);
                    helper.assertTrue(!new GrieveTask().tryStart(helper.getLevel(), mourner, polledAt),
                            "safe grave prematurely consumed the safety retry deadline");
                    assertScheduledRetry(helper, mourner, site, i == 1, now + 200L);
                }
            });

            helper.runAfterDelay(200, () -> {
                long resumedAt = helper.getLevel().getGameTime();
                helper.assertTrue(resumedAt >= now + 200L, "fixture has not reached the safety retry deadline");
                for (int i = 0; i < mourners.size(); i++) {
                    VillagerEntityMCA mourner = mourners.get(i);
                    helper.assertTrue(new GrieveTask().tryStart(helper.getLevel(), mourner, resumedAt),
                            "eligible scheduled mourning did not resume");
                    assertScheduledResumption(helper, mourner, site, i == 1);
                }
                helper.succeed();
            });
        });
    }

    private static void assertScheduledRetry(GameTestHelper helper, VillagerEntityMCA mourner,
                                             GlobalPos site, boolean ambient, long deadline) {
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                        .filter(site::equals).isPresent() && isAmbient(mourner) == ambient
                        && !mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "scheduled retry changed assignment, classification or inactive state");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        .filter(value -> value == deadline).isPresent(),
                "scheduled retry changed the exact deadline");
    }

    private static void assertScheduledResumption(GameTestHelper helper, VillagerEntityMCA mourner,
                                                   GlobalPos site, boolean ambient) {
        helper.assertTrue(mourner.getBrain().isActive(ActivitiesMCA.GRIEVE)
                        && mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                        .filter(site::equals).isPresent()
                        && isAmbient(mourner) == ambient
                        && !mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_RETRY_AT),
                "scheduled resumption lost assignment or classification, or retained its deadline");
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void missingStandingPositionsEndAmbientButDeferPersonalMourning(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                for (int y = -1; y <= 2; y++) {
                    helper.getLevel().setBlock(grave.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "No Ambient Standing Position");
        VillagerEntityMCA personal = spawnVillager(helper, grave.offset(6, 0, 3), "No Personal Standing Position");
        Mourning.startAmbient(ambient, grave);
        Mourning.start(personal, grave);
        long now = helper.getLevel().getGameTime();
        new EnterGraveyardTask().tryStart(helper.getLevel(), ambient, now);
        new EnterGraveyardTask().tryStart(helper.getLevel(), personal, now);
        helper.assertTrue(!ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE)
                        && !isAmbient(ambient), "ambient mourning retained a grave with no standing position");
        helper.assertTrue(isAssignedTo(personal, grave)
                        && personal.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                        .filter(deadline -> deadline == now + 1_200L).isPresent(),
                "missing geometry must preserve the ordinary personal retry policy");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void carpetedGravesideStillProvidesStandingPosition(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    helper.getLevel().setBlock(grave.offset(x, 0, z), Blocks.WHITE_CARPET.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA mourner = spawnVillager(helper, grave.east(6), "Carpeted Graveside Mourner");
        mourner.setOnGround(true);
        BlockPos entrance = grave.east();
        helper.assertTrue(Mourning.isMournableTombstone(helper.getLevel(), grave)
                        && Mourning.isSafeToMourn(helper.getLevel(), grave),
                "carpet fixture did not provide a safe, occupied grave");
        helper.assertTrue(mourner.getNavigation().isStableDestination(entrance),
                "carpeted graveside position is not stable");
        Vec3 standingPose = Vec3.atBottomCenterOf(entrance).add(0, 1.0D / 16.0D, 0);
        helper.assertTrue(helper.getLevel().noBlockCollision(mourner,
                        mourner.getBoundingBox().move(standingPose.subtract(mourner.position()))),
                "carpeted graveside physically obstructs the standing villager");
        var path = mourner.getNavigation().createPath(entrance, 0);
        helper.assertTrue(path != null && path.canReach() && path.getTarget().equals(entrance),
                "carpeted graveside fixture is not a reachable navigation target: " + path);

        Mourning.start(mourner, grave);
        helper.assertTrue(new EnterGraveyardTask().tryStart(helper.getLevel(), mourner, helper.getLevel().getGameTime()),
                "mourning task did not start in carpeted graveside fixture");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(selected -> selected.dimension().equals(helper.getLevel().dimension()))
                        .map(GlobalPos::pos)
                        .filter(pos -> pos.getY() == grave.getY()
                                && !pos.equals(grave)
                                && Math.abs(pos.getX() - grave.getX()) <= 1
                                && Math.abs(pos.getZ() - grave.getZ()) <= 1
                                && helper.getLevel().getBlockState(pos).is(Blocks.WHITE_CARPET))
                        .isPresent(),
                "mourner did not select a reachable carpeted graveside standing position at grave level");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 400)
    public static void failedAmbientApproachDoesNotExcludeReachableNearbyVillager(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        for (int z = -9; z <= 9; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(grave.offset(5, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA blocked = spawnVillager(helper, grave.east(6), "Failed Side Of Wall");
        Mourning.startAmbient(blocked, grave);
        Mourning.unreachable(blocked);
        VillagerEntityMCA reachable = spawnVillager(helper, grave.east(4), "Reachable Side Of Wall");
        assignAmbient(helper, grave, reachable);
        helper.succeedWhen(() -> helper.assertTrue(EnterGraveyardTask.isAtMourningSite(reachable),
                "a failed nearby approach prevented a reachable mourner from arriving"));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 400)
    public static void openingRouteAllowsFreshAmbientAssignment(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        encloseGrave(helper.getLevel(), grave);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(6), "Reopened Grave Approach");
        Mourning.startAmbient(ambient, grave);
        Mourning.unreachable(ambient);
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlock(grave.offset(2, y, 0), Blocks.AIR.defaultBlockState(), 3);
        }
        assignAmbient(helper, grave, ambient);
        helper.succeedWhen(() -> helper.assertTrue(EnterGraveyardTask.isAtMourningSite(ambient)
                        && !ambient.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                "a repaired route did not recover or retained stale navigation failure"));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 1_200)
    public static void completedAmbientMourningCleansAssignmentAndClassification(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        VillagerEntityMCA ambient = spawnVillager(helper, grave.east(), "Successful Ambient Mourner");
        Mourning.startAmbient(ambient, grave);
        boolean[] mourned = {false};
        helper.onEachTick(() -> {
            mourned[0] |= ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER);
            if (mourned[0] && !ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_SITE)) {
                helper.assertTrue(!isAmbient(ambient)
                                && !ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION)
                                && !ambient.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER),
                        "completed mourning retained assignment classification or temporary flower ownership");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void mournerLeavesRaisedGrassPlanterWithUprightTrapdoors(GameTestHelper helper) {
        // Captured read-only from New World (6), (623,64,-948), size 13x9x13.
        // Restore the left planter removed to release the reported villager.
        BlockPos destination = helper.absolutePos(new BlockPos(5, 0, 5));
        prepareFlatArea(helper, destination.offset(6, 1, 6), 8, 10);
        var terrain = helper.getLevel().getStructureManager().get(Identifier.fromNamespaceAndPath(
                "mca", "gametest/reported_mourning_planter")).orElseThrow();
        helper.assertTrue(terrain.placeInWorld(helper.getLevel(), destination, destination,
                        new StructurePlaceSettings().setIgnoreEntities(true), helper.getLevel().getRandom(), 3),
                "could not place captured planter terrain");
        BlockPos grave = destination.offset(4, 2, 7);
        BlockPos grass = destination.offset(5, 2, 6);
        helper.getLevel().setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        for (Direction side : List.of(Direction.WEST, Direction.SOUTH)) {
            helper.getLevel().setBlock(grass.relative(side), Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.FACING, side)
                    .setValue(TrapDoorBlock.HALF, Half.TOP)
                    .setValue(TrapDoorBlock.OPEN, true), 3);
        }
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel()).withAge(0).build();
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
        VillagerEntityMCA mourner = spawnVillager(helper, grass.above(), "Raised Planter Mourner");
        mourner.getTraits().removeTrait(Traits.DWARFISM);
        mourner.getTraits().removeTrait(Traits.TOUGH);
        mourner.getTraits().removeTrait(Traits.WEAK);
        mourner.getGenetics().setGene(Genetics.SIZE, 0.5F);
        mourner.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        mourner.refreshDimensions();
        helper.assertTrue(helper.getLevel().noCollision(mourner, mourner.getBoundingBox()),
                "planter mourner must start with a clear body above the grass");
        Mourning.startAmbient(mourner, grave);
        helper.succeedWhen(() -> helper.assertTrue(
                mourner.getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER),
                "mourner failed to leave raised planter and begin mourning: " + mourner.position()));
    }

    private static BlockPos prepareArena(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(12, 1, 12));
        helper.assertTrue(helper.getBounds().contains(Vec3.atCenterOf(center.offset(9, 2, 9))),
                "mourning fixture must fit inside its allocated template");
        prepareFlatArea(helper, center, 9, 3);
        for (int x = -9; x <= 9; x++) {
            for (int z = -9; z <= 9; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 9) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(center.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        BlockPos grave = center.west(3);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel()).withAge(0).build();
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
        return grave;
    }

    private static void encloseGrave(ServerLevel level, BlockPos grave) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 2) continue;
                for (int y = 0; y < 3; y++) {
                    level.setBlock(grave.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos position, String name) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(position)).withName(name).spawn(EntitySpawnReason.STRUCTURE);
        VILLAGERS.add(villager);
        villager.getBrain().setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return villager;
    }

    private static Village assignAmbient(GameTestHelper helper, BlockPos grave, VillagerEntityMCA villager) {
        Village village = new Village(1, helper.getLevel());
        ExternalBuilding graveyard = new ExternalBuilding(grave);
        graveyard.setId(100);
        graveyard.setType("graveyard");
        graveyard.setTypeForced(true);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.east());
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.west());
        village.registerExternalBuilding(graveyard);
        long now = helper.getLevel().getGameTime();
        CompoundTag saved = village.save();
        saved.putLong("nextMourningTime", now);
        village = new Village(saved, helper.getLevel());
        village.updateResident(villager);
        village.tick(helper.getLevel(), now);
        helper.assertTrue(isAssignedTo(villager, grave), "village did not assign the ambient grave");
        return village;
    }

    private static boolean isAssignedTo(VillagerEntityMCA villager, BlockPos grave) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(GlobalPos.of(villager.level().dimension(), grave)::equals).isPresent();
    }

    private static boolean isAmbient(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_AMBIENT).orElse(false);
    }

    private static VillagerEntityMCA reloadVillager(GameTestHelper helper, CompoundTag saved) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).build();
        VILLAGERS.add(villager);
        villager.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return villager;
    }

    private static long getDayTime(ServerLevel level) {
        return level.clockManager().getTotalTicks(level.dimensionType().defaultClock().orElseThrow());
    }

    private static void setDayTime(ServerLevel level, long ticks) {
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), ticks);
    }

    private static CompoundTag saveVillager(VillagerEntityMCA villager) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, villager.registryAccess());
        villager.saveWithoutId(output);
        return output.buildResult();
    }
}
