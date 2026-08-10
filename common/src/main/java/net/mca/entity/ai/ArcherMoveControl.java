package net.mca.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.NodeEvaluator;

/**
 * Move control used by MCA archers so strafing can report when terrain blocks or redirects a step.
 */
public class ArcherMoveControl extends MCAMoveControl {
    private boolean emergencyFleeing;
    private StrafeResult lastStrafeResult = StrafeResult.NONE;
    private boolean archerStrafeRequested;

    public ArcherMoveControl(Mob entity) {
        super(entity);
    }

    public void setEmergencyFleeing(boolean emergencyFleeing) {
        this.emergencyFleeing = emergencyFleeing;
    }

    public boolean isEmergencyFleeing() {
        return emergencyFleeing;
    }

    public boolean wasLastStrafeBlocked() {
        return lastStrafeResult == StrafeResult.BLOCKED;
    }

    public String getLastStrafeResult() {
        return lastStrafeResult.debugName;
    }

    @Override
    public void strafe(float forwards, float sideways) {
        this.archerStrafeRequested = false;
        super.strafe(forwards, sideways);
    }

    public void strafeForArcher(float forwards, float sideways) {
        super.strafe(forwards, sideways);
        this.archerStrafeRequested = true;
    }

    @Override
    public void tick() {
        if (isClimbNavigationActive()) {
            clearArcherStrafeState();
            super.tick();
            return;
        }

        if (this.operation == Operation.STRAFE && this.archerStrafeRequested) {
            tickArcherStrafe();
            this.archerStrafeRequested = false;
            return;
        }

        clearArcherStrafeState();
        super.tick();
    }

    private void clearArcherStrafeState() {
        this.archerStrafeRequested = false;
        if (this.lastStrafeResult != StrafeResult.NONE) {
            this.mob.setXxa(0.0F);
            this.mob.setZza(0.0F);
            this.lastStrafeResult = StrafeResult.NONE;
        }
    }

    private void tickArcherStrafe() {
        float movementSpeed = (float) this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
        float modifiedSpeed = (float) this.speedModifier * movementSpeed;
        float forward = this.strafeForwards;
        float sideways = this.strafeRight;
        float distance = Mth.sqrt(forward * forward + sideways * sideways);
        if (distance < 1.0F) {
            distance = 1.0F;
        }

        distance = modifiedSpeed / distance;
        forward *= distance;
        sideways *= distance;
        float sin = Mth.sin(this.mob.getYRot() * ((float) Math.PI / 180.0F));
        float cos = Mth.cos(this.mob.getYRot() * ((float) Math.PI / 180.0F));
        float dx = forward * cos - sideways * sin;
        float dz = sideways * cos + forward * sin;
        if (!isWalkable(dx, dz)) {
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

        this.mob.setSpeed(modifiedSpeed);
        this.mob.setZza(this.strafeForwards);
        this.mob.setXxa(this.strafeRight);
        this.operation = Operation.WAIT;
    }

    private boolean isWalkable(float dx, float dz) {
        PathNavigation navigation = this.mob.getNavigation();
        if (navigation != null) {
            NodeEvaluator nodeMaker = navigation.getNodeEvaluator();
            BlockPos pos = BlockPos.containing(this.mob.getX() + dx, this.mob.getBlockY(), this.mob.getZ() + dz);
            if (nodeMaker != null
                    && nodeMaker.getBlockPathType(this.mob.level(), pos.getX(), pos.getY(), pos.getZ(), this.mob) != BlockPathTypes.WALKABLE) {
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
