package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 */
public class MCAMoveControl extends MoveControl {
    private static final double MOVEMENT_EPSILON = 1.0E-6D;
    // Stay within one half-block tread when checking the immediate obstacle.
    private static final double STEP_PROBE_DISTANCE = 0.25D;

    public MCAMoveControl(Mob mob) {
        super(mob);
    }

    public void strafe(float forwards, float right, double speedModifier) {
        super.strafe(forwards, right);
        this.speedModifier = speedModifier;
    }

    public void stopStrafing() {
        if (this.operation == Operation.STRAFE) {
            this.operation = Operation.WAIT;
        }
        // Vanilla WAIT clears forward input only; released lateral input otherwise keeps accelerating the mob.
        this.mob.setXxa(0.0F);
    }

    protected final boolean isClimbNavigationActive() {
        return this.mob.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbableMovement();
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        if (isClimbNavigationActive() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }

    @Override
    public void tick() {
        if (!isClimbNavigationActive()) {
            if (this.operation == Operation.MOVE_TO) {
                tickMoveTo();
            } else {
                if (this.operation == Operation.STRAFE && this.mob.onGround()) {
                    Vec3 landing = StrafeTerrain.findLanding(this.mob, strafeProbe(0.5));
                    if (landing != null && landing.y - this.mob.getY() > this.mob.maxUpStep() + MOVEMENT_EPSILON) {
                        this.mob.getJumpControl().jump();
                    }
                }
                super.tick();
            }
            return;
        }

        if (this.operation == Operation.MOVE_TO) {
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            if (dx * dx + dz * dz > MIN_SPEED_SQR) {
                float wantedYaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, MAX_TURN));
            }
        }

        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
    }

    @Override
    public boolean isWalkable(float x, float z) {
        // Use the same input heading as LivingEntity travel and the tactical
        // probe, preserving vanilla's probe length without its rotated bearing.
        return StrafeTerrain.findLanding(this.mob, strafeProbe(Math.hypot(x, z))) != null;
    }

    private Vec3 strafeProbe(double distance) {
        float yaw = this.mob.getYRot() * ((float) Math.PI / 180.0F);
        return new Vec3(this.strafeRight * Mth.cos(yaw) - this.strafeForwards * Mth.sin(yaw), 0,
                this.strafeForwards * Mth.cos(yaw) + this.strafeRight * Mth.sin(yaw)).normalize().scale(distance);
    }

    private void tickMoveTo() {
        // Keep vanilla MOVE_TO steering, but give ordinary collision stepping a
        // chance before a high waypoint or occupied partial shape requests a jump.
        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dy * dy + dz * dz < MIN_SPEED_SQR) {
            this.mob.setZza(0.0F);
            return;
        }

        float wantedYaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, MAX_TURN));
        this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        double horizontalDistanceSqr = dx * dx + dz * dz;
        // Include the adjacent block-centre boundary that vanilla excludes.
        boolean raisedTarget = dy > this.mob.maxUpStep()
                && horizontalDistanceSqr <= Math.max(1.0F, this.mob.getBbWidth()) + MOVEMENT_EPSILON;
        BlockPos pos = this.mob.blockPosition();
        BlockState state = this.mob.level().getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(this.mob.level(), pos, CollisionContext.of(this.mob));
        boolean insideShape = !shape.isEmpty() && this.mob.getY() < pos.getY() + shape.max(Direction.Axis.Y)
                && !state.is(BlockTags.DOORS) && !state.is(BlockTags.FENCES);
        if ((raisedTarget || insideShape) && !canStepTowards(dx, dz, horizontalDistanceSqr)) {
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }

    private boolean canStepTowards(double dx, double dz, double horizontalDistanceSqr) {
        if (!this.mob.onGround() || this.mob.isInLiquid() || horizontalDistanceSqr < MIN_SPEED_SQR) {
            return false;
        }

        double distance = Math.sqrt(horizontalDistanceSqr);
        double scale = Math.min(STEP_PROBE_DISTANCE, distance) / distance;
        Vec3 requested = new Vec3(dx * scale, 0.0D, dz * scale);
        // Entity.collide is a query: it resolves collisions and ordinary step-up
        // using the live body and headroom without applying movement or a jump.
        Vec3 resolved = this.mob.collide(requested);
        if (Math.abs(resolved.x - requested.x) > MOVEMENT_EPSILON
                || Math.abs(resolved.z - requested.z) > MOVEMENT_EPSILON) {
            return false;
        }

        // Free horizontal movement over a ledge does not establish a safe step.
        return !this.mob.level().noBlockCollision(this.mob,
                this.mob.getBoundingBox().move(resolved).move(0.0D, -0.01D, 0.0D));
    }
}
