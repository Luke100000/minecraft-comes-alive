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
    private static final TagKey<Block> DRAMATIC_DOORS_TALL = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dramaticdoors", "mob_interactable_tall_doors"));
    private static final TagKey<Block> DRAMATIC_DOORS_SHORT = TagKey.create(
            Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("dramaticdoors", "mob_interactable_short_doors"));

    private static final ClassValue<Method> DRAMATIC_DOOR_SET_OPEN = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            Method method = findMethod(type, "setOpen",
                    Entity.class, Level.class, BlockState.class, BlockPos.class, boolean.class
            );
            return method != null && method.getReturnType() == void.class ? method : null;
        }
    };
    private static final ClassValue<Method> DRAMATIC_DOOR_TYPE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            return findMethod(type, "type");
        }
    };

    private PathingBlockInteraction() {
    }

    public static boolean isHandOpenableTrapDoor(BlockState state) {
        return state.getBlock() instanceof TrapDoorBlock trapDoor
                && trapDoor.getType().canOpenByHand();
    }

    public static boolean isFenceGate(BlockState state) {
        return state.is(BlockTags.FENCE_GATES, candidate -> candidate.getBlock() instanceof FenceGateBlock);
    }

    public static boolean canInteractWithFenceGate(BlockState state) {
        return Config.getServerConfig().villagersInteractWithFenceGates && isFenceGate(state);
    }

    /**
     * Vanilla separates physical door type from mob interaction policy. Follow the
     * mob-interactable tag by default, while allowing servers to opt MCA villagers
     * into operating every DoorBlock.
     */
    public static boolean canInteractWithDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                && (Config.getServerConfig().villagersInteractWithAnyDoor
                    || state.is(BlockTags.MOB_INTERACTABLE_DOORS));
    }

    public static boolean isDramaticDoorOpenable(BlockState state) {
        Block block = state.getBlock();
        return !(block instanceof DoorBlock)
                && state.hasProperty(BlockStateProperties.OPEN)
                && hasDramaticDoorSetOpen(BuiltInRegistries.BLOCK.getKey(block), block.getClass())
                && (state.is(DRAMATIC_DOORS_TALL) || state.is(DRAMATIC_DOORS_SHORT)
                    || dramaticDoorOpensByHand(block));
    }

    public static boolean isOpenable(BlockState state) {
        return isHandOpenableTrapDoor(state)
                || canInteractWithDoor(state)
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
     * Dramatic Doors manages its multipart doors through setOpen, which we invoke
     * reflectively so the dependency remains optional.
     */
    private static boolean setDramaticDoorOpen(@Nullable Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
        try {
            DRAMATIC_DOOR_SET_OPEN.get(state.getBlock().getClass()).invoke(state.getBlock(), entity, level, state, pos, open);
            return true;
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean dramaticDoorOpensByHand(Block block) {
        Method type = DRAMATIC_DOOR_TYPE.get(block.getClass());
        if (type == null) {
            return false;
        }
        try {
            return type.invoke(block) instanceof BlockSetType blockSetType && blockSetType.canOpenByHand();
        } catch (ReflectiveOperationException | IllegalArgumentException e) {
            return false;
        }
    }

    @Nullable
    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    static boolean hasDramaticDoorSetOpen(ResourceLocation blockId, Class<?> blockClass) {
        return blockId != null
                && "dramaticdoors".equals(blockId.getNamespace())
                && DRAMATIC_DOOR_SET_OPEN.get(blockClass) != null;
    }
}
