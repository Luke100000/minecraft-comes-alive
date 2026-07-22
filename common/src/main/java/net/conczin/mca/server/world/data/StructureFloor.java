package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;

/** Stable persistent identity for one physical storey in a Structure. */
public record StructureFloor(int id, int anchorY, int ceilingY, BuildingFloorRegion region) {
    public boolean contains(int x, int z) {
        return region != null && region.containsHorizontally(x, z);
    }

    public int area() {
        return region == null ? 0 : region.area();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("anchorY", anchorY);
        tag.putInt("ceilingY", ceilingY);
        if (region != null) {
            tag.put("region", region.save());
        }
        return tag;
    }

    public static StructureFloor load(CompoundTag tag) {
        BuildingFloorRegion region = tag.contains("region")
                ? BuildingFloorRegion.load(tag.getCompound("region"))
                : new BuildingFloorRegion(tag.getInt("anchorY"), 0, java.util.List.of());
        return new StructureFloor(tag.getInt("id"), tag.getInt("anchorY"), tag.getInt("ceilingY"), region);
    }

    public StructureFloor withGeometry(int anchorY, int ceilingY, BuildingFloorRegion region) {
        return new StructureFloor(id, anchorY, ceilingY, region);
    }
}
