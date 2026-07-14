package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;

import java.util.*;

/** One persistent, independently rescannable physical section of a logical building. */
public final class Structure implements VillageBuilding {
    private int id;
    private int logicalBuildingId;
    private int mainRoomId = -1;
    private boolean mainRoomAutomatic = true;
    private int surfaceReferenceY;
    private int nextFloorId;
    private BlockPos source;
    private BlockPos min;
    private BlockPos max;
    private final Map<Integer, StructureFloor> floors = new HashMap<>();

    public Structure(int id, BlockPos source, BlockPos min, BlockPos max, Collection<StructureFloor> floors) {
        this.id = id;
        logicalBuildingId = id;
        this.source = source.immutable();
        this.min = min.immutable();
        this.max = max.immutable();
        surfaceReferenceY = source.getY();
        for (StructureFloor floor : floors) {
            this.floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
    }

    public Structure(CompoundTag tag) {
        id = tag.getInt("id");
        logicalBuildingId = tag.contains("buildingId") ? tag.getInt("buildingId") : id;
        mainRoomId = tag.contains("mainRoomId") ? tag.getInt("mainRoomId") : -1;
        mainRoomAutomatic = !tag.contains("mainRoomAutomatic") || tag.getBoolean("mainRoomAutomatic");
        nextFloorId = tag.getInt("nextFloorId");
        source = NbtHelper.decodeBlockPos(tag.get("source"));
        surfaceReferenceY = tag.contains("surfaceReferenceY") ? tag.getInt("surfaceReferenceY") : source.getY();
        min = NbtHelper.decodeBlockPos(tag.get("min"));
        max = NbtHelper.decodeBlockPos(tag.get("max"));
        for (StructureFloor floor : NbtHelper.toList(tag.getList("floors", Tag.TAG_COMPOUND),
                value -> StructureFloor.load((CompoundTag) value))) {
            floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("buildingId", getLogicalBuildingId());
        tag.putInt("mainRoomId", mainRoomId);
        tag.putBoolean("mainRoomAutomatic", mainRoomAutomatic);
        tag.putInt("surfaceReferenceY", surfaceReferenceY);
        tag.putInt("nextFloorId", nextFloorId);
        tag.put("source", NbtHelper.encodeBlockPos(source));
        tag.put("min", NbtHelper.encodeBlockPos(min));
        tag.put("max", NbtHelper.encodeBlockPos(max));
        tag.put("floors", NbtHelper.fromList(getFloors(), StructureFloor::save));
        return tag;
    }

    int getLogicalBuildingId() {
        return logicalBuildingId;
    }

    void setLogicalBuildingId(int logicalBuildingId) {
        this.logicalBuildingId = logicalBuildingId;
    }

    public Optional<StructureFloor> getFloor(int floorId) {
        return Optional.ofNullable(floors.get(floorId));
    }

    public List<StructureFloor> getFloors() {
        return floors.values().stream()
                .sorted(Comparator.comparingInt(StructureFloor::anchorY).thenComparingInt(StructureFloor::id))
                .toList();
    }

    /** Logical Floor selection is gravity-like: choose the highest Floor at or below the query Y. */
    public Optional<StructureFloor> resolveFloor(int queryY) {
        return getFloors().stream()
                .filter(floor -> floor.anchorY() <= queryY)
                .max(Comparator.comparingInt(StructureFloor::anchorY));
    }

    /** Exact vertical-band membership; unlike resolveFloor, this never falls through above a Floor ceiling. */
    public Optional<StructureFloor> floorAtHeight(int queryY) {
        return resolveFloor(queryY).filter(floor -> queryY < floor.ceilingY());
    }

    /** Chooses the nearest Floor whose exact footprint contains the query X/Z column. */
    Optional<StructureFloor> nearestFloorAtColumn(Vec3i pos) {
        if (pos == null || !containsPosHorizontally(pos)) return Optional.empty();
        return getFloors().stream()
                .filter(floor -> floor.contains(pos.getX(), pos.getZ()))
                .min(Comparator.comparingInt((StructureFloor floor) -> verticalDistance(floor, pos.getY()))
                        .thenComparingInt(StructureFloor::anchorY)
                        .thenComparingInt(StructureFloor::id));
    }


    private static int verticalDistance(StructureFloor floor, int queryY) {
        if (queryY < floor.anchorY()) return floor.anchorY() - queryY;
        if (queryY >= floor.ceilingY()) return queryY - Math.max(floor.anchorY(), floor.ceilingY() - 1);
        return 0;
    }

    /** Exact physical membership is the canonical Floor footprint extruded through its vertical band. */
    Optional<StructureFloor> physicalFloorAt(Vec3i pos) {
        if (pos.getX() < min.getX() || pos.getX() > max.getX()
                || pos.getY() < min.getY() || pos.getY() > max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) {
            return Optional.empty();
        }
        return floorAtHeight(pos.getY())
                .filter(floor -> floor.contains(pos.getX(), pos.getZ()));
    }

    Optional<InteractionPosition> resolveInteractionPosition(Level world,
                                                             BlockPos pos,
                                                             Collection<Building> structureRooms) {
        Collection<Building> localRooms = structureRooms == null ? List.of() : structureRooms;
        StructureFloor floor = physicalFloorAt(pos).orElse(null);
        boolean physical = floor != null;
        int roomX = pos.getX();
        int roomZ = pos.getZ();
        if (floor == null) {
            floor = floorAtHeight(pos.getY()).orElse(null);
            if (floor == null) return Optional.empty();
            if (!StructureConnector.attachesToStructure(world, this, pos)) {
                BlockPos adjacent = adjacentInteractionFloorCell(world, pos, floor);
                if (adjacent == null) return Optional.empty();
                roomX = adjacent.getX();
                roomZ = adjacent.getZ();
            }
        }
        return Optional.of(new InteractionPosition(floor,
                roomAtColumn(localRooms, floor, roomX, roomZ), physical));
    }

    private BlockPos adjacentInteractionFloorCell(Level world, BlockPos pos, StructureFloor floor) {
        if (!StructureConnector.isPassageCell(world, pos)) return null;

        boolean insideEnvelope = pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        if (!insideEnvelope || !StructureScanner.isWalkableAnchor(world, pos)) return null;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != 1) continue;
                int x = pos.getX() + dx;
                int z = pos.getZ() + dz;
                if (floor.contains(x, z)) return new BlockPos(x, floor.anchorY(), z);
            }
        }
        return null;
    }

    private static Building roomAtColumn(Collection<Building> rooms, StructureFloor floor, int x, int z) {
        return rooms.stream()
                .filter(room -> room.getFloorId() == floor.id())
                .filter(room -> room.containsFloorColumn(x, z))
                .min(Comparator.comparingInt(Building::getId))
                .orElse(null);
    }

    record InteractionPosition(StructureFloor floor, Building room, boolean physical) {
    }

    int getMainRoomId() {
        return mainRoomId;
    }

    boolean isMainRoomAutomatic() {
        return mainRoomAutomatic;
    }

    void setManualMainRoom(int roomId) {
        mainRoomId = roomId;
        mainRoomAutomatic = false;
    }

    void setAutomaticMainRoom(int roomId) {
        mainRoomId = roomId;
        mainRoomAutomatic = true;
    }

    void clearMainRoom() {
        mainRoomId = -1;
        mainRoomAutomatic = true;
    }

    int getSurfaceReferenceY() {
        return surfaceReferenceY;
    }

    void setSurfaceReferenceY(int surfaceReferenceY) {
        this.surfaceReferenceY = surfaceReferenceY;
    }

    void setFloorNumber(int floorId, int floorNumber) {
        StructureFloor floor = floors.get(floorId);
        if (floor != null && floor.floorNumber() != floorNumber) {
            floors.put(floorId, floor.withFloorNumber(floorNumber));
        }
    }


    boolean applyScan(StructureScanner.Result scan, Collection<Building> rooms) {
        StructureFloorMatcher.Result match = StructureFloorMatcher.match(
                getFloors(), nextFloorId, scan.floors(), rooms).orElse(null);
        if (match == null) return false;

        floors.clear();
        floors.putAll(match.floors());
        nextFloorId = match.nextFloorId();
        source = scan.source();
        min = scan.min();
        max = scan.max();
        surfaceReferenceY = scan.surfaceReferenceY();
        return true;
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        int previousId = this.id;
        this.id = id;
        if (logicalBuildingId < 0 || logicalBuildingId == previousId) {
            logicalBuildingId = id;
        }
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

    @Override
    public boolean containsPos(Vec3i pos) {
        return physicalFloorAt(pos).isPresent();
    }

    public boolean containsPosHorizontally(Vec3i pos) {
        return pos != null && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
    }

    public boolean intersects(Structure other) {
        if (other == null
                || max.getX() < other.min.getX() || min.getX() > other.max.getX()
                || max.getY() < other.min.getY() || min.getY() > other.max.getY()
                || max.getZ() < other.min.getZ() || min.getZ() > other.max.getZ()) {
            return false;
        }
        for (StructureFloor floor : getFloors()) {
            for (StructureFloor candidate : other.getFloors()) {
                boolean verticalOverlap = floor.anchorY() < candidate.ceilingY()
                        && candidate.anchorY() < floor.ceilingY();
                if (verticalOverlap && floor.region() != null && candidate.region() != null
                        && floor.region().intersectionArea(candidate.region()) > 0) {
                    return true;
                }
            }
        }
        return false;
    }
}
