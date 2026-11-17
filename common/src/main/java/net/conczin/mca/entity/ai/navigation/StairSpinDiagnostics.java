package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;

/** Optional server-side diagnostics for villagers turning repeatedly in place. */
final class StairSpinDiagnostics {
    private static final boolean STAIRS = Boolean.getBoolean("mca.stairSpinDebug");
    private static final boolean GRASS = Boolean.getBoolean("mca.grassSpinDebug");
    private static final boolean ALL_TERRAIN = Boolean.getBoolean("mca.movementSpinDebug");
    static final boolean ENABLED = STAIRS || GRASS || ALL_TERRAIN;

    private static final double MAX_LOCAL_TRAVEL_SQR = 0.49D;
    private static final long WINDOW_TICKS = 60L;
    private static final long MIN_SPIN_TICKS = 8L;
    private static final double SPIN_DEGREES = 270.0D;
    private static final long REPORT_COOLDOWN_TICKS = 100L;
    private static final long STAIR_CONTACT_GRACE_TICKS = 10L;
    private static final int TRACE_LIMIT = 16;

    private boolean observing;
    private boolean onStair;
    private double anchorX;
    private double anchorZ;
    private float previousYaw;
    private float previousBodyYaw;
    private double rotationDegrees;
    private double bodyRotationDegrees;
    private long windowStart;
    private long lastReportedAt = -REPORT_COOLDOWN_TICKS;
    private long lastStairContact = -STAIR_CONTACT_GRACE_TICKS - 1L;
    private int onStairTicks;
    @Nullable
    private ArrayDeque<MovementSample> movementTrace;

    record SpinEvent(String source, double rotationDegrees, long ticks, double anchorX, double anchorZ) {
    }

    void tick(Mob mob, @Nullable Path path) {
        if (mob.level().isClientSide()) return;

        BlockPos feet = mob.blockPosition();
        BlockPos support = BlockPos.containing(mob.getX(), mob.getY() - 0.1D, mob.getZ());
        BlockState feetState = mob.level().getBlockState(feet);
        BlockState supportState = mob.level().getBlockState(support);
        boolean touchingStair = STAIRS && (isStair(feetState) || isStair(supportState));
        boolean inGrass = GRASS && (feetState.is(Blocks.SHORT_GRASS) || feetState.is(Blocks.TALL_GRASS));
        long tick = mob.level().getGameTime();
        SpinEvent spin = sample(tick, ALL_TERRAIN || touchingStair || inGrass,
                mob.getX(), mob.getZ(), mob.getYRot(), mob.yBodyRot);
        boolean nearStair = observing;

        if (GRASS || ALL_TERRAIN) {
            if (ALL_TERRAIN || inGrass || movementTrace != null && observing && !touchingStair) {
                int pathId = System.identityHashCode(path);
                if (tick % 4L == 0L || spin != null || movementTrace == null
                        || movementTrace.getLast().pathId() != pathId) {
                    recordMovementSample(mob, path, tick, pathId);
                }
                if (spin != null) logMovementSpin(mob, path, spin, feetState, supportState);
            } else {
                movementTrace = null;
            }
        }
        if (!STAIRS) return;

        if (nearStair != onStair) {
            MCA.LOGGER.info("[MCA-STAIR-SPIN] event={} uuid={} dimension={} tick={} pos={} yaw={} bodyYaw={} headYaw={} support={} path={}",
                    nearStair ? "enter" : "leave", mob.getUUID(), mob.level().dimension().location(),
                    mob.level().getGameTime(), mob.position(),
                    mob.getYRot(), mob.yBodyRot, mob.yHeadRot,
                    supportState.getBlock(), describePath(path));
            onStair = nearStair;
            onStairTicks = 0;
        }

        if (spin != null) {
            MCA.LOGGER.warn("[MCA-STAIR-SPIN] event=spin source={} uuid={} dimension={} tick={} pos={} anchor=({}, {}) "
                            + "rotation={}deg window={}ticks yaw={} bodyYaw={} headYaw={} onGround={} collision={} velocity={} "
                            + "support={} path={} wanted=({}, {}, {})",
                    spin.source(), mob.getUUID(), mob.level().dimension().location(), mob.level().getGameTime(), mob.position(),
                    spin.anchorX(), spin.anchorZ(),
                    spin.rotationDegrees(), spin.ticks(), mob.getYRot(), mob.yBodyRot, mob.yHeadRot,
                    mob.onGround(), mob.horizontalCollision,
                    mob.getDeltaMovement(), supportState.getBlock(), describePath(path),
                    mob.getMoveControl().getWantedX(), mob.getMoveControl().getWantedY(),
                    mob.getMoveControl().getWantedZ());
        }

        if (nearStair && ++onStairTicks % 20 == 0) {
            MCA.LOGGER.info("[MCA-STAIR-SPIN] event=sample uuid={} tick={} pos={} yaw={} bodyYaw={} headYaw={} "
                            + "movementRotation={}deg bodyRotation={}deg onGround={} path={}",
                    mob.getUUID(), mob.level().getGameTime(), mob.position(),
                    mob.getYRot(), mob.yBodyRot, mob.yHeadRot,
                    rotationDegrees, bodyRotationDegrees, mob.onGround(), describePath(path));
        }
    }

    private void recordMovementSample(Mob mob, @Nullable Path path, long tick, int pathId) {
        if (movementTrace == null) movementTrace = new ArrayDeque<>(TRACE_LIMIT);
        if (movementTrace.size() == TRACE_LIMIT) movementTrace.removeFirst();
        BlockPos target = mob.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .map(walk -> walk.getTarget().currentBlockPosition()).orElse(null);
        int nextIndex = path == null ? -1 : path.getNextNodeIndex();
        movementTrace.addLast(new MovementSample(tick, mob.position(), target, pathId, nextIndex,
                pathNode(path, nextIndex - 1), pathNode(path, nextIndex), pathNode(path, nextIndex + 1),
                mob.getYRot(), mob.yBodyRot, mob.onGround(), mob.horizontalCollision));
    }

    @Nullable
    private static BlockPos pathNode(@Nullable Path path, int index) {
        return path != null && index >= 0 && index < path.getNodeCount() ? path.getNodePos(index) : null;
    }

    private record MovementSample(long tick, Vec3 pos, @Nullable BlockPos target, int pathId, int waypointIndex,
                                  @Nullable BlockPos previousWaypoint, @Nullable BlockPos waypoint,
                                  @Nullable BlockPos followingWaypoint, float yaw, float bodyYaw,
                                  boolean grounded, boolean collision) {
    }

    private void logMovementSpin(Mob mob, @Nullable Path path, SpinEvent spin,
                                 BlockState feet, BlockState support) {
        MCA.LOGGER.warn("{} uuid={} dimension={} tick={} pos={} feet={} support={} "
                        + "rotation={} source={} window={} yaw={} bodyYaw={} headYaw={} onGround={} "
                        + "blockCollision={} velocity={} activity={} walkTarget={} path={} wanted=({}, {}, {}) "
                        + "health={} damage={} nearbyPushables={} recentSamples={}",
                ALL_TERRAIN ? "[MCA-MOVEMENT-SPIN]" : "[MCA-GRASS-SPIN]",
                mob.getUUID(), mob.level().dimension().location(), mob.level().getGameTime(), mob.position(),
                feet, support, spin.rotationDegrees(), spin.source(), spin.ticks(), mob.getYRot(), mob.yBodyRot,
                mob.yHeadRot, mob.onGround(), mob.horizontalCollision, mob.getDeltaMovement(),
                mob.getBrain().getActiveNonCoreActivity().orElse(null),
                mob.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getTarget().currentBlockPosition()).orElse(null),
                describePath(path), mob.getMoveControl().getWantedX(), mob.getMoveControl().getWantedY(),
                mob.getMoveControl().getWantedZ(), mob.getHealth(), mob.getLastDamageSource(),
                mob.level().getEntities(mob, mob.getBoundingBox().inflate(0.5D), entity -> entity.isPushable()).size(),
                movementTrace);
    }

    @Nullable
    SpinEvent sample(long tick, boolean inObservedTerrain, double x, double z, float yaw, float bodyYaw) {
        if (inObservedTerrain) {
            lastStairContact = tick;
        } else if (tick < lastStairContact || tick - lastStairContact > STAIR_CONTACT_GRACE_TICKS) {
            // A stair's partial surface briefly puts the feet in air between steps.
            // Keep the same rotation window across that gap, not across a later journey.
            observing = false;
            return null;
        }

        double dx = x - anchorX;
        double dz = z - anchorZ;
        if (!observing || tick < windowStart || tick - windowStart > WINDOW_TICKS
                || dx * dx + dz * dz > MAX_LOCAL_TRAVEL_SQR) {
            restart(tick, x, z, yaw, bodyYaw);
            return null;
        }

        rotationDegrees += Math.abs(Mth.wrapDegrees(yaw - previousYaw));
        bodyRotationDegrees += Math.abs(Mth.wrapDegrees(bodyYaw - previousBodyYaw));
        previousYaw = yaw;
        previousBodyYaw = bodyYaw;
        long elapsedTicks = tick - windowStart;
        if (elapsedTicks >= MIN_SPIN_TICKS && (rotationDegrees >= SPIN_DEGREES || bodyRotationDegrees >= SPIN_DEGREES)
                && tick - lastReportedAt >= REPORT_COOLDOWN_TICKS) {
            boolean movementSpin = rotationDegrees >= SPIN_DEGREES;
            SpinEvent spin = new SpinEvent(movementSpin ? "movement" : "body",
                    movementSpin ? rotationDegrees : bodyRotationDegrees, elapsedTicks, anchorX, anchorZ);
            lastReportedAt = tick;
            restart(tick, x, z, yaw, bodyYaw);
            return spin;
        }
        return null;
    }

    private void restart(long tick, double x, double z, float yaw, float bodyYaw) {
        observing = true;
        anchorX = x;
        anchorZ = z;
        previousYaw = yaw;
        previousBodyYaw = bodyYaw;
        rotationDegrees = 0.0D;
        bodyRotationDegrees = 0.0D;
        windowStart = tick;
    }

    private static boolean isStair(BlockState state) {
        return state.is(BlockTags.STAIRS) || state.getBlock() instanceof StairBlock;
    }

    private static String describePath(@Nullable Path path) {
        if (path == null) return "none";
        return "next=" + path.getNextNodeIndex() + '/' + path.getNodeCount()
                + (path.isDone() ? " done" : " node=" + path.getNextNodePos())
                + " target=" + path.getTarget();
    }
}
