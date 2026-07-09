package net.mca.entity.ai;

import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

/**
 * Move control used by MCA archers so strafing can report when terrain blocks or redirects a step.
 */
public class ArcherMoveControl extends MoveControl {
    private boolean emergencyFleeing;
    private StrafeResult lastStrafeResult = StrafeResult.NONE;

    public ArcherMoveControl(MobEntity entity) {
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
    public void tick() {
        if (this.state == State.STRAFE) {
            tickStrafe();
            return;
        }

        this.entity.setSidewaysSpeed(0.0F);
        this.entity.setForwardSpeed(0.0F);
        this.lastStrafeResult = StrafeResult.NONE;
        super.tick();
    }

    private void tickStrafe() {
        float movementSpeed = (float) this.entity.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        float modifiedSpeed = (float) this.speed * movementSpeed;
        float forward = this.forwardMovement;
        float sideways = this.sidewaysMovement;
        float distance = MathHelper.sqrt(forward * forward + sideways * sideways);
        if (distance < 1.0F) {
            distance = 1.0F;
        }

        distance = modifiedSpeed / distance;
        forward *= distance;
        sideways *= distance;
        float sin = MathHelper.sin(this.entity.getYaw() * ((float) Math.PI / 180.0F));
        float cos = MathHelper.cos(this.entity.getYaw() * ((float) Math.PI / 180.0F));
        float dx = forward * cos - sideways * sin;
        float dz = sideways * cos + forward * sin;
        if (!isWalkable(dx, dz)) {
            if (this.sidewaysMovement != 0.0F) {
                this.sidewaysMovement = -this.sidewaysMovement;
                this.lastStrafeResult = StrafeResult.REDIRECTED;
            } else {
                this.forwardMovement = 0.0F;
                this.sidewaysMovement = 0.0F;
                this.lastStrafeResult = StrafeResult.BLOCKED;
            }
        } else {
            this.lastStrafeResult = StrafeResult.ACCEPTED;
        }

        this.entity.setMovementSpeed(modifiedSpeed);
        this.entity.setForwardSpeed(this.forwardMovement);
        this.entity.setSidewaysSpeed(this.sidewaysMovement);
        this.state = State.WAIT;
    }

    private boolean isWalkable(float dx, float dz) {
        EntityNavigation navigation = this.entity.getNavigation();
        if (navigation != null) {
            PathNodeMaker nodeMaker = navigation.getNodeMaker();
            BlockPos pos = BlockPos.ofFloored(this.entity.getX() + dx, this.entity.getBlockY(), this.entity.getZ() + dz);
            if (nodeMaker != null
                    && nodeMaker.getNodeType(this.entity.getWorld(), pos.getX(), pos.getY(), pos.getZ(), this.entity) != PathNodeType.WALKABLE) {
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
