package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class SmarterOpenDoorsTask extends Behavior<LivingEntity> {
    private static final int RUN_TIME = 20;
    private static final double PATHING_DISTANCE = 2.0;

    @Nullable
    private Node pathNode;
    private int ticks;

    public SmarterOpenDoorsTask() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.VALUE_PRESENT, MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.REGISTERED));
    }

    public static void closeDoors(ServerLevel world, LivingEntity entity, @Nullable Node lastNode, @Nullable Node currentNode) {
        Brain<?> brain = entity.getBrain();
        Optional<Set<GlobalPos>> rememberedToggleables = brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE);
        if (rememberedToggleables.isEmpty()) {
            return;
        }

        Iterator<GlobalPos> iterator = rememberedToggleables.get().iterator();
        while (iterator.hasNext()) {
            GlobalPos globalPos = iterator.next();
            BlockPos blockPos = globalPos.pos();

            // The active path still owns this block.
            if ((lastNode != null && lastNode.asBlockPos().equals(blockPos))
                    || (currentNode != null && currentNode.asBlockPos().equals(blockPos))) {
                continue;
            }

            // We cannot operate a remembered toggleable from another dimension.
            // Distance alone is not a reason to forget it: the behavior may not
            // run again until after the villager has already cleared the block.
            if (!globalPos.dimension().equals(world.dimension())) {
                iterator.remove();
                continue;
            }

            BlockState blockState = world.getBlockState(blockPos);
            if (!PathingBlockInteraction.isOpenable(blockState)) {
                iterator.remove();
                continue;
            }

            if (!blockState.getValue(BlockStateProperties.OPEN)) {
                iterator.remove();
                continue;
            }

            // Path nodes can advance before the entity's full body has cleared a
            // toggleable. Never close one around this villager.
            if (entity.getBoundingBox().intersects(
                    blockPos.getX(), blockPos.getY(), blockPos.getZ(),
                    blockPos.getX() + 1.0D, blockPos.getY() + 1.0D, blockPos.getZ() + 1.0D
            )) {
                continue;
            }

            if (hasOtherMobReachedToggleable(entity, blockPos)) {
                continue;
            }

            PathingBlockInteraction.setOpen(entity, world, blockState, blockPos, false);
            iterator.remove();
        }
    }

    private static boolean hasOtherMobReachedToggleable(LivingEntity entity, BlockPos pos) {
        Brain<?> brain = entity.getBrain();
        return brain.getMemoryInternal(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                .map(nearbyEntities -> nearbyEntities.stream()
                        .filter(other -> other.getType() == entity.getType())
                        .filter(other -> pos.closerToCenterThan(other.position(), PATHING_DISTANCE))
                        .anyMatch(other -> hasReached(other, pos)))
                .orElse(false);
    }

    private static boolean hasReached(LivingEntity entity, BlockPos pos) {
        Optional<Path> pathMemory = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH);
        if (pathMemory.isEmpty()) {
            return false;
        }

        Path path = pathMemory.get();
        if (path.isDone()) {
            return false;
        }
        Node pathNode = path.getPreviousNode();
        if (pathNode == null) {
            return false;
        }
        return pos.equals(pathNode.asBlockPos()) || pos.equals(path.getNextNode().asBlockPos());
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

    private void makePathToggleablePassable(ServerLevel world, LivingEntity entity,
                                            @Nullable Node pathNode, @Nullable Node adjacentNode) {
        if (pathNode == null) {
            return;
        }

        BlockPos blockPos = pathNode.asBlockPos();
        BlockState blockState = world.getBlockState(blockPos);
        if (PathingBlockInteraction.isHandOpenableTrapDoor(blockState)
                && (adjacentNode == null || adjacentNode.y == pathNode.y)) {
            // A closed trapdoor can be valid floor. Only open it when the path is
            // actually crossing vertically through that block; otherwise opening
            // it underneath the villager would create the obstacle ourselves.
            return;
        }

        boolean shouldBeOpen = PathingBlockInteraction.shouldBeOpenForMovement(
                blockState,
                getHorizontalMovementAxis(pathNode, adjacentNode)
        );
        if (PathingBlockInteraction.setOpen(entity, world, blockState, blockPos, shouldBeOpen)
                && shouldBeOpen) {
            this.rememberToCloseToggleable(world, entity, blockPos);
        }
    }

    @Nullable
    private static Direction.Axis getHorizontalMovementAxis(Node node, @Nullable Node adjacentNode) {
        if (adjacentNode == null || adjacentNode.y != node.y) {
            return null;
        }

        boolean changesX = adjacentNode.x != node.x;
        boolean changesZ = adjacentNode.z != node.z;
        if (changesX == changesZ) {
            return null;
        }
        return changesX ? Direction.Axis.X : Direction.Axis.Z;
    }

    private void openToggleablesBetweenPathNodes(ServerLevel world, LivingEntity entity,
                                                 @Nullable Node firstNode, @Nullable Node secondNode) {
        if (firstNode == null || secondNode == null) {
            return;
        }

        int minY = Math.min(firstNode.y, secondNode.y) + 1;
        int maxY = Math.max(firstNode.y, secondNode.y);
        if (minY >= maxY) {
            return;
        }

        for (int y = minY; y < maxY; y++) {
            openToggleableAt(world, entity, new BlockPos(firstNode.x, y, firstNode.z));
            if (firstNode.x != secondNode.x || firstNode.z != secondNode.z) {
                openToggleableAt(world, entity, new BlockPos(secondNode.x, y, secondNode.z));
            }
        }
    }

    private void openToggleableAt(ServerLevel world, LivingEntity entity, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (PathingBlockInteraction.setOpen(entity, world, state, pos, true)) {
            this.rememberToCloseToggleable(world, entity, pos);
        }
    }

    @Override
    protected void start(ServerLevel world, LivingEntity entity, long time) {
        Path path = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH).orElseThrow();
        this.pathNode = path.getNextNode();

        Node previousNode = path.getPreviousNode();
        Node nextNode = path.getNextNode();
        int nextNodeIndex = path.getNextNodeIndex();
        Node followingNode = nextNodeIndex + 1 < path.getNodeCount()
                ? path.getNode(nextNodeIndex + 1)
                : null;

        // Close toggleables remembered from earlier path progress before opening
        // anything needed by the current transition. Otherwise a lookahead hatch
        // can be opened and immediately closed again in this same invocation.
        closeDoors(world, entity, previousNode, nextNode);

        makePathToggleablePassable(world, entity, previousNode, nextNode);
        makePathToggleablePassable(world, entity, nextNode, previousNode);
        openToggleablesBetweenPathNodes(world, entity, previousNode, nextNode);
        openToggleablesBetweenPathNodes(world, entity, nextNode, followingNode);
    }

    private void rememberToCloseToggleable(ServerLevel world, LivingEntity entity, BlockPos pos) {
        Brain<?> brain = entity.getBrain();
        GlobalPos globalPos = GlobalPos.of(world.dimension(), pos);
        brain.getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE)
                .ifPresentOrElse(
                        toggleables -> toggleables.add(globalPos),
                        () -> brain.setMemory(MemoryModuleType.DOORS_TO_CLOSE, Sets.newHashSet(globalPos))
                );
    }
}
