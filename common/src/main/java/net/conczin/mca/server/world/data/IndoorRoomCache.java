package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.BedDebugLog;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Transient, server-thread floor discovery shared by villagers in one level. */
public final class IndoorRoomCache {
    private static final int MAX_ENTRIES = 128;
    private static final int MAX_SCAN_CELLS = 512;
    private static final int MAX_SCAN_RADIUS = 16;
    private static final int MAX_OBSERVED_BLOCKS = 8192;
    private static final int MAX_TOTAL_OBSERVED_BLOCKS = 65536;
    private static final int MAX_HOUSE_FLOORS = 8;
    private static final int MAX_DISCOVERY_SCANS = 20;
    private static final long IDLE_TICKS = 1200L;
    private static final long FAILED_RETRY_TICKS = 200L;
    private static final long VALIDATION_TICKS = 40L;

    private final ServerLevel level;
    private final LinkedHashMap<BlockPos, Entry> entries = new LinkedHashMap<>(16, 0.75F, true);

    IndoorRoomCache(ServerLevel level) {
        this.level = level;
    }

    public Optional<Room> resolve(BlockPos bed) {
        if (!isLoaded(bed) || !BedPoiCompatibility.isHomePoiState(level.getBlockState(bed))) {
            entries.remove(bed);
            return Optional.empty();
        }
        Entry entry = resolveFloor(bed, new ScanBudget());
        return entry == null ? Optional.empty() : entry.rooms.stream()
                .filter(room -> room.floorCells().contains(bed)).findFirst();
    }

    public Optional<House> resolveHouse(BlockPos bed) {
        return resolveHouse(bed, new ScanBudget());
    }

    /** One discovery budget for an admission attempt; beds are grouped before the house limit. */
    public List<House> resolveHouses(List<BlockPos> beds, int limit) {
        ScanBudget budget = new ScanBudget();
        List<House> houses = new ArrayList<>();
        for (BlockPos bed : beds) {
            RegisteredHouse registered = registeredHouse(bed).orElse(null);
            if (houses.stream().anyMatch(house -> house.floorCells().contains(bed)
                    || registered != null && registered.equals(house.registered))) continue;
            if (houses.size() >= limit) break;
            resolveHouse(bed, budget).ifPresent(houses::add);
        }
        return List.copyOf(houses);
    }

    private Optional<House> resolveHouse(BlockPos bed, ScanBudget budget) {
        if (!isLoaded(bed) || !BedPoiCompatibility.isHomePoiState(level.getBlockState(bed))) return Optional.empty();
        Entry initial = resolveFloor(bed, budget);
        if (initial == null || initial.rooms.stream().noneMatch(room -> room.floorCells().contains(bed))) return Optional.empty();

        RegisteredHouse registered = registeredHouse(bed).orElse(null);
        ArrayDeque<BlockPos> pending = new ArrayDeque<>();
        Set<BlockPos> required = new HashSet<>();
        pending.add(bed);
        if (registered != null) {
            Village village = VillageManager.get(level).getOrEmpty(registered.village).orElseThrow();
            for (Structure structure : village.getStructures().values()) {
                if (village.getLogicalBuildingId(structure.getId()) != registered.building) continue;
                for (StructureFloor floor : structure.getFloors()) {
                    floor.geometry().cells().forEach(cell -> required.add(cell.feet()));
                    pending.add(floor.geometry().cells().stream().map(FloorGeometry.Cell::feet)
                            .min(java.util.Comparator.comparingDouble(pos -> pos.distSqr(structure.getSource()))).orElseThrow());
                }
            }
            village.getRooms().filter(room -> village.getLogicalBuildingId(room.getStructureId()) == registered.building)
                    .forEach(room -> pending.add(room.getSourceBlock()));
        }

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
            Entry entry = seed.equals(bed) ? initial : resolveFloor(seed, budget);
            if (entry == null) {
                complete = false;
                continue;
            }
            if (entry.rooms.isEmpty()) {
                // Exterior connector exits are not another floor of the house.
                if (entry.failure != Building.validationResult.NOT_IN_BUILDING) complete = false;
                continue;
            }
            rooms.addAll(entry.rooms);
            if (entry.scan == null) {
                complete = false;
                entry.rooms.forEach(room -> floorCells.addAll(room.floorCells()));
                continue;
            }
            FloorGeometry floor = entry.scan.floor();
            if (geometry.contains(floor)) continue;
            geometry.add(floor);
            floor.cells().forEach(cell -> floorCells.add(cell.feet()));
            pending.addAll(entry.scan.adjacentFloorSeeds());
            LoadedBlocks blocks = new LoadedBlocks();
            FloorCeilingResolver ceilings = new FloorCeilingResolver(blocks);
            for (FloorConnector.Marker connector : floor.connectorMarkers()) {
                if (connector.type().vertical()) {
                    if (!isLoaded(connector.pos())) {
                        complete = false;
                        continue;
                    }
                    for (BlockPos candidate : StructureConnector.verticalHandoffCandidates(blocks, connector.pos())) {
                        if (!isLoaded(candidate)) {
                            complete = false;
                        } else if (floor.physicalCellAt(candidate.getX(), candidate.getY(), candidate.getZ()).isEmpty()
                                && SelectedFloorScanner.inspectSurfaceCell(blocks, candidate, ceilings).isPresent()) {
                            pending.add(candidate);
                        }
                    }
                }
            }
            if (blocks.firstMissingBlock != null) complete = false;
        }
        if (!floorCells.containsAll(required)) complete = false;
        Set<BlockPos> beds = new HashSet<>();
        for (BlockPos cell : floorCells) {
            if (!isLoaded(cell)) {
                complete = false;
                continue;
            }
            if (BedPoiCompatibility.isHomePoiState(level.getBlockState(cell))) {
                beds.add(cell);
                RegisteredHouse other = registeredHouse(cell).orElse(null);
                if (registered != null && other != null && !registered.equals(other)) complete = false;
            }
        }
        return Optional.of(new House(bed, registered, beds, floorCells, geometry, rooms, complete));
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

    private Entry resolveFloor(BlockPos source, ScanBudget budget) {
        long now = level.getGameTime();
        if (!isLoaded(source)) return null;
        Entry entry = entries.get(source);
        if (entry != null && entry.isCurrent(now)) {
            entry.lastUsed = now;
            return entry;
        }
        entries.remove(source);
        for (var iterator = entries.values().iterator(); iterator.hasNext();) {
            Entry candidate = iterator.next();
            boolean contains = candidate.scan != null && candidate.scan.floor().cellAt(source).isPresent()
                    || candidate.rooms.stream().anyMatch(room -> room.floorCells().contains(source));
            if (!contains) continue;
            if (!candidate.isCurrent(now)) {
                iterator.remove();
                continue;
            }
            candidate.lastUsed = now;
            // Return immediately after changing access order; do not advance this iterator again.
            entries.get(candidate.source);
            return candidate;
        }
        if (budget.remaining-- <= 0) {
            budget.remaining = 0;
            return null;
        }
        entry = discover(source, now);
        int observedCount = entries.values().stream().mapToInt(cached -> cached.observed.size()).sum();
        while (!entries.isEmpty() && (entries.size() >= MAX_ENTRIES
                || observedCount + entry.observed.size() > MAX_TOTAL_OBSERVED_BLOCKS)) {
            observedCount -= entries.pollFirstEntry().getValue().observed.size();
        }
        entries.put(source.immutable(), entry);
        return entry;
    }

    void tick(long now) {
        if (now % FAILED_RETRY_TICKS == 0) {
            entries.values().removeIf(entry -> now - entry.lastUsed >= IDLE_TICKS || !isLoaded(entry.source));
        }
    }

    private Entry discover(BlockPos source, long now) {
        LoadedBlocks blocks = new LoadedBlocks();
        SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(blocks, source, MAX_SCAN_CELLS, MAX_SCAN_RADIUS);
        if (blocks.firstMissingBlock != null) return failed(source, now, Building.validationResult.BLOCK_LIMIT,
                "FLOOR_SCAN", blocks.firstMissingBlock, 0);
        if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
            return failed(source, now, scan.result(), "FLOOR_SCAN", null, 0);
        }
        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(blocks, scan).stream()
                .filter(room -> room.area() >= RoomPartitioner.MIN_ROOM_AREA).toList();
        if (blocks.firstMissingBlock != null) return failed(source, now, Building.validationResult.BLOCK_LIMIT,
                "ROOM_PARTITION", blocks.firstMissingBlock, 0);
        List<Room> rooms = components.stream().map(room -> new Room(room.floorCells())).toList();
        Map<BlockPos, BlockState> observed = new HashMap<>();
        if (observe(scan.floor().cells(), observed, blocks)) {
            return new Entry(source, scan, rooms, Map.copyOf(observed), now, Building.validationResult.SUCCESS);
        }

        // Keep the existing room-sized shelter access if a larger floor exceeds observation limits.
        RoomPartitioner.Component selected = RoomPartitioner.select(source, scan.floor(), components);
        if (selected != null) {
            observed.clear();
            blocks.firstMissingBlock = null;
            if (observe(selected.cells(), observed, blocks)) {
                return new Entry(source, null, List.of(new Room(selected.floorCells())),
                        Map.copyOf(observed), now, Building.validationResult.SUCCESS);
            }
        }
        return failed(source, now, Building.validationResult.BLOCK_LIMIT, "OBSERVATION",
                blocks.firstMissingBlock, observed.size());
    }

    private Entry failed(BlockPos source, long now, Building.validationResult result,
                         String phase, BlockPos missingBlock, int observedBlocks) {
        if (BedDebugLog.SHELTER_ENABLED) {
            String reason = missingBlock != null ? "UNLOADED_CHUNK"
                    : result == Building.validationResult.BLOCK_LIMIT
                    ? (phase.equals("OBSERVATION") ? "OBSERVED_BLOCK_LIMIT" : "FLOOR_CELL_LIMIT") : result.name();
            MCA.LOGGER.info("[MCA-SHELTER][discovery] gameTime={} source={} result={} reason={} phase={} firstMissingBlock={} missingChunk={} observedBlocks={} maxObservedBlocks={} maxFloorCells={} maxScanRadius={} retryAfterTicks={}",
                    now, source, result, reason, phase, missingBlock,
                    missingBlock == null ? null : new ChunkPos(missingBlock),
                    observedBlocks, MAX_OBSERVED_BLOCKS, MAX_SCAN_CELLS, MAX_SCAN_RADIUS, FAILED_RETRY_TICKS);
        }
        return new Entry(source, null, List.of(), Map.of(), now, result);
    }

    private boolean observe(Collection<FloorGeometry.Cell> cells, Map<BlockPos, BlockState> observed,
                            LoadedBlocks blocks) {
        for (FloorGeometry.Cell cell : cells) {
            for (int y = cell.feet().getY() - 1; y <= cell.ceilingY(); y++) {
                BlockPos pos = new BlockPos(cell.feet().getX(), y, cell.feet().getZ());
                if (!observeBlock(observed, pos, blocks)) return false;
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    if (!observeBlock(observed, pos.relative(direction), blocks)) return false;
                }
            }
        }
        return true;
    }

    private boolean observeBlock(Map<BlockPos, BlockState> observed, BlockPos pos, LoadedBlocks blocks) {
        if (!isLoaded(pos)) {
            blocks.markMissing(pos);
            return false;
        }
        observed.putIfAbsent(pos, structuralState(level.getBlockState(pos)));
        return observed.size() <= MAX_OBSERVED_BLOCKS;
    }

    private boolean isLoaded(BlockPos pos) {
        return level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null;
    }

    /** Operation-local read view: never load chunks, and never accept missing evidence as air. */
    private final class LoadedBlocks implements BlockGetter {
        private BlockPos firstMissingBlock;

        private void markMissing(BlockPos pos) {
            if (firstMissingBlock == null) firstMissingBlock = pos.immutable();
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (level.isOutsideBuildHeight(pos)) return Blocks.VOID_AIR.defaultBlockState();
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                markMissing(pos);
                return Blocks.AIR.defaultBlockState();
            }
            return chunk.getBlockState(pos);
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                markMissing(pos);
                return null;
            }
            return chunk.getBlockEntity(pos);
        }

        @Override
        public int getHeight() { return level.getHeight(); }

        @Override
        public int getMinBuildHeight() { return level.getMinBuildHeight(); }
    }

    static BlockState structuralState(BlockState state) {
        if (BedPoiCompatibility.isCompatibleBedState(state)) return state.setValue(BedBlock.OCCUPIED, false);
        if (state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock) {
            return state.setValue(BlockStateProperties.OPEN, false);
        }
        return state;
    }

    public record Room(Set<BlockPos> floorCells) {
        public Room {
            floorCells = Set.copyOf(floorCells);
        }
    }

    /** Immutable admission view; room-local wandering still uses resolve(bed). */
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
                    .min(java.util.Comparator.comparingDouble(bed -> bed.distSqr(endpoint))).orElse(anchor);
        }

        public boolean contains(BlockPos position) {
            return geometry.stream().anyMatch(floor -> floor.physicalCellAt(
                    position.getX(), position.getY(), position.getZ()).isPresent());
        }
    }

    private record RegisteredHouse(int village, int building) { }

    private static final class ScanBudget {
        private int remaining = MAX_DISCOVERY_SCANS;
    }

    private final class Entry {
        private final BlockPos source;
        private final SelectedFloorScanner.Result scan;
        private final List<Room> rooms;
        private final Map<BlockPos, BlockState> observed;
        private final Building.validationResult failure;
        private final long created;
        private long lastUsed;
        private long nextValidation;

        private Entry(BlockPos source, SelectedFloorScanner.Result scan, List<Room> rooms,
                      Map<BlockPos, BlockState> observed, long now, Building.validationResult failure) {
            this.source = source.immutable();
            this.scan = scan;
            this.rooms = List.copyOf(rooms);
            this.observed = observed;
            this.failure = failure;
            this.created = now;
            this.lastUsed = now;
            this.nextValidation = now + VALIDATION_TICKS;
        }

        private boolean isCurrent(long now) {
            if (now - lastUsed >= IDLE_TICKS || !isLoaded(source)) return false;
            if (rooms.isEmpty()) return now - created < FAILED_RETRY_TICKS;
            if (now < nextValidation) return true;
            for (var block : observed.entrySet()) {
                if (!isLoaded(block.getKey())
                        || !structuralState(level.getBlockState(block.getKey())).equals(block.getValue())) return false;
            }
            nextValidation = now + VALIDATION_TICKS;
            return true;
        }
    }
}
