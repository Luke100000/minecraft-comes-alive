package net.conczin.mca.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Shared contract for blocks MCA navigation may expect villagers to operate.
 * Keeping detection and mutation together prevents pathfinding from accepting a
 * toggleable that the brain task does not know how to open.
 */
public final class PathingBlockInteraction {
    private PathingBlockInteraction() {
    }

    public static boolean isHandOpenableTrapDoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock trapDoor
                && trapDoor.getType().canOpenByHand();
    }

    public static boolean isFenceGate(BlockState state) {
        return state.is(BlockTags.FENCE_GATES, candidate -> candidate.getBlock() instanceof FenceGateBlock);
    }

    public static boolean isOpenable(BlockState state) {
        return isHandOpenableTrapDoor(state)
                || state.is(BlockTags.MOB_INTERACTABLE_DOORS, candidate -> candidate.getBlock() instanceof DoorBlock)
                || isFenceGate(state);
    }

    /**
     * A vanilla door only needs to be open when crossing its facing axis. When
     * travelling parallel to the closed door plane, opening it rotates that plane
     * into the route instead. Gates and trapdoors still use the normal open state.
     */
    public static boolean shouldBeOpenForMovement(BlockState state, @Nullable Direction.Axis movementAxis) {
        if (!(state.getBlock() instanceof DoorBlock) || movementAxis == null) {
            return true;
        }
        return state.getValue(DoorBlock.FACING).getAxis() == movementAxis;
    }

    public static boolean setOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        if (!isOpenable(state)
                || !state.hasProperty(BlockStateProperties.OPEN)
                || state.getValue(BlockStateProperties.OPEN) == open) {
            return false;
        }

        if (state.getBlock() instanceof DoorBlock door) {
            door.setOpen(entity, level, state, pos, open);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock trapDoor) {
            if (!trapDoor.getType().canOpenByHand()) {
                return false;
            }
            trapDoor.toggle(state, level, pos, null);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock fenceGate) {
            level.setBlock(
                    pos,
                    state.setValue(BlockStateProperties.OPEN, open),
                    Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE
            );
            level.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            level.playSound(
                    entity,
                    pos,
                    open ? fenceGate.type.fenceGateOpen() : fenceGate.type.fenceGateClose(),
                    SoundSource.BLOCKS,
                    1.0F,
                    level.getRandom().nextFloat() * 0.1F + 0.9F
            );
            return true;
        }

        return false;
    }
}
