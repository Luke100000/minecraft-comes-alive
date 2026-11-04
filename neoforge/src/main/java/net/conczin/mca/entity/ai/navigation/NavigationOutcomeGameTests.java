package net.conczin.mca.entity.ai.navigation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.EnterGraveyardTask;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.BeforeBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.schedule.Activity;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Outcome probes using native navigation, collision, and Brain ticks. */
@PrefixGameTestTemplate(false)
public final class NavigationOutcomeGameTests {
    private static final String BATCH = "mca_navigation_outcome_probes";
    private static final List<Entity> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static boolean previousTeleporting;
    private static boolean previousMourning;

    private NavigationOutcomeGameTests() {
    }

    @BeforeBatch(batch = BATCH)
    public static void prepareBatch(ServerLevel level) {
        previousTeleporting = Config.getInstance().allowVillagerTeleporting;
        previousMourning = Config.getInstance().enableMourning;
        Config.getInstance().allowVillagerTeleporting = false;
        Config.getInstance().enableMourning = true;
    }

    @AfterBatch(batch = BATCH)
    public static void cleanUpBatch(ServerLevel level) {
        ACTORS.forEach(Entity::discard);
        ACTORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
        Config.getInstance().allowVillagerTeleporting = previousTeleporting;
        Config.getInstance().enableMourning = previousMourning;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 900)
    public static void reachableReplansWithoutMovementEventuallyGiveUp(GameTestHelper helper) {
        BlockPos center = prepareArena(helper);
        Shulker blocker = buildBlockedPassage(helper, center);
        BlockPos start = center.west(5);
        BlockPos target = center.east(5);
        VillagerEntityMCA villager = spawnVillager(helper, start);
        int[] gaveUp = {0};
        installJourneyBrain(villager, target, 300, gaveUp);
        boolean[] sawReachablePath = {false};
        helper.onEachTick(() -> {
            var path = villager.getNavigation().getPath();
            sawReachablePath[0] |= path != null && path.canReach();
            if (gaveUp[0] > 0) {
                helper.assertTrue(blocker.isAlive() && blocker.blockPosition().equals(center),
                        "blocking entity left the passage before the failed journey ended");
                helper.assertTrue(sawReachablePath[0] && PathRequestDiagnostics.snapshot(villager).sinkRequests() >= 2,
                        "fixture did not exercise repeated nominally reachable path attempts");
                helper.assertTrue(villager.position().distanceToSqr(Vec3.atBottomCenterOf(start)) >= 1.0D,
                        "villager never moved through the passage before getting blocked");
                helper.assertTrue(villager.getX() < center.getX()
                                && !villager.getBrain().hasMemoryValue(MemoryModuleType.HOME),
                        "journey did not terminate its unreachable destination");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 600)
    public static void temporaryEntityObstructionAllowsJourneyToResume(GameTestHelper helper) {
        BlockPos center = prepareArena(helper);
        Shulker blocker = buildBlockedPassage(helper, center);
        BlockPos target = center.east(5);
        VillagerEntityMCA villager = spawnVillager(helper, center.west(5));
        int[] gaveUp = {0};
        installJourneyBrain(villager, target, 600, gaveUp);
        helper.runAfterDelay(180, () -> {
            helper.assertTrue(villager.getX() < center.getX() && blocker.blockPosition().equals(center),
                    "fixture failed to obstruct real movement before opening the passage");
            blocker.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(gaveUp[0] == 0, "a temporary obstruction prematurely discarded the journey");
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager did not resume its journey after the entity obstruction was removed");
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 1_600)
    public static void mournerUsesReachableAlternativeToEnclosedPreferredStand(GameTestHelper helper) {
        MourningApproach approach = prepareMourningApproach(helper);
        startMourning(helper, approach);
        helper.succeedWhen(() -> helper.assertTrue(
                approach.villager().getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER),
                "mourner never used its reachable alternative; selected=" + approach.preferred()
                        + ", position=" + approach.villager().position()
                        + ", assignment=" + approach.villager().getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 600)
    public static void reachablePreferredStandStartsMourning(GameTestHelper helper) {
        MourningApproach approach = prepareMourningApproach(helper);
        setPreferredEntrance(helper, approach, Blocks.AIR.defaultBlockState());
        startMourning(helper, approach);
        helper.assertTrue(approach.villager().getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .orElseThrow().pos().equals(approach.preferred()),
                "a reachable preferred standing position must retain its preference");
        helper.succeedWhen(() -> helper.assertTrue(
                approach.villager().getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER),
                "mourner did not begin mourning at its reachable preferred standing position"));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 1_600)
    public static void blockedAssignedStandUsesReachableAlternative(GameTestHelper helper) {
        MourningApproach approach = prepareMourningApproach(helper);
        setPreferredEntrance(helper, approach, Blocks.AIR.defaultBlockState());
        startMourning(helper, approach);
        helper.assertTrue(approach.villager().getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .orElseThrow().pos().equals(approach.preferred()),
                "fixture did not first reserve its reachable preferred standing position");
        setPreferredEntrance(helper, approach, Blocks.STONE.defaultBlockState());
        helper.succeedWhen(() -> helper.assertTrue(
                approach.villager().getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_FLOWER),
                "mourner did not switch to a reachable alternative after its assigned approach was blocked"));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void unreachableStandsRetainAssignmentAndFailureAgeAfterDisplacement(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel()).withAge(0).build();
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 2) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(grave.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA villager = spawnVillager(helper, grave.east(6));
        villager.setNoAi(true);
        MourningApproach approach = new MourningApproach(villager, grave, grave.east());
        startMourning(helper, approach);
        GlobalPos assigned = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION).orElseThrow();
        long failureSince = helper.getLevel().getGameTime();
        WalkTargetFailureMemory.record(villager, assigned, failureSince);
        // Isolate selection ownership: displacement must not count as a newly reachable stand.
        villager.setPos(Vec3.atBottomCenterOf(grave.north(6)));
        helper.assertTrue(new EnterGraveyardTask().tryStart(helper.getLevel(), villager, failureSince + 101),
                "failed standing-position selection did not run after displacement");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(assigned::equals).isPresent(),
                "mourner replaced an unreachable assignment without proving another position reachable");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since == failureSince).isPresent(),
                "rechecking fully unreachable positions restarted the existing failure episode");
        helper.succeed();
    }

    private static MourningApproach prepareMourningApproach(GameTestHelper helper) {
        BlockPos grave = prepareArena(helper);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel()).withAge(0).build();
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
        BlockPos preferred = grave.east();
        for (BlockPos wall : List.of(preferred.north(), preferred.east(), preferred.south())) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnVillager(helper, grave.east(6));
        var navigation = (MCAGroundPathNavigation) villager.getNavigation();
        var selectedPath = navigation.createPathForPersistentIntent(Set.of(preferred), 0);
        var alternativePath = navigation.createPathForPersistentIntent(Set.of(grave.south()), 0);
        helper.assertTrue(selectedPath == null || !selectedPath.canReach(),
                "preferred standing position was not actually enclosed");
        helper.assertTrue(alternativePath != null && alternativePath.canReach(),
                "grave did not have the intended reachable alternative standing position");
        return new MourningApproach(villager, grave, preferred);
    }

    private static void startMourning(GameTestHelper helper, MourningApproach approach) {
        Mourning.startAmbient(approach.villager(), approach.grave());
        helper.assertTrue(new EnterGraveyardTask().tryStart(helper.getLevel(), approach.villager(),
                        helper.getLevel().getGameTime()), "could not start standing-position selection");
        helper.assertTrue(approach.villager().getBrain().hasMemoryValue(MemoryModuleTypeMCA.MOURNING_POSITION),
                "mourner did not select a standing position");
    }

    private static void setPreferredEntrance(GameTestHelper helper, MourningApproach approach,
                                             BlockState state) {
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlock(approach.preferred().east().above(y), state, 3);
        }
    }

    private record MourningApproach(VillagerEntityMCA villager, BlockPos grave, BlockPos preferred) { }

    private static BlockPos prepareArena(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(16, 1, 16));
        helper.assertTrue(helper.getBounds().contains(Vec3.atCenterOf(center.offset(12, 0, 12)))
                        && helper.getBounds().contains(Vec3.atCenterOf(center.offset(-12, 0, -12))),
                "outcome fixture must fit within its allocated arena footprint");
        ChunkPos min = ChunkPos.containing(center.offset(-12, 0, -12));
        ChunkPos max = ChunkPos.containing(center.offset(12, 0, 12));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!helper.getLevel().getForceLoadedChunks().contains(chunk.pack())) FORCED_CHUNKS.add(chunk);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, center, 12, 3);
        // Keep native detour recovery inside this arena, including failed tests.
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != 12) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(center.offset(x, y, z), Blocks.BEDROCK.defaultBlockState(), 3);
                }
            }
        }
        return center;
    }

    private static Shulker buildBlockedPassage(GameTestHelper helper, BlockPos center) {
        for (int x = -6; x <= 6; x++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(center.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(center.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(center.offset(x, 2, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        Shulker blocker = EntityType.SHULKER.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (blocker == null) throw new IllegalStateException("Could not create passage blocker");
        blocker.setNoAi(true);
        blocker.setInvulnerable(true);
        blocker.setPos(Vec3.atBottomCenterOf(center));
        helper.getLevel().addFreshEntity(blocker);
        ACTORS.add(blocker);
        return blocker;
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet)).spawn(EntitySpawnReason.STRUCTURE);
        ACTORS.add(villager);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.setOnGround(true);
        villager.getBrain().setSchedule(null);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        return villager;
    }

    @SuppressWarnings("unchecked")
    private static void installJourneyBrain(VillagerEntityMCA villager, BlockPos target, int failureTimeout, int[] gaveUp) {
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll((ServerLevel) villager.level(), villager);
        brain.removeAllBehaviors();
        net.conczin.mca.gametest.GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        net.conczin.mca.gametest.GameTestBrain.addActivity(brain, Activity.CORE, 2, ImmutableList.of(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 1.0F, 0, failureTimeout, ignored -> true, ignored -> gaveUp[0]++)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), target));
    }
}
