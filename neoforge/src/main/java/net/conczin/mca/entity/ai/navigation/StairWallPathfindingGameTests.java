package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.gametest.GameTestBrain;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.core.GlobalPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class StairWallPathfindingGameTests {
    private static final int ROUTE_TIMEOUT_TICKS = 140;

    private StairWallPathfindingGameTests() {
    }

    @GameTest(batch = "mca_stair_wall_across_wall_south_north", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalSouthNorthRouteAroundRaisedWallReachesTarget(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        runBrainRoute(helper, fixture.stair().south(3), fixture.stair().north(3), "south-north");
    }

    @GameTest(batch = "mca_stair_wall_across_wall_north_south", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalNorthSouthRouteAroundRaisedWallReachesTarget(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        runBrainRoute(helper, fixture.stair().north(3), fixture.stair().south(3), "north-south");
    }

    @GameTest(batch = "mca_stair_wall_ascent_guard", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalStairAscentBesideWallReachesRaisedLanding(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        prepareRaisedLanding(helper, fixture.stair());
        runBrainRoute(helper, fixture.stair().west(2), fixture.stair().east(2).above(), "stair-ascent");
    }

    @GameTest(batch = "mca_stair_wall_descent_guard", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalStairDescentBesideWallReachesLowerFloor(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        prepareRaisedLanding(helper, fixture.stair());
        runBrainRoute(helper, fixture.stair().east(2).above(), fixture.stair().west(2), "stair-descent");
    }

    /** Characterization fixture: a three-step staircase with two walls and a ninety-degree exit. */
    @GameTest(batch = "mca_stair_spin_corner_descent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void naturalThreeStepDescentTurningAtFootDoesNotSpin(GameTestHelper helper) {
        runThreeStepDescent(helper, false, null, false);
    }

    /** The reported house staircase has narrow walls, overhead beams and a lower slab beside the stairs. */
    @GameTest(batch = "mca_stair_spin_house_descent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void naturalTightHouseStairSideDropDoesNotSpin(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), false);
    }

    /** Reported possibility: the villager drops two blocks onto a lower slab rather than following the flight. */
    @GameTest(batch = "mca_stair_spin_low_slab_drop", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void naturalStairSideDropOntoBottomSlabDoesNotSpin(GameTestHelper helper) {
        runThreeStepDescent(helper, false,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), false);
    }

    /** Control: same drop opening but with a full-height landing instead of a bottom slab. */
    @GameTest(batch = "mca_stair_spin_full_block_drop", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void naturalStairSideDropOntoFullBlockDoesNotSpin(GameTestHelper helper) {
        runThreeStepDescent(helper, false, Blocks.COBBLESTONE.defaultBlockState(), false);
    }

    /** Require a physical landing on the bottom-half slab, not just passing its waypoint in midair. */
    @GameTest(batch = "mca_stair_spin_direct_slab_landing", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void naturalTwoBlockDropOntoBottomSlabSettlesWithoutSpinning(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), true);
    }

    /** Large villagers need more lateral and overhead clearance around the slab-side turn. */
    @GameTest(batch = "mca_stair_spin_large_house_descent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void largeVillagerDescendsTightHouseOntoBottomSlabWithoutSpinning(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                true, 1.0F, 1.0F);
    }

    /** Small villagers can occupy a partial surface differently from an average-sized villager. */
    @GameTest(batch = "mca_stair_spin_small_house_descent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void smallVillagerDescendsTightHouseOntoBottomSlabWithoutSpinning(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                true, 0.0F, 0.0F);
    }

    /** Two villagers approaching in opposite directions can collide on the same narrow stair. */
    @GameTest(batch = "mca_stair_spin_opposing_villagers", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 280)
    public static void opposingVillagersOnTightHouseStairsRecoverWithoutSpinning(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                true, 0.5F, 0.5F, true);
    }

    /** Start at a valid tread edge, as after a collision, rather than centred on the upper landing. */
    @GameTest(batch = "mca_stair_spin_off_centre_tread", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void offCentreVillagerLeavesStairTreadForLowerSlab(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                true, 0.5F, 0.5F, false, true);
    }

    @GameTest(batch = "mca_stair_spin_wide_off_centre_tread", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void wideVillagerLeavesStairTreadForLowerSlab(GameTestHelper helper) {
        runThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
                true, 1.0F, 1.0F, false, true);
    }

    private static void runThreeStepDescent(GameTestHelper helper, boolean houseCeiling,
                                           @Nullable BlockState sideLanding, boolean targetSideLanding) {
        runThreeStepDescent(helper, houseCeiling, sideLanding, targetSideLanding, 0.5F, 0.5F);
    }

    private static void runThreeStepDescent(GameTestHelper helper, boolean houseCeiling,
                                           @Nullable BlockState sideLanding, boolean targetSideLanding,
                                           float size, float width) {
        runThreeStepDescent(helper, houseCeiling, sideLanding, targetSideLanding, size, width, false);
    }

    private static void runThreeStepDescent(GameTestHelper helper, boolean houseCeiling,
                                           @Nullable BlockState sideLanding, boolean targetSideLanding,
                                           float size, float width, boolean opposingVillager) {
        runThreeStepDescent(helper, houseCeiling, sideLanding, targetSideLanding,
                size, width, opposingVillager, false);
    }

    private static BlockPos prepareThreeStepDescent(GameTestHelper helper, boolean houseCeiling,
                                                   @Nullable BlockState sideLanding) {
        Fixture fixture = prepareFixture(helper);
        // The shared wall fixture includes a suspended inverted stair that is not part
        // of this descending staircase and creates an unintended upper route.
        helper.getLevel().setBlock(fixture.stair().above(3), Blocks.AIR.defaultBlockState(), 3);
        BlockPos lower = fixture.stair().west();
        for (int step = 0; step < 3; step++) {
            BlockPos stair = lower.east(step).above(step);
            for (int y = 0; y < step; y++) {
                helper.getLevel().setBlock(lower.east(step).above(y), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(stair, Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST), 3);
        }
        for (int x = 3; x <= 4; x++) {
            for (int y = 0; y <= 2; y++) {
                helper.getLevel().setBlock(lower.east(x).above(y), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        for (int x = 0; x <= 4; x++) {
            for (int y = 0; y <= 5; y++) {
                helper.getLevel().setBlock(lower.east(x).north().above(y), Blocks.COBBLESTONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(lower.east(x).south().above(y), Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
            if (houseCeiling) {
                // Full-width ceiling at the upper landing, lower over the stair treads.
                // Keep enough real headroom for the villager's full collision box.
                int ceilingHeight = x >= 2 ? 5 : 4;
                helper.getLevel().setBlock(lower.east(x).above(ceilingHeight),
                        Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        if (sideLanding != null) {
            BlockPos sideOpening = lower.east(2).south();
            for (int y = 0; y <= 5; y++) {
                helper.getLevel().setBlock(sideOpening.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(sideOpening, sideLanding, 3);
        }
        return lower;
    }

    private static void runThreeStepDescent(GameTestHelper helper, boolean houseCeiling,
                                           @Nullable BlockState sideLanding, boolean targetSideLanding,
                                           float size, float width, boolean opposingVillager,
                                           boolean startAtTreadEdge) {
        BlockPos lower = prepareThreeStepDescent(helper, houseCeiling, sideLanding);
        BlockPos start = lower.east(4).above(3);
        BlockPos target = sideLanding == null ? lower.west(2).north(2)
                : lower.east(2).south(targetSideLanding ? 1 : 3);
        VillagerEntityMCA villager = spawnVillager(helper, start, "Stair turn probe", size, width);
        if (startAtTreadEdge) {
            double halfWidth = villager.getBbWidth() * 0.5D;
            // The upper half has two blocks of headroom. The lower half straddles
            // the preceding tread's low ceiling and is not a valid displaced pose.
            villager.setPos(lower.getX() + 2.5D + halfWidth + 0.02D,
                    lower.getY() + 3.0D, lower.getZ() + 1.0D - halfWidth - 0.02D);
            helper.assertTrue(helper.getLevel().noBlockCollision(villager, villager.getBoundingBox()),
                    "Off-centre starting pose must not intersect the stair or wall");
            helper.assertTrue(!helper.getLevel().noBlockCollision(villager,
                            villager.getBoundingBox().move(0.0D, -0.01D, 0.0D)),
                    "Off-centre starting pose must be physically supported by the stair");
            villager.setYRot(90.0F);
            MCA.LOGGER.info("[MCA-STAIR-REPRO] event=tread-start pos={} width={} height={} target={}",
                    villager.position(), villager.getBbWidth(), villager.getBbHeight(), target);
        }
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        VillagerEntityMCA other = opposingVillager
                ? spawnVillager(helper, lower.east(2).above(3), "Oncoming stair walker") : null;
        if (other != null) {
            // The opposing villager starts on the upper half of the middle stair tread.
            other.setPos(other.getX(), other.getY() - 0.5D, other.getZ());
            other.refreshBrain(helper.getLevel());
            other.getBrain().eraseMemory(MemoryModuleType.PATH);
            other.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            other.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(Vec3.atBottomCenterOf(start), 0.65F, 0));
            other.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        }
        WalkTarget originalTarget = new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, originalTarget);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        int[] nearbyTicks = {0};
        double[] closestDistanceSqr = {Double.POSITIVE_INFINITY};
        WalkTarget[] previousWalkTarget = {originalTarget};
        int[] ticks = {0};
        int[] stairTicks = {0};
        int[] sideLandingTicks = {0};
        long[] lastContact = {-100L};
        double[] anchorX = {villager.getX()};
        double[] anchorZ = {villager.getZ()};
        double[] localRotation = {0.0D};
        double[] peakRotation = {0.0D};
        float[] previousYaw = {villager.getYRot()};
        int[] anchoredTicks = {0};
        double[] minimumY = {villager.getY()};
        float[] minimumHealth = {villager.getHealth()};
        String[] firstPath = {null};
        helper.onEachTick(() -> {
            ticks[0]++;
            minimumHealth[0] = Math.min(minimumHealth[0], villager.getHealth());
            WalkTarget currentWalkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                    .orElse(null);
            if (sideLanding == null && currentWalkTarget != previousWalkTarget[0]) {
                MCA.LOGGER.info("[MCA-STAIR-REPRO] event=walk-target-change tick={} pos={} previous={} current={} "
                                + "activity={} path={} collision={} yaw={} health={}/{} fallDistance={} "
                                + "lastDamage={} hurtBy={} nearestHostile={}",
                        ticks[0], villager.position(),
                        previousWalkTarget[0] == null ? null : previousWalkTarget[0].getTarget().currentBlockPosition(),
                        currentWalkTarget == null ? null : currentWalkTarget.getTarget().currentBlockPosition(),
                        villager.getBrain().getActiveNonCoreActivity().orElse(null),
                        describePath(villager.getNavigation().getPath()), villager.horizontalCollision,
                        villager.getYRot(), villager.getHealth(), villager.getMaxHealth(),
                        villager.fallDistance, villager.getLastDamageSource(),
                        villager.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY).orElse(null),
                        villager.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_HOSTILE).orElse(null));
            }
            previousWalkTarget[0] = currentWalkTarget;
            if (other != null && other.isAlive()) {
                double distanceSqr = villager.distanceToSqr(other);
                closestDistanceSqr[0] = Math.min(closestDistanceSqr[0], distanceSqr);
                if (distanceSqr <= 2.25D) {
                    nearbyTicks[0]++;
                }
                if (nearbyTicks[0] >= 12 || ticks[0] >= 110) {
                    MCA.LOGGER.info("[MCA-STAIR-ENCOUNTER] event=release tick={} primary={} other={} nearTicks={}",
                            ticks[0], villager.position(), other.position(), nearbyTicks[0]);
                    other.discard();
                }
            }
            if (opposingVillager && ticks[0] % 20 == 0) {
                MCA.LOGGER.info("[MCA-STAIR-ENCOUNTER] event=sample tick={} primary={} other={} nearTicks={} "
                                + "walkTarget={} path={} collision={}",
                        ticks[0], villager.position(), other.position(), nearbyTicks[0],
                        villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null),
                        describePath(villager.getNavigation().getPath()), villager.horizontalCollision);
            }
            minimumY[0] = Math.min(minimumY[0], villager.getY());
            Path path = villager.getNavigation().getPath();
            if (startAtTreadEdge && ticks[0] % 20 == 0) {
                MCA.LOGGER.info("[MCA-STAIR-REPRO] event=tread-sample tick={} pos={} yaw={} bodyYaw={} "
                                + "headYaw={} collision={} onGround={} wanted=({}, {}, {}) path={} walkTarget={} health={}",
                        ticks[0], villager.position(), villager.getYRot(), villager.yBodyRot, villager.yHeadRot,
                        villager.horizontalCollision, villager.onGround(),
                        villager.getMoveControl().getWantedX(), villager.getMoveControl().getWantedY(),
                        villager.getMoveControl().getWantedZ(), describePath(path),
                        currentWalkTarget == null ? null : currentWalkTarget.getTarget().currentBlockPosition(),
                        villager.getHealth());
            }
            if (firstPath[0] == null && path != null) {
                firstPath[0] = describePath(path);
                if (sideLanding == null && Boolean.getBoolean("mca.stairSpinDebug")) {
                    for (int i = 0; i < path.getNodeCount(); i++) {
                        BlockPos node = path.getNodePos(i);
                        MCA.LOGGER.info("[MCA-STAIR-REPRO] event=planned-node index={} pos={} type={} "
                                        + "block={} below={} walkingFeetY={} width={} height={}",
                                i, node, path.getNode(i).type,
                                helper.getLevel().getBlockState(node),
                                helper.getLevel().getBlockState(node.below()),
                                PathNodeGeometry.walkingPosition(helper.getLevel(), node, villager).y,
                                villager.getBbWidth(), villager.getBbHeight());
                    }
                }
            }
            if (sideLanding == null && villager.fallDistance >= 1.5F) {
                MCA.LOGGER.info("[MCA-STAIR-REPRO] event=falling tick={} pos={} fallDistance={} "
                                + "onGround={} verticalVelocity={} next={} path={} health={}",
                        ticks[0], villager.position(), villager.fallDistance,
                        villager.onGround(), villager.getDeltaMovement().y(),
                        path == null || path.isDone() ? null : path.getNextNodePos(),
                        describePath(path), villager.getHealth());
            }

            BlockPos support = BlockPos.containing(villager.getX(), villager.getY() - 0.1D, villager.getZ());
            if (sideLanding != null && support.getX() == lower.east(2).getX()
                    && support.getZ() == lower.south().getZ() && villager.getY() < lower.getY() + 2.0D) {
                sideLandingTicks[0]++;
            }
            if (helper.getLevel().getBlockState(support).is(BlockTags.STAIRS)) {
                stairTicks[0]++;
                lastContact[0] = ticks[0];
            }
            if (ticks[0] - lastContact[0] <= 10) {
                double dx = villager.getX() - anchorX[0];
                double dz = villager.getZ() - anchorZ[0];
                if (dx * dx + dz * dz > 0.49D) {
                    anchorX[0] = villager.getX();
                    anchorZ[0] = villager.getZ();
                    anchoredTicks[0] = 0;
                    localRotation[0] = 0.0D;
                } else {
                    anchoredTicks[0]++;
                    localRotation[0] += Math.abs(Mth.wrapDegrees(villager.getYRot() - previousYaw[0]));
                    peakRotation[0] = Math.max(peakRotation[0], localRotation[0]);
                    if (anchoredTicks[0] >= (startAtTreadEdge ? 100 : 8)
                            && localRotation[0] >= (startAtTreadEdge ? 720.0D : 270.0D)) {
                        if (other != null) other.discard();
                        villager.discard();
                        helper.fail("Reproduced stair spin: pos=" + villager.position() + " yaw="
                                + villager.getYRot() + " localRotation=" + localRotation[0]
                                + " originalTarget=" + target
                                + " currentWalkTarget=" + (currentWalkTarget == null ? null
                                : currentWalkTarget.getTarget().currentBlockPosition())
                                + " activity=" + villager.getBrain().getActiveNonCoreActivity().orElse(null)
                                + " path=" + describePath(path));
                        return;
                    }
                }
            } else {
                anchoredTicks[0] = 0;
                localRotation[0] = 0.0D;
                anchorX[0] = villager.getX();
                anchorZ[0] = villager.getZ();
            }
            previousYaw[0] = villager.getYRot();

            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D
                    && (!targetSideLanding || (sideLandingTicks[0] > 0 && villager.onGround()))) {
                MCA.LOGGER.info("[MCA-STAIR-REPRO] result=arrived ticks={} stairTicks={} landingTicks={} "
                                + "landing={} peakLocalRotation={} minY={} start={} target={} firstPath={}",
                        ticks[0], stairTicks[0], sideLandingTicks[0], sideLanding,
                        peakRotation[0], minimumY[0], start, target, firstPath[0]);
                helper.assertTrue(stairTicks[0] >= (startAtTreadEdge ? 1 : 2)
                                && minimumY[0] <= lower.getY() + 1.0D,
                        "The route must actually descend the stairs: " + firstPath[0]);
                if (sideLanding == null) {
                    helper.assertTrue(minimumHealth[0] >= villager.getMaxHealth(),
                            "Descending the intact staircase must not cause damage: minimumHealth="
                                    + minimumHealth[0] + " path=" + firstPath[0]);
                }
                if (other != null) {
                    MCA.LOGGER.info("[MCA-STAIR-REPRO] result=encounter nearbyTicks={} closestDistanceSqr={}",
                            nearbyTicks[0], closestDistanceSqr[0]);
                    helper.assertTrue(nearbyTicks[0] > 0,
                            "Opposing walker never approached the descending villager");
                    other.discard();
                }
                villager.discard();
                helper.succeed();
                return;
            }
            if (ticks[0] >= (startAtTreadEdge ? 500 : opposingVillager ? 215 : 175)) {
                MCA.LOGGER.info("[MCA-STAIR-REPRO] result=timeout ticks={} stairTicks={} landingTicks={} "
                                + "landing={} peakLocalRotation={} minY={} pos={} path={}",
                        ticks[0], stairTicks[0], sideLandingTicks[0], sideLanding,
                        peakRotation[0], minimumY[0], villager.position(), describePath(path));
                if (other != null) {
                    MCA.LOGGER.info("[MCA-STAIR-ENCOUNTER] event=timeout nearbyTicks={} closestDistanceSqr={} otherPos={}",
                            nearbyTicks[0], closestDistanceSqr[0], other.position());
                    other.discard();
                }
                villager.discard();
                helper.fail("Three-step turning descent stalled: path=" + firstPath[0]
                        + " current=" + describePath(path));
            }
        });
    }

    /** Keep the live path when an external displacement causes a 1.5-block partial-floor landing. */
    @GameTest(batch = "mca_stair_spin_displaced_partial_landing", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void villagerDisplacedOntoRaisedBottomSlabRecovers(GameTestHelper helper) {
        BlockState slab = Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
        BlockPos lower = prepareThreeStepDescent(helper, true, slab);
        BlockPos landing = lower.east(2).south();
        helper.getLevel().setBlock(landing, Blocks.COBBLESTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(landing.above(), slab, 3);
        BlockPos target = lower.west(2).north(2);
        VillagerEntityMCA villager = spawnVillager(helper, lower.east(4).above(3), "Displaced stair walker");
        villager.refreshBrain(helper.getLevel());
        WalkTarget walkTarget = new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walkTarget);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        int[] ticks = {0};
        int[] displacedAt = {-1};
        int[] landingTicks = {0};
        double[] localRotation = {0.0D};
        float[] previousYaw = {villager.getYRot()};
        Vec3[] anchor = {villager.position()};
        int[] anchoredTicks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            Path path = villager.getNavigation().getPath();
            if (displacedAt[0] < 0 && path != null && !path.isDone() && villager.onGround()
                    && Math.abs(villager.getY() - lower.getY() - 3.0D) < 0.01D
                    && villager.getX() < lower.getX() + 3.0D - villager.getBbWidth() * 0.5D - 0.02D
                    && villager.getX() > lower.getX() + 2.5D) {
                // Reproduce only external displacement, preserving AI, path and movement control.
                villager.setPos(villager.getX(), villager.getY(), landing.getZ() + 0.5D);
                if (!helper.getLevel().noBlockCollision(villager, villager.getBoundingBox())) {
                    villager.discard();
                    helper.fail("The displaced pose must be clear of the stair walls");
                    return;
                }
                displacedAt[0] = ticks[0];
                anchor[0] = villager.position();
                MCA.LOGGER.info("[MCA-STAIR-DISPLACEMENT] event=displace tick={} pos={} path={}",
                        ticks[0], villager.position(), describePath(path));
            }
            if (displacedAt[0] >= 0) {
                if (villager.onGround() && villager.getY() == landing.getY() + 1.5D) {
                    landingTicks[0]++;
                }
                double dx = villager.getX() - anchor[0].x;
                double dz = villager.getZ() - anchor[0].z;
                if (dx * dx + dz * dz > 0.49D) {
                    anchor[0] = villager.position();
                    localRotation[0] = 0.0D;
                    anchoredTicks[0] = 0;
                } else {
                    anchoredTicks[0]++;
                    localRotation[0] += Math.abs(Mth.wrapDegrees(villager.getYRot() - previousYaw[0]));
                }
                if (ticks[0] % 10 == 0) {
                    WalkTarget current = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
                    MCA.LOGGER.info("[MCA-STAIR-DISPLACEMENT] event=sample tick={} pos={} yaw={} bodyYaw={} "
                                    + "onGround={} collision={} localRotation={} landingTicks={} path={} "
                                    + "wanted=({}, {}, {}) originalWalkTarget={} health={}",
                            ticks[0], villager.position(), villager.getYRot(), villager.yBodyRot,
                            villager.onGround(), villager.horizontalCollision, localRotation[0], landingTicks[0],
                            describePath(path), villager.getMoveControl().getWantedX(),
                            villager.getMoveControl().getWantedY(), villager.getMoveControl().getWantedZ(),
                            current == walkTarget, villager.getHealth());
                }
                if (anchoredTicks[0] >= 100 && localRotation[0] >= 720.0D) {
                    villager.discard();
                    helper.fail("Sustained rotation after stair displacement: " + describePath(path));
                    return;
                }
            }
            previousYaw[0] = villager.getYRot();
            if (villager.onGround() && villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D) {
                MCA.LOGGER.info("[MCA-STAIR-DISPLACEMENT] result=arrived tick={} displacedAt={} landingTicks={}",
                        ticks[0], displacedAt[0], landingTicks[0]);
                villager.discard();
                helper.assertTrue(displacedAt[0] >= 0 && landingTicks[0] > 0,
                        "The route must include displacement and a physical landing on the raised bottom slab");
                helper.succeed();
            } else if (ticks[0] >= 500) {
                String details = "pos=" + villager.position() + " displacedAt=" + displacedAt[0]
                        + " landingTicks=" + landingTicks[0] + " path=" + describePath(path);
                villager.discard();
                helper.fail("Displaced stair walker failed to recover: " + details);
            }
        });
    }

    @GameTest(batch = "mca_stair_spin_impossible_disposable", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void impossibleStairWalkTargetDoesNotCauseSustainedSpinning(GameTestHelper helper) {
        runImpossibleStairTarget(helper, false);
    }

    @GameTest(batch = "mca_stair_spin_impossible_persistent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void impossiblePersistentStairTargetDoesNotCauseSustainedSpinning(GameTestHelper helper) {
        runImpossibleStairTarget(helper, true);
    }

    private static void runImpossibleStairTarget(GameTestHelper helper, boolean persistent) {
        BlockPos lower = prepareThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos impossibleTarget = lower.east(2).above(4);
        VillagerEntityMCA villager = spawnVillager(helper, lower.east(4).above(3), "Impossible stair target");
        Vec3 impossibleFeet = Vec3.atBottomCenterOf(impossibleTarget);
        if (helper.getLevel().noBlockCollision(villager,
                villager.getBoundingBox().move(impossibleFeet.subtract(villager.position())))) {
            villager.discard();
            helper.fail("The target pose must intersect the low stair ceiling");
            return;
        }
        WalkTarget target = persistent ? new WalkTarget(new PersistentPathTarget(impossibleTarget), 0.65F, 0)
                : new WalkTarget(impossibleTarget, 0.65F, 0);
        setBrainWalkTarget(helper, villager, target);
        StairTurnObservation observation = new StairTurnObservation(villager, target);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            observation.sample(++ticks[0]);
            if (ticks[0] == 500) {
                observation.report("impossible-target-persistent=" + persistent, ticks[0]);
                villager.discard();
                helper.assertTrue(observation.requestedTicks > 0, "The impossible walk request was never observed");
                helper.assertTrue(!observation.sustainedSpin,
                        "Repeated local rotation while pursuing an impossible stair target; persistent=" + persistent);
                helper.succeed();
            }
        });
    }

    /** Neither walker is removed to release the encounter during the observation window. */
    @GameTest(batch = "mca_stair_spin_prolonged_encounter", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void prolongedOpposingStairWalkersDoNotSpin(GameTestHelper helper) {
        helper.assertTrue(Config.getServerConfig().enableVillagerCollisions,
                "Villager collisions must be enabled for the encounter fixture");
        BlockPos lower = prepareThreeStepDescent(helper, true, null);
        // Both bodies must be able to traverse the flight before testing their encounter.
        // A beam over the lower treads can block descent while the feet still overlap the upper tread.
        for (int x = 0; x < 2; x++) {
            helper.getLevel().setBlock(lower.east(x).above(4), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(lower.east(x).above(5), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
        }
        BlockPos upperStart = lower.east(4).above(3);
        BlockPos lowerStart = lower.west();
        VillagerEntityMCA descending = spawnVillager(helper, upperStart, "Persistent descending walker");
        VillagerEntityMCA ascending = spawnVillager(helper, lowerStart, "Persistent ascending walker");
        WalkTarget downTarget = new WalkTarget(new PersistentPathTarget(lowerStart), 0.65F, 0);
        WalkTarget upTarget = new WalkTarget(new PersistentPathTarget(upperStart), 0.65F, 0);
        setBrainWalkTarget(helper, descending, downTarget);
        setBrainWalkTarget(helper, ascending, upTarget);
        StairTurnObservation down = new StairTurnObservation(descending, downTarget);
        StairTurnObservation up = new StairTurnObservation(ascending, upTarget);
        int[] ticks = {0};
        int[] contacts = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            down.sample(ticks[0]);
            up.sample(ticks[0]);
            if (descending.getBoundingBox().inflate(0.05D).intersects(ascending.getBoundingBox())) {
                contacts[0]++;
            }
            if (ticks[0] == 500) {
                down.report("prolonged-descending-contacts=" + contacts[0], ticks[0]);
                up.report("prolonged-ascending-contacts=" + contacts[0], ticks[0]);
                descending.discard();
                ascending.discard();
                helper.assertTrue(contacts[0] > 0 && down.stairTicks > 0 && up.stairTicks > 0,
                        "Both walkers must traverse the staircase and physically encounter each other");
                helper.assertTrue(!down.sustainedSpin && !up.sustainedSpin,
                        "Repeated local rotation during an uninterrupted opposing stair encounter");
                helper.succeed();
            }
        });
    }

    /** Exercise the actual task that republishes a target each tick, rather than a test-driven retry loop. */
    @GameTest(batch = "mca_stair_spin_repeated_interaction_target", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void unreachableInteractionTargetRetriesWithoutSpinningAndRecovers(GameTestHelper helper) {
        runUnreachableInteractionTargetTest(helper, Activity.IDLE, false, "repeated-interaction");
    }

    /** REST movement must not repeatedly replan a blocked player interaction. */
    @GameTest(batch = "mca_stair_spin_rest_interaction_target", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void restingVillagerInteractingWithUnreachablePlayerRetriesWithoutSpinningAndRecovers(GameTestHelper helper) {
        runUnreachableInteractionTargetTest(helper, Activity.REST, false, "rest-interaction");
    }

    /** A real HOME POI must not erase failure backoff for the player's blocked interaction target. */
    @GameTest(batch = "mca_stair_spin_rest_home_interaction_target", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 560)
    public static void restingVillagerWithHomeRetriesBlockedInteractionWithoutSpinningAndRecovers(GameTestHelper helper) {
        runUnreachableInteractionTargetTest(helper, Activity.REST, true, "rest-home-interaction");
    }

    private static void runUnreachableInteractionTargetTest(GameTestHelper helper, Activity scheduledActivity,
                                                           boolean withHome, String reportLabel) {
        BlockPos lower = prepareThreeStepDescent(helper, true, null);
        VillagerEntityMCA villager = spawnVillager(helper, lower.east(4).above(3), "Stair interaction walker");
        // The mock player is a stationary interaction reference, not a registered or ticking player.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos playerCell = lower.east(2).south(2);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(playerCell.relative(direction).above(y),
                        Blocks.COBBLESTONE.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(playerCell.above(3), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
        Vec3 playerFeet = Vec3.atBottomCenterOf(playerCell);
        player.setPos(playerFeet);
        if (!helper.getLevel().noBlockCollision(player, player.getBoundingBox())
                || helper.getLevel().noBlockCollision(player,
                player.getBoundingBox().move(0.0D, -0.01D, 0.0D))) {
            villager.discard();
            player.discard();
            helper.fail("The player reference must stand clear inside the enclosed space beside the stairs");
            return;
        }
        WalkTarget target = new WalkTarget(player, 0.5F, 2);
        setBrainWalkTarget(helper, villager, target);
        if (withHome) {
            assignValidHomeBesideStairEntrance(helper, villager, lower);
        }
        // Keep the intended activity stable without changing the shared GameTest world's time.
        villager.getBrain().setSchedule(GameTestBrain.fixedSchedule(scheduledActivity));
        villager.getBrain().setActiveActivityIfPossible(scheduledActivity);
        if (scheduledActivity == Activity.REST) {
            helper.assertTrue(villager.getBrain().getActiveNonCoreActivity().orElse(null) == Activity.REST,
                    "The nighttime interaction fixture must activate REST movement");
        }
        villager.getInteractions().interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
        StairTurnObservation observation = new StairTurnObservation(villager, target);
        int[] ticks = {0};
        int[] lookingAtPlayerTicks = {0};
        int[] closedRouteSearches = {-1};
        helper.onEachTick(() -> {
            observation.sample(++ticks[0]);
            if (villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null)
                    instanceof EntityTracker tracker && tracker.getEntity() == player) {
                lookingAtPlayerTicks[0]++;
            }
            if (ticks[0] == 300) {
                if (scheduledActivity == Activity.REST) {
                    helper.assertTrue(villager.getBrain().getActiveNonCoreActivity().orElse(null) == Activity.REST,
                            "REST must remain active throughout the blocked interaction window");
                }
                if (withHome) {
                    GlobalPos expectedHome = GlobalPos.of(helper.getLevel().dimension(), lower.west(2));
                    helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME)
                                    .filter(expectedHome::equals).isPresent(),
                            "The valid HOME assignment must persist during blocked interaction");
                }
                PathRequestDiagnostics.SearchSnapshot searches = PathRequestDiagnostics.snapshot(villager);
                closedRouteSearches[0] = searches.ordinarySearches() + searches.extendedSearches();
                MCA.LOGGER.info("[MCA-STAIR-STRESS] event=interaction-route-open closedSearches={} stats={}",
                        closedRouteSearches[0], searches);
                // Open a direct descent and retain a half-height landing to avoid fall damage.
                BlockPos opening = lower.east(2).south();
                for (int y = 1; y <= 5; y++) {
                    helper.getLevel().setBlock(opening.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
                helper.getLevel().setBlock(opening, Blocks.SPRUCE_SLAB.defaultBlockState()
                        .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 3);
            }
            if (ticks[0] == 500) {
                observation.report(reportLabel, ticks[0]);
                boolean reachedPlayer = villager.onGround() && Math.abs(villager.getY() - player.getY()) <= 1.0D
                        && villager.distanceToSqr(player) < 9.0D;
                villager.getInteractions().stopInteracting();
                if (withHome) {
                    villager.releasePoi(MemoryModuleType.HOME);
                }
                villager.discard();
                player.discard();
                helper.assertTrue(lookingAtPlayerTicks[0] >= 400,
                        "The real interaction task must keep looking at the player during failed movement");
                helper.assertTrue(closedRouteSearches[0] > 0 && closedRouteSearches[0] <= 40,
                        "The same blocked interaction must not search every tick: searches=" + closedRouteSearches[0]);
                helper.assertTrue(reachedPlayer, "Opening the route must allow interaction movement to resume");
                helper.assertTrue(!observation.sustainedSpin,
                        "Repeated interaction requests caused a local rotation loop beside the stair wall");
                helper.succeed();
            }
        });
    }

    private static void assignValidHomeBesideStairEntrance(GameTestHelper helper, VillagerEntityMCA villager,
                                                          BlockPos lower) {
        // The prepared flat floor west of the first stair lies inside this template and
        // keeps the bed out of the blocked player enclosure and its later doorway.
        BlockPos foot = lower.west();
        BlockPos head = foot.west();
        var bed = Blocks.BED.pick(DyeColor.RED).defaultBlockState().setValue(BedBlock.FACING, Direction.WEST);
        helper.getLevel().setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        helper.assertTrue(helper.getLevel().getPoiManager().existsAtPosition(PoiTypes.HOME, head),
                "The REST interaction fixture must register a real HOME bed POI");
        helper.assertTrue(BedApproachTarget.create(helper.getLevel(), head)
                        .filter(approach -> !approach.getPathTargets(villager).isEmpty()).isPresent(),
                "The REST interaction fixture HOME must have a usable bedside approach");
        helper.assertTrue(helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head), head, 1).isPresent(),
                "The REST interaction fixture villager must claim its HOME POI");
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));
    }

    private static void setBrainWalkTarget(GameTestHelper helper, VillagerEntityMCA villager, WalkTarget target) {
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, target);
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
    }

    /** Three rotation reports at the same location distinguish a loop from one ordinary turn. */
    private static final class StairTurnObservation {
        private final VillagerEntityMCA villager;
        private final WalkTarget target;
        private final StairSpinDiagnostics rotations = new StairSpinDiagnostics();
        private Vec3 anchor;
        private Path previousPath;
        private WalkTarget previousTarget;
        private int pathChanges;
        private int targetChanges;
        private int localReports;
        private int requestedTicks;
        private int stairTicks;
        private boolean sustainedSpin;

        private StairTurnObservation(VillagerEntityMCA villager, WalkTarget target) {
            this.villager = villager;
            this.target = target;
            this.anchor = villager.position();
        }

        private void sample(int tick) {
            Path path = villager.getNavigation().getPath();
            if (path != previousPath) {
                pathChanges++;
                previousPath = path;
            }
            WalkTarget current = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
            if (current != previousTarget) {
                targetChanges++;
                previousTarget = current;
            }
            boolean requested = current != null && current.getTarget().currentBlockPosition()
                    .equals(target.getTarget().currentBlockPosition());
            if (requested) requestedTicks++;
            BlockPos support = BlockPos.containing(villager.getX(), villager.getY() - 0.1D, villager.getZ());
            boolean onStair = villager.level().getBlockState(support).is(BlockTags.STAIRS)
                    || villager.level().getBlockState(villager.blockPosition()).is(BlockTags.STAIRS);
            if (onStair) stairTicks++;
            double dx = villager.getX() - anchor.x;
            double dz = villager.getZ() - anchor.z;
            if (dx * dx + dz * dz > 0.49D || !requested) {
                anchor = villager.position();
                localReports = 0;
            }
            StairSpinDiagnostics.SpinEvent rotation = rotations.sample(tick, onStair,
                    villager.getX(), villager.getZ(), villager.getYRot(), villager.yBodyRot);
            if (rotation != null && requested) {
                localReports++;
                sustainedSpin |= localReports >= 3;
                MCA.LOGGER.info("[MCA-STAIR-STRESS] event=rotation uuid={} tick={} source={} degrees={} reports={} "
                                + "pos={} path={} health={}",
                        villager.getUUID(), tick, rotation.source(), rotation.rotationDegrees(), localReports,
                        villager.position(), describePath(path), villager.getHealth());
            }
            if (tick % 50 == 0) report("sample", tick);
        }

        private void report(String label, int tick) {
            MCA.LOGGER.info("[MCA-STAIR-STRESS] event={} uuid={} tick={} pos={} yaw={} bodyYaw={} headYaw={} "
                            + "onGround={} collision={} wanted=({}, {}, {}) path={} pathChanges={} "
                            + "originalTarget={} currentTarget={} targetChanges={} requestedTicks={} stairTicks={} sustainedSpin={} health={}",
                    label, villager.getUUID(), tick, villager.position(), villager.getYRot(), villager.yBodyRot,
                    villager.yHeadRot, villager.onGround(), villager.horizontalCollision,
                    villager.getMoveControl().getWantedX(), villager.getMoveControl().getWantedY(),
                    villager.getMoveControl().getWantedZ(), describePath(villager.getNavigation().getPath()), pathChanges,
                    target.getTarget().currentBlockPosition(),
                    villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                            .map(walk -> walk.getTarget().currentBlockPosition()).orElse(null),
                    targetChanges, requestedTicks, stairTicks, sustainedSpin, villager.getHealth());
        }
    }

    /** Observe short turning and recovery across isolated descents, without another actor or interaction. */
    @GameTest(batch = "mca_stair_spin_repeated_solo_descent", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 2440)
    public static void repeatedSoloStairDescentsDoNotBrieflySpin(GameTestHelper helper) {
        BlockPos lower = prepareThreeStepDescent(helper, true,
                Blocks.SPRUCE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM));
        BlockPos target = lower.east(2).south(3);
        VillagerEntityMCA[] actor = {null};
        StairSpinDiagnostics[] rotations = {null};
        int[] trial = {0};
        int[] elapsed = {0};
        int[] stairTicks = {0};
        int[] turningEvents = {0};
        int[] totalTurningEvents = {0};
        float[] minimumHealth = {20.0F};
        helper.onEachTick(() -> {
            if (actor[0] == null) {
                float size = (trial[0] % 3) * 0.5F;
                float width = ((trial[0] / 3) % 3) * 0.5F;
                VillagerEntityMCA villager = spawnVillager(helper, lower.east(4).above(3),
                        "Solo descent " + trial[0], size, width);
                double lateralOffset = ((trial[0] % 3) - 1) * 0.14D;
                villager.setPos(villager.position().add(0.0D, 0.0D, lateralOffset));
                villager.setYRot(trial[0] * 30.0F);
                if (!helper.getLevel().noBlockCollision(villager, villager.getBoundingBox())) {
                    villager.discard();
                    helper.fail("Solo descent starting pose is obstructed; trial=" + trial[0]);
                    return;
                }
                setBrainWalkTarget(helper, villager, new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0));
                actor[0] = villager;
                rotations[0] = new StairSpinDiagnostics();
                elapsed[0] = 0;
                stairTicks[0] = 0;
                turningEvents[0] = 0;
                minimumHealth[0] = villager.getHealth();
            }
            VillagerEntityMCA villager = actor[0];
            elapsed[0]++;
            minimumHealth[0] = Math.min(minimumHealth[0], villager.getHealth());
            BlockPos support = BlockPos.containing(villager.getX(), villager.getY() - 0.1D, villager.getZ());
            boolean touchingStair = villager.level().getBlockState(support).is(BlockTags.STAIRS)
                    || villager.level().getBlockState(villager.blockPosition()).is(BlockTags.STAIRS);
            if (touchingStair) stairTicks[0]++;
            StairSpinDiagnostics.SpinEvent turn = rotations[0].sample(elapsed[0], touchingStair,
                    villager.getX(), villager.getZ(), villager.getYRot(), villager.yBodyRot);
            if (turn != null) {
                turningEvents[0]++;
                totalTurningEvents[0]++;
                MCA.LOGGER.info("[MCA-STAIR-SOLO] event=turn trial={} tick={} pos={} rotation={} source={} "
                                + "health={} activity={} collision={} path={}",
                        trial[0], elapsed[0], villager.position(), turn.rotationDegrees(), turn.source(),
                        villager.getHealth(), villager.getBrain().getActiveNonCoreActivity().orElse(null),
                        villager.horizontalCollision, describePath(villager.getNavigation().getPath()));
            }
            if (villager.onGround() && villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D) {
                MCA.LOGGER.info("[MCA-STAIR-SOLO] event=arrived trial={} ticks={} stairTicks={} turnEvents={} "
                                + "minimumHealth={} width={} height={}",
                        trial[0], elapsed[0], stairTicks[0], turningEvents[0], minimumHealth[0],
                        villager.getBbWidth(), villager.getBbHeight());
                villager.discard();
                actor[0] = null;
                helper.assertTrue(stairTicks[0] >= 2, "Solo route did not traverse the stairs");
                helper.assertTrue(minimumHealth[0] >= villager.getMaxHealth(),
                        "Solo descent caused damage; trial=" + trial[0]);
                if (++trial[0] == 12) {
                    helper.assertTrue(totalTurningEvents[0] == 0,
                            "Brief local turning occurred during a self-recovering solo descent: events="
                                    + totalTurningEvents[0]);
                    helper.succeed();
                }
            } else if (elapsed[0] >= 200) {
                String details = "trial=" + trial[0] + " pos=" + villager.position()
                        + " health=" + villager.getHealth() + " turnEvents=" + turningEvents[0]
                        + " activity=" + villager.getBrain().getActiveNonCoreActivity().orElse(null)
                        + " path=" + describePath(villager.getNavigation().getPath());
                villager.discard();
                helper.fail("Solo descent did not recover unaided: " + details);
            }
        });
    }

    @GameTest(batch = "mca_walk_through_grass", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void villagerCrossesShortAndTallGrassWithoutSpinning(GameTestHelper helper) {
        runGrassRoute(helper, false);
    }

    @GameTest(batch = "mca_turn_on_grassy_slope", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 240)
    public static void villagerTurnsDownUnevenGrassyGroundWithoutSpinning(GameTestHelper helper) {
        runGrassRoute(helper, true);
    }

    private static void runGrassRoute(GameTestHelper helper, boolean uneven) {
        BlockPos start = prepareFixture(helper).stair().west().west(3).south(2);
        if (uneven) {
            // Clear a natural one-block ledge into the lower clearing. The
            // copied house's floor already supplies the underlying support.
            for (int x = 0; x <= 3; x++) {
                helper.getLevel().setBlockAndUpdate(start.east(x), Blocks.GRASS_BLOCK.defaultBlockState());
            }
            start = start.above();
        }
        for (int x = 0; x <= (uneven ? 3 : 6); x++) {
            BlockPos feet = start.east(x);
            helper.getLevel().setBlockAndUpdate(feet.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            if (x % 2 == 1) {
                helper.getLevel().setBlockAndUpdate(feet, Blocks.SHORT_GRASS.defaultBlockState());
            } else if (x > 0) {
                DoublePlantBlock.placeAt(helper.getLevel(), Blocks.TALL_GRASS.defaultBlockState(), feet, 3);
            }
        }
        BlockPos destination = uneven ? start.east(5).north().below() : start.east(6);
        if (uneven) {
            helper.getLevel().setBlockAndUpdate(destination.below(), Blocks.GRASS_BLOCK.defaultBlockState());
            DoublePlantBlock.placeAt(helper.getLevel(), Blocks.TALL_GRASS.defaultBlockState(), destination, 3);
        }
        VillagerEntityMCA villager = spawnVillager(helper, start, "Grass traversal probe");
        WalkTarget request = new WalkTarget(destination, 0.65F, 0);
        setBrainWalkTarget(helper, villager, request);
        StairSpinDiagnostics rotations = new StairSpinDiagnostics();
        int[] tick = {0};
        int[] shortGrassTicks = {0};
        int[] tallGrassTicks = {0};
        int[] collisionTicks = {0};
        int[] spinEvents = {0};
        helper.onEachTick(() -> {
            tick[0]++;
            BlockState feet = helper.getLevel().getBlockState(villager.blockPosition());
            boolean inGrass = feet.is(Blocks.SHORT_GRASS) || feet.is(Blocks.TALL_GRASS);
            if (feet.is(Blocks.SHORT_GRASS)) shortGrassTicks[0]++;
            if (feet.is(Blocks.TALL_GRASS)) tallGrassTicks[0]++;
            if (villager.horizontalCollision) collisionTicks[0]++;
            StairSpinDiagnostics.SpinEvent spin = rotations.sample(tick[0], inGrass,
                    villager.getX(), villager.getZ(), villager.getYRot(), villager.yBodyRot);
            if (spin != null) spinEvents[0]++;
            boolean arrived = villager.onGround() && villager.blockPosition().equals(destination);
            if (arrived || tick[0] >= 180) {
                MCA.LOGGER.info("[MCA-GRASS-PROBE] uneven={} arrived={} ticks={} shortGrassTicks={} tallGrassTicks={} "
                                + "collisionTicks={} spins={} pos={} path={} health={}", uneven, arrived, tick[0],
                        shortGrassTicks[0], tallGrassTicks[0], collisionTicks[0],
                        spinEvents[0], villager.position(), describePath(villager.getNavigation().getPath()),
                        villager.getHealth());
                villager.discard();
                helper.assertTrue(arrived && shortGrassTicks[0] > 0 && tallGrassTicks[0] > 0,
                        "Villager must cross both kinds of grass on the way to the destination");
                helper.assertTrue(spinEvents[0] == 0 && (uneven || collisionTicks[0] == 0),
                        "Grass route produced a local spin or an unexpected collision on flat ground");
                helper.succeed();
            }
        });
    }

    private static void runBrainRoute(GameTestHelper helper, BlockPos start, BlockPos target, String label) {
        VillagerEntityMCA villager = spawnVillager(helper, start, "Stair Wall " + label);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0)
        );
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        int[] ticks = {0};
        String[] firstPath = {null};
        helper.onEachTick(() -> {
            ticks[0]++;
            Path path = villager.getNavigation().getPath();
            if (firstPath[0] == null && path != null) {
                firstPath[0] = describePath(path);
            }

            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.25D) {
                villager.discard();
                helper.succeed();
                return;
            }

            if (ticks[0] >= ROUTE_TIMEOUT_TICKS) {
                Vec3 position = villager.position();
                boolean horizontalCollision = villager.horizontalCollision;
                String currentPath = describePath(villager.getNavigation().getPath());
                villager.discard();
                helper.fail("stair/wall route stalled; route=" + label
                        + ", pos=" + position
                        + ", horizontalCollision=" + horizontalCollision
                        + ", firstPath=" + firstPath[0]
                        + ", currentPath=" + currentPath);
            }
        });
    }

    private static void prepareRaisedLanding(GameTestHelper helper, BlockPos stair) {
        helper.getLevel().setBlock(stair.east(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(stair.east(2), Blocks.COBBLESTONE.defaultBlockState(), 3);
    }

    private static Fixture prepareFixture(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        for (int x = feet.getX() - 3; x <= feet.getX() + 4; x++) {
            for (int z = feet.getZ() - 3; z <= feet.getZ() + 3; z++) {
                helper.getLevel().setBlock(new BlockPos(x, feet.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = feet.getY(); y <= feet.getY() + 5; y++) {
                    helper.getLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        BlockPos stair = feet.east();
        helper.getLevel().setBlock(stair, Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM), 3);
        BlockPos wall = stair.north();
        helper.getLevel().setBlock(wall, Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        helper.getLevel().setBlock(wall.above(), Blocks.TORCH.defaultBlockState(), 3);
        helper.getLevel().setBlock(stair.above(3), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP), 3);
        return new Fixture(stair);
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet, String name) {
        return spawnVillager(helper, feet, name, 0.5F, 0.5F);
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet, String name,
                                                  float size, float width) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName(name)
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.getGenetics().setGene(Genetics.SIZE, size);
        villager.getGenetics().setGene(Genetics.WIDTH, width);
        villager.refreshDimensions();
        villager.setOnGround(true);
        return villager;
    }

    private static String describePath(Path path) {
        if (path == null) {
            return "null";
        }

        int shownNodes = Math.min(path.getNodeCount(), 10);
        StringBuilder description = new StringBuilder("next=")
                .append(path.getNextNodeIndex())
                .append('/').append(path.getNodeCount())
                .append(" nodes=");
        for (int index = 0; index < shownNodes; index++) {
            if (index > 0) {
                description.append("->");
            }
            BlockPos node = path.getNodePos(index);
            description.append('(')
                    .append(node.getX()).append(',')
                    .append(node.getY()).append(',')
                    .append(node.getZ()).append(')');
        }
        if (shownNodes < path.getNodeCount()) {
            description.append("->...");
        }
        return description.toString();
    }

    private record Fixture(BlockPos stair) {
    }
}
