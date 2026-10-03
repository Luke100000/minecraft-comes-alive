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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class VillageManager extends SavedData implements Iterable<Village> {
    static final long ORIGIN_GEOMETRY_REFRESH_INITIAL_DELAY = 20L;
    static final long ORIGIN_GEOMETRY_REFRESH_MAX_DELAY = 20L * 60L;
    private static final int ORIGIN_GEOMETRY_REFRESH_MAX_FAILURES = 6;

    public final Set<BlockPos> cache = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Village> villages = new HashMap<>();
    private final List<BlockPos> buildingQueue = new LinkedList<>();
    private final Map<OriginGeometryRefreshKey, OriginGeometryRefreshRetry> originGeometryRefreshRetries = new HashMap<>();
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
        lastBuildingId = nbt.getInt("lastBuildingId").orElse(0);
        lastVillageId = nbt.getInt("lastVillageId").orElse(0);
        reapers = nbt.getCompound("reapers")
                .map(tag -> new ReaperSpawner(this, tag)).orElseGet(() -> new ReaperSpawner(this));
        ListTag villageList = nbt.getList("villages").orElseGet(ListTag::new);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i).orElseGet(CompoundTag::new), world);
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
        scheduleOriginGeometryRefreshes(world.getGameTime());
    }

    public static VillageManager get(ServerLevel world) {
        return WorldUtils.loadData(world, (nbt, provider) -> new VillageManager(world, nbt),
                VillageManager::new, "mca_villages");
    }

    public ReaperSpawner getReaperSpawner() { return reapers; }
    public Optional<Village> getOrEmpty(int id) { return Optional.ofNullable(villages.get(id)); }

    public boolean removeVillage(int id) {
        if (villages.remove(id) != null) {
            originGeometryRefreshRetries.keySet().removeIf(key -> key.villageId() == id);
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

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("lastBuildingId", lastBuildingId);
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", NbtHelper.fromList(villages.values(), Village::save));
        nbt.put("reapers", reapers.writeNbt());
        return nbt;
    }

    public void tick() {
        if (world.getOverworldClockTime() % 100 == 0) {
            world.players().forEach(player -> PlayerSaveData.get(player).updateLastSeenVillage(this, player));
        }
        int interval = Config.getInstance().bountyHunterInterval;
        if (interval > 0 && world.getOverworldClockTime() % Math.max(1, interval / 10) == 0
                && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.players().forEach(player -> {
                if (world.getRandom().nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.blockPosition()) && !player.isCreative()) {
                    villages.values().stream().filter(village -> village.getPopulation() >= 3)
                            .filter(village -> village.getReputation(player) < Config.getInstance().bountyHunterHearts)
                            .min(Comparator.comparingInt(village -> village.getReputation(player)))
                            .ifPresent(village -> startBountyHunterWave(player, village));
                }
            });
        }

        long time = world.getGameTime();
        for (Village village : this) village.tick(world, time);
        tickOriginGeometryRefresh(time);
        if (time % buildingCooldown == 0 && !buildingQueue.isEmpty()) {
            processBuilding(buildingQueue.removeFirst());
        }
        reapers.tick(world);
        SpawnQueue.getInstance().tick();
    }

    static long originGeometryRefreshDelay(int failures) {
        int boundedFailures = Math.max(0, Math.min(failures, ORIGIN_GEOMETRY_REFRESH_MAX_FAILURES));
        long delay = ORIGIN_GEOMETRY_REFRESH_INITIAL_DELAY << boundedFailures;
        return Math.min(delay, ORIGIN_GEOMETRY_REFRESH_MAX_DELAY);
    }

    private void scheduleOriginGeometryRefreshes(long time) {
        for (Village village : villages.values()) {
            for (Structure structure : village.getStructures().values()) {
                if (!structure.hasOriginGeometryApproximation()) continue;
                OriginGeometryRefreshKey key = new OriginGeometryRefreshKey(village.getId(), structure.getId());
                originGeometryRefreshRetries.putIfAbsent(
                        key, new OriginGeometryRefreshRetry(time + ORIGIN_GEOMETRY_REFRESH_INITIAL_DELAY, 0));
            }
        }
    }

    void tickOriginGeometryRefresh(long time) {
        Map.Entry<OriginGeometryRefreshKey, OriginGeometryRefreshRetry> due = originGeometryRefreshRetries.entrySet()
                .stream()
                .filter(entry -> entry.getValue().retryAt() <= time)
                .min(Comparator
                        .comparingInt((Map.Entry<OriginGeometryRefreshKey, OriginGeometryRefreshRetry> entry) ->
                                entry.getKey().villageId())
                        .thenComparingInt(entry -> entry.getKey().structureId()))
                .orElse(null);
        if (due == null) return;

        OriginGeometryRefreshKey key = due.getKey();
        Village village = villages.get(key.villageId());
        Structure structure = village == null ? null : village.getStructure(key.structureId()).orElse(null);
        if (structure == null || !structure.hasOriginGeometryApproximation()) {
            originGeometryRefreshRetries.remove(key);
            return;
        }

        Building room = village.getRooms()
                .filter(candidate -> candidate.getStructureId() == structure.getId())
                .filter(candidate -> candidate.getFloorId() == 0)
                .min(Comparator.comparingInt(Building::getId))
                .orElse(null);
        if (room == null) {
            rescheduleOriginGeometryRefresh(key, due.getValue(), time);
            return;
        }
        BlockPos source = room.getSourceBlock();
        if (world.getChunkSource().getChunkNow(source.getX() >> 4, source.getZ() >> 4) == null) {
            originGeometryRefreshRetries.put(
                    key, new OriginGeometryRefreshRetry(time + ORIGIN_GEOMETRY_REFRESH_INITIAL_DELAY,
                            due.getValue().failures()));
            return;
        }

        RegisteredRoomUpdate update = new RoomWorkflow(this, world).analyzeRegisteredRoomUpdate(
                village, room.getId(), source);
        Building.validationResult result = applyRegisteredRoomUpdate(update, null, false);
        if (result == Building.validationResult.SUCCESS) {
            originGeometryRefreshRetries.remove(key);
            finalizeVillageMutation(village);
            return;
        }
        rescheduleOriginGeometryRefresh(key, due.getValue(), time);
    }

    private void rescheduleOriginGeometryRefresh(OriginGeometryRefreshKey key,
                                                 OriginGeometryRefreshRetry previous,
                                                 long time) {
        int failures = Math.min(previous.failures() + 1, ORIGIN_GEOMETRY_REFRESH_MAX_FAILURES);
        originGeometryRefreshRetries.put(
                key, new OriginGeometryRefreshRetry(time + originGeometryRefreshDelay(failures), failures));
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
        //spawn the bois
        for (int c = 0; c < count; c++) {
            if (world.getRandom().nextBoolean()) {
                spawnBountyHunter(EntityTypes.PILLAGER, player);
            } else {
                spawnBountyHunter(EntityTypes.VINDICATOR, player);
            }
        }

        //warn the player
        player.sendSystemMessage(Component.translatable(sender.getPopulation() == 0 ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).withStyle(ChatFormatting.RED));
        //civil entry
        sender.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends AbstractIllager> void spawnBountyHunter(EntityType<T> type, ServerPlayer player) {
        T illager = type.create(world, EntitySpawnReason.EVENT);
        if (illager == null) return;
        for (int attempt = 0; attempt < 32; attempt++) {
            float angle = world.getRandom().nextFloat() * 6.2831855F;
            int x = (int) (player.getX() + Mth.cos(angle) * 32.0f);
            int z = (int) (player.getZ() + Mth.sin(angle) * 32.0f);
            int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
            BlockPos pos = new BlockPos(x, y, z);
            if (SpawnPlacements.isSpawnPositionOk(type, world, pos)) {
                illager.setPos(x, y, z);
                illager.setTarget(player);
                WorldUtils.spawnEntity(world, illager, EntitySpawnReason.EVENT);
                break;
            }
        }
    }

    public void reportBuilding(BlockPos pos) {
        if (cache.add(pos)) buildingQueue.add(pos);
    }

    public Building.validationResult processBuilding(BlockPos pos) {
        BuildingType externalType = getGroupedBuildingType(pos);
        if (externalType != null) {
            Building.validationResult result = processExternalBuilding(pos, externalType);
            if (result != Building.validationResult.SUCCESS) cache.remove(pos);
            return result;
        }

        Village village = findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        if (village != null && village.getInteractionStructureAt(pos).isPresent()) {
            // Auto Scan never registers optional Rooms inside known Structures.
            return Building.validationResult.SUCCESS;
        }
        BuildingScanResult scan = new RoomWorkflow(this, world).analyzeReportedBuildingAddition(pos);
        if (scan.result() != Building.validationResult.SUCCESS || scan.isAmbiguous()) {
            cache.remove(pos);
            return scan.result();
        }
        Building.validationResult result = commitRoomAddition(scan, null);
        if (result != Building.validationResult.SUCCESS) cache.remove(pos);
        return result;
    }

    private BuildingType getGroupedBuildingType(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        Identifier id = BuiltInRegistries.BLOCK.getKey(block);
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
        village.calculateDimensions();
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (forcedType != null && !scan.matchesType(forcedType)) return Building.validationResult.INVALID_TYPE;
        if (forcedType == null && scan.isAmbiguous()) return Building.validationResult.INVALID_TYPE;
        Structure pending = scan.pendingStructure();
        if (pending != null) {
            Village village = scan.village();
            if (pending.getId() >= 0) {
                return village != null && village.getStructure(pending.getId()).isPresent()
                        ? commitExpandedRoom(scan, forcedType)
                        : Building.validationResult.NOT_IN_BUILDING;
            }
            return commitInitialRoom(scan, forcedType);
        }
        if (scan.building().getId() >= 0) return Building.validationResult.OVERLAP;

        Village village = scan.village();
        Structure structure = village == null ? null : village.getStructureFor(scan.building()).orElse(null);
        if (village == null || structure == null) return Building.validationResult.NOT_IN_BUILDING;

        Building room = scan.building().copy();
        String category = chooseRoomCategory(scan, forcedType);
        if (category == null) return Building.validationResult.INVALID_TYPE;
        room.setId(lastBuildingId);
        room.setType(category);
        room.setTypeForced(forcedType != null);
        village.publishBuildingMutation(() -> village.registerRoom(room));
        lastBuildingId++;
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
        if (room.getId() >= 0) {
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

        Structure committedStructure = structure.copy();
        committedStructure.setId(lastBuildingId);
        Building committedRoom = room.copy();
        committedRoom.setId(lastBuildingId + 1);
        committedRoom.setStructureId(committedStructure.getId());
        committedRoom.setType(category);
        committedRoom.setTypeForced(forcedType != null);
        Village targetVillage = village;
        targetVillage.publishBuildingMutation(() -> targetVillage.registerStructure(committedStructure, committedRoom));
        lastBuildingId += 2;
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
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
        if (requireTypeChoice && forcedType == null && update.requiresTypeSelection()) {
            return Building.validationResult.INVALID_TYPE;
        }

        Village village = update.village();
        Building current = village == null ? null : village.getBuilding(update.expectedRoomId()).orElse(null);
        if (current == null || !current.isFunctionalRoom()) {
            return Building.validationResult.OVERLAP;
        }
        Structure structure = village.getStructureFor(current).orElse(null);
        if (structure == null || structure.getFloor(current.getFloorId()).isEmpty()) {
            return Building.validationResult.NOT_IN_BUILDING;
        }
        Structure refreshed = update.refreshedStructure();
        Building replacement = update.replacementRoom() == null ? null : update.replacementRoom().copy();
        StructureFloor refreshedFloor = refreshed == null || refreshed.getId() != current.getStructureId()
                ? null : refreshed.getFloor(current.getFloorId()).orElse(null);
        if (replacement == null || refreshedFloor == null
                || replacement.getId() != current.getId()
                || replacement.getStructureId() != current.getStructureId()
                || replacement.getFloorId() != current.getFloorId()
                || refreshedFloor.geometry().roomIdentityOverlapCount(
                current.getFloorCells(), replacement.getFloorCells()) == 0) {
            return Building.validationResult.OVERLAP;
        }

        List<Building> siblingRooms = village.getRooms()
                .filter(room -> room.getStructureId() == current.getStructureId())
                .filter(room -> room.getFloorId() == current.getFloorId())
                .filter(room -> room.getId() != current.getId())
                .toList();
        if (siblingRooms.stream().anyMatch(room -> refreshedFloor.geometry().roomIdentityOverlapCount(
                room.getFloorCells(), replacement.getFloorCells()) > 0)) {
            return Building.validationResult.OVERLAP;
        }
        if (forcedType != null) {
            replacement.setType(forcedType);
            replacement.setTypeForced(true);
        } else if (current.isTypeForced()) {
            replacement.setType(current.getType());
            replacement.setTypeForced(true);
        } else {
            List<Building> prospectiveRooms = village.getRooms()
                    .filter(room -> room.getId() != current.getId())
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            prospectiveRooms.add(replacement);
            String resolvedType = RoomTypeResolver.create(village, prospectiveRooms)
                    .resolve(replacement).updatedType(null);
            if (resolvedType == null) return Building.validationResult.INVALID_TYPE;
            replacement.setType(resolvedType);
            replacement.setTypeForced(false);
        }
        replacement.setContributesToMain(current.contributesToMain());

        List<Building> replacementRooms = new ArrayList<>(siblingRooms);
        replacementRooms.add(replacement);
        return village.publishFloorRefresh(refreshed, current.getFloorId(), replacementRooms)
                ? Building.validationResult.SUCCESS
                : Building.validationResult.OVERLAP;
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

        for (int roomId : fullScanRoomIds(village)) {
            Building room = village.getBuilding(roomId).orElse(null);
            if (room == null) continue;
            RegisteredRoomUpdate update = new RoomWorkflow(this, world).analyzeRegisteredRoomUpdate(
                    village, roomId, room.getSourceBlock());
            if (update.result() != Building.validationResult.SUCCESS) {
                village.restoreBuildingState(snapshot);
                return update.result();
            }
            Building.validationResult result = applyRegisteredRoomUpdate(update, null, false);
            if (result == Building.validationResult.SUCCESS) continue;

            village.restoreBuildingState(snapshot);
            return result;
        }
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public BuildingEditResult forceRoomType(BlockPos pos, String type) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        Building room = village == null ? null : village.findInteractionRoomAt(pos).orElse(null);
        if (room == null) return BuildingEditResult.NO_BUILDING;
        boolean forced = !room.getType().equals(type);
        String resolvedType = forced ? type : RoomTypeResolver.create(village).resolve(room).updatedType(null);
        village.setRoomType(room, resolvedType, forced);
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
            village.publishBuildingMutation(() -> village.removeRooms(orphanedRoomIds));
            return BuildingEditResult.SUCCESS;
        }
        Structure structure = target != null && target.isFunctionalRoom()
                ? village.getStructure(target.getStructureId()).orElse(null)
                : village.getExactStructureAt(pos)
                .or(() -> village.getInteractionStructureAt(pos))
                .orElse(null);
        if (structure == null) return BuildingEditResult.NO_BUILDING;

        village.publishBuildingMutation(() -> village.removeLogicalBuilding(structure.getLogicalBuildingId()));

        if (village.getBuildings().isEmpty() && village.getExternalBuildingMap().isEmpty()
                && village.getStructures().isEmpty()) {
            removeVillage(village.getId());
            setDirty();
        } else {
            finalizeVillageMutation(village);
        }
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
    public void merge(Village into, Village from) {
        into.merge(from);
        if (into.getId() == from.getId()) return;

        List<Map.Entry<OriginGeometryRefreshKey, OriginGeometryRefreshRetry>> movedRetries =
                originGeometryRefreshRetries.entrySet().stream()
                        .filter(entry -> entry.getKey().villageId() == from.getId())
                        .toList();
        for (Map.Entry<OriginGeometryRefreshKey, OriginGeometryRefreshRetry> entry : movedRetries) {
            originGeometryRefreshRetries.remove(entry.getKey());
            int structureId = entry.getKey().structureId();
            Structure moved = into.getStructure(structureId).orElse(null);
            if (moved == null || !moved.hasOriginGeometryApproximation()) continue;

            OriginGeometryRefreshKey movedKey = new OriginGeometryRefreshKey(into.getId(), structureId);
            originGeometryRefreshRetries.merge(movedKey, entry.getValue(),
                    (first, second) -> first.retryAt() <= second.retryAt() ? first : second);
        }
    }

    private record OriginGeometryRefreshKey(int villageId, int structureId) {
    }

    private record OriginGeometryRefreshRetry(long retryAt, int failures) {
    }
}
