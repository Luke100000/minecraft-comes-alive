package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/** Optional server-side diagnostics for villagers turning repeatedly at a staircase. */
final class StairSpinDiagnostics {
    static final boolean ENABLED = Boolean.parseBoolean(System.getProperty("mca.stairSpinDebug", "false"));

    private static final double MAX_LOCAL_TRAVEL_SQR = 0.49D;
    private static final long WINDOW_TICKS = 60L;
    private static final long MIN_SPIN_TICKS = 8L;
    private static final double SPIN_DEGREES = 270.0D;
    private static final long REPORT_COOLDOWN_TICKS = 100L;
    private static final long STAIR_CONTACT_GRACE_TICKS = 10L;

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

    record SpinEvent(String source, double rotationDegrees, long ticks, double anchorX, double anchorZ) {
    }

    void tick(Mob mob, @Nullable Path path) {
        if (mob.level().isClientSide()) return;

        BlockPos feet = mob.blockPosition();
        BlockPos support = BlockPos.containing(mob.getX(), mob.getY() - 0.1D, mob.getZ());
        boolean touchingStair = isStair(mob.level().getBlockState(feet))
                || isStair(mob.level().getBlockState(support));
        SpinEvent spin = sample(mob.level().getGameTime(), touchingStair,
                mob.getX(), mob.getZ(), mob.getYRot(), mob.yBodyRot);
        boolean nearStair = observing;

        if (nearStair != onStair) {
            MCA.LOGGER.info("[MCA-STAIR-SPIN] event={} uuid={} dimension={} tick={} pos={} yaw={} bodyYaw={} headYaw={} support={} path={}",
                    nearStair ? "enter" : "leave", mob.getUUID(), mob.level().dimension().location(),
                    mob.level().getGameTime(), mob.position(),
                    mob.getYRot(), mob.yBodyRot, mob.yHeadRot,
                    mob.level().getBlockState(support).getBlock(), describePath(path));
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
                    mob.getDeltaMovement(), mob.level().getBlockState(support).getBlock(), describePath(path),
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

    @Nullable
    SpinEvent sample(long tick, boolean touchingStair, double x, double z, float yaw, float bodyYaw) {
        if (touchingStair) {
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
