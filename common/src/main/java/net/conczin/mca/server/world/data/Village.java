package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.resources.API;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.villageComponents.*;
import net.conczin.mca.util.BlockBoxExtended;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Village implements Iterable<Building> {
    static final int BUILDING_DATA_VERSION = 1;
    public static final int PLAYER_BORDER_MARGIN = 32;
    public static final int BORDER_MARGIN = 48;
    public static final int MERGE_MARGIN = 64;
    private static final int MOVE_IN_COOLDOWN = 1200;
    private static final long BED_SYNC_TIME = 200;
    private static final Comparator<AttachmentTarget> ATTACHMENT_TARGET_ORDER = Comparator
            .comparingInt(AttachmentTarget::gap)
            .thenComparingInt(AttachmentTarget::buildingId)
            .thenComparingInt(AttachmentTarget::structureId)
            .thenComparingInt(AttachmentTarget::floorId);

    public final List<ItemStack> storageBuffer = new LinkedList<>();

    private final ServerLevel world;
    /** Registered functional Rooms. Physical Structures and grouped/open-air sites live separately. */
    private final Map<Integer, Building> buildings = new HashMap<>();
    private final Map<Integer, ExternalBuilding> externalBuildings = new HashMap<>();
    private final Map<Integer, Structure> structures = new HashMap<>();
    private final Map<Integer, LogicalBuilding> logicalBuildings = new HashMap<>();
    private final int id;
    private final VillageGuardsManager villageGuardsManager = new VillageGuardsManager(this);
    private final VillageInnManager villageInnManager = new VillageInnManager(this);
    private final VillageMarriageManager villageMarriageManager = new VillageMarriageManager(this);
    private final VillageProcreationManager villageProcreationManager = new VillageProcreationManager(this);
    private final VillageTaxesManager villageTaxesManager = new VillageTaxesManager(this);

    public long lastMoveIn;
    private String name = API.getVillagePool().pickVillageName("village");
    private String chatAIPrompt = "";
    private Map<UUID, Map<UUID, Integer>> reputation = new HashMap<>();
    private int beds;
    private long lastBedSync;
    private Map<UUID, String> residentNames = new HashMap<>();
    private Map<UUID, Long> residentHomes = new HashMap<>();
    private float taxes;
    private float populationThreshold = 0.75f;
    private float marriageThreshold = 0.5f;
    private boolean autoScan = Config.getInstance().enableAutoScanByDefault;
    private BlockBoxExtended box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);

    public Village(int id, ServerLevel world) {
        this.id = id;
        this.world = world;
    }

    public Village(CompoundTag tag, ServerLevel world) {
        id = tag.getInt("id");
        name = tag.getString("name");
        chatAIPrompt = tag.getString("chatAIPrompt");
        taxes = tag.getFloat("taxesFloat");
        beds = tag.getInt("beds");
        reputation = NbtHelper.toMap(tag.getCompound("reputation"), UUID::fromString, value ->
                NbtHelper.toMap((CompoundTag) value, UUID::fromString, inner -> ((IntTag) inner).getAsInt()));
        residentNames = NbtHelper.toMap(tag.getCompound("residentNames"), UUID::fromString, Tag::getAsString);
        residentHomes = NbtHelper.toMap(tag.getCompound("residentHomes"), UUID::fromString, value -> ((LongTag) value).getAsLong());
        if (tag.contains("populationThresholdFloat")) populationThreshold = tag.getFloat("populationThresholdFloat");
        if (tag.contains("marriageThresholdFloat")) marriageThreshold = tag.getFloat("marriageThresholdFloat");
        autoScan = tag.contains("autoScan") ? tag.getBoolean("autoScan") : true;
        this.world = world;

        RoomDFU.Result data = RoomDFU.load(tag);
        buildings.putAll(data.buildings());
        externalBuildings.putAll(data.externalBuildings());
        structures.putAll(data.structures());
        logicalBuildings.putAll(data.logicalBuildings());
        validateBuildingData();
        logicalBuildings.values().forEach(this::applyFloorNumbers);
        if (!buildings.isEmpty() || !externalBuildings.isEmpty() || !structures.isEmpty()) calculateDimensions();
    }

    public static Optional<Village> findNearest(Entity entity) {
        return VillageManager.get((ServerLevel) entity.level()).findNearestVillage(entity);
    }

    public boolean isWithinBorder(Entity entity) {
        return isWithinBorder(entity.blockPosition(), entity instanceof Player ? PLAYER_BORDER_MARGIN : BORDER_MARGIN);
    }

    public String getChatAIPrompt() {
        return chatAIPrompt;
    }

    public void setChatAIPrompt(String chatAIPrompt) {
        this.chatAIPrompt = chatAIPrompt;
        markDirty();
    }

    public boolean isWithinBorder(BlockPos pos, int margin) {
        return box.inflatedBy(margin).isInside(pos);
    }

    @Override
    public Iterator<Building> iterator() {
        return Stream.concat(buildings.values().stream(), externalBuildings.values().stream().map(Building.class::cast))
                .iterator();
    }

    public Map<Integer, Building> getBuildings() {
        return Collections.unmodifiableMap(buildings);
    }

    public Map<Integer, Structure> getStructures() {
        return Collections.unmodifiableMap(structures);
    }

    public Stream<Building> getRooms() {
        return buildings.values().stream().filter(Building::isFunctionalRoom);
    }

    public Stream<ExternalBuilding> getExternalBuildings() {
        return externalBuildings.values().stream();
    }

    public Map<Integer, ExternalBuilding> getExternalBuildingMap() {
        return Collections.unmodifiableMap(externalBuildings);
    }

    public Optional<Building> getBuilding(int id) {
        Building room = buildings.get(id);
        return room != null ? Optional.of(room) : Optional.ofNullable(externalBuildings.get(id));
    }

    public Optional<Structure> getStructure(int id) {
        return Optional.ofNullable(structures.get(id));
    }

    public Optional<Structure> getStructureFor(Building room) {
        return room == null ? Optional.empty() : getStructure(room.getStructureId());
    }

    public int getLogicalBuildingId(int structureId) {
        return getStructure(structureId).map(Structure::getLogicalBuildingId).orElse(-1);
    }

    Optional<LogicalBuilding> getLogicalBuilding(int buildingId) {
        return Optional.ofNullable(logicalBuildings.get(buildingId));
    }

    public void registerStructure(Structure structure, Building room) {
        structures.put(structure.getId(), structure);
        buildings.put(room.getId(), room);
        logicalBuildings.computeIfAbsent(structure.getLogicalBuildingId(), id ->
                new LogicalBuilding(id, room.getId(), true));
    }

    public void registerRoom(Building room) {
        if (room == null || !room.isFunctionalRoom()) {
            throw new IllegalArgumentException("Only functional Rooms can be registered");
        }
        Structure structure = structures.get(room.getStructureId());
        StructureFloor floor = structure == null ? null : structure.getFloor(room.getFloorId()).orElse(null);
        if (floor == null || !floorContainsRoomCells(floor, room)) {
            throw new IllegalArgumentException("Room references missing Structure/Floor");
        }
        buildings.put(room.getId(), room);
    }

    void registerExternalBuilding(ExternalBuilding building) {
        externalBuildings.put(building.getId(), building);
    }

    boolean replaceStructureAndRegisterRoom(Structure refreshed, Building room) {
        if (refreshed == null || room == null || !room.isFunctionalRoom() || room.getId() < 0) return false;
        Structure current = structures.get(refreshed.getId());
        if (current == null || current.getLogicalBuildingId() != refreshed.getLogicalBuildingId()) return false;
        if (buildings.containsKey(room.getId()) || externalBuildings.containsKey(room.getId())) return false;
        if (room.getStructureId() != refreshed.getId()) return false;

        StructureFloor floor = refreshed.getFloor(room.getFloorId()).orElse(null);
        if (floor == null || room.getFloorCells().isEmpty() || !floorContainsRoomCells(floor, room)) {
            return false;
        }
        List<Building> floorRooms = getRooms()
                .filter(existing -> existing.getStructureId() == refreshed.getId())
                .filter(existing -> existing.getFloorId() == room.getFloorId())
                .collect(Collectors.toCollection(ArrayList::new));
        floorRooms.add(room);
        return publishFloorRefresh(refreshed, room.getFloorId(), floorRooms);
    }

    /**
     * Atomically validates and publishes one refreshed Floor together with the complete Room set
     * that owns that Floor. No Village maps are mutated until the replacement state is valid.
     */
    boolean publishFloorRefresh(Structure refreshed,
                                int floorId,
                                Collection<Building> replacementRooms) {
        if (refreshed == null || replacementRooms == null) return false;
        Structure current = structures.get(refreshed.getId());
        if (current == null || current.getLogicalBuildingId() != refreshed.getLogicalBuildingId()) return false;
        StructureFloor refreshedFloor = refreshed.getFloor(floorId).orElse(null);
        if (refreshedFloor == null) return false;

        List<Building> currentFloorRooms = getRooms()
                .filter(room -> room.getStructureId() == refreshed.getId())
                .filter(room -> room.getFloorId() == floorId)
                .toList();
        Set<Integer> currentFloorRoomIds = currentFloorRooms.stream()
                .map(Building::getId)
                .collect(Collectors.toSet());
        List<Building> replacements = List.copyOf(replacementRooms);
        Set<Integer> replacementIds = new HashSet<>();
        Set<BlockPos> ownedCells = new HashSet<>();
        for (Building room : replacements) {
            if (room == null || !room.isFunctionalRoom() || room.getId() < 0
                    || room.getStructureId() != refreshed.getId()
                    || room.getFloorId() != floorId
                    || room.getFloorCells().isEmpty()
                    || !floorContainsRoomCells(refreshedFloor, room)
                    || !replacementIds.add(room.getId())) {
                return false;
            }
            Building registered = buildings.get(room.getId());
            if (registered != null && !currentFloorRoomIds.contains(room.getId())) return false;
            if (externalBuildings.containsKey(room.getId())) return false;
            for (BlockPos cell : room.getFloorCells()) {
                if (!ownedCells.add(cell)) return false;
            }
        }

        LogicalBuilding logical = logicalBuildings.get(current.getLogicalBuildingId());
        if (logical != null && currentFloorRoomIds.contains(logical.mainRoomId())
                && !replacementIds.contains(logical.mainRoomId())) {
            return false;
        }

        Map<Integer, Building> nextBuildings = new HashMap<>(buildings);
        currentFloorRoomIds.forEach(nextBuildings::remove);
        for (Building room : replacements) nextBuildings.put(room.getId(), room);

        structures.put(refreshed.getId(), refreshed);
        buildings.clear();
        buildings.putAll(nextBuildings);
        reconcileLogicalBuilding(refreshed.getLogicalBuildingId());
        calculateDimensions();
        return true;
    }

    void removeRooms(Collection<Integer> roomIds) {
        roomIds.forEach(buildings::remove);
    }

    BuildingStateSnapshot snapshotBuildingState() {
        return new BuildingStateSnapshot(
                buildings.values().stream().map(Building::copy).toList(),
                structures.values().stream().map(Structure::copy).toList(),
                logicalBuildings.values().stream().map(LogicalBuilding::copy).toList());
    }

    void restoreBuildingState(BuildingStateSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        buildings.clear();
        snapshot.rooms.forEach(room -> buildings.put(room.getId(), room));
        structures.clear();
        snapshot.structures.forEach(structure -> structures.put(structure.getId(), structure));
        logicalBuildings.clear();
        snapshot.logicalBuildings.forEach(logical -> logicalBuildings.put(logical.id(), logical));
        calculateDimensions();
    }

    static final class BuildingStateSnapshot {
        private final List<Building> rooms;
        private final List<Structure> structures;
        private final List<LogicalBuilding> logicalBuildings;

        private BuildingStateSnapshot(List<Building> rooms,
                                      List<Structure> structures,
                                      List<LogicalBuilding> logicalBuildings) {
            this.rooms = List.copyOf(rooms);
            this.structures = List.copyOf(structures);
            this.logicalBuildings = List.copyOf(logicalBuildings);
        }
    }

    List<Structure> getBuildingStructures(int buildingId) {
        return structures.values().stream()
                .filter(structure -> structure.getLogicalBuildingId() == buildingId)
                .sorted(Comparator.comparingInt(Structure::getId))
                .toList();
    }

    public boolean canRemoveFloor(int buildingId, int floorNumber) {
        if (floorNumber == 0) return false;

        List<Structure> members = getBuildingStructures(buildingId);
        if (members.stream().flatMap(structure -> structure.getFloors().stream())
                .noneMatch(floor -> floor.floorNumber() == floorNumber)) {
            return false;
        }

        boolean hasRooms = buildings.values().stream().anyMatch(room ->
                structures.containsKey(room.getStructureId())
                        && structures.get(room.getStructureId()).getLogicalBuildingId() == buildingId
                        && structures.get(room.getStructureId()).getFloor(room.getFloorId())
                        .map(floor -> floor.floorNumber() == floorNumber)
                        .orElse(false));
        if (hasRooms) return false;

        return members.stream()
                .flatMap(member -> member.getFloors().stream())
                .noneMatch(candidate -> floorNumber > 0
                        ? candidate.floorNumber() > floorNumber
                        : candidate.floorNumber() < floorNumber);
    }

    boolean removeFloor(int buildingId, int floorNumber) {
        if (!canRemoveFloor(buildingId, floorNumber)) return false;

        for (Structure structure : getBuildingStructures(buildingId)) {
            List<Integer> floorIds = structure.getFloors().stream()
                    .filter(floor -> floor.floorNumber() == floorNumber)
                    .map(StructureFloor::id)
                    .toList();
            if (floorIds.isEmpty()) continue;
            if (floorIds.size() == structure.getFloors().size()) {
                structures.remove(structure.getId());
            } else {
                floorIds.forEach(structure::removeFloor);
            }
        }
        refreshLogicalBuildings();
        calculateDimensions();
        markDirty();
        return true;
    }

    public boolean removeRoom(int roomId) {
        Building room = buildings.get(roomId);
        if (room == null || isMainRoom(room)) return false;
        buildings.remove(roomId);
        refreshLogicalBuildings();
        calculateDimensions();
        markDirty();
        return true;
    }

    public boolean removeExternalBuilding(int buildingId) {
        if (externalBuildings.remove(buildingId) == null) return false;
        calculateDimensions();
        markDirty();
        return true;
    }

    public void removeStructure(int structureId) {
        Structure removed = structures.remove(structureId);
        if (removed == null) return;
        int buildingId = removed.getLogicalBuildingId();
        buildings.values().removeIf(room -> room.getStructureId() == structureId);
        if (getBuildingStructures(buildingId).isEmpty()) logicalBuildings.remove(buildingId);
        else reconcileLogicalBuilding(buildingId);
        calculateDimensions();
        markDirty();
    }

    void removeLogicalBuilding(int buildingId) {
        Set<Integer> structureIds = getBuildingStructures(buildingId).stream()
                .map(Structure::getId).collect(Collectors.toSet());
        buildings.values().removeIf(room -> structureIds.contains(room.getStructureId()));
        structureIds.forEach(structures::remove);
        logicalBuildings.remove(buildingId);
    }

    public Stream<Building> getBuildingsOfType(String type) {
        BuildingType definition = BuildingTypes.getInstance().getBuildingType(type);
        if (definition.grouped()) {
            return getExternalBuildings().filter(building -> building.getType().equals(type)).map(Building.class::cast);
        }
        RoomTypeResolver resolver = RoomTypeResolver.create(this);
        return getRooms().filter(room -> {
            BuildingType effective = resolver.resolve(room).effectiveType();
            return effective != null && effective.name().equals(type);
        });
    }

    public Optional<Building> getBuildingAt(Vec3i pos) {
        return findPhysicalRoomAt(pos).or(() -> getExternalBuildings()
                .filter(building -> building.containsPos(pos))
                .min(Comparator.comparingInt(Building::getId)));
    }

    Optional<Structure> getExactStructureAt(Vec3i pos) {
        return structures.values().stream()
                .filter(structure -> structure.containsPos(pos))
                .min(Comparator.comparingInt(Structure::getId));
    }

    boolean hasRegisteredFloorOverlap(Structure candidate) {
        if (candidate == null) return false;
        for (StructureFloor candidateFloor : candidate.getFloors()) {
            for (Structure registered : structures.values()) {
                for (StructureFloor registeredFloor : registered.getFloors()) {
                    if (candidateFloor.overlapsSameSemanticBand(registeredFloor)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void calculateDimensions() {
        List<VillageBuilding> all = new ArrayList<>();
        all.addAll(structures.values());
        getExternalBuildings().forEach(all::add);
        if (all.isEmpty()) all.addAll(buildings.values());
        if (all.isEmpty()) {
            box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);
            return;
        }

        int sx = Integer.MAX_VALUE, sy = Integer.MAX_VALUE, sz = Integer.MAX_VALUE;
        int ex = Integer.MIN_VALUE, ey = Integer.MIN_VALUE, ez = Integer.MIN_VALUE;
        for (VillageBuilding building : all) {
            sx = Math.min(sx, building.getPos0().getX());
            sy = Math.min(sy, building.getPos0().getY());
            sz = Math.min(sz, building.getPos0().getZ());
            ex = Math.max(ex, building.getPos1().getX());
            ey = Math.max(ey, building.getPos1().getY());
            ez = Math.max(ez, building.getPos1().getZ());
        }
        box = new BlockBoxExtended(sx, sy, sz, ex, ey, ez);
    }

    public Vec3i getCenter() {
        return box.getCenter();
    }

    public BlockBoxExtended getBox() {
        return box;
    }

    public List<String> getResidents(int building) {
        return getBuilding(building).map(value -> residentHomes.entrySet().stream().filter(entry -> {
            BlockPos homePos = BlockPos.of(entry.getValue());
            if (value.isFunctionalRoom()) {
                return findPhysicalRoomAt(homePos).map(room -> room.getId() == value.getId()).orElse(false);
            }
            return value.containsPos(homePos);
        }).map(entry -> residentNames.getOrDefault(entry.getKey(), "Unknown")).collect(Collectors.toList())).orElseGet(List::of);
    }

    public float getTaxes() { return taxes; }
    public void setTaxes(float taxes) { this.taxes = taxes; }
    public float getPopulationThreshold() { return populationThreshold; }
    public void setPopulationThreshold(float populationThreshold) { this.populationThreshold = populationThreshold; }
    public float getMarriageThreshold() { return marriageThreshold; }
    public void setMarriageThreshold(float marriageThreshold) { this.marriageThreshold = marriageThreshold; }
    public boolean isAutoScan() { return autoScan; }
    public void setAutoScan(boolean autoScan) { this.autoScan = autoScan; }
    public void toggleAutoScan() { setAutoScan(!isAutoScan()); }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public int getId() { return id; }
    public boolean hasSpace() { return getPopulation() < getMaxPopulation(); }
    public int getPopulation() { return residentNames.size(); }
    public Stream<UUID> getResidentsUUIDs() { return residentNames.keySet().stream(); }

    public boolean isPositionValidBed(BlockPos pos) {
        return getBuildingAt(pos).filter(building -> building.getBuildingType().noBeds()).isEmpty();
    }

    public List<VillagerEntityMCA> getResidents(ServerLevel world) {
        return getResidentsUUIDs().map(world::getEntity)
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast).collect(Collectors.toList());
    }

    public void updateMaxPopulation() {
        if (world == null) return;
        Vec3i dimensions = box.getLength();
        int radius = (int) Math.sqrt(dimensions.getX() * dimensions.getX()
                + dimensions.getY() * dimensions.getY() + dimensions.getZ() * dimensions.getZ());
        beds = (int) world.getPoiManager().findAll(entry -> entry.is(PoiTypes.HOME), this::isPositionValidBed,
                new BlockPos(getCenter()), radius + BORDER_MARGIN, PoiManager.Occupancy.ANY).count();
    }

    public int getMaxPopulation() {
        if (world != null && world.getGameTime() - lastBedSync > BED_SYNC_TIME) {
            lastBedSync = world.getGameTime();
            updateMaxPopulation();
        }
        return beds;
    }

    public boolean hasStoredResource() { return !storageBuffer.isEmpty(); }

    public boolean hasBuilding(String type) {
        BuildingType definition = BuildingTypes.getInstance().getBuildingType(type);
        if (definition.grouped()) {
            return getExternalBuildings().anyMatch(building -> building.getType().equals(type) && building.isComplete());
        }
        return getBuildingsOfType(type).findAny().isPresent();
    }

    List<BuildingType> getMatchingRoomTypes(Building candidate) {
        return candidate == null ? List.of() : List.copyOf(candidate.getVisibleMatchingTypes());
    }

    public void tick(ServerLevel world, long time) {
        time += getId();
        boolean taxSeason = time % Config.getInstance().taxSeason == 0;
        boolean update = time % MOVE_IN_COOLDOWN == 0;
        if (taxSeason && hasBuilding("storage")) villageTaxesManager.taxes(world);
        if (time % 24000 == 0) cleanReputation();
        if (update && lastMoveIn + MOVE_IN_COOLDOWN < time && WorldUtils.isChunkLoaded(world, getCenter())) {
            villageGuardsManager.spawnGuards(world);
            villageInnManager.updateInn(world);
            villageMarriageManager.marry(world);
            villageProcreationManager.procreate(world);
        }
    }

    public void onEnter(ServerLevel world) { villageTaxesManager.deliverTaxes(world); }

    public void broadCastMessage(ServerLevel world, String event, VillagerEntityMCA suitor, VillagerEntityMCA mate) {
        world.players().stream().filter(player -> PlayerSaveData.get(player).getLastSeenVillageId().orElse(-2) == getId()
                        || suitor.getVillagerBrain().getMemoriesForPlayer(player).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend
                        || mate.getVillagerBrain().getMemoriesForPlayer(player).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend)
                .forEach(player -> player.displayClientMessage(Component.translatable(event, suitor.getName(), mate.getName()),
                        !Config.getInstance().showNotificationsAsChat));
    }

    public void broadCastMessage(ServerLevel world, String event, String targetName) {
        world.players().stream().filter(player -> PlayerSaveData.get(player).getLastSeenVillageId().orElse(-2) == getId())
                .forEach(player -> player.displayClientMessage(Component.translatable(event, targetName),
                        !Config.getInstance().showNotificationsAsChat));
    }

    public void markDirty() {
        if (world != null) VillageManager.get(world).setDirty();
    }

    public void cleanReputation() {
        Set<UUID> residents = getResidentsUUIDs().collect(Collectors.toSet());
        for (Map<UUID, Integer> map : reputation.values()) {
            map.keySet().removeIf(uuid -> !residents.contains(uuid));
        }
    }

    public void setReputation(Player player, VillagerEntityMCA villager, int rep) {
        reputation.computeIfAbsent(player.getUUID(), ignored -> new HashMap<>()).put(villager.getUUID(), rep);
        markDirty();
    }

    public int getReputation(Player player) {
        return reputation.getOrDefault(player.getUUID(), Collections.emptyMap()).values().stream().mapToInt(Integer::intValue).sum();
    }

    public void pushHearts(Player player, int hearts) {
        List<Memories> memories = new ArrayList<>();
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                memories.add(villager.getVillagerBrain().getMemoriesForPlayer(player));
            }
        }
        if (memories.isEmpty()) return;
        int split = (int) Math.ceil((double) hearts / memories.size());
        memories.forEach(memory -> memory.modHearts(split));
        markDirty();
    }

    public void pushMood(int mood) {
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                villager.getVillagerBrain().modifyMoodValue(mood);
            }
        }
        markDirty();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("name", name);
        tag.putString("chatAIPrompt", chatAIPrompt);
        tag.putFloat("taxesFloat", taxes);
        tag.putInt("beds", beds);
        tag.put("reputation", NbtHelper.fromMap(new CompoundTag(), reputation, UUID::toString,
                value -> NbtHelper.fromMap(new CompoundTag(), value, UUID::toString, IntTag::valueOf)));
        tag.put("residentNames", NbtHelper.fromMap(new CompoundTag(), residentNames, Object::toString, StringTag::valueOf));
        tag.put("residentHomes", NbtHelper.fromMap(new CompoundTag(), residentHomes, Object::toString, LongTag::valueOf));
        tag.putFloat("populationThresholdFloat", populationThreshold);
        tag.putFloat("marriageThresholdFloat", marriageThreshold);
        tag.putInt("buildingDataVersion", BUILDING_DATA_VERSION);
        tag.put("buildings", NbtHelper.fromList(buildings.values(), Building::save));
        tag.put("externalBuildings", NbtHelper.fromList(externalBuildings.values(), Building::save));
        tag.put("structures", NbtHelper.fromList(structures.values(), Structure::save));
        tag.put("logicalBuildings", NbtHelper.fromList(logicalBuildings.values(), LogicalBuilding::save));
        tag.putBoolean("autoScan", autoScan);
        return tag;
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        externalBuildings.putAll(village.externalBuildings);
        structures.putAll(village.structures);
        logicalBuildings.putAll(village.logicalBuildings);
        refreshLogicalBuildings();
        calculateDimensions();
    }

    public int getStructureCount() {
        return (int) structures.values().stream().mapToInt(Structure::getLogicalBuildingId).distinct().count()
                + (int) externalBuildings.values().stream().filter(Building::isComplete).count();
    }

    public RoomScanPlan getRoomScanPlan(Level level, BlockPos pos) {
        return RoomScanPlanner.plan(this, level, pos);
    }

    Optional<AttachmentTarget> resolveAttachmentTarget(
            Level level,
            StructureFloor candidate,
            Collection<FloorGeometry> connectedFloors) {
        return selectAttachmentTarget(candidate,
                StructureConnector.verticalConnections(level, candidate, structures.values()),
                connectedFloors);
    }

    Optional<AttachmentTarget> selectAttachmentTarget(
            StructureFloor candidate,
            Collection<StructureConnector.VerticalConnection> connections) {
        return selectAttachmentTarget(candidate, connections, List.of());
    }

    Optional<AttachmentTarget> selectAttachmentTarget(
            StructureFloor candidate,
            Collection<StructureConnector.VerticalConnection> verticalConnections,
            Collection<FloorGeometry> connectedFloors) {
        if (candidate == null) return Optional.empty();
        Set<AttachmentConnection> connections = attachmentConnections(
                candidate, verticalConnections, connectedFloors);
        if (hasUnprovenAttachmentOverlap(candidate, connections)) return Optional.empty();

        Map<Integer, AttachmentTarget> nearestByBuilding = new HashMap<>();
        for (AttachmentConnection connection : connections) {
            Structure structure = connection.structure();
            StructureFloor floor = connection.floor();
            if (structures.get(structure.getId()) != structure) continue;
            int gap = candidate.attachmentGapTo(floor);
            if (gap < 0) continue;
            AttachmentTarget target = new AttachmentTarget(
                    structure.getLogicalBuildingId(), structure.getId(), floor.id(), gap);
            nearestByBuilding.merge(target.buildingId(), target,
                    (first, second) -> ATTACHMENT_TARGET_ORDER.compare(first, second) <= 0 ? first : second);
        }

        AttachmentTarget nearest = nearestByBuilding.values().stream()
                .min(ATTACHMENT_TARGET_ORDER).orElse(null);
        if (nearest == null) return Optional.empty();
        return nearestByBuilding.values().stream()
                .anyMatch(target -> target.buildingId() != nearest.buildingId()
                        && target.gap() == nearest.gap())
                ? Optional.empty() : Optional.of(nearest);
    }

    private Set<AttachmentConnection> attachmentConnections(
            StructureFloor candidate,
            Collection<StructureConnector.VerticalConnection> verticalConnections,
            Collection<FloorGeometry> connectedFloors) {
        LinkedHashSet<AttachmentConnection> connections = new LinkedHashSet<>();
        if (verticalConnections != null) {
            for (StructureConnector.VerticalConnection connection : verticalConnections) {
                connections.add(new AttachmentConnection(connection.structure(), connection.floor()));
            }
        }
        if (connectedFloors == null) return Set.copyOf(connections);

        for (FloorGeometry band : connectedFloors) {
            if (StructureFloor.sameSemanticBand(candidate.anchorY(), band.anchorY())) continue;
            for (Structure structure : structures.values()) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (StructureFloor.sameSemanticBand(band.anchorY(), floor.anchorY())
                            && band.projection().intersectionArea(floor.region()) > 0) {
                        connections.add(new AttachmentConnection(structure, floor));
                    }
                }
            }
        }
        return Set.copyOf(connections);
    }

    private boolean hasUnprovenAttachmentOverlap(
            StructureFloor candidate,
            Set<AttachmentConnection> connections) {
        for (Structure structure : structures.values()) {
            for (StructureFloor floor : structure.getFloors()) {
                if (!candidate.overlapsFootprint(floor)
                        || candidate.verticalGapTo(floor) >= 0) {
                    continue;
                }
                if (candidate.sameSemanticBand(floor)
                        || !connections.contains(new AttachmentConnection(structure, floor))) {
                    return true;
                }
            }
        }
        return false;
    }

    Optional<Structure> getInteractionStructureAt(BlockPos pos) {
        return resolveInteractionPosition(pos).map(ResolvedInteraction::structure);
    }

    Optional<ResolvedInteraction> resolveInteractionPosition(BlockPos pos) {
        Map<Integer, List<Building>> roomsByStructure = getRooms()
                .collect(Collectors.groupingBy(Building::getStructureId));
        return structures.values().stream()
                .map(structure -> new ResolvedInteraction(structure,
                        structure.resolveInteractionPosition(pos,
                                roomsByStructure.getOrDefault(structure.getId(), List.of())).orElse(null)))
                .filter(resolved -> resolved.position() != null)
                .min(Comparator
                        .comparing((ResolvedInteraction resolved) -> resolved.position().room() == null)
                        .thenComparingInt(resolved -> resolved.structure().getId()));
    }

    public Optional<Building> getMainRoom(Structure structure) {
        if (structure == null) return Optional.empty();
        LogicalBuilding logical = logicalBuildings.get(structure.getLogicalBuildingId());
        if (logical == null || logical.mainRoomId() < 0) return Optional.empty();
        Building room = buildings.get(logical.mainRoomId());
        return room != null && room.isFunctionalRoom() ? Optional.of(room) : Optional.empty();
    }

    public Optional<Building> findPhysicalRoomAt(Vec3i pos) {
        Optional<Structure> structure = getExactStructureAt(pos);
        if (structure.isEmpty()) {
            BlockPos query = new BlockPos(pos.getX(), pos.getY(), pos.getZ());
            return getRooms()
                    .filter(room -> room.containsPos(pos))
                    .filter(room -> room.getFloorCells().isEmpty() || room.ownsFloorCell(query))
                    .min(Comparator.comparingInt(Building::getId));
        }
        Structure.FloorCell resolved = structure.get().resolvePhysicalFloorCell(pos).orElse(null);
        if (resolved == null) return Optional.empty();
        return getRooms().filter(room -> room.getStructureId() == structure.get().getId())
                .filter(room -> room.getFloorId() == resolved.floor().id())
                .filter(room -> room.ownsFloorCell(resolved.cell().feet()))
                .min(Comparator.comparingInt(Building::getId));
    }

    public Optional<Building> findInteractionRoomAt(BlockPos pos) {
        return resolveInteractionPosition(pos)
                .map(ResolvedInteraction::position)
                .map(Structure.InteractionPosition::room)
                .or(() -> findPhysicalRoomAt(pos));
    }

    record ResolvedInteraction(Structure structure, Structure.InteractionPosition position) {
    }

    public boolean isMainRoom(Building room) {
        if (room == null || !room.isFunctionalRoom()) return false;
        Structure structure = getStructure(room.getStructureId()).orElse(null);
        return structure != null && getMainRoom(structure)
                .map(main -> main.getId() == room.getId()).orElse(false);
    }

    public boolean setMainRoom(Building room) {
        Structure structure = getStructureFor(room).orElse(null);
        if (structure == null) return false;
        LogicalBuilding logical = logicalBuildings.get(structure.getLogicalBuildingId());
        if (logical == null || logical.mainRoomId() == room.getId()
                || !belongsToLogicalBuilding(room, logical.id())) return false;
        logical.setMainRoomId(room.getId());
        applyFloorNumbers(logical);
        markDirty();
        return true;
    }

    public boolean setBuildingInheritanceEnabled(Building room, boolean enabled) {
        Structure structure = getStructureFor(room).orElse(null);
        if (structure == null) return false;
        LogicalBuilding logical = logicalBuildings.get(structure.getLogicalBuildingId());
        if (logical == null || logical.inheritanceEnabled() == enabled) return false;
        logical.setInheritanceEnabled(enabled);
        markDirty();
        return true;
    }

    public boolean setRoomContributesToMain(Building room, boolean contributes) {
        if (room == null || !buildings.containsKey(room.getId())
                || room.contributesToMain() == contributes) return false;
        room.setContributesToMain(contributes);
        markDirty();
        return true;
    }

    public Building.validationResult commitRoomInheritanceUpdate(
            RoomInheritanceUpdate update, String forcedType) {
        if (update == null || !update.valid()) return Building.validationResult.NOT_IN_BUILDING;
        Building room = buildings.get(update.roomId());
        if (room == null || !room.isFunctionalRoom() || isMainRoom(room) != update.mainRoom()) {
            return Building.validationResult.OVERLAP;
        }

        boolean currentEnabled = update.mainRoom()
                ? isBuildingInheritanceEnabled(room)
                : room.contributesToMain();
        if (currentEnabled != update.previousEnabled()) return Building.validationResult.OVERLAP;
        if (forcedType != null && !update.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (update.requiresTypeSelection() && forcedType == null) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (currentEnabled == update.enabled()) return Building.validationResult.SUCCESS;

        String automaticType = null;
        if (!update.enabled() && forcedType == null) {
            automaticType = RoomTypeResolver.create(this).resolve(room).updatedType(null);
            if (automaticType == null) return Building.validationResult.INVALID_TYPE;
        }

        boolean changed = update.mainRoom()
                ? setBuildingInheritanceEnabled(room, update.enabled())
                : setRoomContributesToMain(room, update.enabled());
        if (!changed) return Building.validationResult.OVERLAP;

        if (!update.enabled()) {
            if (forcedType != null) {
                room.setType(forcedType);
                room.setTypeForced(true);
            } else {
                room.setType(automaticType);
                room.setTypeForced(false);
            }
            markDirty();
        }
        return Building.validationResult.SUCCESS;
    }

    public boolean isBuildingInheritanceEnabled(Building room) {
        Structure structure = getStructureFor(room).orElse(null);
        if (structure == null) return false;
        return getLogicalBuilding(structure.getLogicalBuildingId())
                .map(LogicalBuilding::inheritanceEnabled).orElse(false);
    }

    void refreshLogicalBuildings() {
        logicalBuildings.keySet().stream().toList().forEach(this::reconcileLogicalBuilding);
    }

    private void reconcileLogicalBuilding(int buildingId) {
        List<Structure> members = getBuildingStructures(buildingId);
        if (members.isEmpty()) {
            logicalBuildings.remove(buildingId);
            return;
        }
        LogicalBuilding logical = logicalBuildings.get(buildingId);
        if (logical == null) return;

        if (!validMainRoom(logical)) logical.setMainRoomId(lowestRoomId(buildingId));
        if (logical.mainRoomId() < 0) {
            removeLogicalBuilding(buildingId);
            return;
        }
        applyFloorNumbers(logical);
    }

    private boolean validMainRoom(LogicalBuilding logical) {
        if (logical.mainRoomId() < 0) return lowestRoomId(logical.id()) < 0;
        return belongsToLogicalBuilding(buildings.get(logical.mainRoomId()), logical.id());
    }

    private boolean belongsToLogicalBuilding(Building room, int buildingId) {
        Structure structure = room == null ? null : structures.get(room.getStructureId());
        return structure != null && structure.getLogicalBuildingId() == buildingId
                && structure.getFloor(room.getFloorId()).isPresent();
    }

    private int lowestRoomId(int buildingId) {
        return buildings.values().stream()
                .filter(Building::isFunctionalRoom)
                .filter(room -> belongsToLogicalBuilding(room, buildingId))
                .mapToInt(Building::getId).min().orElse(-1);
    }

    private void applyFloorNumbers(LogicalBuilding logical) {
        floorNumbers(getBuildingStructures(logical.id()), logical).forEach(
                (ref, number) -> ref.structure().setFloorNumber(ref.floor().id(), number));
    }

    private void validateBuildingData() {
        for (Structure structure : structures.values()) {
            if (!logicalBuildings.containsKey(structure.getLogicalBuildingId())) {
                throw new IllegalArgumentException("Structure " + structure.getId()
                        + " references missing logical building " + structure.getLogicalBuildingId());
            }
        }
        for (Building room : buildings.values()) {
            Structure structure = structures.get(room.getStructureId());
            StructureFloor floor = structure == null ? null : structure.getFloor(room.getFloorId()).orElse(null);
            if (!room.isFunctionalRoom() || floor == null || !floorContainsRoomCells(floor, room)) {
                throw new IllegalArgumentException("Room " + room.getId() + " references missing Structure/Floor");
            }
        }
        for (LogicalBuilding logical : logicalBuildings.values()) {
            if (!validMainRoom(logical)) {
                throw new IllegalArgumentException("Logical building " + logical.id()
                        + " has invalid Main Room " + logical.mainRoomId());
            }
            if (groundFloor(logical).isEmpty()) {
                throw new IllegalArgumentException("Logical building " + logical.id()
                        + " cannot derive Ground Floor from Main Room " + logical.mainRoomId());
            }
        }
    }

    public boolean updateResident(VillagerEntityMCA e) {
        return updateResident(e, false);
    }

    public void updateResidentAfterClaim(VillagerEntityMCA e) {
        updateResident(e, true);
    }

    private boolean updateResident(VillagerEntityMCA e, boolean authoritativeHomeClaim) {
        UUID resident = e.getUUID();
        String residentName = e.getName().getString();
        String previousName = residentNames.put(resident, residentName);
        Long previousHome = residentHomes.get(resident);

        Optional<GlobalPos> home = e.getResidency().getHome();
        boolean accepted = true;
        if (home.isPresent() && home.get().dimension() == world.dimension()) {
            long homePosition = home.get().pos().asLong();
            if (authoritativeHomeClaim) {
                ResidentHomeAssignments.claimAuthoritatively(residentHomes, resident, homePosition);
            } else {
                accepted = ResidentHomeAssignments.claim(residentHomes, resident, homePosition);
            }
        } else {
            residentHomes.remove(resident);
        }

        if (!Objects.equals(previousName, residentName) || !Objects.equals(previousHome, residentHomes.get(resident))) {
            markDirty();
        }
        return accepted;
    }

    public boolean isResidentHomeCurrent(VillagerEntityMCA resident) {
        Optional<GlobalPos> home = resident.getResidency().getHome();
        if (home.isEmpty()) {
            return !residentHomes.containsKey(resident.getUUID());
        }
        GlobalPos currentHome = home.get();
        return currentHome.dimension() == world.dimension()
                && Objects.equals(residentHomes.get(resident.getUUID()), currentHome.pos().asLong());
    }

    boolean repairDuplicateResidentHomes() {
        return ResidentHomeAssignments.deduplicate(residentHomes) > 0;
    }

    private record FloorRef(Structure structure, StructureFloor floor) {
    }

    private static boolean floorContainsRoomCells(StructureFloor floor, Building room) {
        return room.getFloorCells().stream().allMatch(cell -> floor.geometry().cellAt(cell).isPresent());
    }

    private record AttachmentConnection(Structure structure, StructureFloor floor) {
    }

    record AttachmentTarget(int buildingId, int structureId, int floorId, int gap) {
    }

    public enum RoomScanMode {
        ADD_BUILDING, ADD_ROOM, UPDATE_ROOM, ADD_FLOOR, ADD_BASEMENT;

        public boolean isAttachment() {
            return this == ADD_FLOOR || this == ADD_BASEMENT;
        }
    }

    int prospectiveFloorNumber(int buildingId,
                               Structure candidate,
                               StructureFloor candidateFloor) {
        List<Structure> members = new ArrayList<>(getBuildingStructures(buildingId));
        LogicalBuilding logical = logicalBuildings.get(buildingId);
        if (members.isEmpty() || logical == null || candidate == null || candidateFloor == null) {
            return Integer.MIN_VALUE;
        }
        members.add(candidate);
        return floorNumbers(members, logical)
                .getOrDefault(new FloorRef(candidate, candidateFloor), Integer.MIN_VALUE);
    }

    private Map<FloorRef, Integer> floorNumbers(Collection<Structure> members, LogicalBuilding logical) {
        List<FloorRef> floors = new ArrayList<>();
        for (Structure structure : members) {
            for (StructureFloor floor : structure.getFloors()) {
                floors.add(new FloorRef(structure, floor));
            }
        }
        floors.sort(Comparator.comparingInt((FloorRef ref) -> ref.floor().anchorY())
                .thenComparingInt(ref -> ref.structure().getId())
                .thenComparingInt(ref -> ref.floor().id()));
        if (floors.isEmpty()) return Map.of();

        int tolerance = StructureFloor.BAND_TOLERANCE;
        List<List<FloorRef>> bands = new ArrayList<>();
        for (FloorRef ref : floors) {
            List<FloorRef> band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || ref.floor().anchorY() - band.getFirst().floor().anchorY() > tolerance) {
                band = new ArrayList<>();
                bands.add(band);
            }
            band.add(ref);
        }

        FloorRef ground = groundFloor(logical).orElse(null);
        if (ground == null) return Map.of();
        int groundBand = java.util.stream.IntStream.range(0, bands.size())
                .filter(index -> bands.get(index).stream().anyMatch(ref ->
                        ref.structure().getId() == ground.structure().getId()
                                && ref.floor().id() == ground.floor().id()))
                .findFirst()
                .orElse(-1);
        if (groundBand < 0) return Map.of();

        Map<FloorRef, Integer> numbers = new HashMap<>();
        for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
            int floorNumber = bandIndex - groundBand;
            for (FloorRef ref : bands.get(bandIndex)) numbers.put(ref, floorNumber);
        }
        return Map.copyOf(numbers);
    }

    private Optional<FloorRef> groundFloor(LogicalBuilding logical) {
        if (logical == null || logical.mainRoomId() < 0) return Optional.empty();
        Building main = buildings.get(logical.mainRoomId());
        if (!belongsToLogicalBuilding(main, logical.id())) return Optional.empty();
        Structure structure = structures.get(main.getStructureId());
        return structure.getFloor(main.getFloorId()).map(floor -> new FloorRef(structure, floor));
    }


    public boolean isVillage() {
        return getStructureCount() >= Config.getInstance().minimumBuildingsToBeConsideredAVillage;
    }

    public Map<UUID, String> getResidentNames() { return residentNames; }
    public void removeResident(VillagerEntityMCA villager) { removeResident(villager.getUUID()); }

    public void removeResident(UUID uuid) {
        residentNames.remove(uuid);
        residentHomes.remove(uuid);
        cleanReputation();
        markDirty();
    }

    public VillageGuardsManager getVillageGuardsManager() { return villageGuardsManager; }

    public Optional<CivilRegistryManager> getCivilRegistry() {
        return world != null ? Optional.of(CivilRegistryManager.get(world, this)) : Optional.empty();
    }
}
