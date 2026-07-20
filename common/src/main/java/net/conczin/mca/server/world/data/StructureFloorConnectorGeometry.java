package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pure X/Z projection rules for assigning physical connector cells to one persistent Floor band. */
final class StructureFloorConnectorGeometry {
    private StructureFloorConnectorGeometry() {
    }

    static Set<Cell> expand(Set<Cell> baseCells,
                            Collection<Connector> connectors,
                            int anchorY,
                            int ceilingY) {
        LinkedHashSet<Cell> expanded = new LinkedHashSet<>(baseCells);
        for (Connector connector : connectors) {
            if (connector.y() < anchorY || connector.y() >= ceilingY) {
                continue;
            }
            Cell cell = new Cell(connector.x(), connector.z());
            if (baseCells.contains(cell) || touchesHorizontally(baseCells, cell)) {
                expanded.add(cell);
            }
        }
        return Set.copyOf(expanded);
    }

    private static boolean touchesHorizontally(Set<Cell> cells, Cell cell) {
        return cells.contains(new Cell(cell.x() - 1, cell.z()))
                || cells.contains(new Cell(cell.x() + 1, cell.z()))
                || cells.contains(new Cell(cell.x(), cell.z() - 1))
                || cells.contains(new Cell(cell.x(), cell.z() + 1));
    }

    record Cell(int x, int z) {
    }

    record Connector(int x, int y, int z) {
    }
}
