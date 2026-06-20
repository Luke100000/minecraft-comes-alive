package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNode;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;

public class SmarterOpenDoorsTask extends MultiTickTask<LivingEntity> {
    private static final int RUN_TIME = 20;
    private static final double PATHING_DISTANCE = 2.0;
    private static final double REACH_DISTANCE = 2.0;

    @Nullable
    private PathNode pathNode;
    private int ticks;

    public SmarterOpenDoorsTask() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryModuleState.VALUE_PRESENT, MemoryModuleType.DOORS_TO_CLOSE, MemoryModuleState.REGISTERED));
    }

    @Override
    protected boolean shouldRun(ServerWorld world, LivingEntity entity) {
        Optional<Path> optionalMemory = entity.getBrain().getOptionalMemory(MemoryModuleType.PATH);
        if (optionalMemory.isEmpty()) return false;

        Path path = optionalMemory.get();

        //Luke100000: I removed the path.isStart() check as this caused villagers to slam their face onto the door, not being able to open it anymore.
        if (path.isFinished()) {
            return false;
        }

        // Run if a new node has been reached
        if (!Objects.equals(this.pathNode, path.getCurrentNode())) {
            this.ticks = RUN_TIME;
            return true;
        }

        // Or if the cooldown has been reached
        if (this.ticks > 0) {
            --this.ticks;
        }
        return this.ticks == 0;
    }

    public static boolean setOpen(@Nullable Entity entity, World world, BlockState state, BlockPos pos, boolean open) {
        if (state.contains(Properties.OPEN) && state.get(Properties.OPEN) != open) {
            world.setBlockState(pos, state.with(Properties.OPEN, open), Block.NOTIFY_LISTENERS | Block.REDRAW_ON_MAIN_THREAD);
            world.emitGameEvent(entity, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
            playOpenCloseSound(entity, world, pos, open);
            return true;
        } else {
            return false;
        }
    }

    private static void playOpenCloseSound(@Nullable Entity entity, World world, BlockPos pos, boolean open) {
        world.playSound(entity, pos, open ? SoundEvents.BLOCK_WOODEN_DOOR_OPEN : SoundEvents.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 0.75F, world.getRandom().nextFloat() * 0.1F + 0.9F);
    }

    private void openDoor(ServerWorld world, LivingEntity entity, PathNode pathNode) {
        if (pathNode != null) {
            BlockPos blockPos = pathNode.getBlockPos();
            BlockState blockState = world.getBlockState(blockPos);
            if (isDoor(blockState)) {
                boolean wasOpen = blockState.contains(Properties.OPEN) && blockState.get(Properties.OPEN);
                if (setOpen(entity, world, blockState, blockPos, true) || wasOpen) {
                    this.rememberToCloseDoor(world, entity, blockPos);
                }
            }
        }
    }

    private static boolean isDoor(BlockState blockState) {
        return blockState.isIn(BlockTags.WOODEN_DOORS, state -> state.getBlock() instanceof DoorBlock) || blockState.isIn(BlockTags.FENCE_GATES, state -> state.getBlock() instanceof FenceGateBlock);
    }

    @Override
    protected void run(ServerWorld world, LivingEntity entity, long time) {
        //noinspection OptionalGetWithoutIsPresent
        Path path = entity.getBrain().getOptionalMemory(MemoryModuleType.PATH).get();
        this.pathNode = path.getCurrentNode();

        openDoor(world, entity, path.getLastNode());
        openDoor(world, entity, path.getCurrentNode());

        closeDoors(world, entity, path.getLastNode(), path.getCurrentNode());
    }

    public static void closeDoors(ServerWorld world, LivingEntity entity, @Nullable PathNode lastNode, @Nullable PathNode currentNode) {
        Brain<?> brain = entity.getBrain();
        if (brain.hasMemoryModule(MemoryModuleType.DOORS_TO_CLOSE)) {
            //noinspection OptionalGetWithoutIsPresent
            Iterator<GlobalPos> iterator = brain.getOptionalMemory(MemoryModuleType.DOORS_TO_CLOSE).get().iterator();
            while (iterator.hasNext()) {
                GlobalPos globalPos = iterator.next();
                BlockPos blockPos = globalPos.getPos();

                // Not far enough away
                if (lastNode != null && lastNode.getBlockPos().equals(blockPos) || currentNode != null && currentNode.getBlockPos().equals(blockPos)) continue;

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
                if (blockState.contains(Properties.OPEN) && !blockState.get(Properties.OPEN)) {
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
        if (!brain.hasMemoryModule(MemoryModuleType.MOBS)) {
            return false;
        }
        //noinspection OptionalGetWithoutIsPresent
        return brain.getOptionalMemory(MemoryModuleType.MOBS).get().stream().filter(livingEntity2 -> livingEntity2.getType() == entity.getType()).filter(livingEntity -> pos.isWithinDistance(livingEntity.getPos(), PATHING_DISTANCE)).anyMatch(livingEntity -> SmarterOpenDoorsTask.hasReached(livingEntity, pos));
    }

    private static boolean hasReached(LivingEntity entity, BlockPos pos) {
        if (!entity.getBrain().hasMemoryModule(MemoryModuleType.PATH)) {
            return false;
        }
        //noinspection OptionalGetWithoutIsPresent
        Path path = entity.getBrain().getOptionalMemory(MemoryModuleType.PATH).get();
        if (path.isFinished()) {
            return false;
        }
        PathNode pathNode = path.getLastNode();
        if (pathNode == null) {
            return false;
        }
        return pos.equals(pathNode.getBlockPos()) || pos.equals(path.getCurrentNode().getBlockPos());
    }

    private static boolean cannotReachDoor(ServerWorld world, LivingEntity entity, GlobalPos doorPos) {
        return doorPos.getDimension() != world.getRegistryKey() || !doorPos.getPos().isWithinDistance(entity.getPos(), REACH_DISTANCE);
    }

    private void rememberToCloseDoor(ServerWorld world, LivingEntity entity, BlockPos pos) {
        Brain<?> brain = entity.getBrain();
        GlobalPos globalPos = GlobalPos.create(world.getRegistryKey(), pos);
        if (brain.getOptionalMemory(MemoryModuleType.DOORS_TO_CLOSE).isPresent()) {
            brain.getOptionalMemory(MemoryModuleType.DOORS_TO_CLOSE).get().add(globalPos);
        } else {
            brain.remember(MemoryModuleType.DOORS_TO_CLOSE, Sets.newHashSet(globalPos));
        }
    }
}
