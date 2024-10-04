package net.mca.server.world.data;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.resources.API;
import net.mca.resources.BuildingTypes;
import net.mca.server.world.data.villageComponents.*;
import net.mca.util.BlockBoxExtended;
import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.poi.PointOfInterestStorage;
import net.minecraft.world.poi.PointOfInterestTypes;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Village implements Iterable<Building> {
    private static final int MOVE_IN_COOLDOWN = 1200;
    public static final int PLAYER_BORDER_MARGIN = 32;
    public static final int BORDER_MARGIN = 48;
    public static final int MERGE_MARGIN = 64;
    private static final long BED_SYNC_TIME = 200;

    @Nullable
    private final ServerWorld world;

    private String name = API.getVillagePool().pickVillageName("village");

    public final List<ItemStack> storageBuffer = new LinkedList<>();

    private final Map<Integer, Building> buildings = new HashMap<>();
    private Map<UUID, Integer> unspentHearts = new HashMap<>();
    private Map<UUID, Map<UUID, Integer>> reputation = new HashMap<>();
    private int unspentMood = 0;

    private int beds;
    private long lastBedSync;

    private Map<UUID, String> residentNames = new HashMap<>();
    private Map<UUID, Long> residentHomes = new HashMap<>();

    public long lastMoveIn;
    private final int id;

    private float taxes = 0;
    private float populationThreshold = 0.75f;
    private float marriageThreshold = 0.5f;

    private boolean autoScan = Config.getInstance().enableAutoScanByDefault;

    private BlockBoxExtended box = new BlockBoxExtended(0, 0, 0, 0, 0, 0);

    private final VillageGuardsManager villageGuardsManager = new VillageGuardsManager(this);
    private final VillageInnManager villageInnManager = new VillageInnManager(this);
    private final VillageMarriageManager villageMarriageManager = new VillageMarriageManager(this);
    private final VillageProcreationManager villageProcreationManager = new VillageProcreationManager(this);
    private final VillageTaxesManager villageTaxesManager = new VillageTaxesManager(this);

    public Village(int id, @Nullable ServerWorld world) {
        this.id = id;

        this.world = world;
    }

    public Village(NbtCompound v, @Nullable ServerWorld world) {
        id = v.getInt("id");
        name = v.getString("name");
        taxes = v.getFloat("taxesFloat");
        beds = v.getInt("beds");
        unspentHearts = NbtHelper.toMap(v.getCompound("unspentHearts"), UUID::fromString, i -> ((NbtInt)i).intValue());
        reputation = NbtHelper.toMap(v.getCompound("reputation"), UUID::fromString, i ->
                NbtHelper.toMap((NbtCompound)i, UUID::fromString, i2 -> ((NbtInt)i2).intValue())
        );
        residentNames = NbtHelper.toMap(v.getCompound("residentNames"), UUID::fromString, NbtElement::asString);
        residentHomes = NbtHelper.toMap(v.getCompound("residentHomes"), UUID::fromString, i -> ((NbtLong)i).longValue());
        unspentMood = v.getInt("unspentMood");

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

        NbtList b = v.getList("buildings", NbtElement.COMPOUND_TYPE);
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
        return VillageManager.get((ServerWorld)entity.getWorld()).findNearestVillage(entity);
    }

    public boolean isWithinBorder(Entity entity) {
        return isWithinBorder(entity.getBlockPos(), entity instanceof PlayerEntity ? PLAYER_BORDER_MARGIN : BORDER_MARGIN);
    }

    public boolean isWithinBorder(BlockPos pos, int margin) {
        return box.expand(margin).contains(pos);
    }

    @Override
    public Iterator<Building> iterator() {
        return buildings.values().iterator();
    }

    public void removeBuilding(int id) {
        buildings.remove(id);
        if (!buildings.isEmpty()) {
            calculateDimensions();
        }
        markDirty();
    }

    public Stream<Building> getBuildingsOfType(String type) {
        return getBuildings().values().stream().filter(b -> b.getType().equals(type));
    }

    public Optional<Building> getBuildingAt(Vec3i pos) {
        return getBuildings().values().stream().filter(b -> b.containsPos(pos)).findAny();
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
            return value.containsPos(BlockPos.fromLong(e.getValue()));
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

    public List<VillagerEntityMCA> getResidents(ServerWorld world) {
        return getResidentsUUIDs()
                .map(world::getEntity)
                .filter(VillagerEntityMCA.class::isInstance)
                .map(VillagerEntityMCA.class::cast)
                .collect(Collectors.toList());
    }

    public void updateMaxPopulation() {
        if (world != null) {
            Vec3i dimensions = box.getDimensions();
            int radius = (int)Math.sqrt(dimensions.getX() * dimensions.getX() + dimensions.getY() * dimensions.getY() + dimensions.getZ() * dimensions.getZ());
            beds = (int)world.getPointOfInterestStorage().getPositions(
                    registryEntry -> registryEntry.matchesKey(PointOfInterestTypes.HOME),
                    this::isPositionValidBed,
                    new BlockPos(getCenter()),
                    radius + BORDER_MARGIN,
                    PointOfInterestStorage.OccupationStatus.ANY).count();
        }
    }

    public int getMaxPopulation() {
        if (world != null && world.getTime() - lastBedSync > BED_SYNC_TIME) {
            lastBedSync = world.getTime();
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

    public void tick(ServerWorld world, long time) {
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

    public void onEnter(ServerWorld world) {
        villageTaxesManager.deliverTaxes(world);
    }

    public void broadCastMessage(ServerWorld world, String event, VillagerEntityMCA suitor, VillagerEntityMCA mate) {
        world.getPlayers().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId()
                        || suitor.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend
                        || mate.getVillagerBrain().getMemoriesForPlayer(p).getHearts() > Config.getInstance().heartsToBeConsideredAsFriend)
                .forEach(player -> player.sendMessage(Text.translatable(event, suitor.getName(), mate.getName()), !Config.getInstance().showNotificationsAsChat));
    }

    public void broadCastMessage(ServerWorld world, String event, String targetName) {
        world.getPlayers().stream().filter(p -> PlayerSaveData.get(p).getLastSeenVillageId().orElse(-2) == getId())
                .forEach(player -> player.sendMessage(Text.translatable(event, targetName), !Config.getInstance().showNotificationsAsChat));
    }

    public void markDirty() {
        VillageManager.get(world).markDirty();
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

    public void setReputation(PlayerEntity player, VillagerEntityMCA villager, int rep) {
        reputation.computeIfAbsent(player.getUuid(), i -> new HashMap<>()).put(villager.getUuid(), rep);
        markDirty();
    }

    public int getReputation(PlayerEntity player) {
        return reputation.getOrDefault(player.getUuid(), Collections.emptyMap()).values().stream().mapToInt(i -> i).sum()
                + unspentHearts.getOrDefault(player.getUuid(), 0);
    }

    public void resetHearts(PlayerEntity player) {
        unspentHearts.remove(player.getUuid());
        markDirty();
    }

    public void pushHearts(PlayerEntity player, int rep) {
        pushHearts(player.getUuid(), rep);
        markDirty();
    }

    public void pushHearts(UUID player, int rep) {
        unspentHearts.put(player, unspentHearts.getOrDefault(player, 0) + rep);
        markDirty();
    }

    public int popHearts(PlayerEntity player) {
        int v = unspentHearts.getOrDefault(player.getUuid(), 0);
        int step = (int)Math.ceil(Math.abs(((double)v) / getPopulation()));
        if (v > 0) {
            v -= step;
            if (v == 0) {
                unspentHearts.remove(player.getUuid());
            } else {
                unspentHearts.put(player.getUuid(), v);
            }
            markDirty();
            return step;
        } else if (v < 0) {
            v += step;
            if (v == 0) {
                unspentHearts.remove(player.getUuid());
            } else {
                unspentHearts.put(player.getUuid(), v);
            }
            markDirty();
            return -step;
        } else {
            return 0;
        }
    }

    public void pushMood(int m) {
        unspentMood += m;
        markDirty();
    }

    public int popMood() {
        int step = (int)Math.ceil(Math.abs(((double)unspentMood) / getPopulation()));
        if (unspentMood > 0) {
            unspentMood -= step;
            markDirty();
            return step;
        } else if (unspentMood < 0) {
            unspentMood += step;
            markDirty();
            return -step;
        } else {
            return 0;
        }
    }

    public NbtCompound save() {
        NbtCompound v = new NbtCompound();
        v.putInt("id", id);
        v.putString("name", name);
        v.putFloat("taxesFloat", taxes);
        v.putInt("beds", beds);
        v.put("unspentHearts", NbtHelper.fromMap(new NbtCompound(), unspentHearts, UUID::toString, NbtInt::of));
        v.put("reputation", NbtHelper.fromMap(new NbtCompound(), reputation, UUID::toString, i ->
                NbtHelper.fromMap(new NbtCompound(), i, UUID::toString, NbtInt::of)
        ));
        v.put("residentNames", NbtHelper.fromMap(new NbtCompound(), residentNames, Object::toString, NbtString::of));
        v.put("residentHomes", NbtHelper.fromMap(new NbtCompound(), residentHomes, Object::toString, NbtLong::of));
        v.putInt("unspentMood", unspentMood);
        v.putFloat("populationThresholdFloat", populationThreshold);
        v.putFloat("marriageThresholdFloat", marriageThreshold);
        v.put("buildings", NbtHelper.fromList(buildings.values(), Building::save));
        v.putBoolean("autoScan", autoScan);
        return v;
    }

    public void merge(Village village) {
        buildings.putAll(village.buildings);
        unspentMood += village.unspentMood;
        calculateDimensions();
    }

    public boolean isVillage() {
        return getBuildings().size() >= Config.getInstance().minimumBuildingsToBeConsideredAVillage;
    }

    public void updateResident(VillagerEntityMCA e) {
        residentNames.put(e.getUuid(), e.getName().getString());

        Optional<GlobalPos> home = e.getResidency().getHome();
        if (home.isPresent()) {
            residentHomes.put(e.getUuid(), home.get().getPos().asLong());
        } else {
            residentHomes.remove(e.getUuid());
        }
    }

    public Map<UUID, String> getResidentNames() {
        return residentNames;
    }

    public boolean hasResident(UUID id) {
        return residentNames.containsKey(id);
    }

    public void removeResident(VillagerEntityMCA villager) {
        removeResident(villager.getUuid());
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
