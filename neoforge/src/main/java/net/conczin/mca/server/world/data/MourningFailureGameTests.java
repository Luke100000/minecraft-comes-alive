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
import net.conczin.mca.entity.ai.brain.tasks.EnterGraveyardTask;
import net.conczin.mca.entity.ai.brain.tasks.GrieveTask;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.conczin.mca.gametest.GameTestBrain;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class MourningFailureGameTests {
    private static final String BATCH = "mca_mourning_failure_policy";
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
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
                helper.fail("unreachable ambient mourning retained its grave instead of ending the assignment");
            }
        });
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
                    helper.getLevel().setBlock(grave.offset(x, 0, z), Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState(), 3);
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
                                && helper.getLevel().getBlockState(pos).is(Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE)))
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
