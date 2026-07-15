package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class MCAGroundPathNavigation extends GroundPathNavigation {
    private static final int LADDER_SEARCH_MARGIN = 8;
    private static final int MAX_LADDER_SEARCH_BLOCKS = 32_768;
    private static final int MIN_LADDER_ASCENT = 2;
    private static final double LADDER_TRANSITION_SPEED = 1.0D;
    private static final int LADDER_SEARCH_COOLDOWN = 10;
    @Nullable
    private LadderRoute ladderRoute;
    @Nullable
    private BlockPos ladderRouteTarget;
    private int ladderRouteReachRange;
    private long nextLadderSearchTick;
    private LadderRoutePhase ladderRoutePhase = LadderRoutePhase.APPROACH;
    public MCAGroundPathNavigation(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new MCAWalkNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        this.nodeEvaluator.setCanOpenDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    public boolean moveToLadderTarget(BlockPos target, int reachRange, double speedModifier) {
        if (this.ladderRoute != null && this.ladderRouteTarget != null) {
            if (this.ladderRoute.exit() == null) {
                if (isTargetOnLadderColumn(this.ladderRoute, target)) {
                    this.ladderRoute = this.ladderRoute.withClimbY(target.getY());
                    this.ladderRouteTarget = target;
                    this.ladderRouteReachRange = reachRange;
                    this.speedModifier = speedModifier;
                    return true;
                }
                cancelLadderRoute();
            } else if (this.ladderRoutePhase != LadderRoutePhase.APPROACH
                    || target.getY() >= this.ladderRoute.exit().getY() - 1) {
                this.ladderRouteTarget = target;
                this.ladderRouteReachRange = reachRange;
                this.speedModifier = speedModifier;
                return true;
            }
            cancelLadderRoute();
        }

        if (this.level.getGameTime() < this.nextLadderSearchTick) {
            return false;
        }
        if (this.level.getBlockState(target).is(BlockTags.CLIMBABLE)
                && target.distManhattan(this.mob.blockPosition()) <= 1) {
            return false;
        }
        this.nextLadderSearchTick = this.level.getGameTime() + LADDER_SEARCH_COOLDOWN;

        Path directPath = super.createPath(target, reachRange);
        if (reachesTargetHeight(directPath, target)) {
            return false;
        }

        Optional<LadderRoute> route = findLadderRoute(target, reachRange);
        if (route.isEmpty()) {
            MCA.LOGGER.debug("[LadderRoute] villager={} target={} vanillaReachable=false result=no-route", this.mob.getUUID(), target);
            return false;
        }

        this.ladderRoute = route.get();
        this.ladderRouteTarget = target;
        this.ladderRouteReachRange = reachRange;
        this.ladderRoutePhase = LadderRoutePhase.APPROACH;
        if (!super.moveTo(this.ladderRoute.approachPath(), speedModifier)) {
            clearLadderRoute();
            return false;
        }
        MCA.LOGGER.info("[LadderRoute] villager={} target={} ladder={} climbY={} exit={} approachNodes={}",
                this.mob.getUUID(), target, this.ladderRoute.ladderBottom(), this.ladderRoute.climbY(), this.ladderRoute.exit(),
                this.ladderRoute.approachPath().getNodeCount());
        return true;
    }

    private boolean reachesTargetHeight(@Nullable Path path, BlockPos target) {
        if (path == null || !path.canReach()) {
            return false;
        }

        Node end = path.getEndNode();
        return end != null && end.y == target.getY();
    }

    @Override
    public boolean moveTo(@Nullable Path path, double speedModifier) {
        if (this.ladderRoute != null && this.ladderRoutePhase != LadderRoutePhase.APPROACH
                && (path == null || path.isDone())) {
            this.speedModifier = speedModifier;
            return true;
        }
        return super.moveTo(path, speedModifier);
    }

    @Override
    public void stop() {
        if (this.ladderRoute == null) {
            super.stop();
        }
    }

    public void cancelLadderRoute() {
        if (this.ladderRoute != null) {
            clearLadderRoute();
            super.stop();
        }
    }

    @Override
    public void recomputePath() {
        if (this.ladderRoute != null && this.ladderRoutePhase != LadderRoutePhase.APPROACH) {
            return;
        }
        super.recomputePath();
    }

    @Override
    public void tick() {
        if (this.ladderRoute == null || this.ladderRouteTarget == null) {
            super.tick();
            return;
        }

        if (this.ladderRoutePhase == LadderRoutePhase.APPROACH && this.path != null && !this.path.isDone()) {
            super.tick();
            return;
        }

        BlockPos ladder = this.ladderRoute.ladderBottom();
        if (!this.level.getBlockState(ladder).is(BlockTags.CLIMBABLE)) {
            MCA.LOGGER.info("[LadderRoute] villager={} ladder={} result=missing-ladder", this.mob.getUUID(), ladder);
            clearLadderRoute();
            super.tick();
            return;
        }

        if (this.ladderRoutePhase == LadderRoutePhase.APPROACH) {
            this.ladderRoutePhase = LadderRoutePhase.CENTER;
        }

        BlockPos climbAnchor = getClimbAnchor(ladder);
        if (this.ladderRoutePhase == LadderRoutePhase.CENTER) {
            if (!isCenteredOnLadder(ladder)) {
                this.mob.getMoveControl().setWantedPosition(
                        climbAnchor.getX() + 0.5D,
                        this.mob.getY(),
                        climbAnchor.getZ() + 0.5D,
                        Math.max(this.speedModifier, LADDER_TRANSITION_SPEED)
                );
                return;
            }

            this.ladderRoutePhase = LadderRoutePhase.CLIMB;
            MCA.LOGGER.info("[LadderRoute] villager={} ladder={} result=climb-start y={} climbY={} exit={} climbAnchor={}",
                    this.mob.getUUID(), ladder, this.mob.getY(), this.ladderRoute.climbY(), this.ladderRoute.exit(), climbAnchor);
        }

        if (this.ladderRoutePhase == LadderRoutePhase.CLIMB) {
            if (this.mob.getY() < this.ladderRoute.climbY() - 0.25D) {
                this.mob.getMoveControl().setWantedPosition(
                        climbAnchor.getX() + 0.5D,
                        this.ladderRoute.climbY(),
                        climbAnchor.getZ() + 0.5D,
                        this.speedModifier
                );
                return;
            }

            if (this.ladderRoute.exit() == null) {
                MCA.LOGGER.info("[LadderRoute] villager={} ladder={} result=target-height-reached y={}",
                        this.mob.getUUID(), ladder, this.mob.getY());
                clearLadderRoute();
                super.stop();
                return;
            }
            this.ladderRoutePhase = LadderRoutePhase.EXIT;
        }

        BlockPos exit = this.ladderRoute.exit();
        if (this.mob.onClimbable() || !this.mob.blockPosition().equals(exit)) {
            this.mob.getMoveControl().setWantedPosition(
                    exit.getX() + 0.5D,
                    exit.getY(),
                    exit.getZ() + 0.5D,
                    Math.max(this.speedModifier, LADDER_TRANSITION_SPEED)
            );
            return;
        }

        Path exitPath = super.createPath(this.ladderRouteTarget, this.ladderRouteReachRange);
        MCA.LOGGER.info("[LadderRoute] villager={} ladder={} result=exit-reached exitPath={}",
                this.mob.getUUID(), ladder, exitPath == null ? "null" : exitPath.canReach());
        clearLadderRoute();
        if (exitPath != null) {
            this.moveTo(exitPath, this.speedModifier);
        }
    }

    private Optional<LadderRoute> findLadderRoute(BlockPos target, int reachRange) {
        BlockPos start = this.mob.blockPosition();
        boolean targetIsOnLadder = this.level.getBlockState(target).is(BlockTags.CLIMBABLE);
        if (target.getY() <= start.getY() || !targetIsOnLadder && target.getY() - start.getY() < MIN_LADDER_ASCENT) {
            return Optional.empty();
        }

        int minX = Math.min(start.getX(), target.getX()) - LADDER_SEARCH_MARGIN;
        int maxX = Math.max(start.getX(), target.getX()) + LADDER_SEARCH_MARGIN;
        int minY = Math.max(this.level.getMinBuildHeight(), start.getY() - 2);
        int maxY = Math.min(this.level.getMaxBuildHeight() - 1, target.getY() + 2);
        int minZ = Math.min(start.getZ(), target.getZ()) - LADDER_SEARCH_MARGIN;
        int maxZ = Math.max(start.getZ(), target.getZ()) + LADDER_SEARCH_MARGIN;
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_LADDER_SEARCH_BLOCKS) {
            return Optional.empty();
        }

        LadderRoute bestRoute = null;
        int bestCost = Integer.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(minX, minY, minZ, maxX, maxY, maxZ)) {
            if (!this.level.getBlockState(pos).is(BlockTags.CLIMBABLE)
                    || this.level.getBlockState(pos.below()).is(BlockTags.CLIMBABLE)) {
                continue;
            }
            if (targetIsOnLadder && (pos.getX() != target.getX() || pos.getZ() != target.getZ())) {
                continue;
            }

            Optional<LadderRoute> route = createLadderRoute(pos.immutable(), target, reachRange);
            if (route.isEmpty()) {
                continue;
            }

            LadderRoute candidate = route.get();
            int cost = candidate.approachPath().getNodeCount()
                    + candidate.climbY() - candidate.ladderBottom().getY()
                    + (candidate.exit() == null ? 0 : candidate.exit().distManhattan(target));
            if (cost < bestCost) {
                bestRoute = candidate;
                bestCost = cost;
            }
        }
        return Optional.ofNullable(bestRoute);
    }

    private Optional<LadderRoute> createLadderRoute(BlockPos bottom, BlockPos target, int reachRange) {
        BlockPos top = bottom;
        while (this.level.getBlockState(top.above()).is(BlockTags.CLIMBABLE)) {
            top = top.above();
        }

        Optional<Path> approachPath = findLadderApproach(bottom);
        if (approachPath.isEmpty()) {
            return Optional.empty();
        }
        if (this.level.getBlockState(target).is(BlockTags.CLIMBABLE)
                && target.getX() == bottom.getX() && target.getZ() == bottom.getZ()
                && target.getY() >= bottom.getY() && target.getY() <= top.getY()) {
            return Optional.of(new LadderRoute(bottom, top, target.getY(), null, approachPath.get()));
        }

        Optional<BlockPos> exit = findLadderExit(bottom, top, target);
        if (exit.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new LadderRoute(bottom, top, exit.get().getY(), exit.get(), approachPath.get()));
    }

    private Optional<Path> findLadderApproach(BlockPos bottom) {
        Direction preferredApproach = getPreferredExitDirection(bottom);
        if (preferredApproach != null) {
            Path path = super.createPath(bottom.relative(preferredApproach), 0);
            return path != null && path.canReach() ? Optional.of(path) : Optional.empty();
        }

        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos approach = bottom.relative(direction);
            Path path = super.createPath(approach, 0);
            if (path != null && path.canReach()) {
                return Optional.of(path);
            }
        }
        return Optional.empty();
    }

    private Optional<BlockPos> findLadderExit(BlockPos bottom, BlockPos top, BlockPos target) {
        int exitY = target.getY();
        if (exitY < bottom.getY() + MIN_LADDER_ASCENT || exitY > top.getY() + 1) {
            return Optional.empty();
        }

        BlockPos bestExit = null;
        int bestCost = Integer.MAX_VALUE;
        Direction preferredExitDirection = getPreferredExitDirection(bottom);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (preferredExitDirection != null && direction != preferredExitDirection) {
                continue;
            }
            BlockPos exit = new BlockPos(bottom.getX(), exitY, bottom.getZ()).relative(direction);
            BlockPos floor = exit.below();
            if (!this.level.getBlockState(exit).isAir()
                    || !this.level.getBlockState(exit.above()).isAir()
                    || !this.level.getBlockState(floor).entityCanStandOn(this.level, floor, this.mob)) {
                continue;
            }

            int cost = exit.distManhattan(target);
            if (cost < bestCost) {
                bestExit = exit;
                bestCost = cost;
            }
        }
        return Optional.ofNullable(bestExit);
    }

    @Nullable
    private Direction getPreferredExitDirection(BlockPos ladder) {
        BlockState state = this.level.getBlockState(ladder);
        return state.getBlock() instanceof LadderBlock ? state.getValue(LadderBlock.FACING) : null;
    }

    private BlockPos getClimbAnchor(BlockPos ladder) {
        BlockState state = this.level.getBlockState(ladder);
        if (state.getBlock() instanceof LadderBlock) {
            return ladder.relative(state.getValue(LadderBlock.FACING).getOpposite());
        }
        return ladder;
    }

    private boolean isCenteredOnLadder(BlockPos ladder) {
        if (!this.mob.onClimbable()) {
            return false;
        }

        BlockState state = this.level.getBlockState(ladder);
        if (state.getBlock() instanceof LadderBlock) {
            Direction facing = state.getValue(LadderBlock.FACING);
            double alignedCoordinate = facing.getAxis() == Direction.Axis.Z ? this.mob.getX() : this.mob.getZ();
            double ladderCenter = facing.getAxis() == Direction.Axis.Z
                    ? ladder.getX() + 0.5D
                    : ladder.getZ() + 0.5D;
            return Math.abs(alignedCoordinate - ladderCenter) < 0.1D;
        }

        return ladder.closerToCenterThan(this.mob.position(), 0.15D);
    }

    private boolean isTargetOnLadderColumn(LadderRoute route, BlockPos target) {
        return this.level.getBlockState(target).is(BlockTags.CLIMBABLE)
                && target.getX() == route.ladderBottom().getX()
                && target.getZ() == route.ladderBottom().getZ()
                && target.getY() >= route.ladderBottom().getY()
                && target.getY() <= route.ladderTop().getY();
    }

    private void clearLadderRoute() {
        this.ladderRoute = null;
        this.ladderRouteTarget = null;
        this.ladderRoutePhase = LadderRoutePhase.APPROACH;
    }

    private record LadderRoute(BlockPos ladderBottom, BlockPos ladderTop, int climbY, @Nullable BlockPos exit, Path approachPath) {
        private LadderRoute withClimbY(int climbY) {
            return new LadderRoute(this.ladderBottom, this.ladderTop, climbY, null, this.approachPath);
        }
    }

    private enum LadderRoutePhase {
        APPROACH,
        CENTER,
        CLIMB,
        EXIT
    }
}
