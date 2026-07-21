package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;
import java.util.stream.Collectors;

/** Compact persistent X/Z footprint for one semantic Floor band. */
public record BuildingFloorRegion(int anchorY, int area, List<Component> components) {
    public BuildingFloorRegion {
        components = List.copyOf(components);
    }

    static BuildingFloorRegion load(CompoundTag tag) {
        return new BuildingFloorRegion(tag.getInt("anchorY"), tag.getInt("area"),
                NbtHelper.toList(tag.getList("components", Tag.TAG_COMPOUND),
                        value -> Component.load((CompoundTag) value)));
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
        return components.stream()
                .flatMap(component -> component.cells(anchorY).stream())
                .collect(Collectors.toUnmodifiableSet());
    }

    BuildingFloorRegion withAnchorY(int anchorY) {
        return this.anchorY == anchorY ? this : new BuildingFloorRegion(anchorY, area, components);
    }

    static BuildingFloorRegion fromFootprint(int anchorY, Collection<BlockPos> footprintCells) {
        if (footprintCells == null || footprintCells.isEmpty()) {
            return new BuildingFloorRegion(anchorY, 0, List.of());
        }
        Set<Cell> cells = footprintCells.stream()
                .map(pos -> new Cell(pos.getX(), pos.getZ()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Component> components = splitComponents(cells);
        return new BuildingFloorRegion(anchorY,
                components.stream().mapToInt(Component::area).sum(), components);
    }

    private static List<Component> splitComponents(Set<Cell> cells) {
        Set<Cell> remaining = new HashSet<>(cells);
        List<Component> components = new ArrayList<>();
        while (!remaining.isEmpty()) {
            Cell seed = remaining.stream().min(Comparator.comparingInt(Cell::x).thenComparingInt(Cell::z)).orElseThrow();
            remaining.remove(seed);
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            List<Cell> connected = new ArrayList<>();
            queue.add(seed);
            while (!queue.isEmpty()) {
                Cell current = queue.removeFirst();
                connected.add(current);
                for (Cell next : List.of(
                        new Cell(current.x() + 1, current.z()), new Cell(current.x() - 1, current.z()),
                        new Cell(current.x(), current.z() + 1), new Cell(current.x(), current.z() - 1))) {
                    if (remaining.remove(next)) queue.addLast(next);
                }
            }
            components.add(componentFromCells(connected));
        }
        components.sort(Comparator.comparingInt(Component::minX).thenComparingInt(Component::minZ)
                .thenComparingInt(Component::maxX).thenComparingInt(Component::maxZ));
        return List.copyOf(components);
    }

    private static Component componentFromCells(Collection<Cell> cells) {
        Map<Integer, TreeSet<Integer>> xsByZ = new TreeMap<>();
        for (Cell cell : cells) xsByZ.computeIfAbsent(cell.z(), ignored -> new TreeSet<>()).add(cell.x());
        List<Span> spans = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int area = 0;
        for (Map.Entry<Integer, TreeSet<Integer>> entry : xsByZ.entrySet()) {
            int z = entry.getKey();
            Iterator<Integer> xs = entry.getValue().iterator();
            if (!xs.hasNext()) continue;
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            int start = xs.next(), previous = start;
            minX = Math.min(minX, start);
            maxX = Math.max(maxX, start);
            area++;
            while (xs.hasNext()) {
                int x = xs.next();
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                area++;
                if (x != previous + 1) {
                    spans.add(new Span(z, start, previous));
                    start = x;
                }
                previous = x;
            }
            spans.add(new Span(z, start, previous));
        }
        return new Component(minX, minZ, maxX, maxZ, area, spans);
    }

    public int intersectionArea(BuildingFloorRegion other) {
        int intersection = 0;
        for (Component component : components) {
            for (Component candidate : other.components) intersection += component.intersectionArea(candidate);
        }
        return intersection;
    }

    private record Cell(int x, int z) {
    }

    public record Component(int minX, int minZ, int maxX, int maxZ, int area, List<Span> spans) {
        public Component {
            spans = spans == null || spans.isEmpty()
                    ? rectangleSpans(minX, minZ, maxX, maxZ)
                    : List.copyOf(spans);
        }

        private static Component load(CompoundTag tag) {
            return new Component(tag.getInt("minX"), tag.getInt("minZ"), tag.getInt("maxX"), tag.getInt("maxZ"),
                    tag.getInt("area"), NbtHelper.toList(tag.getList("spans", Tag.TAG_COMPOUND),
                    value -> Span.load((CompoundTag) value)));
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
            return spans.stream().anyMatch(span -> span.containsHorizontally(x, z));
        }

        Set<BlockPos> cells(int anchorY) {
            LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
            for (Span span : spans) {
                for (int x = span.minX(); x <= span.maxX(); x++) cells.add(new BlockPos(x, anchorY, span.z()));
            }
            return Set.copyOf(cells);
        }

        private int intersectionArea(Component other) {
            int intersection = 0;
            int minSharedZ = Math.max(minZ, other.minZ);
            int maxSharedZ = Math.min(maxZ, other.maxZ);
            for (int z = minSharedZ; z <= maxSharedZ; z++) {
                for (Span own : spansAt(z)) {
                    for (Span candidate : other.spansAt(z)) {
                        int minSharedX = Math.max(own.minX(), candidate.minX());
                        int maxSharedX = Math.min(own.maxX(), candidate.maxX());
                        if (minSharedX <= maxSharedX) intersection += maxSharedX - minSharedX + 1;
                    }
                }
            }
            return intersection;
        }

        private List<Span> spansAt(int z) {
            return z < minZ || z > maxZ ? List.of() : spans.stream().filter(span -> span.z() == z).toList();
        }

        private static List<Span> rectangleSpans(int minX, int minZ, int maxX, int maxZ) {
            List<Span> spans = new ArrayList<>();
            for (int z = minZ; z <= maxZ; z++) spans.add(new Span(z, minX, maxX));
            return List.copyOf(spans);
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
