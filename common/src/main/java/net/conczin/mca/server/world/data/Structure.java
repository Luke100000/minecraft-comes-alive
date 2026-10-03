package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/** One persistent, independently rescannable physical section of a logical building. */
public final class Structure implements VillageBuilding {
    private static final Comparator<FloorCell> FLOOR_CELL_ORDER = Comparator
            .comparingInt((FloorCell resolved) -> resolved.cell().feet().getY())
            .thenComparingInt(resolved -> resolved.floor().anchorY())
            .thenComparingInt(resolved -> resolved.floor().id());

    private int id;
    private int logicalBuildingId;
    private boolean originGeometryApproximate;
    private BlockPos source;
    private BlockPos min;
    private BlockPos max;
    private final Map<Integer, StructureFloor> floors = new HashMap<>();

    public Structure(int id, BlockPos source, Collection<StructureFloor> floors) {
        this.id = id;
        logicalBuildingId = id;
        this.source = source.immutable();
        for (StructureFloor floor : floors) {
            putFloorUnique(floor);
        }
        recomputeBoundsFromFloors();
    }

    public Structure(CompoundTag tag) {
        id = tag.getInt("id").orElse(0);
        logicalBuildingId = tag.getInt("buildingId").orElse(id);
        originGeometryApproximate = tag.getBoolean("originGeometryApproximate").orElse(false);
        source = NbtHelper.decodeBlockPos(tag.get("source"));
        for (StructureFloor floor : NbtHelper.toList(tag.getList("floors").orElseGet(net.minecraft.nbt.ListTag::new),
                value -> StructureFloor.load((CompoundTag) value))) {
            putFloorUnique(floor);
        }
        recomputeBoundsFromFloors();
    }

    private void putFloorUnique(StructureFloor floor) {
        Objects.requireNonNull(floor, "floor");
        if (floors.putIfAbsent(floor.id(), floor) != null) {
            throw new IllegalArgumentException("Duplicate StructureFloor id " + floor.id()
                    + " in Structure " + id);
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("buildingId", getLogicalBuildingId());
        if (originGeometryApproximate) {
            tag.putBoolean("originGeometryApproximate", true);
        }
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

    void setOriginGeometryApproximate(boolean originGeometryApproximate) {
        this.originGeometryApproximate = originGeometryApproximate;
    }

    boolean hasOriginGeometryApproximation() {
        return originGeometryApproximate;
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
     * Semantic Floor boundary. Non-top Floors end at the next semantic Floor anchor;
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
        Optional<FloorCell> direct = getFloors().stream()
                .flatMap(floor -> floor.geometry().interactionCellAt(pos.getX(), pos.getY(), pos.getZ())
                        .stream().map(cell -> new FloorCell(floor, cell)))
                .max(FLOOR_CELL_ORDER);
        if (direct.isPresent()) return direct;

        return getFloors().stream()
                .flatMap(floor -> floor.connectors().stream()
                        .filter(marker -> marker.pos().equals(pos))
                        .flatMap(marker -> floor.geometry().cellAt(marker.floorCell())
                                .stream().map(cell -> new FloorCell(floor, cell))))
                .max(FLOOR_CELL_ORDER);
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
                .max(FLOOR_CELL_ORDER);
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

    /** Use exact geometry first, then the scan's connector evidence when a ladder occupies the column. */
    Optional<InteractionPosition> resolveVerticalSide(BlockPos pos, List<BlockPos> connectorColumn,
                                                      Collection<Building> structureRooms) {
        Collection<Building> localRooms = structureRooms == null ? List.of() : structureRooms;
        FloorCell physical = resolvePhysicalFloorCell(pos).orElse(null);
        if (physical != null) {
            return Optional.of(new InteractionPosition(physical.floor(),
                    roomAtCell(localRooms, physical.floor(), physical.cell().feet())));
        }
        if (connectorColumn.isEmpty()) return Optional.empty();
        StructureFloor floor = floorAtHeight(pos.getY()).orElse(null);
        if (floor == null) return Optional.empty();
        Building owner = null;
        boolean matched = false;
        for (FloorConnector.Marker marker : floor.connectors()) {
            if (!marker.type().vertical() || !connectorColumn.contains(marker.pos())) continue;
            matched = true;
            Building candidate = roomAtCell(localRooms, floor, marker.floorCell());
            if (candidate == null || owner != null && owner != candidate) {
                return Optional.of(new InteractionPosition(floor, null));
            }
            owner = candidate;
        }
        return matched ? Optional.of(new InteractionPosition(floor, owner)) : Optional.empty();
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
        copy.originGeometryApproximate = originGeometryApproximate;
        return copy;
    }

    boolean replaceFloorGeometry(int floorId, StructureFloor scannedFloor) {
        return replaceFloorGeometry(floorId, scannedFloor == null ? null : scannedFloor.geometry());
    }

    boolean replaceFloorGeometry(int floorId, FloorGeometry scannedFloor) {
        StructureFloor existing = floors.get(floorId);
        if (existing == null || scannedFloor == null) return false;
        floors.put(floorId, new StructureFloor(floorId, existing.floorNumber(), scannedFloor));
        if (floorId == 0) originGeometryApproximate = false;
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
        FloorGeometry.Bounds bounds = FloorGeometry.bounds(cells, 0);
        min = bounds.min();
        max = bounds.max();
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
                if (floor.overlapsFootprint(candidate)
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
