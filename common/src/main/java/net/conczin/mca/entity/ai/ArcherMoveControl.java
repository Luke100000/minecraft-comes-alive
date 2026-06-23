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
    private static final double STRAFE_EDGE_LOOKAHEAD = 0.8;

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
                float redirectedZa = -za;
                float redirectedDx = xa * cos - redirectedZa * sin;
                float redirectedDz = redirectedZa * cos + xa * sin;
                if (this.isWalkable(redirectedDx, redirectedDz)) {
                    this.strafeRight = -this.strafeRight;
                    this.lastStrafeResult = StrafeResult.REDIRECTED;
                } else {
                    this.strafeForwards = 0.0F;
                    this.strafeRight = 0.0F;
                    this.lastStrafeResult = StrafeResult.BLOCKED;
                }
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
            BlockPos nextPos = BlockPos.containing(this.mob.getX() + dx, this.mob.getBlockY(), this.mob.getZ() + dz);
            BlockPos lookaheadPos = this.getStrafeLookaheadPos(dx, dz);
            if (!isWalkableDestination(pathNavigation, nodeEvaluator, nextPos)
                    || !isWalkableDestination(pathNavigation, nodeEvaluator, lookaheadPos)) {
                return false;
            }
        }

        return true;
    }

    private boolean isWalkableDestination(PathNavigation pathNavigation, NodeEvaluator nodeEvaluator, BlockPos pos) {
        return isStableWalkable(pathNavigation, nodeEvaluator, pos)
                || isStableWalkable(pathNavigation, nodeEvaluator, pos.below());
    }

    private boolean isStableWalkable(PathNavigation pathNavigation, NodeEvaluator nodeEvaluator, BlockPos pos) {
        return (nodeEvaluator == null || nodeEvaluator.getPathType(this.mob, pos) == PathType.WALKABLE)
                && pathNavigation.isStableDestination(pos);
    }

    private BlockPos getStrafeLookaheadPos(float dx, float dz) {
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0E-5) {
            return this.mob.blockPosition();
        }

        double lookahead = Math.max(STRAFE_EDGE_LOOKAHEAD, this.mob.getBbWidth() * 0.5 + 0.25);
        return BlockPos.containing(
                this.mob.getX() + dx / length * lookahead,
                this.mob.getBlockY(),
                this.mob.getZ() + dz / length * lookahead
        );
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
