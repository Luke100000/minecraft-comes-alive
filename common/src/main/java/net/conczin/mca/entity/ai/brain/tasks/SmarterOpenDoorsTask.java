package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class SmarterOpenDoorsTask extends Behavior<LivingEntity> {
    private static final int RUN_TIME = 20;
    private static final double PATHING_DISTANCE = 2.0;
    private static final double REACH_DISTANCE = 2.0;

    @Nullable
    private Node pathNode;
    private int ticks;

    public SmarterOpenDoorsTask() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.VALUE_PRESENT, MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.REGISTERED));
    }

    public static boolean setOpen(@Nullable Entity entity, Level world, BlockState state, BlockPos pos, boolean open) {
        if (!state.hasProperty(BlockStateProperties.OPEN) || state.getValue(BlockStateProperties.OPEN) == open) {
            return false;
        }

        Block block = state.getBlock();
        if (block instanceof DoorBlock door && state.is(BlockTags.MOB_INTERACTABLE_DOORS)) {
            door.setOpen(entity, world, state, pos, open);
            return true;
        }

        if (block instanceof FenceGateBlock && state.is(BlockTags.FENCE_GATES)) {
            world.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            world.playSound(entity, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, world.getRandom().nextFloat() * 0.1F + 0.9F);
            world.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            return true;
        }

        return false;
    }

    private static boolean isDoor(BlockState blockState) {
        return blockState.is(BlockTags.MOB_INTERACTABLE_DOORS, state -> state.getBlock() instanceof DoorBlock)
               || blockState.is(BlockTags.FENCE_GATES, state -> state.getBlock() instanceof FenceGateBlock);
    }

    public static void closeDoors(ServerLevel world, LivingEntity entity, @Nullable Node lastNode, @Nullable Node currentNode) {
        Brain<?> brain = entity.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
            //noinspection OptionalGetWithoutIsPresent
            Iterator<GlobalPos> iterator = brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE).get().iterator();
            while (iterator.hasNext()) {
                GlobalPos globalPos = iterator.next();
                BlockPos blockPos = globalPos.pos();

                // Not far enough away
                if (lastNode != null && lastNode.asBlockPos().equals(blockPos) || currentNode != null && currentNode.asBlockPos().equals(blockPos))
                    continue;

                // Out of range
                if (SmarterOpenDoorsTask.cannotReachDoor(world, entity, globalPos)) {
                    iterator.remove();
                    continue;
                }

                // That's no door
                BlockState blockState = world.getBlockState(blockPos);
                if (!isDoor(blockState)) {
                    iterator.remove();
                    continue;
                }

                // Door isn't even open
                if (blockState.hasProperty(BlockStateProperties.OPEN) && !blockState.getValue(BlockStateProperties.OPEN)) {
                    iterator.remove();
                    continue;
                }

                // Door is blocked by entities
                if (SmarterOpenDoorsTask.hasOtherMobReachedDoor(entity, blockPos)) {
                    iterator.remove();
                    continue;
                }

                // Close the door
                setOpen(entity, world, blockState, blockPos, false);
                iterator.remove();
            }
        }
    }

    private static boolean hasOtherMobReachedDoor(LivingEntity entity, BlockPos pos) {
        Brain<?> brain = entity.getBrain();
        if (!brain.hasMemoryValue(MemoryModuleType.NEAREST_LIVING_ENTITIES)) {
            return false;
        }
        //noinspection OptionalGetWithoutIsPresent
        return brain.getMemoryInternal(MemoryModuleType.NEAREST_LIVING_ENTITIES).get().stream().filter(livingEntity2 -> livingEntity2.getType() == entity.getType()).filter(livingEntity -> pos.closerToCenterThan(livingEntity.position(), PATHING_DISTANCE)).anyMatch(livingEntity -> SmarterOpenDoorsTask.hasReached(livingEntity, pos));
    }

    private static boolean hasReached(LivingEntity entity, BlockPos pos) {
        if (!entity.getBrain().hasMemoryValue(MemoryModuleType.PATH)) {
            return false;
        }
        //noinspection OptionalGetWithoutIsPresent
        Path path = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH).get();
        if (path.isDone()) {
            return false;
        }
        Node pathNode = path.getPreviousNode();
        if (pathNode == null) {
            return false;
        }
        return pos.equals(pathNode.asBlockPos()) || pos.equals(path.getNextNode().asBlockPos());
    }

    private static boolean cannotReachDoor(ServerLevel world, LivingEntity entity, GlobalPos doorPos) {
        return doorPos.dimension() != world.dimension() || !doorPos.pos().closerToCenterThan(entity.position(), REACH_DISTANCE);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, LivingEntity entity) {
        Optional<Path> optionalMemory = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH);
        if (optionalMemory.isEmpty()) return false;

        Path path = optionalMemory.get();

        //Luke100000: I removed the path.isStart() check as this caused villagers to slam their face onto the door, not being able to open it anymore.
        if (path.isDone()) {
            return false;
        }

        // Run if a new node has been reached
        if (!Objects.equals(this.pathNode, path.getNextNode())) {
            this.ticks = RUN_TIME;
            return true;
        }

        // Or if the cooldown has been reached
        if (this.ticks > 0) {
            --this.ticks;
        }
        return this.ticks == 0;
    }

    private void openDoor(ServerLevel world, LivingEntity entity, Node pathNode) {
        if (pathNode != null) {
            BlockPos blockPos = pathNode.asBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            if (isDoor(blockState)) {
                if (setOpen(entity, world, blockState, blockPos, true)) {
                    this.rememberToCloseDoor(world, entity, blockPos);
                }
            }
        }
    }

    @Override
    protected void start(ServerLevel world, LivingEntity entity, long time) {
        //noinspection OptionalGetWithoutIsPresent
        Path path = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH).get();
        this.pathNode = path.getNextNode();

        openDoor(world, entity, path.getPreviousNode());
        openDoor(world, entity, path.getNextNode());

        closeDoors(world, entity, path.getPreviousNode(), path.getNextNode());
    }

    private void rememberToCloseDoor(ServerLevel world, LivingEntity entity, BlockPos pos) {
        Brain<?> brain = entity.getBrain();
        GlobalPos globalPos = GlobalPos.of(world.dimension(), pos);
        if (brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE).isPresent()) {
            brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE).get().add(globalPos);
        } else {
            brain.setMemory(MemoryModuleType.DOORS_TO_CLOSE, Sets.newHashSet(globalPos));
        }
    }
}

