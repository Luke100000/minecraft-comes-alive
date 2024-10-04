package net.mca.entity.ai.goal;

import net.mca.entity.GrimReaperEntity;
import net.minecraft.entity.ai.NoWaterTargeting;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;

public class GrimReaperIdleGoal extends Goal {
    protected final GrimReaperEntity reaper;

    protected final double speedModifier;
    protected final int interval;

    protected double wantedX;
    protected double wantedY;
    protected double wantedZ;

    public GrimReaperIdleGoal(GrimReaperEntity reaper, double speed) {
        this(reaper, speed, 120);
    }

    public GrimReaperIdleGoal(GrimReaperEntity reaper, double speed, int interval) {
        this.reaper = reaper;
        this.speedModifier = speed;
        this.interval = interval;

        setControls(EnumSet.of(Goal.Control.MOVE));
    }

    @Override
    public boolean canStart() {
        if (this.reaper.getRandom().nextInt(this.interval) != 0) {
            return false;
        }

        Vec3d vector3d = getPosition();
        if (vector3d == null) {
            return false;
        }

        wantedX = vector3d.x;
        wantedY = vector3d.y;
        wantedZ = vector3d.z;
        return true;
    }

    @Nullable
    protected Vec3d getPosition() {
        if (reaper.getTarget() != null) {
            return reaper.getTarget().getPos();
        }

        return NoWaterTargeting.find(reaper, 8, 6, -1, Vec3d.ofBottomCenter(reaper.getBlockPos()), 1);
    }

    @Override
    public boolean shouldContinue() {
        return !reaper.getNavigation().isIdle();
    }

    @Override
    public void start() {
        reaper.getNavigation().startMovingTo(wantedX, wantedY, wantedZ, speedModifier);
    }

    @Override
    public void stop() {
        reaper.getNavigation().stop();
        super.stop();
    }
}
