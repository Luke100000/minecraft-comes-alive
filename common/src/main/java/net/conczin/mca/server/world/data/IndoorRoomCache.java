package net.conczin.mca.server.world.data;

import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.util.MaxSizeHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;

import java.util.*;

/** Village-first O(1) room lookup with a small bounded TTL cache for unregistered shelter beds. */
public final class IndoorRoomCache {
    static final long TTL_TICKS = 100L;
    private static final long VALIDATION_TICKS = 40L;
    private static final int MAX_UNREGISTERED_ENTRIES = 64;
    private static final int MAX_SCAN_CELLS = 512;
    private static final int MAX_SCAN_RADIUS = 16;
    private static final int RETRY_SCAN_RADIUS = 32;
    private static final int MAX_HOUSE_FLOORS = 8;
    private static final int MAX_DISCOVERY_SCANS = 16;

    private final ServerLevel level;
    private final Map<BlockPos, CachedUnregistered> unregistered = new MaxSizeHashMap<>(MAX_UNREGISTERED_ENTRIES);
    private final Map<Integer, CachedRegisteredRoom> registeredRooms = new HashMap<>();
    private final Map<RegisteredHouse, RegisteredValidation> registeredValidations = new HashMap<>();

    IndoorRoomCache(ServerLevel level) {
        this.level = level;
    }

    public Optional<Room> resolve(BlockPos bed) {
        return resolveRoom(bed, bed);
    }

    public Optional<Room> resolveRoom(BlockPos bed, BlockPos occupant) {
        if (!isLoaded(bed) || !BedPoiCompatibility.isHomePoiState(level.getBlockState(bed))) {
            invalidate(bed);
            return Optional.empty();
        }
        for (Village village : VillageManager.get(level)) {
            Building bedRoom = village.findPhysicalRoomAt(bed).orElse(null);
            if (bedRoom == null) continue;
            if (occupant.equals(bed) || bedRoom.ownsFloorCell(occupant)) {
                return Optional.of(registeredRoomView(bedRoom));
            }
            int buildingId = village.getLogicalBuildingId(bedRoom.getStructureId());
            return village.findPhysicalRoomAt(occupant)
                    .filter(room -> village.getLogicalBuildingId(room.getStructureId()) == buildingId)
                    .map(this::registeredRoomView);
        }

        CachedUnregistered entry = resolveUnregistered(bed, new int[]{MAX_DISCOVERY_SCANS});
        return entry == null || entry.rooms.stream().noneMatch(room -> room.floorCells().contains(bed))
                ? Optional.empty()
                : entry.rooms.stream().filter(room -> room.floorCells().contains(occupant)).findFirst();
    }

    public Optional<House> resolveHouse(BlockPos bed) {
        if (!isLoaded(bed) || !BedPoiCompatibility.isHomePoiState(level.getBlockState(bed))) {
            return Optional.empty();
        }
        return resolveHouseValidBed(bed, new int[]{MAX_DISCOVERY_SCANS});
    }

    public List<House> resolveHouses(List<BlockPos> beds, int limit) {
        int[] budget = {MAX_DISCOVERY_SCANS};
        List<House> houses = new ArrayList<>();
        for (BlockPos bed : beds) {
            if (houses.size() >= limit) break;
            if (!isLoaded(bed) || !BedPoiCompatibility.isHomePoiState(level.getBlockState(bed))) continue;
            if (houses.stream().anyMatch(house -> house.floorCells().contains(bed))) continue;
            resolveHouseValidBed(bed, budget).ifPresent(house -> {
                if (house.registered == null || houses.stream().noneMatch(h -> house.registered.equals(h.registered))) {
                    houses.add(house);
                }
            });
        }
        return List.copyOf(houses);
    }

    public Optional<House> resolveHouseValidBed(BlockPos bed, int[] budget) {
        for (Village village : VillageManager.get(level)) {
            Building bedRoom = village.findPhysicalRoomAt(bed).orElse(null);
            if (bedRoom == null) continue;
            int buildingId = village.getLogicalBuildingId(bedRoom.getStructureId());
            if (buildingId < 0) continue;

            RegisteredHouse key = new RegisteredHouse(village.getId(), buildingId);
            List<FloorGeometry> geometry = new ArrayList<>();
            Set<BlockPos> floorCells = new HashSet<>();
            List<Map.Entry<Structure, StructureFloor>> persistedFloors = new ArrayList<>();
            for (Structure structure : village.getBuildingStructures(buildingId)) {
                for (StructureFloor floor : structure.getFloors()) {
                    persistedFloors.add(Map.entry(structure, floor));
                    geometry.add(floor.geometry());
                    floor.geometry().cells().forEach(cell -> floorCells.add(cell.feet()));
                }
            }
            Set<Room> rooms = new HashSet<>();
            village.getRooms()
                    .filter(room -> village.getLogicalBuildingId(room.getStructureId()) == buildingId)
                    .forEach(room -> rooms.add(registeredRoomView(room)));
            Set<BlockPos> bedHeads = new HashSet<>();
            boolean complete = collectLoadedBeds(floorCells, bedHeads)
                    && validateRegisteredHouse(key, persistedFloors, floorCells, bedHeads, budget);
            return Optional.of(new House(bed, key, bedHeads, floorCells, geometry, rooms, complete));
        }

        CachedUnregistered initial = resolveUnregistered(bed, budget);
        if (initial == null || initial.floor == null
                || initial.rooms.stream().noneMatch(room -> room.floorCells().contains(bed))) {
            return Optional.empty();
        }

        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        pending.add(bed);
        List<FloorGeometry> geometry = new ArrayList<>();
        Set<Room> rooms = new HashSet<>();
        Set<BlockPos> floorCells = new HashSet<>();
        Set<BlockPos> visited = new HashSet<>();
        boolean complete = true;

        while (!pending.isEmpty()) {
            BlockPos seed = pending.removeFirst();
            if (!visited.add(seed) || floorCells.contains(seed)) continue;
            if (geometry.size() >= MAX_HOUSE_FLOORS) {
                complete = false;
                break;
            }
            CachedUnregistered entry = seed.equals(bed) ? initial : resolveUnregistered(seed, budget);
            if (entry == null) {
                complete = false;
                continue;
            }
            if (entry.rooms.isEmpty() || entry.floor == null) {
                // Exterior connector exits are not another floor of the house.
                if (entry.failure != Building.validationResult.NOT_IN_BUILDING) complete = false;
                continue;
            }
            rooms.addAll(entry.rooms);
            if (geometry.contains(entry.floor)) continue;
            geometry.add(entry.floor);
            entry.floor.cells().forEach(cell -> floorCells.add(cell.feet()));
            pending.addAll(entry.adjacentFloorSeeds);

            NonLoadingBlocks blocks = nonLoadingBlocks();
            FloorCeilingResolver ceilings = new FloorCeilingResolver(blocks);
            for (FloorConnector.Marker connector : entry.floor.connectorMarkers()) {
                if (!connector.type().vertical()) continue;
                if (!isLoaded(connector.pos())) {
                    complete = false;
                    continue;
                }
                for (BlockPos candidate : StructureConnector.verticalHandoffCandidates(blocks, connector.pos())) {
                    if (!isLoaded(candidate)) {
                        complete = false;
                    } else if (entry.floor.physicalCellAt(candidate.getX(), candidate.getY(), candidate.getZ()).isEmpty()
                            && SelectedFloorScanner.inspectSurfaceCell(blocks, candidate, ceilings).isPresent()) {
                        pending.add(candidate);
                    }
                }
            }
            if (blocks.missingColumn) complete = false;
        }

        Set<BlockPos> bedHeads = new HashSet<>();
        if (!collectLoadedBeds(floorCells, bedHeads)) {
            complete = false;
        }
        return Optional.of(new House(bed, null, bedHeads, floorCells, geometry, rooms, complete));
    }

    private Optional<RegisteredHouse> registeredHouse(BlockPos pos) {
        for (Village village : VillageManager.get(level)) {
            var room = village.findPhysicalRoomAt(pos);
            if (room.isPresent()) {
                int building = village.getLogicalBuildingId(room.orElseThrow().getStructureId());
                if (building >= 0) return Optional.of(new RegisteredHouse(village.getId(), building));
            }
        }
        return Optional.empty();
    }

    private boolean validateRegisteredHouse(RegisteredHouse key,
                                            List<Map.Entry<Structure, StructureFloor>> persistedFloors,
                                            Set<BlockPos> floorCells,
                                            Set<BlockPos> bedHeads,
                                            int[] budget) {
        for (BlockPos head : bedHeads) {
            RegisteredHouse other = registeredHouse(head).orElse(null);
            if (other != null && !key.equals(other)) {
                return false;
            }
        }
        long now = level.getGameTime();
        RegisteredValidation cached = registeredValidations.get(key);
        if (cached != null && now < cached.expiresAt && cached.floorCells.equals(floorCells)) {
            return cached.valid;
        }
        if (budget[0] < persistedFloors.size()) {
            return false;
        }
        boolean valid = !persistedFloors.isEmpty();
        for (var entry : persistedFloors) {
            Structure structure = entry.getKey();
            FloorGeometry expected = entry.getValue().geometry();
            BlockPos seed = expected.cells().stream()
                    .map(FloorGeometry.Cell::feet)
                    .min(Comparator.comparingDouble(pos -> pos.distSqr(structure.getSource())))
                    .orElse(null);
            if (seed == null || !isLoaded(seed)) {
                valid = false;
                break;
            }
            if (budget[0] <= 0) return false;
            budget[0]--;
            NonLoadingBlocks blocks = nonLoadingBlocks();
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(blocks, seed, MAX_SCAN_CELLS, MAX_SCAN_RADIUS);
            if (scan.result() == Building.validationResult.SIZE_LIMIT && !blocks.missingColumn) {
                if (budget[0] <= 0) return false;
                budget[0]--;
                scan = SelectedFloorScanner.scan(blocks, seed, MAX_SCAN_CELLS, RETRY_SCAN_RADIUS);
            }
            if (blocks.missingColumn || scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
                valid = false;
                break;
            }
            for (FloorGeometry.Cell cell : expected.cells()) {
                if (scan.floor().cellAt(cell.feet()).isEmpty()) {
                    valid = false;
                    break;
                }
            }
            if (!valid) break;
        }
        registeredValidations.put(key, new RegisteredValidation(Set.copyOf(floorCells), valid, now + VALIDATION_TICKS));
        return valid;
    }

    public void invalidate(BlockPos pos) {
        if (pos == null) {
            unregistered.clear();
            registeredValidations.clear();
            return;
        }
        unregistered.remove(pos);
        unregistered.entrySet().removeIf(entry ->
                entry.getValue().floor != null && entry.getValue().floor.cellAt(pos).isPresent());
        registeredValidations.entrySet().removeIf(entry -> entry.getValue().floorCells.contains(pos));
    }

    void tick(long now) {
        if (now % TTL_TICKS == 0) {
            unregistered.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt || !isLoaded(entry.getKey()));
            registeredValidations.entrySet().removeIf(entry -> now >= entry.getValue().expiresAt);
            if (registeredRooms.size() > MAX_UNREGISTERED_ENTRIES) {
                registeredRooms.clear();
            }
        }
    }

    private Room registeredRoomView(Building room) {
        Set<BlockPos> cells = room.getFloorCells();
        CachedRegisteredRoom cached = registeredRooms.get(room.getId());
        if (cached != null && cached.sourceCells == cells) {
            return cached.room;
        }
        Room created = new Room(cells);
        registeredRooms.put(room.getId(), new CachedRegisteredRoom(cells, created));
        return created;
    }

    private CachedUnregistered resolveUnregistered(BlockPos source, int[] budget) {
        long now = level.getGameTime();
        CachedUnregistered direct = unregistered.get(source);
        if (direct != null) {
            if (now < direct.expiresAt && isLoaded(source)) return direct;
            unregistered.remove(source);
        }
        for (var iterator = unregistered.entrySet().iterator(); iterator.hasNext(); ) {
            var candidateEntry = iterator.next();
            CachedUnregistered candidate = candidateEntry.getValue();
            if (now >= candidate.expiresAt || !isLoaded(candidateEntry.getKey())) {
                iterator.remove();
                continue;
            }
            if (candidate.floor != null && candidate.floor.cellAt(source).isPresent()) {
                return candidate;
            }
        }
        if (budget[0]-- <= 0) {
            budget[0] = 0;
            return null;
        }
        CachedUnregistered discovered = discoverUnregistered(source, now, budget);
        unregistered.put(source.immutable(), discovered);
        return discovered;
    }

    private CachedUnregistered discoverUnregistered(BlockPos source, long now, int[] budget) {
        NonLoadingBlocks blocks = nonLoadingBlocks();
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(blocks, source, MAX_SCAN_CELLS, MAX_SCAN_RADIUS);
        if (scan.result() == Building.validationResult.SIZE_LIMIT && !blocks.missingColumn && budget[0] > 0) {
            budget[0]--;
            scan = SelectedFloorScanner.scan(blocks, source, MAX_SCAN_CELLS, RETRY_SCAN_RADIUS);
        }
        if (blocks.missingColumn || scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            Building.validationResult failure = blocks.missingColumn
                    ? Building.validationResult.BLOCK_LIMIT : scan.result();
            return new CachedUnregistered(null, Set.of(), List.of(), failure, now + TTL_TICKS);
        }
        List<Room> rooms = StructureScanner.components(blocks, scan).stream()
                .filter(component -> component.area() >= RoomPartitioner.MIN_ROOM_AREA)
                .map(component -> new Room(component.floorCells()))
                .toList();
        if (blocks.missingColumn) {
            return new CachedUnregistered(null, Set.of(), List.of(),
                    Building.validationResult.BLOCK_LIMIT, now + TTL_TICKS);
        }
        return new CachedUnregistered(scan.floor(), scan.adjacentFloorSeeds(), rooms,
                Building.validationResult.SUCCESS, now + TTL_TICKS);
    }

    private boolean collectLoadedBeds(Set<BlockPos> floorCells, Set<BlockPos> bedHeads) {
        boolean complete = true;
        for (BlockPos cell : floorCells) {
            if (!isLoaded(cell)) {
                complete = false;
                continue;
            }
            if (BedPoiCompatibility.isHomePoiState(level.getBlockState(cell))) {
                bedHeads.add(cell);
            }
        }
        return complete;
    }

    private boolean isLoaded(BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    NonLoadingBlocks nonLoadingBlocks() {
        return new NonLoadingBlocks();
    }

    final class NonLoadingBlocks implements SelectedFloorScanner.LoadedColumns, FloorCeilingResolver.ColumnHeight {
        private boolean missingColumn;

        @Override
        public boolean hasColumn(BlockPos pos) {
            return isLoaded(pos);
        }

        @Override
        public void recordUnknownBoundary(BlockPos pos) {
            missingColumn = true;
        }

        @Override
        public int highestBlockY(BlockPos pos) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                missingColumn = true;
                return level.getMinY() - 1;
            }
            return chunk.getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX() & 15, pos.getZ() & 15);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                missingColumn = true;
                return Blocks.AIR.defaultBlockState();
            }
            return chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                missingColumn = true;
                return null;
            }
            return chunk.getBlockEntity(pos);
        }

        @Override
        public int getHeight() { return level.getHeight(); }

        @Override
        public int getMinY() { return level.getMinY(); }
    }

    public record Room(Set<BlockPos> floorCells) {
        public Room {
            floorCells = Set.copyOf(floorCells);
        }
    }

    public static final class House {
        private final BlockPos anchor;
        private final RegisteredHouse registered;
        private final Set<BlockPos> bedHeads;
        private final Set<BlockPos> floorCells;
        private final List<FloorGeometry> geometry;
        private final Set<Room> rooms;
        private final boolean membershipKnown;

        private House(BlockPos anchor, RegisteredHouse registered, Set<BlockPos> bedHeads,
                      Set<BlockPos> floorCells, List<FloorGeometry> geometry, Set<Room> rooms, boolean membershipKnown) {
            this.anchor = anchor.immutable();
            this.registered = registered;
            this.bedHeads = Set.copyOf(bedHeads);
            this.floorCells = Set.copyOf(floorCells);
            this.geometry = List.copyOf(geometry);
            this.rooms = Set.copyOf(rooms);
            this.membershipKnown = membershipKnown;
        }

        public BlockPos anchor() { return anchor; }
        public Set<BlockPos> bedHeads() { return bedHeads; }
        public Set<BlockPos> floorCells() { return floorCells; }
        public Set<Room> rooms() { return rooms; }
        public boolean membershipKnown() { return membershipKnown; }

        public BlockPos anchorFor(BlockPos endpoint) {
            return rooms.stream().filter(room -> room.floorCells().contains(endpoint))
                    .flatMap(room -> bedHeads.stream().filter(room.floorCells()::contains))
                    .min(Comparator.comparingDouble(bed -> bed.distSqr(endpoint))).orElse(anchor);
        }

        public boolean contains(BlockPos position) {
            return geometry.stream().anyMatch(floor -> floor.physicalCellAt(
                    position.getX(), position.getY(), position.getZ()).isPresent());
        }
    }

    private record RegisteredHouse(int village, int building) { }
    private record RegisteredValidation(Set<BlockPos> floorCells, boolean valid, long expiresAt) { }
    private record CachedRegisteredRoom(Set<BlockPos> sourceCells, Room room) { }
    private record CachedUnregistered(FloorGeometry floor, Set<BlockPos> adjacentFloorSeeds,
                                      List<Room> rooms, Building.validationResult failure, long expiresAt) { }
}
