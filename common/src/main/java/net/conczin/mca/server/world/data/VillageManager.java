package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.CriterionMCA;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.ReaperSpawner;
import net.conczin.mca.server.SpawnQueue;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VillageManager extends SavedData implements Iterable<Village> {
    public final Set<BlockPos> cache = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Village> villages = new HashMap<>();
    private final List<BlockPos> buildingQueue = new LinkedList<>();
    private final ServerLevel world;
    private final ReaperSpawner reapers;
    private int lastBuildingId;
    private int lastVillageId;
    private int buildingCooldown = 21;

    VillageManager(ServerLevel world) {
        this.world = world;
        reapers = new ReaperSpawner(this);
    }

    VillageManager(ServerLevel world, CompoundTag nbt) {
        this.world = world;
        lastBuildingId = nbt.getInt("lastBuildingId");
        lastVillageId = nbt.getInt("lastVillageId");
        reapers = nbt.contains("reapers", Tag.TAG_COMPOUND)
                ? new ReaperSpawner(this, nbt.getCompound("reapers")) : new ReaperSpawner(this);
        ListTag villageList = nbt.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i), world);
            if (village.repairDuplicateResidentHomes()) {
                setDirty();
            }
            if (village.getBuildings().isEmpty() && village.getStructures().isEmpty()
                    && village.getExternalBuildingMap().isEmpty()) {
                MCA.LOGGER.warn("Empty village detected ({}), removing...", village.getName());
                setDirty();
            } else {
                villages.put(village.getId(), village);
            }
        }
    }

    public static VillageManager get(ServerLevel world) {
        return WorldUtils.loadData(world, (nbt, provider) -> new VillageManager(world, nbt),
                VillageManager::new, "mca_villages");
    }

    public ReaperSpawner getReaperSpawner() { return reapers; }
    public Optional<Village> getOrEmpty(int id) { return Optional.ofNullable(villages.get(id)); }

    public boolean removeVillage(int id) {
        if (villages.remove(id) != null) {
            cache.clear();
            return true;
        }
        return false;
    }

    @Override
    public Iterator<Village> iterator() { return villages.values().iterator(); }
    public Stream<Village> findVillages(Predicate<Village> predicate) { return villages.values().stream().filter(predicate); }

    public Optional<Village> findNearestVillage(Entity entity) {
        BlockPos pos = entity.blockPosition();
        return findVillages(village -> village.isWithinBorder(entity))
                .min(Comparator.comparingDouble(village -> village.getCenter().distSqr(pos)));
    }

    public Optional<Village> findNearestVillage(BlockPos pos, int margin) {
        return findVillages(village -> village.isWithinBorder(pos, margin))
                .min(Comparator.comparingDouble(village -> village.getCenter().distSqr(pos)));
    }

    public boolean isWithinHorizontalBoundaries(BlockPos pos) {
        return villages.values().stream().anyMatch(village -> village.getBox().expand(0, 1000, 0).isInside(pos));
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("lastBuildingId", lastBuildingId);
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", NbtHelper.fromList(villages.values(), Village::save));
        nbt.put("reapers", reapers.writeNbt());
        return nbt;
    }

    public void tick() {
        if (world.getDayTime() % 100 == 0) {
            world.players().forEach(player -> PlayerSaveData.get(player).updateLastSeenVillage(this, player));
        }
        int interval = Config.getInstance().bountyHunterInterval;
        if (interval > 0 && world.getDayTime() % Math.max(1, interval / 10) == 0
                && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.players().forEach(player -> {
                if (world.random.nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.blockPosition()) && !player.isCreative()) {
                    villages.values().stream().filter(village -> village.getPopulation() >= 3)
                            .filter(village -> village.getReputation(player) < Config.getInstance().bountyHunterHearts)
                            .min(Comparator.comparingInt(village -> village.getReputation(player)))
                            .ifPresent(village -> startBountyHunterWave(player, village));
                }
            });
        }

        long time = world.getGameTime();
        for (Village village : this) village.tick(world, time);
        if (time % buildingCooldown == 0 && !buildingQueue.isEmpty()) {
            processBuilding(buildingQueue.removeFirst());
        }
        reapers.tick(world);
        SpawnQueue.getInstance().tick();
    }

    private void startBountyHunterWave(ServerPlayer player, Village sender) {
        int heartsPerHunter = 100;
        int count = Math.min(15, -sender.getReputation(player) / heartsPerHunter + 2);
        if (sender.getPopulation() == 0) {
            sender.cleanReputation();
            count *= 2;
        } else {
            sender.pushHearts(player, count * heartsPerHunter / 2);
        }
        CriterionMCA.GENERIC_EVENT.trigger(player, "bounty_hunter");
        for (int i = 0; i < count; i++) {
            if (world.random.nextBoolean()) spawnBountyHunter(EntityType.PILLAGER, player);
            else spawnBountyHunter(EntityType.VINDICATOR, player);
        }
        player.displayClientMessage(Component.translatable(sender.getPopulation() == 0
                ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).withStyle(ChatFormatting.RED), false);
        sender.getCivilRegistry().ifPresent(registry -> registry.addText(Component.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends AbstractIllager> void spawnBountyHunter(EntityType<T> type, ServerPlayer player) {
        T illager = type.create(world);
        if (illager == null) return;
        for (int attempt = 0; attempt < 32; attempt++) {
            float angle = world.random.nextFloat() * 6.2831855F;
            int x = (int) (player.getX() + Mth.cos(angle) * 32.0f);
            int z = (int) (player.getZ() + Mth.sin(angle) * 32.0f);
            int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (SpawnPlacements.isSpawnPositionOk(type, world, pos)) {
                illager.setPos(x, y, z);
                illager.setTarget(player);
                WorldUtils.spawnEntity(world, illager, MobSpawnType.EVENT);
                break;
            }
        }
    }

    public void reportBuilding(BlockPos pos) {
        cache.add(pos);
        buildingQueue.add(pos);
    }

    public Building.validationResult processBuilding(BlockPos pos) {
        BuildingType externalType = getGroupedBuildingType(pos);
        if (externalType != null) return processExternalBuilding(pos, externalType);

        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village != null && village.getInteractionStructureAt(pos).isPresent()) {
            // Auto Scan never registers optional Rooms inside known Structures.
            return Building.validationResult.SUCCESS;
        }
        BuildingScanResult scan = analyzeReportedBuildingAddition(pos);
        if (scan.result() != Building.validationResult.SUCCESS || scan.isAmbiguous()) return scan.result();
        return commitRoomAddition(scan, null);
    }

    private BuildingType getGroupedBuildingType(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        for (BuildingType type : BuildingTypes.getInstance()) {
            if (type.grouped() && type.matchesBlock(id)) return type;
        }
        return null;
    }

    private Building.validationResult processExternalBuilding(BlockPos pos, BuildingType type) {
        Optional<Village> nearest = findNearestVillage(pos, Village.MERGE_MARGIN);
        if (nearest.isPresent()) {
            double range = type.mergeRange() * type.mergeRange();
            ExternalBuilding existing = nearest.get().getExternalBuildings()
                    .filter(building -> building.getType().equals(type.name()))
                    .min(Comparator.comparingDouble(building -> building.getCenter().distSqr(pos)))
                    .filter(building -> building.getCenter().distSqr(pos) < range).orElse(null);
            if (existing != null) {
                existing.addPOI(world, pos);
                nearest.get().calculateDimensions();
                setDirty();
                return Building.validationResult.SUCCESS;
            }
        }
        Village village = nearest.orElseGet(() -> new Village(lastVillageId++, world));
        ExternalBuilding building = new ExternalBuilding(pos);
        building.setId(lastBuildingId++);
        building.setType(type.name());
        building.addPOI(world, pos);
        village.registerExternalBuilding(building);
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public BuildingScanResult analyzeAttachedRoom(BlockPos pos,
                                                  Village.RoomScanMode requestedMode,
                                                  int expectedTargetBuildingId) {
        if (requestedMode != Village.RoomScanMode.ADD_FLOOR
                && requestedMode != Village.RoomScanMode.ADD_BASEMENT) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, null);
        }

        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, null);
        RoomScanPlan plan = village.getRoomScanPlan(world, pos);
        return analyzeAttachedRoom(village, plan, requestedMode, expectedTargetBuildingId);
    }

    BuildingScanResult analyzeAttachedRoom(Village village,
                                           RoomScanPlan plan,
                                           Village.RoomScanMode requestedMode,
                                           int expectedTargetBuildingId) {
        BlockPos source = plan == null ? BlockPos.ZERO : plan.interactionSource();
        if (village == null || plan == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        if (plan.mode() != requestedMode || plan.targetBuildingId() < 0
                || (expectedTargetBuildingId >= 0
                && plan.targetBuildingId() != expectedTargetBuildingId)) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        StructureScanner.Result structureScan = StructureScanner.scanPlannedStructure(
                world, plan, village.getStructures().values());
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), source, village);
        }

        StructureFloor scannedFloor = structureScan.floor();
        if (scannedFloor == null) {
            return failedRoom(Building.validationResult.AMBIGUOUS_STRUCTURE, source, village);
        }

        Structure candidate = structureScan.toStructure(-1);
        StructureFloor attachmentFloor = candidate.getFloor(scannedFloor.id()).orElse(null);
        if (attachmentFloor == null || !validAttachment(
                village, candidate, attachmentFloor, structureScan.connectedFloors(),
                plan.targetBuildingId(), requestedMode)) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        candidate.setLogicalBuildingId(plan.targetBuildingId());
        return scanResolvedRoom(village, candidate, plan.scanSeed(), -1,
                attachmentFloor, structureScan.scannedFloor(), Set.of())
                .withSource(source)
                .withPendingStructure(candidate);
    }

    private boolean validAttachment(Village village,
                                    Structure candidate,
                                    StructureFloor playerFloor,
                                    Collection<FloorGeometry> connectedFloors,
                                    int targetBuildingId,
                                    Village.RoomScanMode requestedMode) {
        Village.AttachmentTarget resolved = village.resolveAttachmentTarget(
                world, playerFloor, connectedFloors).orElse(null);
        if (resolved == null || resolved.buildingId() != targetBuildingId) return false;

        int floorNumber = village.prospectiveFloorNumber(
                targetBuildingId, candidate, playerFloor);
        return requestedMode == Village.RoomScanMode.ADD_BASEMENT
                ? floorNumber < 0
                : floorNumber != Integer.MIN_VALUE && floorNumber >= 0;
    }

    public BuildingScanResult analyzeBuildingAddition(BlockPos pos) {
        return analyzeBuildingAddition(pos, false);
    }

    private BuildingScanResult analyzeReportedBuildingAddition(BlockPos pos) {
        return analyzeBuildingAddition(pos, true);
    }

    private BuildingScanResult analyzeBuildingAddition(BlockPos pos, boolean reportedSource) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Collection<Structure> existing = village == null ? List.of() : village.getStructures().values();
        if (village != null && village.getInteractionStructureAt(pos).isPresent()) {
            return failedRoom(Building.validationResult.IDENTICAL, pos, village);
        }

        StructureScanner.Result structureScan = reportedSource
                ? StructureScanner.scanReportedStructure(world, pos, existing)
                : StructureScanner.scanNewStructure(world, pos, existing);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), pos, village);
        }

        Structure candidate = structureScan.toStructure(-1);
        StructureFloor floor = candidate.getFloors().getFirst();
        return scanResolvedRoom(village, candidate, structureScan.source(), -1,
                floor, structureScan.scannedFloor(), Set.of()).withPendingStructure(candidate);
    }

    public BuildingScanResult analyzeRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, null);
        RoomScanPlan plan = village.getRoomScanPlan(world, pos);
        if (plan.mode() != Village.RoomScanMode.ADD_ROOM
                || plan.targetStructureId() < 0 || plan.targetFloorId() < 0) {
            return failedRoom(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                    ? Building.validationResult.IDENTICAL
                    : Building.validationResult.NOT_IN_BUILDING, pos, village);
        }

        Structure structure = village.getStructure(plan.targetStructureId()).orElse(null);
        StructureFloor floor = structure == null
                ? null : structure.getFloor(plan.targetFloorId()).orElse(null);
        if (structure == null || floor == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
                world, structure, floor, pos, village.getStructures().values());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return failedRoom(fresh.result(), pos, village);
        }

        Structure refreshed = structure.copy();
        if (!refreshed.replaceFloorGeometry(floor.id(), fresh.floor())) {
            return failedRoom(Building.validationResult.OVERLAP, pos, village);
        }
        StructureFloor refreshedFloor = refreshed.getFloor(floor.id()).orElse(null);
        if (refreshedFloor == null) {
            return failedRoom(Building.validationResult.OVERLAP, pos, village);
        }
        return scanResolvedRoom(village, refreshed, pos, -1, refreshedFloor, fresh.scannedFloor(),
                registeredRoomCells(village, structure.getId(), floor.id(), -1))
                .withSource(pos)
                .withPendingStructure(refreshed);
    }


    public RegisteredRoomUpdate analyzeRegisteredRoomUpdate(Village village, int buildingId, BlockPos pos) {
        Building expected = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        Structure structure = village.getStructureFor(expected).orElse(null);
        if (structure == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        return analyzeRegisteredFloor(village, structure, expected, pos);
    }

    static Optional<List<Building>> updateLineage(Building selected,
                                                  Collection<Building> freshComponents,
                                                  Collection<Building> otherRooms) {
        List<Building> lineage = freshComponents.stream()
                .filter(component -> component.getFloorFootprintIntersectionArea(selected) > 0)
                .sorted(Comparator.comparingInt((Building room) -> room.getRawPos0().getX())
                        .thenComparingInt(room -> room.getRawPos0().getZ())
                        .thenComparingInt(room -> room.getRawPos1().getX())
                        .thenComparingInt(room -> room.getRawPos1().getZ()))
                .toList();
        if (lineage.isEmpty()) return Optional.empty();
        for (Building component : lineage) {
            for (Building other : otherRooms) {
                if (component.getFloorFootprintIntersectionArea(other) > 0) return Optional.empty();
            }
        }
        return Optional.of(lineage);
    }

    static boolean lineageOverlapsRegisteredRooms(
            Collection<RegisteredRoomReconciler.Assignment> assignments,
            Collection<Building> otherRooms) {
        for (RegisteredRoomReconciler.Assignment assignment : assignments) {
            Building component = assignment.component();
            for (Building other : otherRooms) {
                if (component.getFloorFootprintIntersectionArea(other) > 0) return true;
            }
        }
        return false;
    }

    private RegisteredRoomUpdate analyzeRegisteredFloor(Village village,
                                                        Structure structure,
                                                        Building expected,
                                                        BlockPos pos) {
        StructureFloor persistedFloor = structure.getFloor(expected.getFloorId()).orElse(null);
        if (persistedFloor == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }

        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
                world, structure, persistedFloor, pos, village.getStructures().values());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return RegisteredRoomUpdate.failure(fresh.result(), pos, village);
        }

        List<Building> freshComponents = BuildingRoomScanner.partition(
                        world, pos, Config.getInstance().maxBuildingSize,
                        persistedFloor.id(), fresh.scannedFloor()).stream()
                .map(geometry -> roomResultFromGeometry(
                        village, structure, persistedFloor, geometry, -1))
                .filter(scan -> scan.result() == Building.validationResult.SUCCESS)
                .map(BuildingScanResult::building)
                .toList();
        if (freshComponents.isEmpty()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.TOO_SMALL, pos, village);
        }

        List<Building> otherRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == persistedFloor.id())
                .filter(room -> room.getId() != expected.getId())
                .toList();

        List<Building> lineage = updateLineage(expected, freshComponents, otherRooms).orElse(null);
        if (lineage == null || lineage.isEmpty()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }

        int mainRoomId = village.getMainRoom(structure).map(Building::getId).orElse(-1);
        RegisteredRoomReconciler.Result reconciled = RegisteredRoomReconciler.reconcile(
                pos, expected.getId(), mainRoomId, List.of(expected), lineage).orElse(null);
        if (reconciled == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }

        Structure refreshed = structure.copy();
        if (!refreshed.replaceFloorGeometry(persistedFloor.id(), fresh.floor())) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }

        Building playerComponent = reconciled.playerComponent();
        List<String> matchingTypes = village.getMatchingRoomTypes(playerComponent).stream()
                .map(BuildingType::name)
                .toList();
        return new RegisteredRoomUpdate(Building.validationResult.SUCCESS, pos, village,
                refreshed, structure.getId(), persistedFloor.id(), expected.getId(),
                reconciled.previousRoomIds(), reconciled.assignments(),
                playerComponent, matchingTypes);
    }

    private static Set<BlockPos> registeredRoomCells(Village village,
                                                     int structureId,
                                                     int floorId,
                                                     int excludedRoomId) {
        if (village == null) return Set.of();
        return village.getRooms()
                .filter(room -> room.getId() != excludedRoomId)
                .filter(room -> room.getStructureId() == structureId)
                .filter(room -> room.getFloorId() == floorId)
                .flatMap(room -> room.getFloorCells().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    private BuildingScanResult scanResolvedRoom(Village village,
                                                Structure structure,
                                                BlockPos pos,
                                                int existingRoomId,
                                                StructureFloor floor,
                                                FloorGeometry scannedFloor,
                                                Set<BlockPos> blocked) {
        BuildingRoomScanner.Result geometry = BuildingRoomScanner.scan(
                world, pos, blocked, Config.getInstance().maxBuildingSize, floor.id(), scannedFloor);
        return roomResultFromGeometry(village, structure, floor, geometry, existingRoomId);
    }

    private BuildingScanResult roomResultFromGeometry(Village village, Structure structure, StructureFloor floor,
                                                      BuildingRoomScanner.Result geometry, int existingRoomId) {
        Building room = new Building(geometry.seed());
        Building.validationResult result = room.applyRoomScan(world, geometry);
        if (result != Building.validationResult.SUCCESS) return failedRoom(result, geometry.seed(), village);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        if (existingRoomId >= 0) room.setId(existingRoomId);

        List<String> types = village == null
                ? room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : village.getMatchingRoomTypes(room).stream().map(BuildingType::name).toList();
        return new BuildingScanResult(Building.validationResult.SUCCESS, room.getSourceBlock(), room,
                types, village);
    }

    private static BuildingScanResult failedRoom(Building.validationResult result, BlockPos pos, Village village) {
        return new BuildingScanResult(result, pos, new Building(pos), List.of(), village);
    }

    public Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (forcedType != null && !scan.matchesType(forcedType)) return Building.validationResult.INVALID_TYPE;
        if (forcedType == null && scan.isAmbiguous()) return Building.validationResult.INVALID_TYPE;
        if (scan.pendingStructure() != null) {
            Village village = scan.village();
            Structure pending = scan.pendingStructure();
            if (village != null && pending.getId() >= 0 && village.getStructure(pending.getId()).isPresent()) {
                return commitExpandedRoom(scan, forcedType);
            }
            return commitInitialRoom(scan, forcedType);
        }
        if (scan.building().getId() >= 0) return Building.validationResult.OVERLAP;

        Village village = scan.village();
        Structure structure = village == null ? null : village.getStructureFor(scan.building()).orElse(null);
        if (village == null || structure == null) return Building.validationResult.NOT_IN_BUILDING;

        Building room = scan.building();
        String category = chooseRoomCategory(scan, forcedType);
        if (category == null) return Building.validationResult.INVALID_TYPE;
        room.setId(lastBuildingId++);
        room.setType(category);
        room.setTypeForced(forcedType != null);
        village.registerRoom(room);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private Building.validationResult commitExpandedRoom(BuildingScanResult scan, String forcedType) {
        Village village = scan.village();
        Structure refreshed = scan.pendingStructure();
        Building room = scan.building();
        if (village == null || refreshed == null || room == null) {
            return Building.validationResult.NOT_IN_BUILDING;
        }

        Structure current = village.getStructure(refreshed.getId()).orElse(null);
        if (current == null || current.getLogicalBuildingId() != refreshed.getLogicalBuildingId()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        if (room.getId() >= 0 || room.getStructureId() != refreshed.getId()
                || refreshed.getFloor(room.getFloorId()).isEmpty()) {
            return Building.validationResult.OVERLAP;
        }

        String category = chooseRoomCategory(scan, forcedType);
        if (category == null) return Building.validationResult.INVALID_TYPE;

        Building committed = room.copy();
        committed.setId(lastBuildingId);
        committed.setType(category);
        committed.setTypeForced(forcedType != null);
        if (!village.replaceStructureAndRegisterRoom(refreshed, committed)) {
            return Building.validationResult.OVERLAP;
        }
        lastBuildingId++;
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private Building.validationResult commitInitialRoom(BuildingScanResult scan, String forcedType) {
        Village village = scan.village();
        if (village == null) village = new Village(lastVillageId++, world);

        Structure structure = scan.pendingStructure();
        if (village.hasRegisteredFloorOverlap(structure)) {
            return Building.validationResult.IDENTICAL;
        }

        Building room = scan.building();
        String category = chooseRoomCategory(scan, forcedType);
        if (category == null) return Building.validationResult.INVALID_TYPE;

        int targetBuildingId = structure.getLogicalBuildingId();
        if (targetBuildingId != structure.getId()
                && village.getBuildingStructures(targetBuildingId).isEmpty()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }

        registerInitialRoom(village, structure, room, category, forcedType != null);
        village.refreshLogicalBuildings();
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private void registerInitialRoom(Village village,
                                     Structure structure,
                                     Building room,
                                     String category,
                                     boolean typeForced) {
        int structureId;
        if (structure.getId() >= 0 && village.getStructure(structure.getId()).isPresent()) {
            structureId = structure.getId();
        } else {
            structureId = lastBuildingId++;
            structure.setId(structureId);
        }
        room.setId(lastBuildingId++);
        room.setStructureId(structureId);
        room.setType(category);
        room.setTypeForced(typeForced);
        if (village.getStructure(structureId).isPresent()) {
            village.registerRoom(room);
            return;
        }
        village.registerStructure(structure, room);
    }

    public Building.validationResult commitRegisteredRoomUpdate(RegisteredRoomUpdate update,
                                                                 String forcedType) {
        Building.validationResult result = applyRegisteredRoomUpdate(update, forcedType, true);
        if (result == Building.validationResult.SUCCESS) {
            finalizeVillageMutation(update.village());
        }
        return result;
    }

    private Building.validationResult applyRegisteredRoomUpdate(RegisteredRoomUpdate update,
                                                                 String forcedType,
                                                                 boolean requireTypeChoice) {
        if (update == null || update.result() != Building.validationResult.SUCCESS) {
            return update == null ? Building.validationResult.TOO_SMALL : update.result();
        }
        if (forcedType != null && !update.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (requireTypeChoice && forcedType == null && update.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }

        Village village = update.village();
        Structure structure = village == null
                ? null : village.getStructure(update.structureId()).orElse(null);
        if (structure == null || structure.getFloor(update.floorId()).isEmpty()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        if (update.refreshedStructure() == null
                || update.refreshedStructure().getId() != update.structureId()
                || update.refreshedStructure().getFloor(update.floorId()).isEmpty()
                || !update.previousRoomIds().equals(List.of(update.expectedPlayerRoomId()))) {
            return Building.validationResult.OVERLAP;
        }
        List<Building> currentFloorRooms = village.getRooms()
                .filter(room -> room.getStructureId() == update.structureId())
                .filter(room -> room.getFloorId() == update.floorId())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        Building playerRoom = village.getBuilding(update.expectedPlayerRoomId()).orElse(null);
        if (playerRoom == null || !currentFloorRooms.contains(playerRoom)) {
            return Building.validationResult.OVERLAP;
        }
        Building playerComponent = update.playerComponent();
        if (playerComponent == null) {
            return Building.validationResult.OVERLAP;
        }

        List<RegisteredRoomReconciler.Assignment> assignments = update.assignments();
        Building.validationResult assignmentValidation = validateRoomAssignments(
                assignments, playerRoom, currentFloorRooms);
        if (assignmentValidation != Building.validationResult.SUCCESS) {
            return assignmentValidation;
        }

        OptionalInt nextRoomId = assignRoomIdentities(update, village, playerRoom, assignments);
        if (nextRoomId.isEmpty()) {
            return Building.validationResult.OVERLAP;
        }

        Building.validationResult typeResolution = resolveRoomTypes(
                update, forcedType, village, structure, assignments);
        if (typeResolution != Building.validationResult.SUCCESS) {
            return typeResolution;
        }

        applyRoomAssignments(update, village, structure, assignments, nextRoomId.getAsInt());
        return Building.validationResult.SUCCESS;
    }

    private static Building.validationResult validateRoomAssignments(
            List<RegisteredRoomReconciler.Assignment> assignments,
            Building playerRoom,
            List<Building> currentFloorRooms) {
        long previousAssignments = assignments.stream()
                .filter(assignment -> assignment.previous() != null)
                .count();
        if (previousAssignments != 1
                || assignments.stream().noneMatch(assignment -> assignment.previous() == playerRoom)) {
            return Building.validationResult.OVERLAP;
        }
        List<Building> otherRooms = currentFloorRooms.stream()
                .filter(room -> room != playerRoom)
                .toList();
        if (lineageOverlapsRegisteredRooms(assignments, otherRooms)) {
            return Building.validationResult.OVERLAP;
        }
        return Building.validationResult.SUCCESS;
    }

    private OptionalInt assignRoomIdentities(RegisteredRoomUpdate update,
                                             Village village,
                                             Building playerRoom,
                                             List<RegisteredRoomReconciler.Assignment> assignments) {
        int nextRoomId = lastBuildingId;
        for (RegisteredRoomReconciler.Assignment assignment : assignments) {
            Building component = assignment.component();
            if (component.getStructureId() != update.structureId()
                    || component.getFloorId() != update.floorId()
                    || component.getFloorFootprintArea() <= 0) {
                return OptionalInt.empty();
            }
            int roomId = assignment.createsRoom() ? nextRoomId++ : assignment.roomId();
            Building previous = assignment.previous();
            if (previous != null && village.getBuilding(roomId).orElse(null) != previous) {
                return OptionalInt.empty();
            }
            component.setId(roomId);
            component.setStructureId(update.structureId());
            component.setFloorId(update.floorId());
            if (previous != null) {
                component.setType(previous.getType());
                component.setTypeForced(previous.isTypeForced());
                component.setContributesToMain(previous.contributesToMain());
            } else {
                component.setContributesToMain(playerRoom.contributesToMain());
            }
        }
        List<Building> components = assignments.stream()
                .map(RegisteredRoomReconciler.Assignment::component)
                .toList();
        for (int i = 0; i < components.size(); i++) {
            for (int j = i + 1; j < components.size(); j++) {
                if (components.get(i).getFloorFootprintIntersectionArea(components.get(j)) > 0) {
                    return OptionalInt.empty();
                }
            }
        }
        return OptionalInt.of(nextRoomId);
    }

    private Building.validationResult resolveRoomTypes(RegisteredRoomUpdate update,
                                                        String forcedType,
                                                        Village village,
                                                        Structure structure,
                                                        List<RegisteredRoomReconciler.Assignment> assignments) {
        Building playerComponent = update.playerComponent();
        List<Building> components = assignments.stream()
                .map(RegisteredRoomReconciler.Assignment::component)
                .toList();
        Set<Integer> removedRoomIds = new HashSet<>(update.previousRoomIds());
        assignments.stream()
                .map(RegisteredRoomReconciler.Assignment::roomId)
                .filter(id -> id >= 0)
                .forEach(removedRoomIds::remove);
        int mainRoomId = village.getMainRoom(structure).map(Building::getId).orElse(-1);
        Building replacementMain = removedRoomIds.contains(mainRoomId) ? playerComponent : null;
        List<Building> prospectiveRooms = village.getRooms()
                .filter(room -> !update.previousRoomIds().contains(room.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prospectiveRooms.addAll(components);
        Building prospectiveMain = prospectiveRooms.stream()
                .filter(room -> room.getId() == mainRoomId)
                .findFirst().orElse(replacementMain);
        RoomTypeResolver resolver = RoomTypeResolver.create(village, prospectiveRooms);

        if (forcedType != null) {
            String selectedType = resolver.resolve(playerComponent, prospectiveMain).updatedType(forcedType);
            if (selectedType == null) return Building.validationResult.INVALID_TYPE;
            playerComponent.setType(selectedType);
            playerComponent.setTypeForced(true);
        } else if (!update.requiresTypeSelection()) {
            String selectedType = resolver.resolve(playerComponent, prospectiveMain).updatedType(null);
            if (selectedType == null) return Building.validationResult.INVALID_TYPE;
            playerComponent.setType(selectedType);
            playerComponent.setTypeForced(false);
        }
        for (RegisteredRoomReconciler.Assignment assignment : assignments) {
            if (!assignment.createsRoom()) continue;
            Building component = assignment.component();
            String type = resolver.resolve(component, prospectiveMain).updatedType(null);
            if (type == null) return Building.validationResult.INVALID_TYPE;
            component.setType(type);
            component.setTypeForced(false);
        }
        return Building.validationResult.SUCCESS;
    }

    private void applyRoomAssignments(RegisteredRoomUpdate update,
                                      Village village,
                                      Structure structure,
                                      List<RegisteredRoomReconciler.Assignment> assignments,
                                      int nextRoomId) {
        Building playerComponent = update.playerComponent();
        Set<Integer> removedRoomIds = new HashSet<>(update.previousRoomIds());
        assignments.stream()
                .map(RegisteredRoomReconciler.Assignment::roomId)
                .filter(id -> id >= 0)
                .forEach(removedRoomIds::remove);
        int mainRoomId = village.getMainRoom(structure).map(Building::getId).orElse(-1);
        Building replacementMain = removedRoomIds.contains(mainRoomId) ? playerComponent : null;

        village.replaceStructure(update.refreshedStructure());
        for (RegisteredRoomReconciler.Assignment assignment : assignments) {
            if (assignment.previous() == null) continue;
            Building existing = assignment.previous();
            Building component = assignment.component();
            existing.copyScannedGeometryFrom(component, world);
            existing.setType(component.getType());
            existing.setTypeForced(component.isTypeForced());
            existing.setContributesToMain(component.contributesToMain());
        }
        village.removeRooms(removedRoomIds);
        assignments.stream().filter(RegisteredRoomReconciler.Assignment::createsRoom)
                .map(RegisteredRoomReconciler.Assignment::component)
                .forEach(village::registerRoom);
        if (replacementMain != null) {
            village.setMainRoom(replacementMain);
        }
        village.refreshLogicalBuildings();
        lastBuildingId = nextRoomId;
    }

    private static String chooseRoomCategory(BuildingScanResult scan, String forcedType) {
        return RoomTypeResolver.resolveTypeChoice(scan.matchingTypes(), forcedType, "building");
    }

    static List<Integer> fullScanRoomIds(Village village) {
        if (village == null) return List.of();
        return village.getRooms().map(Building::getId).sorted().toList();
    }

    public Building.validationResult fullScan(Village village) {
        if (village == null) return Building.validationResult.NOT_IN_BUILDING;
        Village.BuildingStateSnapshot snapshot = village.snapshotBuildingState();
        int previousLastBuildingId = lastBuildingId;

        for (int roomId : fullScanRoomIds(village)) {
            Building room = village.getBuilding(roomId).orElse(null);
            if (room == null) continue;
            RegisteredRoomUpdate update = analyzeRegisteredRoomUpdate(
                    village, roomId, room.getSourceBlock());
            if (update.result() != Building.validationResult.SUCCESS) {
                restoreFullScanSnapshot(village, snapshot, previousLastBuildingId);
                return update.result();
            }
            Building.validationResult result = applyRegisteredRoomUpdate(update, null, false);
            if (result == Building.validationResult.SUCCESS) continue;

            restoreFullScanSnapshot(village, snapshot, previousLastBuildingId);
            return result;
        }
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private void restoreFullScanSnapshot(
            Village village,
            Village.BuildingStateSnapshot snapshot,
            int previousLastBuildingId) {
        village.restoreBuildingState(snapshot);
        lastBuildingId = previousLastBuildingId;
    }


    public BuildingEditResult forceRoomType(BlockPos pos, String type) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        Building room = village == null ? null : village.findInteractionRoomAt(pos).orElse(null);
        if (room == null) return BuildingEditResult.NO_BUILDING;
        if (room.getType().equals(type)) {
            room.setTypeForced(false);
            room.setType(RoomTypeResolver.create(village).resolve(room).updatedType(null));
        } else {
            room.setTypeForced(true);
            room.setType(type);
        }
        village.markDirty();
        return BuildingEditResult.SUCCESS;
    }

    public BuildingEditResult removeRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) return BuildingEditResult.NO_BUILDING;
        Building room = village.findInteractionRoomAt(pos).orElse(null);
        if (room == null) return village.getRoomScanPlan(world, pos).mode() == Village.RoomScanMode.ADD_ROOM
                ? BuildingEditResult.NO_ROOM : BuildingEditResult.NO_BUILDING;
        if (village.isMainRoom(room)) return BuildingEditResult.MAIN_ROOM;
        return village.removeRoom(room.getId())
                ? BuildingEditResult.SUCCESS : BuildingEditResult.NO_ROOM;
    }

    public BuildingEditResult removeFloor(BlockPos pos, int floorNumber) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) return BuildingEditResult.NO_BUILDING;
        if (floorNumber == Integer.MIN_VALUE) return BuildingEditResult.NO_FLOOR;

        Building room = village.findInteractionRoomAt(pos).orElse(null);
        if (room == null) return BuildingEditResult.NO_ROOM;
        Structure structure = village.getStructureFor(room).orElse(null);
        if (structure == null) return BuildingEditResult.NO_BUILDING;

        return village.removeFloor(structure.getLogicalBuildingId(), floorNumber)
                ? BuildingEditResult.SUCCESS : BuildingEditResult.NO_FLOOR;
    }

    public BuildingEditResult removeBuilding(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) return BuildingEditResult.NO_BUILDING;
        Building target = village.getBuildingAt(pos).orElse(null);
        if (target instanceof ExternalBuilding) {
            return village.removeExternalBuilding(target.getId())
                    ? BuildingEditResult.SUCCESS : BuildingEditResult.NO_BUILDING;
        }
        if (target != null && target.isFunctionalRoom() && village.getStructure(target.getStructureId()).isEmpty()) {
            int orphanedStructureId = target.getStructureId();
            List<Integer> orphanedRoomIds = village.getBuildings().values().stream()
                    .filter(building -> building.getStructureId() == orphanedStructureId)
                    .map(Building::getId)
                    .toList();
            if (orphanedRoomIds.isEmpty()) village.removeRoom(target.getId());
            else orphanedRoomIds.forEach(village::removeRoom);
            setDirty();
            return BuildingEditResult.SUCCESS;
        }
        Structure structure = target != null && target.isFunctionalRoom()
                ? village.getStructure(target.getStructureId()).orElse(null)
                : village.getExactStructureAt(pos)
                .or(() -> village.getInteractionStructureAt(pos))
                .orElse(null);
        if (structure == null) return BuildingEditResult.NO_BUILDING;

        village.removeLogicalBuilding(structure.getLogicalBuildingId());

        if (village.getBuildings().isEmpty() && village.getExternalBuildingMap().isEmpty()
                && village.getStructures().isEmpty()) {
            removeVillage(village.getId());
        } else {
            finalizeVillageMutation(village);
        }
        setDirty();
        return BuildingEditResult.SUCCESS;
    }

    public void removeStructure(Village village, int structureId) {
        if (village == null) return;
        village.removeStructure(structureId);
        if (village.getBuildings().isEmpty() && village.getExternalBuildingMap().isEmpty()
                && village.getStructures().isEmpty()) removeVillage(village.getId());
        setDirty();
    }

    private void finalizeVillageMutation(Village target) {
        target.refreshLogicalBuildings();
        target.calculateDimensions();
        Village finalVillage = target;
        villages.values().stream().filter(village -> village != finalVillage)
                .filter(village -> village.getBox().inflatedBy(Village.MERGE_MARGIN).intersects(finalVillage.getBox()))
                .findAny().ifPresent(village -> {
                    if (village.getPopulation() > finalVillage.getPopulation()) {
                        merge(village, finalVillage);
                        villages.remove(finalVillage.getId());
                    } else {
                        merge(finalVillage, village);
                        villages.remove(village.getId());
                    }
                });
        setDirty();
    }


    public enum BuildingEditResult {
        SUCCESS,
        NO_BUILDING,
        NO_ROOM,
        NO_FLOOR,
        MAIN_ROOM
    }

    public void setBuildingCooldown(int buildingCooldown) { this.buildingCooldown = buildingCooldown; }
    public void merge(Village into, Village from) { into.merge(from); }
}
