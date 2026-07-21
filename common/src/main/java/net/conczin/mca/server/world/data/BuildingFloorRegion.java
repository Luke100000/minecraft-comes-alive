package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Compact persistent metadata for one semantic walkable floor band inside a
 * connected building interior. A fresh scan starts with a physically detected
 * {@code anchorY}; registered rooms may retain that value as their semantic floor
 * assignment while replacing the component geometry from a newer scan. Components
 * preserve disconnected balconies or platforms, while row spans preserve atrium
 * voids without storing every cell.
 */
public record BuildingFloorRegion(int anchorY, int area, List<Component> components) {
    public BuildingFloorRegion {
        components = List.copyOf(components);
    }

    static BuildingFloorRegion load(CompoundTag tag) {
        List<Component> components = NbtHelper.toList(
                tag.getList("components", Tag.TAG_COMPOUND),
                value -> Component.load((CompoundTag) value)
        );
        return new BuildingFloorRegion(tag.getInt("anchorY"), tag.getInt("area"), components);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("anchorY", anchorY);
        tag.putInt("area", area);
        tag.put("components", NbtHelper.fromList(components, Component::save));
        return tag;
    }

    public boolean containsHorizontally(int x, int z) {
        return components.stream().anyMatch(component -> component.containsHorizontally(x, z));
    }

    public Set<BlockPos> cells() {
        LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
        for (Component component : components) {
            if (component.spans().isEmpty()) {
                for (int z = component.minZ(); z <= component.maxZ(); z++) {
                    for (int x = component.minX(); x <= component.maxX(); x++) {
                        cells.add(new BlockPos(x, anchorY, z));
                    }
                }
                continue;
            }
            for (Span span : component.spans()) {
                for (int x = span.minX(); x <= span.maxX(); x++) {
                    cells.add(new BlockPos(x, anchorY, span.z()));
                }
            }
        }
        return Set.copyOf(cells);
    }

    /**
     * Keeps newly scanned floor geometry attached to an already-assigned semantic floor.
     */
    BuildingFloorRegion withAnchorY(int anchorY) {
        return this.anchorY == anchorY ? this : new BuildingFloorRegion(anchorY, area, components);
    }

    static BuildingFloorRegion fromFootprint(int anchorY, Collection<BlockPos> footprintCells) {
        if (footprintCells == null || footprintCells.isEmpty()) {
            return new BuildingFloorRegion(anchorY, 0, List.of());
        }

        Map<Integer, TreeSet<Integer>> xsByZ = new TreeMap<>();
        for (BlockPos cell : footprintCells) {
            xsByZ.computeIfAbsent(cell.getZ(), ignored -> new TreeSet<>()).add(cell.getX());
        }

        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int area = 0;
        List<Span> spans = new ArrayList<>();

        for (Map.Entry<Integer, TreeSet<Integer>> entry : xsByZ.entrySet()) {
            TreeSet<Integer> xs = entry.getValue();
            if (xs.isEmpty()) {
                continue;
            }

            int z = entry.getKey();
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            area += xs.size();

            Iterator<Integer> iterator = xs.iterator();
            int start = iterator.next();
            int previous = start;
            minX = Math.min(minX, start);
            maxX = Math.max(maxX, start);

            while (iterator.hasNext()) {
                int current = iterator.next();
                minX = Math.min(minX, current);
                maxX = Math.max(maxX, current);
                if (current != previous + 1) {
                    spans.add(new Span(z, start, previous));
                    start = current;
                }
                previous = current;
            }
            spans.add(new Span(z, start, previous));
        }

        Component component = new Component(minX, minZ, maxX, maxZ, area, spans);
        return new BuildingFloorRegion(anchorY, area, List.of(component));
    }

    public int intersectionArea(BuildingFloorRegion other) {
        int intersection = 0;
        for (Component component : components) {
            for (Component otherComponent : other.components) {
                intersection += component.intersectionArea(otherComponent);
            }
        }
        return intersection;
    }

    public record Component(int minX,
                            int minZ,
                            int maxX,
                            int maxZ,
                            int area,
                            List<Span> spans) {
        public Component {
            spans = List.copyOf(spans);
        }

        private static Component load(CompoundTag tag) {
            List<Span> spans = NbtHelper.toList(
                    tag.getList("spans", Tag.TAG_COMPOUND),
                    value -> Span.load((CompoundTag) value)
            );
            return new Component(
                    tag.getInt("minX"),
                    tag.getInt("minZ"),
                    tag.getInt("maxX"),
                    tag.getInt("maxZ"),
                    tag.getInt("area"),
                    spans
            );
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("minX", minX);
            tag.putInt("minZ", minZ);
            tag.putInt("maxX", maxX);
            tag.putInt("maxZ", maxZ);
            tag.putInt("area", area);
            tag.put("spans", NbtHelper.fromList(spans, Span::save));
            return tag;
        }

        public boolean containsHorizontally(int x, int z) {
            if (spans.isEmpty()) {
                return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
            }
            return spans.stream().anyMatch(span -> span.containsHorizontally(x, z));
        }

        private int intersectionArea(Component other) {
            int minSharedZ = Math.max(minZ, other.minZ);
            int maxSharedZ = Math.min(maxZ, other.maxZ);
            if (minSharedZ > maxSharedZ) {
                return 0;
            }

            int intersection = 0;
            for (int z = minSharedZ; z <= maxSharedZ; z++) {
                for (Span own : spansAt(z)) {
                    for (Span candidate : other.spansAt(z)) {
                        int minSharedX = Math.max(own.minX(), candidate.minX());
                        int maxSharedX = Math.min(own.maxX(), candidate.maxX());
                        if (minSharedX <= maxSharedX) {
                            intersection += maxSharedX - minSharedX + 1;
                        }
                    }
                }
            }
            return intersection;
        }

        private List<Span> spansAt(int z) {
            if (z < minZ || z > maxZ) {
                return List.of();
            }
            if (spans.isEmpty()) {
                return List.of(new Span(z, minX, maxX));
            }
            return spans.stream().filter(span -> span.z() == z).toList();
        }
    }

    public record Span(int z, int minX, int maxX) {
        private static Span load(CompoundTag tag) {
            return new Span(tag.getInt("z"), tag.getInt("minX"), tag.getInt("maxX"));
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("z", z);
            tag.putInt("minX", minX);
            tag.putInt("maxX", maxX);
            return tag;
        }

        public boolean containsHorizontally(int x, int z) {
            return this.z == z && x >= minX && x <= maxX;
        }
    }
}
