package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.*;

/** Persistent physical building geometry. Functional semantics live exclusively on Rooms. */
public final class Structure implements VillageBuilding {
    private int id;
    private int rootRoomId = -1;
    private int nextFloorId;
    private BlockPos source;
    private BlockPos min;
    private BlockPos max;
    private final Map<Integer, StructureFloor> floors = new HashMap<>();
    /** Exact reachable Structure volume, compressed into one horizontal region per Y slice. */
    private List<BuildingFloorRegion> volumeSlices = List.of();

    public Structure(int id, BlockPos source, BlockPos min, BlockPos max, Collection<StructureFloor> floors) {
        this(id, source, min, max, floors, List.of());
    }

    Structure(int id,
              BlockPos source,
              BlockPos min,
              BlockPos max,
              Collection<StructureFloor> floors,
              Collection<BuildingFloorRegion> volumeSlices) {
        this.id = id;
        this.source = source.immutable();
        this.min = min.immutable();
        this.max = max.immutable();
        this.volumeSlices = List.copyOf(volumeSlices);
        for (StructureFloor floor : floors) {
            this.floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
    }

    public Structure(CompoundTag tag) {
        id = tag.getInt("id");
        rootRoomId = tag.contains("rootRoomId") ? tag.getInt("rootRoomId") : -1;
        nextFloorId = tag.getInt("nextFloorId");
        source = NbtHelper.decodeBlockPos(tag.get("source"));
        min = NbtHelper.decodeBlockPos(tag.get("min"));
        max = NbtHelper.decodeBlockPos(tag.get("max"));
        for (StructureFloor floor : NbtHelper.toList(tag.getList("floors", Tag.TAG_COMPOUND),
                value -> StructureFloor.load((CompoundTag) value))) {
            floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
        if (tag.contains("volume", Tag.TAG_LIST)) {
            volumeSlices = List.copyOf(NbtHelper.toList(tag.getList("volume", Tag.TAG_COMPOUND),
                    value -> BuildingFloorRegion.load((CompoundTag) value)));
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("rootRoomId", rootRoomId);
        tag.putInt("nextFloorId", nextFloorId);
        tag.put("source", NbtHelper.encodeBlockPos(source));
        tag.put("min", NbtHelper.encodeBlockPos(min));
        tag.put("max", NbtHelper.encodeBlockPos(max));
        tag.put("floors", NbtHelper.fromList(getFloors(), StructureFloor::save));
        if (!volumeSlices.isEmpty()) {
            tag.put("volume", NbtHelper.fromList(volumeSlices, BuildingFloorRegion::save));
        }
        return tag;
    }

    public Optional<StructureFloor> getFloor(int floorId) {
        return Optional.ofNullable(floors.get(floorId));
    }

    public List<StructureFloor> getFloors() {
        return floors.values().stream()
                .sorted(Comparator.comparingInt(StructureFloor::anchorY).thenComparingInt(StructureFloor::id))
                .toList();
    }

    public Optional<StructureFloor> resolveFloor(Level world, BlockPos pos) {
        return resolveFloor(pos);
    }

    /** Resolves a persisted Structure position to its physical Floor without querying world state. */
    public Optional<StructureFloor> resolveFloor(Vec3i pos) {
        if (!containsPos(pos)) {
            return Optional.empty();
        }
        List<StructureFloor> verticalBand = getFloors().stream()
                .filter(floor -> pos.getY() >= floor.anchorY() && pos.getY() < floor.ceilingY())
                .toList();
        if (verticalBand.isEmpty()) {
            return Optional.empty();
        }
        return verticalBand.stream()
                .filter(floor -> floor.contains(pos.getX(), pos.getZ()))
                .min(Comparator.comparingInt(floor -> Math.abs(pos.getY() - floor.anchorY())))
                .or(() -> verticalBand.stream()
                        .min(Comparator.comparingInt(floor -> Math.abs(pos.getY() - floor.anchorY()))));
    }

    public Optional<StructureFloor> getGroundFloor(Village village) {
        Building root = village == null ? null : village.getBuilding(rootRoomId).orElse(null);
        return root == null ? Optional.empty() : getFloor(root.getFloorId());
    }

    public boolean isRootRoom(int roomId) {
        return rootRoomId == roomId;
    }

    public int getRootRoomId() {
        return rootRoomId;
    }

    public void setRootRoomId(int rootRoomId) {
        this.rootRoomId = rootRoomId;
    }

    int allocateFloorId() {
        return nextFloorId++;
    }

    /**
     * Replaces physical geometry while preserving stable Floor IDs. Rooms are the strongest
     * identity anchors; a rescan is rejected when any registered Room loses its Floor.
     */
    boolean applyScan(StructureScanner.Result scan, Collection<Building> rooms) {
        return applyScan(scan, rooms, -1);
    }

    /**
     * Refreshes physical Structure/Floor geometry for Update Room without requiring the Room's
     * stale pre-update footprint to fit the new Floor. Other registered Rooms remain hard anchors,
     * and the updated Room's existing Floor ID is preserved instead of silently jumping storeys.
     */
    boolean applyScanForRoomUpdate(StructureScanner.Result scan,
                                   Collection<Building> rooms,
                                   int updatingRoomId) {
        return applyScan(scan, rooms, updatingRoomId);
    }

    private boolean applyScan(StructureScanner.Result scan,
                              Collection<Building> rooms,
                              int updatingRoomId) {
        StructureFloorMatcher.Result match = StructureFloorMatcher.match(
                getFloors(), nextFloorId, scan.floors(), rooms, updatingRoomId).orElse(null);
        if (match == null) {
            return false;
        }

        floors.clear();
        floors.putAll(match.floors());
        nextFloorId = match.nextFloorId();
        source = scan.source();
        min = scan.min();
        max = scan.max();
        volumeSlices = scan.volumeSlices();
        return true;
    }

    void copyPhysicalGeometryFrom(Structure other) {
        if (other == null || other.id != id) {
            throw new IllegalArgumentException("Structure identity mismatch");
        }
        nextFloorId = other.nextFloorId;
        source = other.source;
        min = other.min;
        max = other.max;
        floors.clear();
        floors.putAll(other.floors);
        volumeSlices = other.volumeSlices;
    }

    private static boolean roomFootprintInside(Building room, StructureFloor floor) {
        if (room.getFloorRegions().isEmpty() || floor.region() == null) {
            return floor.contains(room.getSourceBlock().getX(), room.getSourceBlock().getZ());
        }
        BuildingFloorRegion region = room.getFloorRegions().getFirst();
        return region.intersectionArea(floor.region()) == region.area();
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public BlockPos getSource() {
        return source;
    }

    public BlockPos getRawPos0() {
        return min;
    }

    public BlockPos getRawPos1() {
        return max;
    }

    @Override
    public BlockPos getPos0() {
        return min;
    }

    @Override
    public BlockPos getPos1() {
        return max;
    }

    @Override
    public BlockPos getCenter() {
        return new BlockPos((min.getX() + max.getX()) / 2,
                (min.getY() + max.getY()) / 2,
                (min.getZ() + max.getZ()) / 2);
    }

    public List<BuildingFloorRegion> getVolumeSlices() {
        return volumeSlices;
    }

    @Override
    public boolean containsPos(Vec3i pos) {
        if (pos.getX() < min.getX() || pos.getX() > max.getX()
                || pos.getY() < min.getY() || pos.getY() > max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) {
            return false;
        }
        return volumeSlices.stream().anyMatch(slice -> slice.anchorY() == pos.getY()
                && slice.containsHorizontally(pos.getX(), pos.getZ()));
    }

    public boolean intersects(Structure other) {
        if (other == null
                || max.getX() < other.min.getX() || min.getX() > other.max.getX()
                || max.getY() < other.min.getY() || min.getY() > other.max.getY()
                || max.getZ() < other.min.getZ() || min.getZ() > other.max.getZ()) {
            return false;
        }
        Map<Integer, BuildingFloorRegion> otherByY = new HashMap<>();
        for (BuildingFloorRegion slice : other.volumeSlices) {
            otherByY.put(slice.anchorY(), slice);
        }
        for (BuildingFloorRegion slice : volumeSlices) {
            BuildingFloorRegion candidate = otherByY.get(slice.anchorY());
            if (candidate != null && slice.intersectionArea(candidate) > 0) {
                return true;
            }
        }
        return false;
    }

}
