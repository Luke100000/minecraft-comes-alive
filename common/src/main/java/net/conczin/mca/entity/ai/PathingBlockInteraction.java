package net.conczin.mca.entity.ai;

import java.lang.reflect.Method;

import net.conczin.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
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

    private static final TagKey<Block> DRAMATIC_DOORS_TALL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dramaticdoors", "mob_interactable_tall_doors"));
    private static final TagKey<Block> DRAMATIC_DOORS_SHORT = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dramaticdoors", "mob_interactable_short_doors"));

    /**
     * Vanilla opens any door whose block set type may be opened by hand, and its
     * path finder accepts those doors without consulting the door tags, which
     * modded doors frequently do not join.
     */
    private static boolean isHandOpenableDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock door
                && door.type().canOpenByHand();
    }

    /**
     * Dramatic Doors' tall and short doors extend Block rather than DoorBlock, so
     * neither the door tags nor the DoorBlock checks above see them. Detect them by
     * namespace; {@link #isDramaticDoorOpenable} then mirrors the mod's own rule for
     * whether a mob may open one.
     */
    private static boolean isDramaticDoor(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return state.hasProperty(BlockStateProperties.OPEN)
                && id != null
                && id.getNamespace().equals("dramaticdoors");
    }

    public static boolean isDramaticDoorOpenable(BlockState state) {
        return isDramaticDoor(state)
                && (dramaticDoorOpensByHand(state)
                    || state.is(DRAMATIC_DOORS_TALL)
                    || state.is(DRAMATIC_DOORS_SHORT));
    }

    private static boolean dramaticDoorOpensByHand(BlockState state) {
        try {
            Object type = state.getBlock().getClass().getMethod("type").invoke(state.getBlock());
            return type instanceof BlockSetType setType && setType.canOpenByHand();
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static boolean isFenceGate(BlockState state) {
        return state.is(BlockTags.FENCE_GATES, candidate -> candidate.getBlock() instanceof FenceGateBlock);
    }

    public static boolean canInteractWithFenceGate(BlockState state) {
        return Config.getServerConfig().villagersInteractWithFenceGates && isFenceGate(state);
    }

    public static boolean isOpenable(BlockState state) {
        return isHandOpenableTrapDoor(state)
                || isHandOpenableDoor(state)
                || isDramaticDoorOpenable(state)
                || canInteractWithFenceGate(state);
    }

    /**
     * A vanilla door only needs to be open when crossing its facing axis. When
     * travelling parallel to the closed door plane, opening it rotates that plane
     * into the route instead. Gates and trapdoors still use the normal open state.
     */
    public static boolean shouldBeOpenForMovement(BlockState state, @Nullable Direction.Axis movementAxis) {
        if (movementAxis == null) {
            return true;
        }
        if (state.getBlock() instanceof DoorBlock) {
            return state.getValue(DoorBlock.FACING).getAxis() == movementAxis;
        }
        if (isDramaticDoorOpenable(state) && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.getValue(BlockStateProperties.HORIZONTAL_FACING).getAxis() == movementAxis;
        }
        return true;
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

        if (isDramaticDoorOpenable(state)) {
            return setDramaticDoorOpen(entity, level, state, pos, open);
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

    /**
     * Dramatic Doors exposes a plain setOpen(Entity, Level, BlockState, BlockPos, boolean)
     * on its door blocks; call it reflectively so MCA needs no dependency on the mod.
     */
    private static boolean setDramaticDoorOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        try {
            Method setOpen = state.getBlock().getClass().getMethod("setOpen", Entity.class, Level.class, BlockState.class, BlockPos.class, boolean.class);
            setOpen.invoke(state.getBlock(), entity, level, state, pos, open);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
