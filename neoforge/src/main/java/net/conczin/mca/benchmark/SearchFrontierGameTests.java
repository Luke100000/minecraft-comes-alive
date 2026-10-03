package net.conczin.mca.benchmark;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.pathfinder.BinaryHeap;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;

import java.lang.reflect.Field;

/**
 * Read-only, post-search inspection for the opt-in GameTest fixtures.
 * Reflection stays test-only rather than widening private vanilla fields in release jars.
 * Vanilla 1.21.1 retains the node map and frontier after NodeEvaluator.done().
 */
final class SearchFrontierGameTests {
    private static final Field OPEN_SET = field(PathFinder.class, "openSet");
    private static final Field NODES = field(NodeEvaluator.class, "nodes");

    private SearchFrontierGameTests() {
    }

    static void log(String label, PathFinder finder, NodeEvaluator evaluator,
                    BlockPos origin, int effectiveBudget, float maxLength, Path path) {
        Summary summary = inspect(finder, evaluator, origin);
        String termination = path != null && path.canReach() ? "REACHED"
                : summary.frontier() > 0 && summary.closed() == effectiveBudget - 1 ? "NODE_BUDGET"
                : summary.frontier() == 0 && summary.closed() > 0 ? "FRONTIER_EMPTY"
                : "UNCLASSIFIED";
        MCA.LOGGER.info("[MCA Search Frontier] label={} maxLength={} budget={} termination={} cached={} "
                        + "closed={} frontier={} closedMaxAbsZ={} closedMinX={} closedMaxX={} "
                        + "frontierHead={} frontierHeadF={} reachable={} pathNodes={} end={} origin={}",
                label, maxLength, effectiveBudget, termination, summary.cached(), summary.closed(),
                summary.frontier(), summary.maxAbsZ(), summary.minX(), summary.maxX(),
                summary.frontierHead(), summary.frontierHeadF(), path != null && path.canReach(),
                path == null ? 0 : path.getNodeCount(), path == null ? null : path.getEndNode().asBlockPos(), origin);
    }

    private static Summary inspect(PathFinder finder, NodeEvaluator evaluator, BlockPos origin) {
        try {
            BinaryHeap frontier = (BinaryHeap) OPEN_SET.get(finder);
            Int2ObjectMap<?> nodes = (Int2ObjectMap<?>) NODES.get(evaluator);
            int closed = 0;
            int maxAbsZ = 0;
            int minX = 0;
            int maxX = 0;
            for (Object value : nodes.values()) {
                Node node = (Node) value;
                if (node.closed) {
                    closed++;
                    maxAbsZ = Math.max(maxAbsZ, Math.abs(node.z - origin.getZ()));
                    minX = Math.min(minX, node.x - origin.getX());
                    maxX = Math.max(maxX, node.x - origin.getX());
                }
            }
            Node head = frontier.isEmpty() ? null : frontier.peek();
            return new Summary(nodes.size(), closed, frontier.size(), maxAbsZ, minX, maxX,
                    head == null ? null : head.asBlockPos(), head == null ? Float.NaN : head.f);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot inspect vanilla search in GameTest", e);
        }
    }

    private static Field field(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Vanilla 1.21.1 diagnostic field unavailable: " + name, e);
        }
    }

    private record Summary(int cached, int closed, int frontier, int maxAbsZ, int minX, int maxX,
                           BlockPos frontierHead, float frontierHeadF) {
    }
}
