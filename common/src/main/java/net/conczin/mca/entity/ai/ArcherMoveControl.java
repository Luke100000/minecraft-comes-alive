package net.conczin.mca.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.PathType;

public class ArcherMoveControl extends MoveControl {
    private boolean emergencyFleeing;
    private StrafeResult lastStrafeResult = StrafeResult.NONE;

    public ArcherMoveControl(Mob mob) {
        super(mob);
    }

    public void setEmergencyFleeing(boolean emergencyFleeing) {
        this.emergencyFleeing = emergencyFleeing;
    }

    public boolean isEmergencyFleeing() {
        return this.emergencyFleeing;
    }

    public boolean wasLastStrafeBlocked() {
        return this.lastStrafeResult == StrafeResult.BLOCKED;
    }

    public String getLastStrafeResult() {
        return this.lastStrafeResult.debugName;
    }

    @Override
    public void tick() {
        if (this.operation == Operation.STRAFE) {
            tickStrafe();
            return;
        }

        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
        this.lastStrafeResult = StrafeResult.NONE;
        super.tick();
    }

    private void tickStrafe() {
        float speed = (float)this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float speedModified = (float)this.speedModifier * speed;
        float xa = this.strafeForwards;
        float za = this.strafeRight;
        float dist = Mth.sqrt(xa * xa + za * za);
        if (dist < 1.0F) {
            dist = 1.0F;
        }

        dist = speedModified / dist;
        xa *= dist;
        za *= dist;
        float sin = Mth.sin(this.mob.getYRot() * (float)(Math.PI / 180.0));
        float cos = Mth.cos(this.mob.getYRot() * (float)(Math.PI / 180.0));
        float dx = xa * cos - za * sin;
        float dz = za * cos + xa * sin;
        if (!this.isWalkable(dx, dz)) {
            if (this.strafeRight != 0.0F) {
                this.strafeRight = -this.strafeRight;
                this.lastStrafeResult = StrafeResult.REDIRECTED;
            } else {
                this.strafeForwards = 0.0F;
                this.strafeRight = 0.0F;
                this.lastStrafeResult = StrafeResult.BLOCKED;
            }
        } else {
            this.lastStrafeResult = StrafeResult.ACCEPTED;
        }

        this.mob.setSpeed(speedModified);
        this.mob.setZza(this.strafeForwards);
        this.mob.setXxa(this.strafeRight);
        this.operation = Operation.WAIT;
    }

    private boolean isWalkable(float dx, float dz) {
        PathNavigation pathNavigation = this.mob.getNavigation();
        if (pathNavigation != null) {
            NodeEvaluator nodeEvaluator = pathNavigation.getNodeEvaluator();
            if (nodeEvaluator != null
                    && nodeEvaluator.getPathType(this.mob, BlockPos.containing(this.mob.getX() + dx, this.mob.getBlockY(), this.mob.getZ() + dz))
                    != PathType.WALKABLE) {
                return false;
            }
        }

        return true;
    }

    private enum StrafeResult {
        NONE("none"),
        ACCEPTED("accepted"),
        REDIRECTED("redirected"),
        BLOCKED("blocked");

        private final String debugName;

        StrafeResult(String debugName) {
            this.debugName = debugName;
        }
    }
}
