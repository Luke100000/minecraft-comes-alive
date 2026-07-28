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
    public static final int PLAYER_BORDER_MARGIN = 32;
    public static final int BORDER_MARGIN = 48;
    public static final int MERGE_MARGIN = 64;
    private static final int MOVE_IN_COOLDOWN = 1200;
    private static final long BED_SYNC_TIME = 200;

    public final List<ItemStack> storageBuffer = new LinkedList<>();

    private final ServerLevel world;
    /** Registered functional Rooms. Physical Structures and grouped/open-air sites live separately. */
    private final Map<Integer, Building> buildings = new HashMap<>();
    private final Map<Integer, ExternalBuilding> externalBuildings = new HashMap<>();
    private final Map<Integer, Structure> structures = new HashMap<>();
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
        normalizeLogicalBuildings();
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
        return buildings;
    }

    public Map<Integer, Structure> getStructures() {
        return structures;
    }

    public Stream<Building> getRooms() {
        return buildings.values().stream().filter(Building::isFunctionalRoom);
    }

    public Stream<ExternalBuilding> getExternalBuildings() {
        return externalBuildings.values().stream();
    }

    public Map<Integer, ExternalBuilding> getExternalBuildingMap() {
        return externalBuildings;
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
        return getStructure(structureId).map(Structure::getLogicalBuildingId).orElse(structureId);
    }

    List<Structure> getBuildingStructures(int buildingId) {
        return structures.values().stream()
                .filter(structure -> structure.getLogicalBuildingId() == buildingId)
                .sorted(Comparator.comparingInt(Structure::getId))
                .toList();
    }

    public void removeBuilding(int id) {
        boolean roomRemoved = buildings.remove(id) != null;
        boolean externalRemoved = externalBuildings.remove(id) != null;
        if (!roomRemoved && !externalRemoved) return;
        if (roomRemoved) refreshLogicalBuildings();
        calculateDimensions();
        markDirty();
    }

    public void removeStructure(int structureId) {
        Structure removed = structures.remove(structureId);
        if (removed == null) return;
        buildings.values().removeIf(building -> building.isFunctionalRoom() && building.getStructureId() == structureId);

        int buildingId = removed.getLogicalBuildingId();
        List<Structure> remaining = getBuildingStructures(buildingId);
        if (structureId == buildingId && !remaining.isEmpty()) {
            int replacementId = remaining.getFirst().getId();
            remaining.forEach(structure -> structure.setLogicalBuildingId(replacementId));
            buildingId = replacementId;
        }
        refreshLogicalBuildings();
        calculateDimensions();
        markDirty();
    }

    void removeLogicalBuilding(int buildingId) {
        Set<Integer> memberIds = getBuildingStructures(buildingId).stream()
                .map(Structure::getId)
                .collect(Collectors.toSet());
        if (memberIds.isEmpty()) return;
        buildings.values().removeIf(building -> memberIds.contains(building.getStructureId()));
        memberIds.forEach(structures::remove);
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
        return getFunctionalRoomAt(pos).or(() -> getExternalBuildings()
                .filter(building -> building.containsPos(pos))
                .min(Comparator.comparingInt(Building::getId)));
    }

    Optional<Structure> getExactStructureAt(Vec3i pos) {
        return structures.values().stream()
                .filter(structure -> structure.containsPos(pos))
                .min(Comparator.comparingInt(Structure::getId));
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
                return getFunctionalRoomAt(homePos).map(room -> room.getId() == value.getId()).orElse(false);
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
        tag.put("buildings", NbtHelper.fromList(getRooms().toList(), Building::save));
        tag.put("externalBuildings", NbtHelper.fromList(externalBuildings.values(), Building::save));
        tag.put("structures", NbtHelper.fromList(structures.values(), Structure::save));
        tag.putBoolean("autoScan", autoScan);
        return tag;
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        externalBuildings.putAll(village.externalBuildings);
        structures.putAll(village.structures);
        normalizeLogicalBuildings();
        calculateDimensions();
    }

    public int getStructureCount() {
        return (int) structures.values().stream().mapToInt(Structure::getLogicalBuildingId).distinct().count()
                + (int) externalBuildings.values().stream().filter(Building::isComplete).count();
    }

    public boolean hasStructuralBuildingAt(Level level, BlockPos pos) {
        return getRoomScanContext(level, pos).position() != StructuralPosition.OUTSIDE;
    }

    public StructuralPosition getStructuralPosition(Level level, BlockPos pos) {
        return getRoomScanContext(level, pos).position();
    }

    public RoomScanContext getRoomScanContext(Level level, BlockPos pos) {
        Optional<ResolvedInteraction> resolved = resolveInteractionPosition(level, pos);
        if (resolved.isPresent()) {
            Building room = resolved.get().position().room();
            if (room != null) {
                return new RoomScanContext(StructuralPosition.REGISTERED_ROOM,
                        Optional.of(room), RoomScanMode.UPDATE_ROOM, -1);
            }
            return new RoomScanContext(StructuralPosition.ATTACHABLE_ROOM,
                    getMainRoomForStructure(resolved.get().structure().getId()),
                    RoomScanMode.ADD_ROOM, -1);
        }

        RoomScanTarget target = level != null && pos != null && StructureScanner.isWalkableAnchor(level, pos)
                ? attachmentTarget(pos).orElse(RoomScanTarget.ADD_BUILDING)
                : RoomScanTarget.ADD_BUILDING;
        return new RoomScanContext(StructuralPosition.OUTSIDE, Optional.empty(),
                target.mode(), target.targetBuildingId());
    }

    private Optional<RoomScanTarget> attachmentTarget(BlockPos pos) {
        List<AttachmentTarget> targets = structures.values().stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .map(floor -> attachmentTarget(structure, floor, pos)))
                .filter(Objects::nonNull)
                .filter(target -> target.gap() <= MAX_FLOOR_ATTACHMENT_GAP)
                .toList();
        AttachmentTarget target = targets.stream()
                .min(Comparator.comparingInt(AttachmentTarget::gap)
                        .thenComparingInt(AttachmentTarget::targetBuildingId)
                        .thenComparingInt(AttachmentTarget::targetStructureId)
                        .thenComparingInt(AttachmentTarget::floorId))
                .orElse(null);
        if (target == null) return Optional.empty();
        if (targets.stream().anyMatch(candidate -> candidate.gap() == target.gap()
                && candidate.targetBuildingId() != target.targetBuildingId())) {
            return Optional.empty();
        }

        return Optional.of(new RoomScanTarget(
                attachmentMode(target.targetBuildingId(), pos.getY()),
                target.targetBuildingId()));
    }

    private RoomScanMode attachmentMode(int buildingId, int queryY) {
        List<Structure> members = getBuildingStructures(buildingId);
        if (members.isEmpty()) return RoomScanMode.ADD_BUILDING;
        int groundReferenceY = members.stream()
                .flatMap(structure -> structure.getFloors().stream())
                .filter(floor -> floor.floorNumber() == 0)
                .mapToInt(StructureFloor::anchorY)
                .min()
                .orElseGet(() -> getStructure(buildingId)
                        .orElse(members.getFirst()).getSurfaceReferenceY());
        return queryY >= groundReferenceY - BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE
                ? RoomScanMode.ADD_FLOOR : RoomScanMode.ADD_BASEMENT;
    }

    private static AttachmentTarget attachmentTarget(Structure structure,
                                                       StructureFloor floor,
                                                       BlockPos pos) {
        if (floor.region() == null || !floor.contains(pos.getX(), pos.getZ())) return null;
        int gap;
        if (pos.getY() >= floor.ceilingY()) {
            gap = pos.getY() - floor.ceilingY();
        } else if (pos.getY() < floor.anchorY()) {
            gap = floor.anchorY() - pos.getY();
        } else {
            return null;
        }
        return new AttachmentTarget(structure.getLogicalBuildingId(), structure.getId(), floor.id(), gap);
    }

    Optional<Structure> getInteractionStructureAt(Level level, BlockPos pos) {
        return resolveInteractionPosition(level, pos).map(ResolvedInteraction::structure);
    }

    private Optional<ResolvedInteraction> resolveInteractionPosition(Level level, BlockPos pos) {
        Map<Integer, List<Building>> roomsByStructure = getRooms()
                .collect(Collectors.groupingBy(Building::getStructureId));
        return structures.values().stream()
                .map(structure -> new ResolvedInteraction(structure,
                        structure.resolveInteractionPosition(level, pos,
                                roomsByStructure.getOrDefault(structure.getId(), List.of())).orElse(null)))
                .filter(resolved -> resolved.position() != null)
                .min(Comparator
                        .comparing((ResolvedInteraction resolved) -> !resolved.position().physical())
                        .thenComparing(resolved -> resolved.position().room() == null)
                        .thenComparingInt(resolved -> resolved.structure().getId()));
    }

    private Optional<Building> getMainRoomForStructure(int structureId) {
        Structure structure = getStructure(structureId).orElse(null);
        if (structure == null) return Optional.empty();
        for (Structure member : getBuildingStructures(structure.getLogicalBuildingId())) {
            int mainRoomId = member.getMainRoomId();
            if (mainRoomId < 0) continue;
            Building room = buildings.get(mainRoomId);
            if (room != null && room.isFunctionalRoom()) return Optional.of(room);
        }
        return Optional.empty();
    }

    public Optional<Building> getMainRoom(Structure structure) {
        if (structure == null) return Optional.empty();
        return getMainRoomForStructure(structure.getId());
    }

    public Optional<Building> getFunctionalRoomAt(Vec3i pos) {
        Optional<Structure> structure = getExactStructureAt(pos);
        if (structure.isEmpty()) {
            return getRooms().filter(room -> room.containsFloorPosition(pos))
                    .min(Comparator.comparingInt(Building::getId));
        }
        StructureFloor floor = structure.get().physicalFloorAt(pos).orElse(null);
        if (floor == null) return Optional.empty();
        return getRooms().filter(room -> room.getStructureId() == structure.get().getId())
                .filter(room -> room.getFloorId() == floor.id())
                .filter(room -> room.containsFloorColumn(pos.getX(), pos.getZ()))
                .min(Comparator.comparingInt(Building::getId));
    }

    public Optional<Building> getFunctionalRoomAt(Level level, BlockPos pos) {
        return resolveInteractionPosition(level, pos)
                .map(ResolvedInteraction::position)
                .map(Structure.InteractionPosition::room)
                .or(() -> getFunctionalRoomAt(pos));
    }

    private record ResolvedInteraction(Structure structure, Structure.InteractionPosition position) {
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
        boolean changed = MainRoomSelector.setManual(
                getBuildingStructures(structure.getLogicalBuildingId()), getRooms().toList(), room.getId());
        if (!changed) return false;
        markDirty();
        return true;
    }

    public boolean useAutomaticMainRoom(Structure structure) {
        if (structure == null || !structures.containsKey(structure.getId())) return false;
        boolean changed = MainRoomSelector.useAutomatic(
                getBuildingStructures(structure.getLogicalBuildingId()), getRooms().toList());
        if (!changed) return false;
        markDirty();
        return true;
    }

    public boolean isMainRoomAutomatic(Structure structure) {
        if (structure == null) return true;
        return getBuildingStructures(structure.getLogicalBuildingId()).stream()
                .filter(member -> member.getMainRoomId() >= 0)
                .findFirst()
                .map(Structure::isMainRoomAutomatic)
                .orElse(true);
    }

    void transferMainRoom(Structure structure,
                          int removedRoomId,
                          int survivorRoomId,
                          int survivorStructureId) {
        MainRoomSelector.transfer(getBuildingStructures(structure.getLogicalBuildingId()),
                removedRoomId, survivorRoomId, survivorStructureId);
    }

    void refreshLogicalBuildings() {
        Map<Integer, List<Structure>> byBuilding = structures.values().stream()
                .collect(Collectors.groupingBy(Structure::getLogicalBuildingId));
        byBuilding.forEach(this::applyFloorNumbers);

        List<Building> rooms = getRooms().toList();
        byBuilding.values().forEach(members -> MainRoomSelector.ensureValid(members, rooms));
    }

    static final int MAX_FLOOR_ATTACHMENT_GAP = 4;

    private record AttachmentTarget(int targetBuildingId,
                                    int targetStructureId,
                                    int floorId,
                                    int gap) {
    }

    private record FloorRef(Structure structure, StructureFloor floor) {
    }

    public enum RoomScanMode {
        ADD_BUILDING, ADD_ROOM, UPDATE_ROOM, ADD_FLOOR, ADD_BASEMENT;

        public boolean isAttachment() {
            return this == ADD_FLOOR || this == ADD_BASEMENT;
        }
    }

    private record RoomScanTarget(RoomScanMode mode, int targetBuildingId) {
        private static final RoomScanTarget ADD_BUILDING =
                new RoomScanTarget(RoomScanMode.ADD_BUILDING, -1);
    }

    public enum StructuralPosition { OUTSIDE, REGISTERED_ROOM, ATTACHABLE_ROOM }

    public record RoomScanContext(StructuralPosition position,
                                  Optional<Building> building,
                                  RoomScanMode mode,
                                  int targetBuildingId) {
        public Optional<Building> functionalRoom() {
            return position == StructuralPosition.REGISTERED_ROOM ? building : Optional.empty();
        }
    }

    private void applyFloorNumbers(int buildingId, Collection<Structure> members) {
        if (members.isEmpty()) return;
        floorNumbers(members, buildingId).forEach((ref, number) ->
                ref.structure().setFloorNumber(ref.floor().id(), number));
    }

    int prospectiveFloorNumber(int buildingId,
                               Structure candidate,
                               StructureFloor candidateFloor) {
        List<Structure> members = new ArrayList<>(getBuildingStructures(buildingId));
        if (members.isEmpty() || candidate == null || candidateFloor == null) {
            return Integer.MIN_VALUE;
        }
        members.add(candidate);
        return floorNumbers(members, buildingId)
                .getOrDefault(new FloorRef(candidate, candidateFloor), Integer.MIN_VALUE);
    }

    private Map<FloorRef, Integer> floorNumbers(Collection<Structure> members, int rootStructureId) {
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

        int tolerance = BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        List<List<FloorRef>> bands = new ArrayList<>();
        for (FloorRef ref : floors) {
            List<FloorRef> band = bands.isEmpty() ? null : bands.getLast();
            if (band == null || ref.floor().anchorY() - band.getFirst().floor().anchorY() > tolerance) {
                band = new ArrayList<>();
                bands.add(band);
            }
            band.add(ref);
        }

        Structure root = getStructure(rootStructureId).orElseGet(() -> members.stream()
                .min(Comparator.comparingInt(Structure::getId)).orElseThrow());
        int surfaceY = root.getSurfaceReferenceY();
        int groundBand = java.util.stream.IntStream.range(0, bands.size())
                .filter(index -> (long) bands.get(index).getFirst().floor().anchorY()
                        >= (long) surfaceY - tolerance)
                .boxed()
                .min(Comparator.comparingLong((Integer index) -> Math.abs(
                                (long) bands.get(index).getFirst().floor().anchorY() - surfaceY))
                        .thenComparing(Comparator.comparingInt((Integer index) ->
                                bands.get(index).getFirst().floor().anchorY()).reversed())
                        .thenComparingInt(Integer::intValue))
                .orElse(bands.size());

        Map<FloorRef, Integer> numbers = new HashMap<>();
        for (int bandIndex = 0; bandIndex < bands.size(); bandIndex++) {
            int floorNumber = bandIndex - groundBand;
            for (FloorRef ref : bands.get(bandIndex)) numbers.put(ref, floorNumber);
        }
        return Map.copyOf(numbers);
    }

    private void normalizeLogicalBuildings() {
        structures.values().forEach(this::normalizeLogicalBuilding);
        Map<Integer, Integer> canonicalIds = new HashMap<>();
        structures.values().stream()
                .sorted(Comparator.comparingInt(Structure::getId))
                .forEach(structure -> canonicalIds.put(
                        structure.getId(), resolveBuildingRoot(structure)));
        canonicalIds.forEach((structureId, buildingId) ->
                structures.get(structureId).setLogicalBuildingId(buildingId));

        refreshLogicalBuildings();
    }

    private int resolveBuildingRoot(Structure start) {
        Set<Integer> visited = new HashSet<>();
        int currentId = start.getId();
        while (visited.add(currentId)) {
            Structure current = structures.get(currentId);
            if (current == null) return start.getId();
            int nextId = current.getLogicalBuildingId();
            if (nextId == currentId) return currentId;
            currentId = nextId;
        }
        return start.getId();
    }

    private void normalizeLogicalBuilding(Structure structure) {
        if (structure == null) return;
        int buildingId = structure.getLogicalBuildingId();
        if (buildingId < 0
                || (buildingId != structure.getId() && !structures.containsKey(buildingId))) {
            structure.setLogicalBuildingId(structure.getId());
        }
    }

    public boolean isVillage() {
        return getStructureCount() >= Config.getInstance().minimumBuildingsToBeConsideredAVillage;
    }

    public void updateResident(VillagerEntityMCA entity) {
        residentNames.put(entity.getUUID(), entity.getName().getString());
        Optional<GlobalPos> home = entity.getResidency().getHome();
        if (home.isPresent()) residentHomes.put(entity.getUUID(), home.get().pos().asLong());
        else residentHomes.remove(entity.getUUID());
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
