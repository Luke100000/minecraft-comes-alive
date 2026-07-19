package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

public class MCABodyRotationControl extends BodyRotationControl {
    private final Mob controlledMob;
    private boolean wasGroundedOnClimbable;

    public MCABodyRotationControl(Mob mob) {
        super(mob);
        this.controlledMob = mob;
    }

    @Override
    public void clientTick() {
        super.clientTick();

        traceGroundedLadderExit();

        if (!this.controlledMob.onClimbable()) {
            return;
        }

        BlockPos pos = this.controlledMob.getLastClimbablePos()
                .orElse(this.controlledMob.blockPosition());
        BlockState state = this.controlledMob.level().getBlockState(pos);
        if (state.getBlock() instanceof TrapDoorBlock) {
            state = this.controlledMob.level().getBlockState(pos.below());
        }

        if (!(state.getBlock() instanceof LadderBlock)) {
            return;
        }

        Direction ladderFacing = state.getValue(LadderBlock.FACING);
        float ladderYaw = ladderFacing.getOpposite().toYRot();
        this.controlledMob.yBodyRot = ladderYaw;
        this.controlledMob.yHeadRot = ladderYaw;
        this.controlledMob.setXRot(0.0F);
    }
    private void traceGroundedLadderExit() {
        boolean groundedOnClimbable = this.controlledMob.onClimbable()
                && this.controlledMob.onGround();
        if (groundedOnClimbable == this.wasGroundedOnClimbable) {
            return;
        }

        this.wasGroundedOnClimbable = groundedOnClimbable;
        Path path = this.controlledMob.getNavigation().getPath();
        int nextNodeIndex = path == null ? -1 : path.getNextNodeIndex();
        String nextNode = path == null || path.isDone()
                || nextNodeIndex < 0 || nextNodeIndex >= path.getNodeCount()
                ? "none"
                : path.getNode(nextNodeIndex).asBlockPos().toShortString();

        var moveControl = this.controlledMob.getMoveControl();
        MCA.LOGGER.info(
                "[LadderExitTrace] groundedOnClimbable={} xxa={} zza={} yRot={} yBodyRot={} yHeadRot={} "
                        + "velocity={} blockPos={} moveWanted={} wanted=({},{},{}) pathIndex={} nextNode={}",
                groundedOnClimbable,
                this.controlledMob.xxa,
                this.controlledMob.zza,
                this.controlledMob.getYRot(),
                this.controlledMob.yBodyRot,
                this.controlledMob.yHeadRot,
                this.controlledMob.getDeltaMovement(),
                this.controlledMob.blockPosition(),
                moveControl.hasWanted(),
                moveControl.getWantedX(),
                moveControl.getWantedY(),
                moveControl.getWantedZ(),
                nextNodeIndex,
                nextNode
        );
    }

}
