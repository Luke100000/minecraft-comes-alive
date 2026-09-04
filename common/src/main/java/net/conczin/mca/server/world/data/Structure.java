package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** One persistent, independently rescannable physical section of a logical building. */
public final class Structure implements VillageBuilding {
    private int id;
    private int logicalBuildingId;
    private int nextFloorId;
    private BlockPos source;
    private BlockPos min;
    private BlockPos max;
    private final Map<Integer, StructureFloor> floors = new HashMap<>();

    public Structure(int id, BlockPos source, Collection<StructureFloor> floors) {
        this.id = id;
        logicalBuildingId = id;
        this.source = source.immutable();
        for (StructureFloor floor : floors) {
            this.floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
        recomputeBoundsFromFloors();
    }

    public Structure(CompoundTag tag) {
        id = tag.getInt("id");
        logicalBuildingId = tag.getInt("buildingId");
        nextFloorId = tag.getInt("nextFloorId");
        source = NbtHelper.decodeBlockPos(tag.get("source"));
        for (StructureFloor floor : NbtHelper.toList(tag.getList("floors", Tag.TAG_COMPOUND),
                value -> StructureFloor.load((CompoundTag) value))) {
            floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
        recomputeBoundsFromFloors();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("buildingId", getLogicalBuildingId());
        tag.putInt("nextFloorId", nextFloorId);
        tag.put("source", NbtHelper.encodeBlockPos(source));
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
    private Optional<StructureFloor> resolveFloor(int queryY) {
        return getFloors().stream()
                .filter(floor -> floor.anchorY() <= queryY)
                .max(Comparator.comparingInt(StructureFloor::anchorY));
    }

    /** Exact vertical-band membership; unlike resolveFloor, this never falls through above a Floor ceiling. */
    private Optional<StructureFloor> floorAtHeight(int queryY) {
        return resolveFloor(queryY).filter(floor -> queryY < semanticCeilingY(floor));
    }

    /**
     * Semantic storey boundary. Non-top Floors end at the next semantic Floor anchor;
     * the top Floor ends at the highest physical ceiling observed in its exact geometry.
     */
    int semanticCeilingY(StructureFloor floor) {
        if (floor == null) return Integer.MIN_VALUE;
        return getFloors().stream()
                .filter(candidate -> candidate.anchorY() > floor.anchorY())
                .mapToInt(StructureFloor::anchorY)
                .min()
                .orElse(floor.maxPhysicalCeilingY());
    }

    /** Direct positions resolve by vertical Floor band. Connector handoffs are resolved separately. */
    Optional<StructureFloor> resolveFloorAt(BlockPos pos) {
        if (pos == null) return Optional.empty();
        return floorAtHeight(pos.getY());
    }

    private Optional<FloorCell> resolveInteractionFloorCell(BlockPos pos) {
        if (pos == null) return Optional.empty();
        return getFloors().stream()
                .flatMap(floor -> floor.geometry().interactionCellAt(pos.getX(), pos.getY(), pos.getZ())
                        .stream().map(cell -> new FloorCell(floor, cell)))
                .max(Comparator
                        .comparingInt((FloorCell resolved) -> resolved.cell().feet().getY())
                        .thenComparingInt(resolved -> resolved.floor().anchorY())
                        .thenComparingInt(resolved -> resolved.floor().id()));
    }

    Optional<FloorCell> resolvePhysicalFloorCell(Vec3i pos) {
        if (pos == null
                || pos.getX() < min.getX() || pos.getX() > max.getX()
                || pos.getY() < min.getY() || pos.getY() > max.getY()
                || pos.getZ() < min.getZ() || pos.getZ() > max.getZ()) {
            return Optional.empty();
        }
        return getFloors().stream()
                .flatMap(floor -> floor.geometry().physicalCellAt(pos.getX(), pos.getY(), pos.getZ())
                        .stream().map(cell -> new FloorCell(floor, cell)))
                .max(Comparator
                        .comparingInt((FloorCell resolved) -> resolved.cell().feet().getY())
                        .thenComparingInt(resolved -> resolved.floor().anchorY())
                        .thenComparingInt(resolved -> resolved.floor().id()));
    }

    /** Exact physical membership resolves through exact Floor cells, never a 2D extrusion. */
    Optional<StructureFloor> physicalFloorAt(Vec3i pos) {
        return resolvePhysicalFloorCell(pos).map(FloorCell::floor);
    }

    Optional<InteractionPosition> resolveInteractionPosition(BlockPos pos,
                                                             Collection<Building> structureRooms) {
        Collection<Building> localRooms = structureRooms == null ? List.of() : structureRooms;
        FloorCell resolved = resolveInteractionFloorCell(pos).orElse(null);
        if (resolved == null) return Optional.empty();
        return Optional.of(new InteractionPosition(
                resolved.floor(), roomAtCell(localRooms, resolved.floor(), resolved.cell().feet())));
    }

    private static Building roomAtCell(Collection<Building> rooms, StructureFloor floor, BlockPos feet) {
        return rooms.stream()
                .filter(room -> room.getFloorId() == floor.id())
                .filter(room -> room.ownsFloorCell(feet))
                .min(Comparator.comparingInt(Building::getId))
                .orElse(null);
    }

    record InteractionPosition(StructureFloor floor,
                               Building room) {
    }

    record FloorCell(StructureFloor floor, FloorGeometry.Cell cell) {
    }

    void setFloorNumber(int floorId, int floorNumber) {
        StructureFloor floor = floors.get(floorId);
        if (floor != null && floor.floorNumber() != floorNumber) {
            floors.put(floorId, floor.withFloorNumber(floorNumber));
        }
    }

    Structure copy() {
        Structure copy = new Structure(id, source, getFloors());
        copy.logicalBuildingId = logicalBuildingId;
        copy.nextFloorId = nextFloorId;
        return copy;
    }

    boolean replaceFloorGeometry(int floorId, StructureFloor scannedFloor) {
        StructureFloor existing = floors.get(floorId);
        if (existing == null || scannedFloor == null) return false;
        floors.put(floorId, new StructureFloor(floorId, existing.floorNumber(), scannedFloor.geometry()));
        recomputeBoundsFromFloors();
        return true;
    }

    boolean removeFloor(int floorId) {
        if (floors.remove(floorId) == null) return false;
        if (!floors.isEmpty()) recomputeBoundsFromFloors();
        return true;
    }

    private void recomputeBoundsFromFloors() {
        List<StructureFloor> current = getFloors();
        if (current.isEmpty()) return;

        List<FloorGeometry.Cell> cells = current.stream()
                .flatMap(floor -> floor.geometry().cells().stream())
                .toList();
        if (cells.isEmpty()) return;

        int minX = cells.stream().mapToInt(cell -> cell.feet().getX()).min().orElse(source.getX());
        int minZ = cells.stream().mapToInt(cell -> cell.feet().getZ()).min().orElse(source.getZ());
        int maxX = cells.stream().mapToInt(cell -> cell.feet().getX()).max().orElse(source.getX());
        int maxZ = cells.stream().mapToInt(cell -> cell.feet().getZ()).max().orElse(source.getZ());
        int minY = cells.stream().mapToInt(cell -> cell.feet().getY()).min().orElse(source.getY());
        int maxY = cells.stream().mapToInt(cell -> cell.ceilingY() - 1).max().orElse(source.getY());
        min = new BlockPos(minX, minY, minZ);
        max = new BlockPos(maxX, maxY, maxZ);
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
                if (floor.region().intersectionArea(candidate.region()) > 0
                        && exactGeometryOverlaps(floor.geometry(), candidate.geometry())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean exactGeometryOverlaps(FloorGeometry first, FloorGeometry second) {
        for (FloorGeometry.Cell cell : first.cells()) {
            for (FloorGeometry.Cell candidate : second.cellsAtColumn(
                    cell.feet().getX(), cell.feet().getZ())) {
                if (cell.feet().getY() < candidate.ceilingY()
                        && candidate.feet().getY() < cell.ceilingY()) {
                    return true;
                }
            }
        }
        return false;
    }
}
