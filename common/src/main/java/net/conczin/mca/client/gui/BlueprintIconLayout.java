package net.conczin.mca.client.gui;

import java.util.ArrayList;
import java.util.List;

/** Pure screen-space layout for icons that share the same Room-map anchor. */
final class BlueprintIconLayout {
    private static final double COLLISION_RADIUS_PIXELS = 10.0D;

    private BlueprintIconLayout() {
    }

    static List<Offset> offsets(int count) {
        if (count <= 0) return List.of();
        if (count == 1) return List.of(new Offset(0.0D, 0.0D));

        List<Offset> offsets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / count;
            offsets.add(new Offset(
                    Math.cos(angle) * COLLISION_RADIUS_PIXELS,
                    Math.sin(angle) * COLLISION_RADIUS_PIXELS));
        }
        return List.copyOf(offsets);
    }

    record Offset(double x, double y) {
    }
}
