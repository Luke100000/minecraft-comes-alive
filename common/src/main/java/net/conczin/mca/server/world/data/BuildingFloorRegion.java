package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.List;

/**
 * Compact persistent metadata for one semantic walkable floor band inside a
 * connected building interior. Components preserve disconnected balconies or
 * platforms, while row spans preserve atrium voids without storing every cell.
 */
public record BuildingFloorRegion(int anchorY, int area, List<Component> components) {
    public BuildingFloorRegion {
        components = List.copyOf(components);
    }

    static BuildingFloorRegion fromDetected(BuildingFloorRegionDetector.DetectedRegion region) {
        return new BuildingFloorRegion(
                region.anchorY(),
                region.area(),
                region.components().stream().map(Component::fromDetected).toList()
        );
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

    public record Component(int minX,
                            int minZ,
                            int maxX,
                            int maxZ,
                            int area,
                            List<Span> spans) {
        public Component {
            spans = List.copyOf(spans);
        }

        private static Component fromDetected(BuildingFloorRegionDetector.DetectedComponent component) {
            return new Component(
                    component.minX(),
                    component.minZ(),
                    component.maxX(),
                    component.maxZ(),
                    component.area(),
                    component.spans().stream().map(Span::fromDetected).toList()
            );
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
    }

    public record Span(int z, int minX, int maxX) {
        private static Span fromDetected(BuildingFloorRegionDetector.DetectedSpan span) {
            return new Span(span.z(), span.minX(), span.maxX());
        }

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
