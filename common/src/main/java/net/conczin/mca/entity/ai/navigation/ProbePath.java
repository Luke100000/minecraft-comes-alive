package net.conczin.mca.entity.ai.navigation;

import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.List;

/** A detached search result retains its arrival range if a caller later starts moving along it. */
final class ProbePath extends Path {
    private final int reachRange;

    ProbePath(Path path, int reachRange) {
        super(copyNodes(path), path.getTarget(), path.canReach());
        this.reachRange = reachRange;
        setNextNodeIndex(path.getNextNodeIndex());
        Path.DebugData debug = path.debugData();
        if (debug != null) {
            setDebug(debug.openSet(), debug.closedSet(), debug.targetNodes());
        }
    }

    int reachRange() {
        return this.reachRange;
    }

    @Override
    public Path copy() {
        return new ProbePath(this, this.reachRange);
    }

    private static List<Node> copyNodes(Path path) {
        List<Node> nodes = new ArrayList<>(path.getNodeCount());
        for (int i = 0; i < path.getNodeCount(); i++) {
            nodes.add(path.getNode(i));
        }
        return nodes;
    }
}
