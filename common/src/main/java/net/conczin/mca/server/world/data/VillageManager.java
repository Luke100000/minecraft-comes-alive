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
        return processAutoScannedBuilding(pos);
    }

    private Building.validationResult processAutoScannedBuilding(BlockPos pos) {
        BuildingType externalType = getGroupedBuildingType(pos);
        if (externalType != null) return processExternalBuilding(pos, externalType);

        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village != null && village.getInteractionStructureAt(world, pos).isPresent()) {
            // Auto Scan never registers optional Rooms inside known Structures.
            return Building.validationResult.SUCCESS;
        }
        BuildingScanResult scan = analyzeRoomAddition(pos);
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
        village.getExternalBuildingMap().put(building.getId(), building);
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public BuildingScanResult analyzeRoomAddition(BlockPos pos) {
        BuildingScanResult existingRoom = analyzeRoom(pos);
        return existingRoom.result() == Building.validationResult.NOT_IN_BUILDING
                ? analyzeInitialRoom(pos)
                : existingRoom;
    }

    private BuildingScanResult analyzeInitialRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Collection<Structure> existing = village == null ? List.of() : village.getStructures().values();
        if (village != null && village.getInteractionStructureAt(world, pos).isPresent()) {
            return failedRoom(Building.validationResult.IDENTICAL, pos, village);
        }

        StructureScanner.Result structureScan = StructureScanner.scan(world, pos, existing, -1);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), pos, village);
        }

        Structure candidate = structureScan.toStructure(-1);
        return scanRoom(village, candidate, pos, -1).withPendingStructure(candidate);
    }

    public BuildingScanResult analyzeRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Structure structure = village == null ? null : village.getInteractionStructureAt(world, pos).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        if (village.getFunctionalRoomAt(world, pos).isPresent()) {
            return failedRoom(Building.validationResult.IDENTICAL, pos, village);
        }
        return scanRoom(village, structure, pos, -1);
    }

    public BuildingScanResult analyzeRegisteredRoom(Village village, int buildingId, BlockPos pos) {
        Building expected = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        Structure structure = village.getStructureFor(expected).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        return scanRoom(village, structure, pos, buildingId);
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

    private RegisteredRoomUpdate analyzeRegisteredFloor(Village village,
                                                        Structure structure,
                                                        Building expected,
                                                        BlockPos pos) {
        StructureFloor floor = structure.getFloor(expected.getFloorId()).orElse(null);
        if (floor == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }

        List<Building> previousRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        Set<BlockPos> registeredCells = previousRooms.stream()
                .flatMap(room -> room.getFloorRegions().stream())
                .flatMap(region -> region.cells().stream())
                .collect(java.util.stream.Collectors.toSet());
        List<BuildingScanResult> scanned = BuildingRoomScanner.partitionRegistered(
                        world, pos, Config.getInstance().maxBuildingSize,
                        Config.getInstance().maxBuildingRadius, floor, registeredCells).stream()
                .map(geometry -> roomResultFromGeometry(village, structure, floor, geometry, -1))
                .toList();
        if (scanned.isEmpty()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.TOO_SMALL, pos, village);
        }
        BuildingScanResult failure = scanned.stream()
                .filter(scan -> scan.result() != Building.validationResult.SUCCESS)
                .findFirst().orElse(null);
        if (failure != null) return RegisteredRoomUpdate.failure(failure.result(), pos, village);

        RegisteredRoomReconciler.Result reconciled = RegisteredRoomReconciler.reconcile(
                pos, expected.getId(), previousRooms,
                scanned.stream().map(BuildingScanResult::building).toList()).orElse(null);
        if (reconciled == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
        }
        Building playerComponent = reconciled.playerComponent();
        List<String> matchingTypes = village.getMatchingRoomTypes(playerComponent).stream()
                .map(BuildingType::name)
                .toList();
        return new RegisteredRoomUpdate(Building.validationResult.SUCCESS, pos, village,
                structure.getId(), floor.id(), expected.getId(),
                reconciled.previousRoomIds(), reconciled.assignments(),
                reconciled.removedRoomIds(), playerComponent, matchingTypes);
    }

    private BuildingScanResult scanRoom(Village village,
                                        Structure structure,
                                        BlockPos pos,
                                        int existingRoomId) {
        StructureFloor floor = resolveRoomFloor(village, structure, pos, existingRoomId);
        if (floor == null) return failedRoom(Building.validationResult.TOO_SMALL, pos, village);
        return scanResolvedRoom(village, structure, pos, existingRoomId, floor,
                registeredRoomCells(village, structure.getId(), floor.id(), existingRoomId));
    }

    private static StructureFloor resolveRoomFloor(Village village,
                                                   Structure structure,
                                                   BlockPos pos,
                                                   int existingRoomId) {
        return existingRoomId >= 0 && village != null
                ? village.getBuilding(existingRoomId).flatMap(room -> structure.getFloor(room.getFloorId())).orElse(null)
                : structure.floorAtHeight(pos.getY()).orElse(null);
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
                .flatMap(room -> room.getFloorRegions().stream())
                .flatMap(region -> region.cells().stream())
                .collect(java.util.stream.Collectors.toSet());
    }

    private BuildingScanResult scanResolvedRoom(Village village,
                                                Structure structure,
                                                BlockPos pos,
                                                int existingRoomId,
                                                StructureFloor floor,
                                                Set<BlockPos> blocked) {
        BuildingRoomScanner.Result geometry = BuildingRoomScanner.scan(world, pos, blocked,
                Config.getInstance().maxBuildingSize, Config.getInstance().maxBuildingRadius, floor);
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
        if (scan.pendingStructure() != null) return commitInitialRoom(scan, forcedType);
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
        village.getBuildings().put(room.getId(), room);
        village.refreshAutomaticMainRoom(structure);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private Building.validationResult commitInitialRoom(BuildingScanResult scan, String forcedType) {
        Village village = scan.village();
        if (village == null) village = new Village(lastVillageId++, world);

        Structure structure = scan.pendingStructure();
        if (village.getStructureAt(structure.getSource()).isPresent()) {
            return Building.validationResult.IDENTICAL;
        }

        Building room = scan.building();
        String category = chooseInitialRoomCategory(scan, forcedType);
        if (category == null) return Building.validationResult.INVALID_TYPE;

        int groupId = registerInitialRoom(village, structure, room, category, forcedType != null);

        // An external basement entrance may be physically disconnected from the enclosed room
        // immediately above it. Register that stacked section as another physical Structure in the
        // same logical Building, including its first Room, during the original Add Building action.
        for (StructureScanner.Result stackedScan : StructureScanner.scanStacked(
                world, structure, village.getStructures().values())) {
            Structure stacked = stackedScan.toStructure(-1);
            BuildingScanResult stackedRoom = scanRoom(village, stacked, stackedScan.source(), -1);
            if (stackedRoom.result() != Building.validationResult.SUCCESS) continue;

            String stackedCategory = chooseInitialRoomCategory(stackedRoom, null);
            if (stackedCategory == null) continue;
            groupId = registerInitialRoom(
                    village, stacked, stackedRoom.building(), stackedCategory, false);
        }

        StructureGrouping.renumberStructureGroup(village, groupId);
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private int registerInitialRoom(Village village,
                                    Structure structure,
                                    Building room,
                                    String category,
                                    boolean typeForced) {
        int structureId = lastBuildingId++;
        structure.setId(structureId);
        int groupId = StructureGrouping.attachStructureGroup(village, structure);
        room.setId(lastBuildingId++);
        room.setStructureId(structureId);
        room.setType(category);
        room.setTypeForced(typeForced);
        structure.setAutomaticMainRoom(room.getId());
        village.getBuildings().put(room.getId(), room);
        village.getStructures().put(structureId, structure);
        return groupId;
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
        List<Building> currentFloorRooms = village.getRooms()
                .filter(room -> room.getStructureId() == update.structureId())
                .filter(room -> room.getFloorId() == update.floorId())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        if (!currentFloorRooms.stream().map(Building::getId).toList().equals(update.previousRoomIds())) {
            return Building.validationResult.OVERLAP;
        }
        Building playerRoom = village.getBuilding(update.expectedPlayerRoomId()).orElse(null);
        if (playerRoom == null || !currentFloorRooms.contains(playerRoom)) {
            return Building.validationResult.OVERLAP;
        }

        int nextRoomId = lastBuildingId;
        for (RegisteredRoomReconciler.Assignment assignment : update.assignments()) {
            Building component = assignment.component();
            if (component.getStructureId() != update.structureId()
                    || component.getFloorId() != update.floorId()
                    || component.getFloorFootprintArea() <= 0) {
                return Building.validationResult.OVERLAP;
            }
            int roomId = assignment.createsRoom() ? nextRoomId++ : assignment.roomId();
            Building previous = assignment.previous();
            if (previous != null && village.getBuilding(roomId).orElse(null) != previous) {
                return Building.validationResult.OVERLAP;
            }
            component.setId(roomId);
            component.setStructureId(update.structureId());
            component.setFloorId(update.floorId());
            if (previous != null) {
                component.setType(previous.getType());
                component.setTypeForced(previous.isTypeForced());
                component.setInheritanceEnabled(previous.isInheritanceEnabled());
            } else {
                component.setInheritanceEnabled(playerRoom.isInheritanceEnabled());
            }
        }
        List<Building> components = update.assignments().stream()
                .map(RegisteredRoomReconciler.Assignment::component)
                .toList();
        for (int i = 0; i < components.size(); i++) {
            for (int j = i + 1; j < components.size(); j++) {
                if (components.get(i).getFloorFootprintIntersectionArea(components.get(j)) > 0) {
                    return Building.validationResult.OVERLAP;
                }
            }
        }

        Set<Integer> removedRoomIds = update.removedRoomIds();
        int currentMainRoomId = village.getMainRoom(structure)
                .map(Building::getId)
                .orElse(-1);
        int prospectiveMainRoomId = removedRoomIds.contains(currentMainRoomId)
                ? update.expectedPlayerRoomId() : currentMainRoomId;
        List<Building> prospectiveRooms = village.getRooms()
                .filter(room -> !update.previousRoomIds().contains(room.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prospectiveRooms.addAll(components);
        Building prospectiveMain = prospectiveRooms.stream()
                .filter(room -> room.getId() == prospectiveMainRoomId)
                .findFirst().orElse(null);
        RoomTypeResolver resolver = RoomTypeResolver.create(
                village, prospectiveRooms);

        Building playerComponent = update.playerComponent();
        if (playerComponent == null) return Building.validationResult.OVERLAP;
        if (forcedType != null) {
            String selectedType = resolver.resolve(playerComponent, prospectiveMain).updatedType(forcedType);
            if (selectedType == null) return Building.validationResult.INVALID_TYPE;
            playerComponent.setType(selectedType);
            playerComponent.setTypeForced(true);
        }
        for (RegisteredRoomReconciler.Assignment assignment : update.assignments()) {
            if (!assignment.createsRoom()) continue;
            Building component = assignment.component();
            String type = resolver.resolve(component, prospectiveMain).updatedType(null);
            if (type == null) return Building.validationResult.INVALID_TYPE;
            component.setType(type);
            component.setTypeForced(false);
        }

        for (RegisteredRoomReconciler.Assignment assignment : update.assignments()) {
            Building component = assignment.component();
            if (assignment.previous() == null) continue;
            Building existing = assignment.previous();
            existing.copyScannedGeometryFrom(component, world, true);
            existing.setType(component.getType());
            existing.setTypeForced(component.isTypeForced());
            existing.setInheritanceEnabled(component.isInheritanceEnabled());
        }
        for (int removedRoomId : removedRoomIds) {
            village.getBuildings().remove(removedRoomId);
            village.transferMainRoom(structure, removedRoomId,
                    update.expectedPlayerRoomId(), update.structureId());
        }
        for (RegisteredRoomReconciler.Assignment assignment : update.assignments()) {
            if (!assignment.createsRoom()) continue;
            Building created = assignment.component();
            village.getBuildings().put(created.getId(), created);
        }
        lastBuildingId = nextRoomId;
        return Building.validationResult.SUCCESS;
    }

    private static String chooseRoomCategory(BuildingScanResult scan, String forcedType) {
        if (forcedType != null) return forcedType;
        if (scan.matchingTypes().size() == 1) return scan.matchingTypes().getFirst();
        if (!scan.matchingTypes().isEmpty()) return null;
        return "building";
    }

    private static String chooseInitialRoomCategory(BuildingScanResult scan, String forcedType) {
        if (forcedType != null) return forcedType;
        if (scan.matchingTypes().isEmpty()) return "house";
        return scan.matchingTypes().getFirst();
    }

    public Building.validationResult fullScan(Village village) {
        if (village == null) return Building.validationResult.NOT_IN_BUILDING;
        Building.validationResult result = Building.validationResult.SUCCESS;
        List<Integer> ids = village.getStructures().keySet().stream().sorted().toList();
        for (int id : ids) {
            Building.validationResult scanned = rescanStructure(village, id);
            if (result == Building.validationResult.SUCCESS && scanned != Building.validationResult.SUCCESS) {
                result = scanned;
            }
        }
        return result;
    }

    public Building.validationResult rescanStructure(Village village, int structureId) {
        Structure structure = village == null ? null : village.getStructure(structureId).orElse(null);
        if (structure == null) return Building.validationResult.NOT_IN_BUILDING;
        StructureScanner.Result scan = StructureScanner.scan(world, structure.getSource(),
                village.getStructures().values(), structureId);
        if (scan.result() != Building.validationResult.SUCCESS) return scan.result();
        List<Building> rooms = village.getRooms().filter(room -> room.getStructureId() == structureId).toList();

        // Rescan into a detached copy first so physical geometry and automatic Ground evidence
        // commit together. A failed match leaves the persisted Structure untouched.
        Structure updated = new Structure(structure.save());
        if (!updated.applyScan(scan, rooms)) return Building.validationResult.OVERLAP;
        int previousGroupId = structure.getStructureGroupId();

        List<RegisteredRoomUpdate> roomUpdates = new ArrayList<>();
        Map<Integer, List<Building>> roomsByFloor = rooms.stream()
                .collect(java.util.stream.Collectors.groupingBy(Building::getFloorId));
        for (Map.Entry<Integer, List<Building>> entry : roomsByFloor.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            Building expected = entry.getValue().stream()
                    .min(Comparator.comparingInt(Building::getId))
                    .orElseThrow();
            RegisteredRoomUpdate update = analyzeRegisteredFloor(
                    village, updated, expected, expected.getSourceBlock());
            if (update.result() != Building.validationResult.SUCCESS) return update.result();
            roomUpdates.add(update);
        }

        List<Building> roomSnapshots = rooms.stream()
                .map(room -> new Building(room.save()))
                .toList();
        Map<Integer, Structure> structureSnapshots = village.getStructures().values().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Structure::getId,
                        value -> new Structure(value.save())));
        int previousLastBuildingId = lastBuildingId;
        village.getStructures().put(structureId, updated);
        for (RegisteredRoomUpdate update : roomUpdates) {
            Building.validationResult result = applyRegisteredRoomUpdate(update, null, false);
            if (result == Building.validationResult.SUCCESS) continue;

            village.getBuildings().values().removeIf(
                    room -> room.isFunctionalRoom() && room.getStructureId() == structureId);
            roomSnapshots.forEach(room -> village.getBuildings().put(room.getId(), room));
            structureSnapshots.forEach((id, snapshot) -> village.getStructures().put(id, snapshot));
            lastBuildingId = previousLastBuildingId;
            return result;
        }
        int groupId = StructureGrouping.attachStructureGroup(village, updated);
        StructureGrouping.renumberStructureGroup(village, groupId);
        if (previousGroupId != groupId) {
            StructureGrouping.renumberStructureGroup(village, previousGroupId);
        }
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult rescanBuilding(Village village, int buildingId) {
        Building building = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (building == null) return Building.validationResult.TOO_SMALL;
        if (building instanceof ExternalBuilding external) {
            external.validateBlocks(world);
            setDirty();
            return Building.validationResult.SUCCESS;
        }
        RegisteredRoomUpdate update = analyzeRegisteredRoomUpdate(
                village, buildingId, building.getSourceBlock());
        return commitRegisteredRoomUpdate(update, null);
    }

    public BuildingEditResult forceRoomType(BlockPos pos, String type) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        Building room = village == null ? null : village.getFunctionalRoomAt(world, pos).orElse(null);
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
        Building room = village.getFunctionalRoomAt(world, pos).orElse(null);
        if (room == null) return village.hasStructuralBuildingAt(world, pos)
                ? BuildingEditResult.NO_ROOM : BuildingEditResult.NO_BUILDING;
        if (village.isMainRoom(room)) return BuildingEditResult.MAIN_ROOM;
        village.removeBuilding(room.getId());
        return BuildingEditResult.SUCCESS;
    }

    public BuildingEditResult removeBuilding(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) return BuildingEditResult.NO_BUILDING;
        Building target = village.getBuildingTarget(pos).orElse(null);
        if (target instanceof ExternalBuilding) {
            village.removeBuilding(target.getId());
            return BuildingEditResult.SUCCESS;
        }
        Structure structure = target != null && target.isFunctionalRoom()
                ? village.getStructure(target.getStructureId()).orElse(null)
                : village.getInteractionStructureAt(world, pos).orElse(null);
        if (structure == null) return BuildingEditResult.NO_BUILDING;
        List<Structure> groupStructures = village.getBuildingStructures(structure);
        groupStructures.forEach(s -> village.removeStructure(s.getId()));
        if (village.getBuildings().isEmpty() && village.getExternalBuildingMap().isEmpty()
                && village.getStructures().isEmpty()) removeVillage(village.getId());
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

    public Set<BlockPos> getBlockedSet(Village village) {
        Set<BlockPos> blocked = new HashSet<>();
        if (village != null) village.getRooms().forEach(room -> blocked.add(room.getSourceBlock()));
        return blocked;
    }

    public BuildingBlockedResult getBlockedResult(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Building existing = village == null ? null : village.getBuildingAt(pos).orElse(null);
        return new BuildingBlockedResult(village == null ? Set.of() : getBlockedSet(village), existing, village);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce) {
        return processBuilding(pos, enforce, null);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, String forcedType) {
        BuildingType external = getGroupedBuildingType(pos);
        if (external != null) return processExternalBuilding(pos, external);
        BuildingScanResult scan = analyzeRoomAddition(pos);
        if (scan.result() != Building.validationResult.SUCCESS) {
            if (enforce) {
                Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
                if (village != null) village.getStructureAt(pos).ifPresent(structure -> removeStructure(village, structure.getId()));
            }
            return scan.result();
        }
        return commitRoomAddition(scan, forcedType);
    }

    private void finalizeVillageMutation(Village target) {
        target.calculateDimensions();
        target.ensureMainRooms();
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
        MAIN_ROOM
    }

    public void setBuildingCooldown(int buildingCooldown) { this.buildingCooldown = buildingCooldown; }
    public void merge(Village into, Village from) { into.merge(from); }
}
