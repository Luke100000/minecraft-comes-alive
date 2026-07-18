package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.resources.API;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.server.world.data.villageComponents.*;
import net.conczin.mca.util.BlockBoxExtended;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

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
    private final Map<Integer, Building> buildings = new HashMap<>();
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
    private float taxes = 0;
    private float populationThreshold = 0.75f;
    private float marriageThreshold = 0.5f;
    private boolean autoScan = Config.getInstance().enableAutoScanByDefault;
    private BlockBoxExtended box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);

    public Village(int id, ServerLevel world) {
        this.id = id;

        this.world = world;
    }

    public Village(CompoundTag v, ServerLevel world) {
        id = v.getInt("id");
        name = v.getString("name");
        chatAIPrompt = v.getString("chatAIPrompt");
        taxes = v.getFloat("taxesFloat");
        beds = v.getInt("beds");
        reputation = NbtHelper.toMap(v.getCompound("reputation"), UUID::fromString, i ->
                NbtHelper.toMap((CompoundTag) i, UUID::fromString, i2 -> ((IntTag) i2).getAsInt())
        );
        residentNames = NbtHelper.toMap(v.getCompound("residentNames"), UUID::fromString, Tag::getAsString);
        residentHomes = NbtHelper.toMap(v.getCompound("residentHomes"), UUID::fromString, i -> ((LongTag) i).getAsLong());

        if (v.contains("populationThresholdFloat")) {
            populationThreshold = v.getFloat("populationThresholdFloat");
        }
        if (v.contains("marriageThresholdFloat")) {
            marriageThreshold = v.getFloat("marriageThresholdFloat");
        }
        this.world = world;

        if (v.contains("autoScan")) {
            autoScan = v.getBoolean("autoScan");
        } else {
            autoScan = true;
        }

        ListTag b = v.getList("buildings", Tag.TAG_COMPOUND);
        for (int i = 0; i < b.size(); i++) {
            Building building = new Building(b.getCompound(i));

            if (world == null || BuildingTypes.getInstance().getBuildingTypes().containsKey(building.getType())) {
                buildings.put(building.getId(), building);
            }
        }

        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
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
        return buildings.values().iterator();
    }

    public void removeBuilding(int id) {
        removeBuildings(List.of(id));
    }

    void removeBuildings(Collection<Integer> ids) {
        boolean changed = false;
        for (int id : ids) {
            changed |= buildings.remove(id) != null;
        }
        if (!changed) {
            return;
        }
        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
        markDirty();
    }

    public Stream<Building> getBuildingsOfType(String type) {
        return getBuildings().values().stream().filter(b -> b.getType().equals(type));
    }

    public Optional<Building> getBuildingAt(Vec3i pos) {
        return getFunctionalRoomAt(pos)
                .or(() -> getBuildings().values().stream()
                        .filter(building -> building.getBuildingType().grouped())
                        .filter(building -> building.containsPos(pos))
                        .min(Comparator.comparingInt(Building::getId)))
                .or(() -> getBuildings().values().stream()
                        .filter(Building::isStructureRoot)
                        .filter(building -> !building.getBuildingType().grouped())
                        .filter(building -> building.containsPos(pos))
                        .min(Comparator.comparingInt(Building::getId)))
                .or(() -> getBuildings().values().stream()
                        .filter(building -> building.containsPos(pos))
                        .min(Comparator.comparingInt(Building::getId)));
    }

    Optional<Building> getBuildingTarget(Vec3i pos) {
        Optional<Building> nearby = buildings.values().stream()
                .filter(building -> building.containsPos(pos)
                        || building.containsPositionWithMargin(
                        pos, Building.PLAYER_POSITION_HORIZONTAL_MARGIN, Building.PLAYER_POSITION_VERTICAL_MARGIN))
                .min(Comparator
                        .comparing((Building building) -> !building.containsPos(pos))
                        .thenComparingDouble(building -> building.getCenter().distSqr(pos)));
        if (nearby.isPresent()) {
            return nearby;
        }

        return buildings.values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .filter(building -> building.containsHorizontalPosition(pos))
                .filter(building -> building.getVerticalDistanceTo(pos) <= 16)
                .min(Comparator.comparingDouble(building -> building.getCenter().distSqr(pos)));
    }

    public void calculateDimensions() {
        int sx = Integer.MAX_VALUE;
        int sy = Integer.MAX_VALUE;
        int sz = Integer.MAX_VALUE;
        int ex = Integer.MIN_VALUE;
        int ey = Integer.MIN_VALUE;
        int ez = Integer.MIN_VALUE;

        for (Building building : buildings.values()) {
            ex = Math.max(building.getPos1().getX(), ex);
            sx = Math.min(building.getPos0().getX(), sx);

            ey = Math.max(building.getPos1().getY(), ey);
            sy = Math.min(building.getPos0().getY(), sy);

            ez = Math.max(building.getPos1().getZ(), ez);
            sz = Math.min(building.getPos0().getZ(), sz);
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
        return getBuilding(building).map(value -> residentHomes.entrySet().stream().filter(e -> {
            BlockPos homePos = BlockPos.of(e.getValue());
            if (value.isFunctionalRoom()) {
                return getFunctionalRoomAt(homePos)
                        .map(room -> room.getId() == value.getId())
                        .orElse(false);
            }
            return value.containsPos(homePos);
        }).map(k -> residentNames.getOrDefault(k.getKey(), "Unknown")).collect(Collectors.toList())).orElseGet(List::of);
    }

    public float getTaxes() {
        return taxes;
    }

    public void setTaxes(float taxes) {
        this.taxes = taxes;
    }

    public float getPopulationThreshold() {
        return populationThreshold;
    }

    public void setPopulationThreshold(float populationThreshold) {
        this.populationThreshold = populationThreshold;
    }

    public float getMarriageThreshold() {
        return marriageThreshold;
    }

    public void setMarriageThreshold(float marriageThreshold) {
        this.marriageThreshold = marriageThreshold;
    }

    public boolean isAutoScan() {
        return autoScan;
    }

    public void setAutoScan(boolean autoScan) {
        this.autoScan = autoScan;
    }

    public void toggleAutoScan() {
        setAutoScan(!isAutoScan());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<Integer, Building> getBuildings() {
        return buildings;
    }

    public Optional<Building> getBuilding(int id) {
        return Optional.ofNullable(buildings.get(id));
    }

    public int getId() {
        return id;
    }

    public boolean hasSpace() {
        return getPopulation() < getMaxPopulation();
    }

    public int getPopulation() {
        return residentNames.size();
    }

    public Stream<UUID> getResidentsUUIDs() {
        return residentNames.keySet().stream();
    }

    // verify that this bed is not blocked
    public boolean isPositionValidBed(BlockPos pos) {
        return getBuildingAt(pos).filter(b -> b.getBuildingType().noBeds()).isEmpty();
    }

    public List<VillagerEntityMCA> getResidents(ServerLevel world) {
        return getResidentsUUIDs()
                .map(world::getEntity)
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast)
                .collect(Collectors.toList());
    }

    public void updateMaxPopulation() {
        if (world != null) {
            Vec3i dimensions = box.getLength();
            int radius = (int) Math.sqrt(dimensions.getX() * dimensions.getX() + dimensions.getY() * dimensions.getY() + dimensions.getZ() * dimensions.getZ());
            beds = (int) world.getPoiManager().findAll(
                    registryEntry -> registryEntry.is(PoiTypes.HOME),
                    this::isPositionValidBed,
                    new BlockPos(getCenter()),
                    radius + BORDER_MARGIN,
                    PoiManager.Occupancy.ANY).count();
        }
    }

    public int getMaxPopulation() {
        if (world != null && world.getGameTime() - lastBedSync > BED_SYNC_TIME) {
            lastBedSync = world.getGameTime();
            updateMaxPopulation();
        }
        return beds;
    }

    public boolean hasStoredResource() {
        return !storageBuffer.isEmpty();
    }

    public boolean hasBuilding(String building) {
        return buildings.values().stream().anyMatch(b -> b.getType().equals(building) && b.isComplete());
    }

    public void tick(ServerLevel world, long time) {
        // spread performance to avoid lag spikes
        time += getId();

        boolean isTaxSeason = time % Config.getInstance().taxSeason == 0;
        boolean isVillageUpdateTime = time % MOVE_IN_COOLDOWN == 0;

        if (isTaxSeason && hasBuilding("storage")) {
            villageTaxesManager.taxes(world);
        }

        if (time % 24000 == 0) {
            cleanReputation();
        }

        if (isVillageUpdateTime && lastMoveIn + MOVE_IN_COOLDOWN < time && WorldUtils.isChunkLoaded(world, getCenter())) {
            villageGuardsManager.spawnGuards(world);
            villageInnManager.updateInn(world);
            villageMarriageManager.marry(world);
            villageProcreationManager.procreate(world);
        }
    }

    public void onEnter(ServerLevel world) {
        villageTaxesManager.deliverTaxes(world);
    }

    public void broadCastMessage(ServerLevel world, String event, VillagerEntityMCA suitor, VillagerEntityMCA mate) {
        world.players().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId()
                                             || suitor.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend
                                             || mate.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend)
                .forEach(player -> player.displayClientMessage(Component.translatable(event, suitor.getName(), mate.getName()), !Config.getInstance().showNotificationsAsChat));
    }

    public void broadCastMessage(ServerLevel world, String event, String targetName) {
        world.players().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId())
                .forEach(player -> player.displayClientMessage(Component.translatable(event, targetName), !Config.getInstance().showNotificationsAsChat));
    }

    public void markDirty() {
        VillageManager.get(world).setDirty();
    }

    // removes all villagers no longer living here
    public void cleanReputation() {
        Set<UUID> residents = getResidentsUUIDs().collect(Collectors.toSet());
        for (Map<UUID, Integer> map : reputation.values()) {
            Set<UUID> toRemove = map.keySet().stream().filter(v -> !residents.contains(v)).collect(Collectors.toSet());
            for (UUID uuid : toRemove) {
                map.remove(uuid);
            }
        }
    }

    public void setReputation(Player player, VillagerEntityMCA villager, int rep) {
        reputation.computeIfAbsent(player.getUUID(), i -> new HashMap<>()).put(villager.getUUID(), rep);
        markDirty();
    }

    public int getReputation(Player player) {
        return reputation.getOrDefault(player.getUUID(), Collections.emptyMap()).values().stream().mapToInt(i -> i).sum();
    }

    public void pushHearts(Player player, int h) {
        List<Memories> loadedMemories = new ArrayList<>();
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                loadedMemories.add(villager.getVillagerBrain().getMemoriesForPlayer(player));
            }
        }
        if (loadedMemories.isEmpty()) {
            return;
        }
        int splitHearts = (int) Math.ceil((double) h / loadedMemories.size());
        for (Memories memories : loadedMemories) {
            memories.modHearts(splitHearts);
        }
        markDirty();
    }

    public void pushMood(int m) {
        for (UUID uuid : residentNames.keySet()) {
            if (world.getEntity(uuid) instanceof VillagerEntityMCA villager) {
                villager.getVillagerBrain().modifyMoodValue(m);
            }
        }
        markDirty();
    }

    public CompoundTag save() {
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putString("name", name);
        v.putString("chatAIPrompt", chatAIPrompt);
        v.putFloat("taxesFloat", taxes);
        v.putInt("beds", beds);
        v.put("reputation", NbtHelper.fromMap(new CompoundTag(), reputation, UUID::toString, i ->
                NbtHelper.fromMap(new CompoundTag(), i, UUID::toString, IntTag::valueOf)
        ));
        v.put("residentNames", NbtHelper.fromMap(new CompoundTag(), residentNames, Object::toString, StringTag::valueOf));
        v.put("residentHomes", NbtHelper.fromMap(new CompoundTag(), residentHomes, Object::toString, LongTag::valueOf));
        v.putFloat("populationThresholdFloat", populationThreshold);
        v.putFloat("marriageThresholdFloat", marriageThreshold);
        v.put("buildings", NbtHelper.fromList(buildings.values(), Building::save));
        v.putBoolean("autoScan", autoScan);
        return v;
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        calculateDimensions();
    }

    public int getStructureCount() {
        long structural = getBuildings().values().stream()
                .filter(building -> !building.getBuildingType().grouped())
                .mapToInt(Building::getEffectiveStructureId)
                .distinct()
                .count();

        long grouped = getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(building -> building.getBuildingType().grouped())
                .count();

        return Math.toIntExact(structural + grouped);
    }

    public boolean hasStructuralBuildingAt(Vec3i pos) {
        return getStructuralPosition(pos) != StructuralPosition.OUTSIDE;
    }

    public StructuralPosition getStructuralPosition(Vec3i pos) {
        return getStructuralLookup(pos).position();
    }

    public StructuralLookup getStructuralLookup(Vec3i pos) {
        Optional<Building> functionalRoom = getFunctionalRoomAt(pos);
        if (functionalRoom.isPresent()) {
            return new StructuralLookup(StructuralPosition.REGISTERED_ROOM, functionalRoom);
        }

        Optional<Building> structureRoot = BuildingStructureManager.containingRoot(this, pos);
        return structureRoot.isPresent()
                ? new StructuralLookup(StructuralPosition.ATTACHABLE_ROOM, structureRoot)
                : new StructuralLookup(StructuralPosition.OUTSIDE, Optional.empty());
    }

    public Optional<Building> getFunctionalRoomAt(Vec3i pos) {
        Building root = BuildingStructureManager.containingRoot(this, pos).orElse(null);
        if (root != null) {
            Building.FloorBand selectedBand = root.resolveFloorBand(pos.getY()).orElse(null);
            if (selectedBand != null) {
                Optional<Building> roomOnFloor = getBuildings().values().stream()
                        .filter(Building::isComplete)
                        .filter(Building::isFunctionalRoom)
                        .filter(room -> room.getEffectiveStructureId() == root.getEffectiveStructureId())
                        .filter(room -> root.resolvePhysicalFloorBand(room.getFloorY())
                                .map(roomBand -> roomBand.anchorY() == selectedBand.anchorY())
                                .orElse(false))
                        .filter(room -> room.containsFloorColumn(pos.getX(), pos.getZ()))
                        .min(Comparator.comparingInt(Building::getId));
                if (roomOnFloor.isPresent()) {
                    return roomOnFloor;
                }
            }
        }

        return getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(building -> building.containsRawPos(pos))
                /*
                 * Raw bounds can span open stairs, atriums, and multi-floor interiors.
                 * A player only belongs to a registered room on that room's semantic floor.
                 */
                .filter(building -> building.getFloorDistanceTo(pos) <= Building.SEMANTIC_FLOOR_TOLERANCE)
                .min(Comparator.comparingInt((Building building) -> building.getFloorDistanceTo(pos))
                        .thenComparingInt(Building::getId));
    }

    public boolean isStructuralGroundFloor(Building room) {
        return getStructureRoot(room)
                .filter(root -> root.isOnGroundFloorY(room.getFloorY()))
                .isPresent();
    }

    public boolean setStructureGroundFloorAnchor(Building room) {
        if (room == null) {
            return false;
        }

        Building root = getStructureRoot(room).orElse(null);
        if (root == null || root.isOnGroundFloorY(room.getFloorY())) {
            return false;
        }

        root.setGroundFloorY(room.getFloorY());
        markDirty();
        return true;
    }

    private Optional<Building> getStructureRoot(Building room) {
        return BuildingStructureManager.root(this, room.getEffectiveStructureId());
    }

    public enum StructuralPosition {
        OUTSIDE,
        REGISTERED_ROOM,
        ATTACHABLE_ROOM
    }

    public record StructuralLookup(StructuralPosition position, Optional<Building> building) {
        public Optional<Building> functionalRoom() {
            return position == StructuralPosition.REGISTERED_ROOM ? building : Optional.empty();
        }
    }

    public boolean isVillage() {
        return getStructureCount() >= Config.getInstance().minimumBuildingsToBeConsideredAVillage;
    }

    public void updateResident(VillagerEntityMCA e) {
        residentNames.put(e.getUUID(), e.getName().getString());

        Optional<GlobalPos> home = e.getResidency().getHome();
        if (home.isPresent()) {
            residentHomes.put(e.getUUID(), home.get().pos().asLong());
        } else {
            residentHomes.remove(e.getUUID());
        }
    }

    public Map<UUID, String> getResidentNames() {
        return residentNames;
    }

    public void removeResident(VillagerEntityMCA villager) {
        removeResident(villager.getUUID());
    }

    public void removeResident(UUID uuid) {
        residentNames.remove(uuid);
        residentHomes.remove(uuid);
        cleanReputation();
        markDirty();
    }

    public VillageGuardsManager getVillageGuardsManager() {
        return villageGuardsManager;
    }

    public Optional<CivilRegistryManager> getCivilRegistry() {
        return world != null ? Optional.of(CivilRegistryManager.get(world, this)) : Optional.empty();
    }
}
