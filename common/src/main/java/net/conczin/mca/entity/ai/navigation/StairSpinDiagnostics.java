package net.conczin.mca.entity.ai.navigation;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** Windowed yaw/body-rotation detector for stationary spin checks. */
final class StairSpinDiagnostics {
    private static final double MAX_LOCAL_TRAVEL_SQR = 0.49D;
    private static final long WINDOW_TICKS = 60L;
    private static final long MIN_SPIN_TICKS = 8L;
    private static final double SPIN_DEGREES = 270.0D;
    private static final long REPORT_COOLDOWN_TICKS = 100L;
    private static final long STAIR_CONTACT_GRACE_TICKS = 10L;

    private boolean observing;
    private double anchorX;
    private double anchorZ;
    private float previousYaw;
    private float previousBodyYaw;
    private double rotationDegrees;
    private double bodyRotationDegrees;
    private long windowStart;
    private long lastReportedAt = -REPORT_COOLDOWN_TICKS;
    private long lastStairContact = -STAIR_CONTACT_GRACE_TICKS - 1L;

    record SpinEvent(String source, double rotationDegrees, long ticks, double anchorX, double anchorZ) {
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
}
