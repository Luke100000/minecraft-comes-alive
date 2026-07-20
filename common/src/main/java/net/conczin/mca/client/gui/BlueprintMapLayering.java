package net.conczin.mca.client.gui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure ordering and display rules for Blueprint floor layers. */
final class BlueprintMapLayering {
    private BlueprintMapLayering() {
    }

    /** Back-to-front physical paint order: deepest basement first, highest upper Floor last. */
    static List<Integer> floorRenderOrder(Collection<Integer> ordinals) {
        return ordinals.stream().sorted().toList();
    }

    /** Hit testing mirrors paint order so the visually topmost Room wins overlapping cells. */
    static <T> List<T> frontToBack(List<T> renderOrder) {
        ArrayList<T> hitOrder = new ArrayList<>(renderOrder);
        java.util.Collections.reverse(hitOrder);
        return List.copyOf(hitOrder);
    }

    /**
     * Structure shade starts from one stable physical building region. Rooms remove only the cells
     * relevant to the active view: one selected Floor or every Room in All Floors. Connector cells
     * remain shaded because connectors are physical Floor nodes but not ordinary Room interior cells.
     */
    static <T> Set<T> structureShade(Collection<T> shadeBaseCells,
                                     Collection<T> relevantRoomCells) {
        LinkedHashSet<T> shade = new LinkedHashSet<>(shadeBaseCells);
        shade.removeAll(relevantRoomCells);
        return Set.copyOf(shade);
    }

    /** Basements remain visible as Room geometry, but never enlarge the All Floors building outline. */
    static boolean contributesToStructureOutline(int floorOrdinal) {
        return floorOrdinal >= 0;
    }

}
