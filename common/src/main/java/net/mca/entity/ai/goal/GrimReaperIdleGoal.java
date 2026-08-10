package net.mca.entity.ai.goal;

import net.mca.entity.GrimReaperEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.AirRandomPos;
import net.minecraft.world.phys.Vec3;
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

        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.reaper.getRandom().nextInt(this.interval) != 0) {
            return false;
        }

        Vec3 vector3d = getPosition();
        if (vector3d == null) {
            return false;
        }

        wantedX = vector3d.x;
        wantedY = vector3d.y;
        wantedZ = vector3d.z;
        return true;
    }

    @Nullable
    protected Vec3 getPosition() {
        if (reaper.getTarget() != null) {
            return reaper.getTarget().position();
        }

        return AirRandomPos.getPosTowards(reaper, 8, 6, -1, Vec3.atBottomCenterOf(reaper.blockPosition()), 1);
    }

    @Override
    public boolean canContinueToUse() {
        return !reaper.getNavigation().isDone();
    }

    @Override
    public void start() {
        reaper.getNavigation().moveTo(wantedX, wantedY, wantedZ, speedModifier);
    }

    @Override
    public void stop() {
        reaper.getNavigation().stop();
        super.stop();
    }
}
