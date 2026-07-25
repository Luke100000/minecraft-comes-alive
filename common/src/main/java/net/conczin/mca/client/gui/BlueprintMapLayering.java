package net.conczin.mca.client.gui;

import java.util.*;

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

    /** Outdoor sites are associated with the whole building, not with an indoor Floor ordinal. */
    static boolean isOutdoorVisible(Integer selectedFloor) {
        return selectedFloor == null || selectedFloor == 0;
    }

}
