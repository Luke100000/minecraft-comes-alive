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
        if (village != null && village.getInteractionStructureAt(world, pos).isPresent()) {
            StructureScanner.Result failure = StructureScanner.Result.failure(Building.validationResult.IDENTICAL, pos);
            return new InitialStructureScan(failure, village,
                    failedRoom(failure.result(), pos, village));
        }

        StructureScanner.Result structureScan = StructureScanner.scan(world, pos, existing, -1);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return new InitialStructureScan(structureScan, village,
                    failedRoom(structureScan.result(), pos, village));
        }

        Structure candidate = structureScan.toStructure(-1);
        BuildingScanResult room = scanRoom(village, candidate, pos, -1);
        return new InitialStructureScan(structureScan, village, room);
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

    public BuildingScanResult analyzeRegisteredRoomUpdate(Village village, int buildingId, BlockPos pos) {
        Building expected = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        }
        Structure structure = village.getStructureFor(expected).orElse(null);
        if (structure == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, pos, village);
        StructureFloor floor = structure.getFloor(expected.getFloorId()).orElse(null);
        if (floor == null) return failedRoom(Building.validationResult.OVERLAP, pos, village);

        List<BuildingScanResult> components = BuildingRoomScanner.partition(
                        world, expected.getSourceBlock(), Config.getInstance().maxBuildingSize,
                        Config.getInstance().maxBuildingRadius, floor).stream()
                .map(geometry -> roomResultFromGeometry(village, structure, floor, geometry, -1))
                .filter(scan -> scan.result() == Building.validationResult.SUCCESS)
                .filter(scan -> scan.building().getFloorFootprintIntersectionArea(expected) > 0)
                .toList();
        if (components.isEmpty()) return failedRoom(Building.validationResult.TOO_SMALL, pos, village);

        BuildingScanResult retained = selectRetainedRoom(expected, components);
        retained.building().setId(buildingId);
        List<Building> created = new ArrayList<>();
        for (BuildingScanResult component : components) {
            if (component == retained) continue;
            Building room = component.building();
            long overlap = room.getFloorFootprintIntersectionArea(expected);
            if (overlap != room.getFloorFootprintArea()) {
                return failedRoom(Building.validationResult.OVERLAP, pos, village);
            }
            if (overlapsRegisteredRoom(village, expected, room)) {
                return failedRoom(Building.validationResult.OVERLAP, pos, village);
            }
            created.add(room);
        }

        Optional<List<Integer>> absorbed = findAbsorbedRooms(village, expected, retained.building());
        if (absorbed.isEmpty()) return failedRoom(Building.validationResult.OVERLAP, pos, village);
        return new BuildingScanResult(retained.result(), retained.source(), retained.building(), retained.matchingTypes(),
                retained.village(), absorbed.get(), created);
    }

    private static BuildingScanResult selectRetainedRoom(Building expected, List<BuildingScanResult> components) {
        Comparator<BuildingScanResult> preference = Comparator
                .comparing((BuildingScanResult scan) -> !scan.building().containsFloorPosition(expected.getSourceBlock()))
                .thenComparing(Comparator.comparingLong((BuildingScanResult scan) ->
                        scan.building().getFloorFootprintIntersectionArea(expected)).reversed())
                .thenComparing(Comparator.comparingLong((BuildingScanResult scan) ->
                        scan.building().getFloorFootprintArea()).reversed())
                .thenComparingInt(scan -> scan.building().getRawPos0().getX())
                .thenComparingInt(scan -> scan.building().getRawPos0().getZ());
        return components.stream().min(preference).orElseThrow();
    }

    private static boolean overlapsRegisteredRoom(Village village, Building expected, Building candidate) {
        return village.getRooms()
                .filter(room -> room.getId() != expected.getId())
                .filter(room -> room.getStructureId() == expected.getStructureId())
                .filter(room -> room.getFloorId() == expected.getFloorId())
                .anyMatch(room -> candidate.getFloorFootprintIntersectionArea(room) > 0);
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
                types, village, List.of());
    }

    private static boolean validCreatedRooms(Village village, Building existing, Building retained,
                                             List<Building> createdRooms) {
        for (int i = 0; i < createdRooms.size(); i++) {
            Building created = createdRooms.get(i);
            if (created.getId() >= 0
                    || created.getStructureId() != existing.getStructureId()
                    || created.getFloorId() != existing.getFloorId()
                    || created.getFloorFootprintIntersectionArea(existing) != created.getFloorFootprintArea()
                    || created.getFloorFootprintIntersectionArea(retained) > 0) {
                return false;
            }
            for (int j = i + 1; j < createdRooms.size(); j++) {
                if (created.getFloorFootprintIntersectionArea(createdRooms.get(j)) > 0) return false;
            }
            boolean overlapsRegistered = village.getRooms()
                    .filter(room -> room.getId() != existing.getId())
                    .filter(room -> room.getStructureId() == existing.getStructureId())
                    .filter(room -> room.getFloorId() == existing.getFloorId())
                    .anyMatch(room -> created.getFloorFootprintIntersectionArea(room) > 0);
            if (overlapsRegistered) return false;
        }
        return true;
    }

    private static Optional<List<Integer>> findAbsorbedRooms(Village village,
                                                             Building existing,
                                                             Building scanned) {
        List<Integer> absorbed = new ArrayList<>();
        for (Building other : village.getRooms()
                .filter(room -> room.getId() != existing.getId())
                .filter(room -> room.getStructureId() == existing.getStructureId())
                .filter(room -> room.getFloorId() == existing.getFloorId())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList()) {
            long intersection = scanned.getFloorFootprintIntersectionArea(other);
            if (intersection == 0) continue;
            if (intersection != other.getFloorFootprintArea()) return Optional.empty();
            absorbed.add(other.getId());
        }
        return Optional.of(List.copyOf(absorbed));
    }

    private static BuildingScanResult failedRoom(Building.validationResult result, BlockPos pos, Village village) {
        return new BuildingScanResult(result, pos, new Building(pos), List.of(), village, List.of());
    }

    public Building.validationResult commitInitialStructure(InitialStructureScan scan, String forcedRoomType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (forcedRoomType != null && !scan.room().matchesType(forcedRoomType)) return Building.validationResult.INVALID_TYPE;
        if (forcedRoomType == null && scan.room().isAmbiguous()) return Building.validationResult.INVALID_TYPE;

        Village village = scan.village();
        if (village == null) village = new Village(lastVillageId++, world);
        if (village.getStructureAt(scan.structure().source()).isPresent()) return Building.validationResult.IDENTICAL;

        int structureId = lastBuildingId++;
        Structure structure = scan.structure().toStructure(structureId);
        Building room = scan.room().building();
        String category = chooseRootCategory(scan.room(), forcedRoomType);
        if (category == null) return Building.validationResult.INVALID_TYPE;
        room.setId(lastBuildingId++);
        room.setStructureId(structureId);
        room.setType(category);
        room.setTypeForced(forcedRoomType != null);
        room.setLayoutOverride(false);
        village.getBuildings().put(room.getId(), room);
        village.getStructures().put(structureId, structure);
        villages.put(village.getId(), village);
        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
        if (scan == null || scan.result() != Building.validationResult.SUCCESS) return scan == null
                ? Building.validationResult.TOO_SMALL : scan.result();
        if (scan.building().getId() >= 0) return commitRegisteredRoomUpdate(scan, scan.building().getId(), forcedType);
        if (forcedType != null && !scan.matchesType(forcedType)) return Building.validationResult.INVALID_TYPE;
        if (forcedType == null && scan.isAmbiguous()) return Building.validationResult.INVALID_TYPE;
        Village village = scan.village();
        Structure structure = village == null ? null : village.getStructureFor(scan.building()).orElse(null);
        if (village == null || structure == null) return Building.validationResult.NOT_IN_BUILDING;

        Building room = scan.building();
        String category = chooseCategory(scan, forcedType, false);
        if (category == null) return Building.validationResult.INVALID_TYPE;
        room.setId(lastBuildingId++);
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
        if (existing == null || scan.building().getId() != expectedRoomId) return Building.validationResult.OVERLAP;
        Structure currentStructure = village.getStructureFor(existing).orElse(null);
        if (currentStructure == null) return Building.validationResult.NOT_IN_BUILDING;

        if (scan.building().getStructureId() != existing.getStructureId()
                || scan.building().getFloorId() != existing.getFloorId()
                || currentStructure.getFloor(existing.getFloorId()).isEmpty()) {
            return Building.validationResult.OVERLAP;
        }

        Optional<List<Integer>> absorbed = findAbsorbedRooms(village, existing, scan.building());
        if (absorbed.isEmpty() || !absorbed.get().equals(scan.absorbedRoomIds())
                || !validCreatedRooms(village, existing, scan.building(), scan.createdRooms())) {
            return Building.validationResult.OVERLAP;
        }
        List<Integer> absorbedRoomIds = absorbed.get();
        boolean inheritanceEnabled = existing.isInheritanceEnabled();
        boolean layoutOverride = existing.isLayoutOverride() || absorbedRoomIds.stream()
                .map(village::getBuilding)
                .flatMap(Optional::stream)
                .anyMatch(Building::isLayoutOverride);
        scan.building().setInheritanceEnabled(inheritanceEnabled);
        scan.building().setLayoutOverride(layoutOverride);
        scan.createdRooms().forEach(room -> {
            room.setInheritanceEnabled(inheritanceEnabled);
            room.setLayoutOverride(false);
        });

        List<Building> prospectiveRooms = village.getRooms()
                .filter(room -> room.getId() != existing.getId() && !absorbedRoomIds.contains(room.getId()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prospectiveRooms.add(scan.building());
        prospectiveRooms.addAll(scan.createdRooms());

        StructureLayout.Layout layout = StructureLayout.build(village);
        Building currentMain = layout.buildingFor(existing.getStructureId())
                .map(StructureLayout.LogicalBuilding::rootRoomId)
                .flatMap(village::getBuilding)
                .filter(Building::isFunctionalRoom)
                .orElse(null);
        Building prospectiveMain = currentMain;
        if (currentMain != null && (currentMain.getId() == existing.getId()
                || absorbedRoomIds.contains(currentMain.getId()))) {
            prospectiveMain = scan.building();
        }

        RoomTypeResolver resolver = RoomTypeResolver.create(village, layout, prospectiveRooms);
        String requestedForcedType = forcedType != null ? forcedType
                : existing.isTypeForced() ? existing.getType() : null;
        RoomTypeResolver.Context retainedContext = resolver.resolve(scan.building(), prospectiveMain);
        String retainedType = retainedContext.updatedType(requestedForcedType);
        if (retainedType == null) return Building.validationResult.INVALID_TYPE;

        Map<Building, String> createdTypes = new IdentityHashMap<>();
        for (Building created : scan.createdRooms()) {
            String type = resolver.resolve(created, prospectiveMain).updatedType(null);
            if (type == null) return Building.validationResult.INVALID_TYPE;
            createdTypes.put(created, type);
        }

        if (prospectiveMain != null && prospectiveMain.getId() != existing.getId()
                && prospectiveMain.isTypeForced()
                && !resolver.resolve(prospectiveMain, prospectiveMain).matchesForcedType(prospectiveMain.getType())) {
            return Building.validationResult.INVALID_TYPE;
        }

        existing.copyScannedGeometryFrom(scan.building(), world, true);
        existing.setType(retainedType);
        existing.setTypeForced(requestedForcedType != null);
        existing.setLayoutOverride(layoutOverride);

        village.removeBuildings(absorbedRoomIds);
        for (Building created : scan.createdRooms()) {
            created.setId(lastBuildingId++);
            created.setStructureId(existing.getStructureId());
            created.setFloorId(existing.getFloorId());
            created.setType(createdTypes.get(created));
            created.setTypeForced(false);
            village.getBuildings().put(created.getId(), created);
        }

        finalizeVillageMutation(village);
        return Building.validationResult.SUCCESS;
    }

    private static String chooseCategory(BuildingScanResult scan, String forcedType, boolean root) {
        if (forcedType != null) return forcedType;
        if (scan.matchingTypes().size() == 1) return scan.matchingTypes().getFirst();
        if (!scan.matchingTypes().isEmpty()) return null;
        return root ? "house" : "building";
    }

    private static String chooseRootCategory(BuildingScanResult scan, String forcedType) {
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

        village.getStructures().put(structureId, updated);
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
            StructureLayout.Layout layout = StructureLayout.build(village);
            room.setType(RoomTypeResolver.create(village, layout).resolve(room).updatedType(null));
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
                : village.getInteractionStructureAt(world, pos).orElse(null);
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
        target.normalizeLayoutOverrides();
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
