package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
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

    public static boolean setOpen(@Nullable Entity entity, Level world, BlockState state, BlockPos pos, boolean open) {
        if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN) != open) {
            world.setBlock(pos, state.setValue(BlockStateProperties.OPEN, open), Block.UPDATE_CLIENTS | Block.UPDATE_IMMEDIATE);
            world.gameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            playOpenCloseSound(entity, world, pos, open);
            return true;
        } else {
            return false;
        }
    }

    private static void playOpenCloseSound(@Nullable Entity entity, Level world, BlockPos pos, boolean open) {
        world.playSound(entity, pos, open ? SoundEvents.WOODEN_DOOR_OPEN : SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.BLOCKS, 0.75F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private void openDoor(ServerLevel world, LivingEntity entity, Node pathNode) {
        if (pathNode != null) {
            BlockPos blockPos = pathNode.asBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            if (isDoor(blockState)) {
                boolean wasOpen = blockState.hasProperty(BlockStateProperties.OPEN) && blockState.getValue(BlockStateProperties.OPEN);
                if (setOpen(entity, world, blockState, blockPos, true) || wasOpen) {
                    this.rememberToCloseDoor(world, entity, blockPos);
                }
            }
        }
    }

    private static boolean isDoor(BlockState blockState) {
        return blockState.is(BlockTags.WOODEN_DOORS, state -> state.getBlock() instanceof DoorBlock) || blockState.is(BlockTags.FENCE_GATES, state -> state.getBlock() instanceof FenceGateBlock);
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

    public static void closeDoors(ServerLevel world, LivingEntity entity, @Nullable Node lastNode, @Nullable Node currentNode) {
        Brain<?> brain = entity.getBrain();
        if (brain.hasMemoryValue(MemoryModuleType.DOORS_TO_CLOSE)) {
            //noinspection OptionalGetWithoutIsPresent
            Iterator<GlobalPos> iterator = brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE).get().iterator();
            while (iterator.hasNext()) {
                GlobalPos globalPos = iterator.next();
                BlockPos blockPos = globalPos.pos();

                // Not far enough away
                if (lastNode != null && lastNode.asBlockPos().equals(blockPos) || currentNode != null && currentNode.asBlockPos().equals(blockPos)) continue;

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
