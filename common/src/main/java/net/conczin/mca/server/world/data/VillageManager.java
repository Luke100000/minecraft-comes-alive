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
            if (village.getBuildings().isEmpty() && village.getStructures().isEmpty()) {
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
        if (village != null && village.getStructureAt(pos).isPresent()) {
            // Auto Scan never registers optional Rooms inside known Structures.
            return Building.validationResult.SUCCESS;
        }
        InitialStructureScan scan = analyzeInitialStructure(pos);
        if (scan.result() != Building.validationResult.SUCCESS || scan.isRoomAmbiguous()) return scan.result();
        return commitInitialStructure(scan, null);
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

    public InitialStructureScan analyzeInitialStructure(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Collection<Structure> existing = village == null ? List.of() : village.getStructures().values();
        if (village != null && village.getStructureAt(pos).isPresent()) {
            StructureScanner.Result failure = StructureScanner.Result.failure(Building.validationResult.IDENTICAL, pos);
            return new InitialStructureScan(new StructureScanResult(failure.result(), failure, village, -1),
                    failedRoom(failure.result(), pos, village), null);
        }

        StructureScanner.Result structureScan = StructureScanner.scan(world, pos, existing, -1);
        MCA.LOGGER.info("[BlueprintStructureDebug] stage=initial-analysis-structure source={} result={} floors={} groundFloorId={} groundSeed={} bounds={}..{}",
                pos, structureScan.result(), debugFloors(structureScan.floors()), structureScan.groundFloorId(),
                structureScan.groundSeed(), structureScan.min(), structureScan.max());
        StructureScanResult structureResult = new StructureScanResult(structureScan.result(), structureScan, village, -1);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return new InitialStructureScan(structureResult, failedRoom(structureScan.result(), pos, village), null);
        }

        Structure candidate = structureScan.toStructure(-1);
        // Initial Structure creation already proved physical ownership. The mandatory first Room
        // must not be rejected merely because this floor has no local doorway (for example a basement
        // reached by stairs); explicit Add Room keeps the normal entrance requirement.
        BuildingScanResult room = scanRoom(village, candidate, pos, -1, true);
        if (room.result() != Building.validationResult.SUCCESS) {
            return new InitialStructureScan(structureResult, room, null);
        }
        // Root Room identity is physical-room identity, not merely Floor identity. A player may
        // start in a different Room on the Ground Floor, so always scan the Ground Floor candidate
        // and deduplicate only when the two Room footprints are actually identical.
        BuildingScanResult root = scanRoom(village, candidate, structureScan.groundSeed(), -1, true);
        if (root.result() != Building.validationResult.SUCCESS) {
            return new InitialStructureScan(structureResult, room, root);
        }
        boolean identicalRooms = root.building().isIdentical(room.building());
        MCA.LOGGER.info("[BlueprintStructureDebug] stage=initial-analysis-rooms source={} playerRoom={} rootCandidate={} identical={}",
                pos, debugRoom(room.building()), debugRoom(root.building()), identicalRooms);
        if (identicalRooms) {
            root = null;
        }
        return new InitialStructureScan(structureResult, room, root);
    }

    public BuildingScanResult analyzeRoom(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Structure structure = village == null ? null : village.getStructureAt(pos).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        if (village.getFunctionalRoomAt(world, pos).isPresent()) {
            return failedRoom(Building.validationResult.IDENTICAL, pos, village);
        }
        return scanRoom(village, structure, pos, -1, false);
    }

    public BuildingScanResult analyzeRegisteredRoom(Village village, int buildingId, BlockPos pos) {
        Building expected = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        Structure structure = village.getStructure(expected.getStructureId()).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        return scanRoom(village, structure, pos, buildingId, true);
    }

    public BuildingScanResult analyzeRegisteredRoomUpdate(Village village, int buildingId, BlockPos pos) {
        Building expected = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        Structure structure = village.getStructure(expected.getStructureId()).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);

        // Update Room refreshes physical Structure/Floor truth first, but only in a detached copy.
        // This keeps analysis/type-selection non-destructive and lets a remodel expand or shrink the
        // current Room without the stale Room footprint blocking the Floor rescan.
        StructureScanner.Result physical = StructureScanner.scan(
                world, structure.getSource(), village.getStructures().values(), structure.getId());
        if (physical.result() != Building.validationResult.SUCCESS) {
            return failedRoom(physical.result(), pos, village);
        }
        Structure refreshed = new Structure(structure.save());
        List<Building> rooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .toList();
        if (!refreshed.applyScanForRoomUpdate(physical, rooms, buildingId)) {
            return failedRoom(Building.validationResult.OVERLAP, pos, village);
        }
        if (refreshed.getFloor(expected.getFloorId()).isEmpty()) {
            return failedRoom(Building.validationResult.OVERLAP, pos, village);
        }
        return scanRoom(village, refreshed, pos, buildingId, true, refreshed);
    }

    private BuildingScanResult scanRoom(Village village,
                                        Structure structure,
                                        BlockPos pos,
                                        int existingRoomId,
                                        boolean allowMissingEntrance) {
        return scanRoom(village, structure, pos, existingRoomId, allowMissingEntrance, null);
    }

    private BuildingScanResult scanRoom(Village village,
                                        Structure structure,
                                        BlockPos pos,
                                        int existingRoomId,
                                        boolean allowMissingEntrance,
                                        Structure structureUpdate) {
        StructureFloor floor = existingRoomId >= 0 && village != null
                ? village.getBuilding(existingRoomId).flatMap(room -> structure.getFloor(room.getFloorId())).orElse(null)
                : structure.resolveFloor(world, pos).orElse(null);
        if (floor == null) return failedRoom(Building.validationResult.TOO_SMALL, pos, village);

        Set<BlockPos> blocked = new HashSet<>();
        // New Rooms may not consume registered Room space. Updates deliberately scan through
        // other registered Rooms so the commit phase can prove a physical merge and delete
        // only fully-covered stale duplicates; partial overlap remains a conflict.
        if (village != null && existingRoomId < 0) {
            village.getRooms().filter(room -> room.getStructureId() == structure.getId())
                    .filter(room -> room.getFloorId() == floor.id())
                    .flatMap(room -> room.getFloorRegions().stream())
                    .flatMap(region -> region.components().stream())
                    .flatMap(component -> component.spans().stream())
                    .forEach(span -> {
                        for (int x = span.minX(); x <= span.maxX(); x++) blocked.add(new BlockPos(x, floor.anchorY(), span.z()));
                    });
        }

        BuildingRoomScanner.Result geometry = BuildingRoomScanner.scan(world, pos, blocked,
                Config.getInstance().maxBuildingSize, Config.getInstance().maxBuildingRadius, structure, floor);
        Building room = new Building(pos);
        Building.validationResult result = room.applyRoomScan(world, geometry, allowMissingEntrance);
        if (result != Building.validationResult.SUCCESS) return failedRoom(result, pos, village);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        if (existingRoomId >= 0) room.setId(existingRoomId);

        List<String> types;
        if (village == null) {
            types = room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList();
        } else {
            types = village.getMatchingRoomTypes(structure.getId(), room).stream().map(BuildingType::name).toList();
            if (types.isEmpty()) {
                Building root = village.getBuilding(structure.getRootRoomId()).orElse(null);
                if (root != null && !root.getType().equals("building")) types = List.of(root.getType());
            }
        }
        MCA.LOGGER.info("[BlueprintStructureDebug] stage=room-scan source={} structureId={} existingRoomId={} floorId={} floorY={} geometryArea={} geometryBounds={}..{} room={} matchingTypes={}",
                pos, structure.getId(), existingRoomId, floor.id(), floor.anchorY(), geometry.footprintCells().size(),
                geometry.min(), geometry.max(), debugRoom(room), types);
        return new BuildingScanResult(Building.validationResult.SUCCESS, room.getSourceBlock(), room,
                types, village, existingRoomId, structure.getId(), floor.id(), structureUpdate);
    }

    private static String debugRoom(Building room) {
        if (room == null) return "null";
        return "id=" + room.getId() + ":structure=" + room.getStructureId() + ":floor=" + room.getFloorId()
                + ":source=" + room.getSourceBlock() + ":area=" + room.getFloorFootprintArea()
                + ":bounds=" + room.getRawPos0() + ".." + room.getRawPos1() + ":type=" + room.getType();
    }

    private static List<String> debugFloors(Collection<StructureFloor> floors) {
        return floors.stream().map(floor -> "id=" + floor.id() + ":y=" + floor.anchorY()
                + ":ceiling=" + floor.ceilingY() + ":area=" + floor.area()
                + ":components=" + floor.region().components().size()).toList();
    }

    private static BuildingScanResult failedRoom(Building.validationResult result, BlockPos pos, Village village) {
        return new BuildingScanResult(result, pos, new Building(pos), List.of(), village, -1, -1, -1);
    }

    public Building.validationResult commitInitialStructure(InitialStructureScan scan, String forcedRoomType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (forcedRoomType != null && !scan.room().matchesType(forcedRoomType)) return Building.validationResult.INVALID_TYPE;
        if (forcedRoomType == null && scan.room().isAmbiguous()) return Building.validationResult.INVALID_TYPE;

        Village village = scan.structure().village();
        if (village == null) village = new Village(lastVillageId++, world);
        if (village.getStructureAt(scan.structure().scan().source()).isPresent()) return Building.validationResult.IDENTICAL;

        int structureId = lastBuildingId++;
        Structure structure = scan.structure().scan().toStructure(structureId);
        Building playerRoom = scan.room().building();
        MCA.LOGGER.info("[BlueprintStructureDebug] stage=initial-commit-before structureId={} structureFloors={} playerRoom={} rootCandidate={} forcedRoomType={}",
                structureId, debugFloors(structure.getFloors()), debugRoom(playerRoom),
                scan.rootRoom() == null ? "SAME_AS_PLAYER" : debugRoom(scan.rootRoom().building()), forcedRoomType);
        String category = chooseCategory(scan.room(), forcedRoomType, null);
        if (category == null) return Building.validationResult.INVALID_TYPE;

        Building rootRoom = scan.rootRoom() == null ? playerRoom : scan.rootRoom().building();
        rootRoom.setId(lastBuildingId++);
        rootRoom.setStructureId(structureId);
        rootRoom.setFloorId(scan.rootRoom() == null ? playerRoom.getFloorId() : scan.rootRoom().floorId());
        rootRoom.setType(category);
        rootRoom.setTypeForced(forcedRoomType != null);
        structure.setRootRoomId(rootRoom.getId());
        village.getBuildings().put(rootRoom.getId(), rootRoom);

        if (rootRoom != playerRoom) {
            playerRoom.setId(lastBuildingId++);
            playerRoom.setStructureId(structureId);
            playerRoom.setFloorId(scan.room().floorId());
            playerRoom.setType(category);
            playerRoom.setTypeForced(forcedRoomType != null);
            village.getBuildings().put(playerRoom.getId(), playerRoom);
        }
        village.getStructures().put(structureId, structure);
        MCA.LOGGER.info("[BlueprintStructureDebug] stage=initial-commit-after structureId={} rootRoomId={} rootAndPlayerSame={} persistedRooms={}",
                structureId, structure.getRootRoomId(), rootRoom == playerRoom,
                village.getRooms().filter(candidate -> candidate.getStructureId() == structureId)
                        .map(VillageManager::debugRoom).toList());
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (scan.hasExistingBuilding()) return commitRegisteredRoomUpdate(scan, scan.existingBuildingId(), forcedType);
        if (forcedType != null && !scan.matchesType(forcedType)) return Building.validationResult.INVALID_TYPE;
        if (forcedType == null && scan.isAmbiguous()) return Building.validationResult.INVALID_TYPE;
        Village village = scan.village();
        Structure structure = village == null ? null : village.getStructure(scan.structureId()).orElse(null);
        if (village == null || structure == null) return Building.validationResult.NOT_IN_BUILDING;

        Building room = scan.building();
        String inherited = village.getBuilding(structure.getRootRoomId()).map(Building::getType).orElse(null);
        String category = chooseCategory(scan, forcedType, inherited);
        if (category == null) return Building.validationResult.INVALID_TYPE;
        room.setId(lastBuildingId++);
        room.setStructureId(structure.getId());
        room.setFloorId(scan.floorId());
        room.setType(category);
        room.setTypeForced(forcedType != null);
        village.getBuildings().put(room.getId(), room);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitRegisteredRoomUpdate(BuildingScanResult scan,
                                                                  int expectedRoomId,
                                                                  String forcedType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        Village village = scan.village();
        Building existing = village == null ? null : village.getBuilding(expectedRoomId).orElse(null);
        if (existing == null || scan.existingBuildingId() != expectedRoomId) return Building.validationResult.OVERLAP;
        if (forcedType != null && !scan.matchesType(forcedType)) return Building.validationResult.INVALID_TYPE;
        if (forcedType == null && scan.isAmbiguous()) return Building.validationResult.INVALID_TYPE;

        Structure structure = village.getStructure(existing.getStructureId()).orElse(null);
        if (structure == null) return Building.validationResult.NOT_IN_BUILDING;
        Structure preparedStructure = scan.structureUpdate();
        if (preparedStructure != null
                && (preparedStructure.getId() != structure.getId()
                || preparedStructure.getFloor(existing.getFloorId()).isEmpty())) {
            return Building.validationResult.OVERLAP;
        }

        Building scanned = scan.building();
        List<Building> stale = new ArrayList<>();
        for (Building other : village.getRooms().filter(room -> room.getId() != expectedRoomId)
                .filter(room -> room.getStructureId() == existing.getStructureId())
                .filter(room -> room.getFloorId() == existing.getFloorId()).toList()) {
            long intersection = scanned.getFloorFootprintIntersectionArea(other);
            if (intersection == 0) continue;
            if (intersection != other.getFloorFootprintArea()) return Building.validationResult.OVERLAP;
            stale.add(other);
        }

        // All validation is complete: commit physical Structure/Floor geometry and Room geometry together.
        if (preparedStructure != null) {
            structure.copyPhysicalGeometryFrom(preparedStructure);
        }
        existing.copyScannedGeometryFrom(scanned, world, true);
        if (forcedType != null) {
            existing.setType(forcedType);
            existing.setTypeForced(true);
        } else if (scan.matchingTypes().size() == 1) {
            existing.setType(scan.matchingTypes().getFirst());
            existing.setTypeForced(false);
        }

        if (stale.stream().anyMatch(room -> structure.isRootRoom(room.getId()))) {
            structure.setRootRoomId(existing.getId());
        }
        village.removeBuildings(stale.stream().map(Building::getId).toList());
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }


    private static String chooseCategory(BuildingScanResult scan, String forcedType, String inherited) {
        if (forcedType != null) return forcedType;
        if (scan.matchingTypes().size() == 1) return scan.matchingTypes().getFirst();
        // A new Room with no special match inherits the Root Room category. During initial
        // Structure creation there is no Root Room yet, so use MCA's generic functional house
        // category rather than resurrecting the old fake structural "building" type.
        if (scan.matchingTypes().isEmpty()) return inherited == null ? "house" : inherited;
        return null;
    }

    public void fullScan(Village village) {
        if (village == null) return;
        List<Integer> ids = village.getStructures().keySet().stream().sorted().toList();
        ids.forEach(id -> rescanStructure(village, id));
    }

    public Building.validationResult rescanStructure(Village village, int structureId) {
        Structure structure = village == null ? null : village.getStructure(structureId).orElse(null);
        if (structure == null) return Building.validationResult.NOT_IN_BUILDING;
        StructureScanner.Result scan = StructureScanner.scan(world, structure.getSource(),
                village.getStructures().values(), structureId);
        if (scan.result() != Building.validationResult.SUCCESS) return scan.result();
        List<Building> rooms = village.getRooms().filter(room -> room.getStructureId() == structureId).toList();

        // Rescan into a detached copy first. Structure geometry and zero-Room recovery commit
        // together, so a failed recovery cannot leave a half-updated Structure behind.
        Structure updated = new Structure(structure.save());
        if (!updated.applyScan(scan, rooms)) return Building.validationResult.OVERLAP;

        Building recovered = null;
        if (rooms.isEmpty()) {
            StructureFloor ground = updated.resolveFloor(world, scan.groundSeed()).orElse(null);
            if (ground == null) return Building.validationResult.TOO_SMALL;
            BuildingScanResult recovery = scanRoom(village, updated, StructureScanner.bestSeed(ground), -1, true);
            if (recovery.result() != Building.validationResult.SUCCESS) return recovery.result();
            recovered = recovery.building();
            recovered.setId(lastBuildingId++);
            recovered.setStructureId(structureId);
            recovered.setFloorId(ground.id());
            if (!recovered.determineType()) recovered.setType("house");
            updated.setRootRoomId(recovered.getId());
        }

        village.getStructures().put(structureId, updated);
        if (recovered != null) village.getBuildings().put(recovered.getId(), recovered);
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
        BuildingScanResult scan = analyzeRegisteredRoom(village, buildingId, building.getSourceBlock());
        return commitRegisteredRoomUpdate(scan, buildingId, null);
    }

    public BuildingEditResult forceRoomType(BlockPos pos, String type) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        Building room = village == null ? null : village.getFunctionalRoomAt(world, pos).orElse(null);
        if (room == null) return BuildingEditResult.NO_BUILDING;
        if (room.getType().equals(type)) {
            room.setTypeForced(false);
            room.determineType();
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
        if (village.isRootRoom(room)) return BuildingEditResult.ROOT_ROOM;
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
                : village.getStructureAt(pos).orElse(null);
        if (structure == null) return BuildingEditResult.NO_BUILDING;
        removeStructure(village, structure.getId());
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

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan) {
        return processBuilding(pos, enforce, strictScan, null);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan, String forcedType) {
        BuildingType external = getGroupedBuildingType(pos);
        if (external != null) return processExternalBuilding(pos, external);
        InitialStructureScan scan = analyzeInitialStructure(pos);
        if (scan.result() != Building.validationResult.SUCCESS) {
            if (enforce) {
                Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
                if (village != null) village.getStructureAt(pos).ifPresent(structure -> removeStructure(village, structure.getId()));
            }
            return scan.result();
        }
        return commitInitialStructure(scan, forcedType);
    }

    private void finalizeVillageMutation(Village target) {
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
        ROOT_ROOM
    }

    public void setBuildingCooldown(int buildingCooldown) { this.buildingCooldown = buildingCooldown; }
    public void merge(Village into, Village from) { into.merge(from); }
}
