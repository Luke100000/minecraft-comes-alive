package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

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

    public Structure(int id, BlockPos source, BlockPos min, BlockPos max, Collection<StructureFloor> floors) {
        this.id = id;
        logicalBuildingId = id;
        this.source = source.immutable();
        this.min = min.immutable();
        this.max = max.immutable();
        for (StructureFloor floor : floors) {
            this.floors.put(floor.id(), floor);
            nextFloorId = Math.max(nextFloorId, floor.id() + 1);
        }
        recomputeBoundsFromFloors();
    }

    public Structure(CompoundTag tag) {
        id = tag.getInt("id");
        logicalBuildingId = tag.contains("buildingId") ? tag.getInt("buildingId") : id;
        nextFloorId = tag.getInt("nextFloorId");
        source = NbtHelper.decodeBlockPos(tag.get("source"));
        min = NbtHelper.decodeBlockPos(tag.get("min"));
        max = NbtHelper.decodeBlockPos(tag.get("max"));
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
    private Optional<StructureFloor> resolveFloor(int queryY) {
        return getFloors().stream()
                .filter(floor -> floor.anchorY() <= queryY)
                .max(Comparator.comparingInt(StructureFloor::anchorY));
    }

    /** Exact vertical-band membership; unlike resolveFloor, this never falls through above a Floor ceiling. */
    private Optional<StructureFloor> floorAtHeight(int queryY) {
        return resolveFloor(queryY).filter(floor -> queryY < floor.ceilingY());
    }

    /** Chooses the nearest Floor whose exact footprint contains the query X/Z column. */
    private Optional<StructureFloor> nearestFloorAtColumn(Vec3i pos) {
        if (pos == null || !containsPosHorizontally(pos)) return Optional.empty();
        return getFloors().stream()
                .filter(floor -> floor.contains(pos.getX(), pos.getZ()))
                .min(Comparator.comparingInt((StructureFloor floor) -> verticalDistance(floor, pos.getY()))
                        .thenComparingInt(StructureFloor::anchorY)
                        .thenComparingInt(StructureFloor::id));
    }

    /** One floor-resolution rule for direct positions. Vertical connectors are ranked separately by landing proximity. */
    Optional<StructureFloor> resolveFloorAt(BlockPos pos) {
        if (pos == null) return Optional.empty();
        return nearestFloorAtColumn(pos).or(() -> floorAtHeight(pos.getY()));
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
        BlockState state = world.getBlockState(pos);
        if (StructureConnector.isVerticalInteraction(world, pos)) {
            StructureFloor connectorFloor = null;
            BlockPos connectorFloorCell = null;
            for (StructureFloor candidate : getFloors()) {
                BlockPos floorCell = StructureConnector.resolveVerticalFloorCell(world, candidate, pos);
                if (floorCell == null) continue;
                if (connectorFloor == null
                        || Math.abs(candidate.anchorY() - pos.getY())
                        < Math.abs(connectorFloor.anchorY() - pos.getY())
                        || Math.abs(candidate.anchorY() - pos.getY())
                        == Math.abs(connectorFloor.anchorY() - pos.getY())
                        && candidate.anchorY() < connectorFloor.anchorY()) {
                    connectorFloor = candidate;
                    connectorFloorCell = floorCell;
                }
            }
            if (connectorFloor == null) return Optional.empty();
            return Optional.of(new InteractionPosition(
                    connectorFloor,
                    roomAtColumn(localRooms, connectorFloor, connectorFloorCell.getX(), connectorFloorCell.getZ()),
                    InteractionKind.VERTICAL_CONNECTOR,
                    Math.abs(connectorFloor.anchorY() - pos.getY())));
        }

        StructureFloor physicalFloor = physicalFloorAt(pos).orElse(null);
        if (physicalFloor != null) {
            return Optional.of(new InteractionPosition(
                    physicalFloor,
                    roomAtColumn(localRooms, physicalFloor, pos.getX(), pos.getZ()),
                    InteractionKind.PHYSICAL,
                    0));
        }

        StructureFloor floor = resolveFloorAt(pos).orElse(null);
        if (floor == null) return Optional.empty();

        if (StructureConnector.isHorizontalBoundary(state)) {
            Building connectorOwner = roomAtColumn(localRooms, floor, pos.getX(), pos.getZ());
            if (connectorOwner != null) {
                return Optional.of(new InteractionPosition(
                        floor, connectorOwner, InteractionKind.HORIZONTAL_CONNECTOR,
                        verticalDistance(floor, pos.getY())));
            }
        }

        int distance = verticalDistance(floor, pos.getY());
        if (floor.contains(pos.getX(), pos.getZ())
                && distance == 1
                && StructureScanner.resolveStandingSurfaceSeed(world, pos).isPresent()) {
            return Optional.of(new InteractionPosition(
                    floor,
                    roomAtColumn(localRooms, floor, pos.getX(), pos.getZ()),
                    InteractionKind.LANDING_HANDOFF,
                    distance));
        }

        BlockPos floorCell = StructureConnector.resolveFloorCell(world, this, floor, pos);
        if (floorCell == null) return Optional.empty();
        return Optional.of(new InteractionPosition(floor,
                roomAtColumn(localRooms, floor, floorCell.getX(), floorCell.getZ()),
                InteractionKind.HORIZONTAL_CONNECTOR,
                verticalDistance(floor, pos.getY())));
    }

    private static Building roomAtColumn(Collection<Building> rooms, StructureFloor floor, int x, int z) {
        return rooms.stream()
                .filter(room -> room.getFloorId() == floor.id())
                .filter(room -> room.containsFloorColumn(x, z))
                .min(Comparator.comparingInt(Building::getId))
                .orElse(null);
    }

    enum InteractionKind {
        PHYSICAL(0),
        HORIZONTAL_CONNECTOR(1),
        VERTICAL_CONNECTOR(2),
        LANDING_HANDOFF(3);

        private final int priority;

        InteractionKind(int priority) {
            this.priority = priority;
        }

        int priority() {
            return priority;
        }
    }

    record InteractionPosition(StructureFloor floor,
                               Building room,
                               InteractionKind kind,
                               int verticalDistance) {
        boolean physical() {
            return kind == InteractionKind.PHYSICAL;
        }

        boolean verticalConnector() {
            return kind == InteractionKind.VERTICAL_CONNECTOR;
        }
    }

    void setFloorNumber(int floorId, int floorNumber) {
        StructureFloor floor = floors.get(floorId);
        if (floor != null && floor.floorNumber() != floorNumber) {
            floors.put(floorId, floor.withFloorNumber(floorNumber));
        }
    }

    Structure copy() {
        Structure copy = new Structure(id, source, min, max, getFloors());
        copy.logicalBuildingId = logicalBuildingId;
        copy.nextFloorId = nextFloorId;
        return copy;
    }

    boolean replaceFloorGeometry(int floorId, StructureFloor scannedFloor) {
        StructureFloor existing = floors.get(floorId);
        if (existing == null || scannedFloor == null || scannedFloor.region() == null) return false;
        floors.put(floorId, new StructureFloor(
                floorId,
                scannedFloor.anchorY(),
                scannedFloor.ceilingY(),
                existing.floorNumber(),
                scannedFloor.region(),
                scannedFloor.connectors()));
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

        List<BlockPos> cells = current.stream()
                .filter(floor -> floor.region() != null)
                .flatMap(floor -> floor.region().cells().stream())
                .toList();
        if (cells.isEmpty()) return;

        int minX = cells.stream().mapToInt(BlockPos::getX).min().orElse(source.getX());
        int minZ = cells.stream().mapToInt(BlockPos::getZ).min().orElse(source.getZ());
        int maxX = cells.stream().mapToInt(BlockPos::getX).max().orElse(source.getX());
        int maxZ = cells.stream().mapToInt(BlockPos::getZ).max().orElse(source.getZ());
        int minY = current.stream().mapToInt(StructureFloor::anchorY).min().orElse(source.getY());
        int maxY = current.stream().mapToInt(floor -> floor.ceilingY() - 1).max().orElse(source.getY());
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

    boolean containsEnvelope(Vec3i pos) {
        return pos != null
                && pos.getX() >= min.getX() && pos.getX() <= max.getX()
                && pos.getY() >= min.getY() && pos.getY() <= max.getY()
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
