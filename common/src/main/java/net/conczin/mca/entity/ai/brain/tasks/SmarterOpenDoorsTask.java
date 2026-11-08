package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.navigation.PathNodeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class SmarterOpenDoorsTask extends Behavior<LivingEntity> {
    private static final int RUN_TIME = 20;
    private static final double PATHING_DISTANCE = 2.0;
    private static final long LOST_PATH_GRACE_TICKS = 40L;

    @Nullable
    private Node pathNode;
    private int ticks;
    private long lastActivePathTick = Long.MIN_VALUE;

    public SmarterOpenDoorsTask() {
        super(ImmutableMap.of(MemoryModuleType.PATH, MemoryStatus.REGISTERED,
                MemoryModuleType.DOORS_TO_CLOSE, MemoryStatus.REGISTERED,
                MemoryModuleTypeMCA.FOLDED_TRAPDOORS, MemoryStatus.REGISTERED));
    }

    public static void closeDoors(ServerLevel world, LivingEntity entity, @Nullable Node lastNode, @Nullable Node currentNode) {
        closeDoors(world, entity, lastNode, currentNode, false);
    }

    private static void closeDoors(ServerLevel world, LivingEntity entity, @Nullable Node lastNode,
                                   @Nullable Node currentNode, boolean recentPathLoss) {
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
            if (isPathNode(lastNode, blockPos) || isPathNode(currentNode, blockPos)) {
                continue;
            }

            // MCA deliberately remembers a toggleable until the villager's body
            // has cleared it, which can be farther than vanilla's 3-block cutoff.
            // Never retain that behavior by synchronously loading an old chunk.
            if (!globalPos.dimension().equals(world.dimension())
                    || world.getChunkSource().getChunkNow(blockPos.getX() >> 4, blockPos.getZ() >> 4) == null) {
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

            // A gate above a path node was opened to clear the villager's head.
            // Keep it open while that node is active, just like a gate at foot level.
            if (PathingBlockInteraction.canInteractWithFenceGate(blockState)
                    && (isInPathBodyClearance(lastNode, blockPos, entity)
                    || isInPathBodyClearance(currentNode, blockPos, entity))) {
                continue;
            }

            // The Brain can briefly drop PATH while a slowed villager still
            // approaches a gate. Keep its nearby passage open for a bounded
            // transition, never indefinitely just because it remains nearby.
            if (recentPathLoss && blockPos.closerToCenterThan(entity.position(), PATHING_DISTANCE)) {
                continue;
            }

            // Path nodes can advance before the entity's full body has cleared a
            // toggleable. Never close one around this villager.
            if (bodyIntersectsBlock(entity, blockPos)) {
                continue;
            }

            if (hasOtherMobReachedToggleable(entity, blockPos)) {
                continue;
            }

            PathingBlockInteraction.setOpen(entity, world, blockState, blockPos, false);
            iterator.remove();
        }
    }

    private static boolean isPathNode(@Nullable Node node, BlockPos pos) {
        return node != null && node.asBlockPos().equals(pos);
    }

    private static boolean bodyIntersectsBlock(LivingEntity entity, BlockPos pos) {
        return entity.getBoundingBox().intersects(pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D);
    }

    private static boolean isInPathBodyClearance(@Nullable Node node, BlockPos gate, LivingEntity entity) {
        return node != null
                && node.x == gate.getX() && node.z == gate.getZ()
                && gate.getY() > node.y
                && gate.getY() - node.y < Mth.ceil(entity.getBbHeight());
    }

    private static boolean hasOtherMobReachedToggleable(LivingEntity entity, BlockPos pos) {
        List<LivingEntity> nearbyEntities = entity.getBrain()
                .getMemoryInternal(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of());
        AABB toggleableBounds = new AABB(pos);
        for (LivingEntity other : nearbyEntities) {
            if (other.getType() == entity.getType()
                    && (other.getBoundingBox().intersects(toggleableBounds)
                    || pos.closerToCenterThan(other.position(), PATHING_DISTANCE) && hasReached(other, pos))) {
                return true;
            }
        }
        return false;
    }

    private static void restoreFoldedUprightTrapdoors(ServerLevel world, LivingEntity entity,
                                                       @Nullable Node lastNode, @Nullable Node currentNode) {
        Brain<?> brain = entity.getBrain();
        List<Pair<GlobalPos, BlockState>> folded = brain.getMemory(MemoryModuleTypeMCA.FOLDED_TRAPDOORS).orElse(List.of());
        if (folded.isEmpty()) return;

        List<Pair<GlobalPos, BlockState>> remaining = new ArrayList<>(folded.size());
        for (Pair<GlobalPos, BlockState> entry : folded) {
            GlobalPos globalPos = entry.getFirst();
            BlockPos pos = globalPos.pos();
            if (isPathNode(lastNode, pos) || isPathNode(currentNode, pos)
                    || !globalPos.dimension().equals(world.dimension())
                    || world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                remaining.add(entry);
                continue;
            }
            BlockState state = world.getBlockState(pos);
            // Only restore the recorded post-fold state. If the state changes,
            // another actor may have taken over this panel.
            if (state != entry.getSecond()) {
                continue;
            }
            if (bodyIntersectsBlock(entity, pos) || hasOtherMobReachedToggleable(entity, pos)) {
                remaining.add(entry);
                continue;
            }
            PathingBlockInteraction.setOpen(entity, world, state, pos, true);
        }
        if (remaining.size() != folded.size()) {
            brain.setMemory(MemoryModuleTypeMCA.FOLDED_TRAPDOORS, List.copyOf(remaining));
        }
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
        Path path = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH).orElse(null);
        if (path != null && !path.isDone()) {
            // Door interactions may be less frequent than Brain path checks.
            // Measure lost-path grace from the last observed active path.
            lastActivePathTick = world.getGameTime();
        }

        //Luke100000: I removed the path.isStart() check as this caused villagers to slam their face onto the door, not being able to open it anymore.
        if (path == null || path.isDone()) {
            this.pathNode = null;
            this.ticks = 0;
            return entity.getBrain().hasMemoryValue(MemoryModuleTypeMCA.FOLDED_TRAPDOORS)
                    || entity.getBrain().getMemoryInternal(MemoryModuleType.DOORS_TO_CLOSE)
                    .filter(remembered -> !remembered.isEmpty()).isPresent();
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

    private void makePathToggleablePassable(ServerLevel world, LivingEntity entity, Path path,
                                            boolean previous) {
        Node nextNode = path.getNextNode();
        Node previousNode = path.getPreviousNode();
        Node pathNode = previous ? previousNode : nextNode;
        if (pathNode == null) {
            return;
        }

        Node adjacentNode = previous ? nextNode : previousNode;
        BlockPos blockPos = pathNode.asBlockPos();
        BlockState blockState = world.getBlockState(blockPos);
        this.openFenceGatesInBodyClearance(world, entity, blockPos, blockState, previous);
        if (PathingBlockInteraction.canInteractWithFenceGate(blockState)) {
            return;
        }
        if (PathingBlockInteraction.isHandOpenableTrapDoor(blockState)
                && !isVerticalSameColumnTransition(pathNode, adjacentNode)
                && !PathingBlockInteraction.canFoldUprightTrapdoor(blockState, blockPos,
                        adjacentNode == null ? null : adjacentNode.asBlockPos())) {
            // A trapdoor is a hatch only when the path actually crosses its column
            // vertically. A stair or other raised path beside a decorative trapdoor
            // must not cause the villager to operate it.
            return;
        }

        boolean shouldBeOpen = PathingBlockInteraction.shouldBeOpenForMovement(
                blockState,
                blockPos,
                adjacentNode == null ? null : adjacentNode.asBlockPos()
        );
        boolean wasAlreadyOpen = PathingBlockInteraction.isOpenable(blockState)
                && blockState.hasProperty(BlockStateProperties.OPEN)
                && blockState.getValue(BlockStateProperties.OPEN);
        if (shouldBeOpen && !wasAlreadyOpen && blockState.getBlock().getClass() == DoorBlock.class
                && entity instanceof Mob mob
                && !closedDoorBlocksMovement(world, mob, nextNode.asBlockPos(), blockPos, blockState)) {
            return;
        }
        boolean changed = PathingBlockInteraction.setOpen(entity, world, blockState, blockPos, shouldBeOpen);
        if (changed && !shouldBeOpen && PathingBlockInteraction.isHandOpenableTrapDoor(blockState)) {
            Brain<?> brain = entity.getBrain();
            GlobalPos position = GlobalPos.of(world.dimension(), blockPos);
            List<Pair<GlobalPos, BlockState>> folded = new ArrayList<>(
                    brain.getMemory(MemoryModuleTypeMCA.FOLDED_TRAPDOORS).orElse(List.of()));
            folded.removeIf(entry -> entry.getFirst().equals(position));
            folded.add(Pair.of(position, blockState.setValue(BlockStateProperties.OPEN, false)));
            brain.setMemory(MemoryModuleTypeMCA.FOLDED_TRAPDOORS, List.copyOf(folded));
        }
        boolean rememberExistingOpenState = previous
                && wasAlreadyOpen
                && !PathingBlockInteraction.isHandOpenableTrapDoor(blockState);
        if (shouldBeOpen && (changed || rememberExistingOpenState)) {
            this.rememberToCloseToggleable(world, entity, blockPos);
        }
    }

    private static boolean closedDoorBlocksMovement(ServerLevel world, Mob mob, BlockPos waypoint,
                                                    BlockPos pos, BlockState state) {
        AABB body = mob.getBoundingBox();
        AABB panel = state.getCollisionShape(world, pos).bounds().move(pos);
        int otherHalfY = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? 1 : -1;
        if (world.getBlockState(pos.above(otherHalfY)).is(state.getBlock())) {
            panel = panel.expandTowards(0.0D, otherHalfY, 0.0D);
        }
        // Expanding the panel lets the center segment test the whole body.
        double halfHeight = body.getYsize() * 0.5D;
        panel = panel.inflate(body.getXsize() * 0.5D, halfHeight, body.getZsize() * 0.5D)
                .deflate(Shapes.EPSILON);
        Vec3 from = body.getCenter();
        Vec3 to = PathNodeGeometry.walkingPosition(world, waypoint, mob).add(0.0D, halfHeight, 0.0D);
        return panel.contains(from) || panel.contains(to) || panel.clip(from, to).isPresent();
    }

    private static boolean isVerticalSameColumnTransition(Node node, @Nullable Node adjacentNode) {
        return adjacentNode != null
                && adjacentNode.x == node.x
                && adjacentNode.z == node.z
                && adjacentNode.y != node.y;
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

        boolean sameColumn = firstNode.x == secondNode.x && firstNode.z == secondNode.z;

        for (int y = minY; y < maxY; y++) {
            openToggleableAt(world, entity, new BlockPos(firstNode.x, y, firstNode.z), sameColumn);
            if (!sameColumn) {
                openToggleableAt(world, entity, new BlockPos(secondNode.x, y, secondNode.z), false);
            }
        }
    }

    private void openTrapdoorAboveClimbableTransition(ServerLevel world, LivingEntity entity,
                                                      @Nullable Node firstNode, @Nullable Node secondNode) {
        if (firstNode == null || secondNode == null) {
            return;
        }

        Node lowerNode = firstNode.y < secondNode.y ? firstNode : secondNode;
        Node upperNode = lowerNode == firstNode ? secondNode : firstNode;
        // A ladder can exit directly onto an adjacent upper floor two blocks
        // above its last climbable node. The hatch is between those nodes.
        int rise = upperNode.y - lowerNode.y;
        if (rise < 1 || rise > 2) {
            return;
        }
        int horizontalDistance = Math.abs(lowerNode.x - upperNode.x) + Math.abs(lowerNode.z - upperNode.z);
        if (horizontalDistance > 1) {
            return;
        }

        BlockPos lowerPos = lowerNode.asBlockPos();
        if (!world.getBlockState(lowerPos).is(BlockTags.CLIMBABLE)) {
            return;
        }

        BlockPos trapdoorPos = lowerPos.above();
        if (PathingBlockInteraction.isHandOpenableTrapDoor(world.getBlockState(trapdoorPos))) {
            this.openToggleableAt(world, entity, trapdoorPos);
        }
    }

    private void openToggleableAt(ServerLevel world, LivingEntity entity, BlockPos pos) {
        openToggleableAt(world, entity, pos, true);
    }

    private void openToggleableAt(ServerLevel world, LivingEntity entity, BlockPos pos, boolean verticalRoute) {
        BlockState state = world.getBlockState(pos);
        if (!verticalRoute && PathingBlockInteraction.isHandOpenableTrapDoor(state)) return;
        if (PathingBlockInteraction.setOpen(entity, world, state, pos, true)) {
            this.rememberToCloseToggleable(world, entity, pos);
        }
    }

    private void openFenceGatesInBodyClearance(ServerLevel world, LivingEntity entity, BlockPos pathPos,
                                               BlockState pathState, boolean rememberIfAlreadyOpen) {
        int bodyHeightBlocks = Mth.ceil(entity.getBbHeight());
        for (int offset = 0; offset < bodyHeightBlocks; offset++) {
            BlockPos pos = offset == 0 ? pathPos : pathPos.above(offset);
            BlockState state = offset == 0 ? pathState : world.getBlockState(pos);
            if (!PathingBlockInteraction.canInteractWithFenceGate(state)) {
                continue;
            }
            boolean wasAlreadyOpen = state.getValue(BlockStateProperties.OPEN);
            boolean changed = PathingBlockInteraction.setOpen(entity, world, state, pos, true);
            if (changed || (rememberIfAlreadyOpen && wasAlreadyOpen)) {
                this.rememberToCloseToggleable(world, entity, pos);
            }
        }
    }

    @Override
    protected void start(ServerLevel world, LivingEntity entity, long time) {
        Path path = entity.getBrain().getMemoryInternal(MemoryModuleType.PATH).orElse(null);
        if (path == null || path.isDone()) {
            boolean recentPathLoss = lastActivePathTick != Long.MIN_VALUE
                    && time - lastActivePathTick <= LOST_PATH_GRACE_TICKS;
            closeDoors(world, entity, null, null, recentPathLoss);
            restoreFoldedUprightTrapdoors(world, entity, null, null);
            return;
        }
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
        restoreFoldedUprightTrapdoors(world, entity, previousNode, nextNode);

        // Vanilla remembers a door after the mob has passed through it even when
        // that door was already open. It only remembers the upcoming door when it
        // actually had to open it.
        makePathToggleablePassable(world, entity, path, true);
        makePathToggleablePassable(world, entity, path, false);
        openToggleablesBetweenPathNodes(world, entity, previousNode, nextNode);
        openToggleablesBetweenPathNodes(world, entity, nextNode, followingNode);
        openTrapdoorAboveClimbableTransition(world, entity, previousNode, nextNode);
        openTrapdoorAboveClimbableTransition(world, entity, nextNode, followingNode);
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
