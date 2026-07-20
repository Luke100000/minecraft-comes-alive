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
     * Structure shade is one stable canonical shell ring on every floor view. Floor selection
     * changes Room/Floor content, never the Structure presentation itself. This preserves HEAD's
     * one-region-per-building behavior and prevents selected floors from becoming artificially darker.
     */
    static <T> Set<T> structureShade(Collection<T> physicalCells,
                                     Collection<T> outlineBaseCells,
                                     Collection<T> outlineCells,
                                     Collection<T> registeredRoomCells,
                                     boolean allFloors) {
        LinkedHashSet<T> shade = new LinkedHashSet<>(outlineCells);
        shade.removeAll(outlineBaseCells);
        return Set.copyOf(shade);
    }

    /** Basements remain visible as Room geometry, but never enlarge the All Floors building outline. */
    static boolean contributesToAllFloorsOutline(int floorOrdinal) {
        return floorOrdinal >= 0;
    }

}
