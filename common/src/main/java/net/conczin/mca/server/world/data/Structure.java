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

    public Structure(int id, BlockPos source, BlockPos min, BlockPos max, Collection<StructureFloor> floors) {
        this.id = id;
        this.source = source.immutable();
        this.min = min.immutable();
        this.max = max.immutable();
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

    Optional<InteractionPosition> resolveInteractionPosition(Level world, BlockPos pos, Collection<Building> structureRooms) {
        Collection<Building> localRooms = structureRooms == null ? List.of() : structureRooms;
        StructureFloor floor = physicalFloorAt(pos).orElse(null);
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
                roomAtColumn(localRooms, floor, roomX, roomZ)));
    }

    private BlockPos adjacentInteractionFloorCell(Level world, BlockPos pos, StructureFloor floor) {
        if (!StructureConnector.isPassageCell(world, pos)) return null;

        boolean supported = StructureScanner.isSupported(world, pos.getX(), pos.getY(), pos.getZ());
        boolean insideEnvelope = pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        if (supported && (!insideEnvelope || !StructureScanner.isWalkableAnchor(world, pos))) return null;

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

    record InteractionPosition(StructureFloor floor, Building room) {
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
        return true;
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

    @Override
    public boolean containsPos(Vec3i pos) {
        return physicalFloorAt(pos).isPresent();
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
