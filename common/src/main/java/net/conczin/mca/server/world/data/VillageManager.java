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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.illager.AbstractIllager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
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
        lastBuildingId = nbt.getInt("lastBuildingId").orElse(0);
        lastVillageId = nbt.getInt("lastVillageId").orElse(0);
        reapers = nbt.getCompound("reapers").map(reapers -> new ReaperSpawner(this, reapers)).orElseGet(() -> new ReaperSpawner(this));

        ListTag villageList = nbt.getList("villages").orElseGet(ListTag::new);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i).orElseGet(CompoundTag::new), world);
            if (village.repairDuplicateResidentHomes()) {
                setDirty();
            }
            if (village.getBuildings().isEmpty()) {
                MCA.LOGGER.warn("Empty village detected ({}), removing...", village.getName());
                setDirty();
            } else {
                villages.put(village.getId(), village);
            }
        }
    }

    public static VillageManager get(ServerLevel world) {
        return WorldUtils.loadData(world, (nbt, provider) -> new VillageManager(world, nbt), VillageManager::new, "mca_villages");
    }

    public ReaperSpawner getReaperSpawner() {
        return reapers;
    }

    public Optional<Village> getOrEmpty(int id) {
        return Optional.ofNullable(villages.get(id));
    }

    public boolean removeVillage(int id) {
        if (villages.remove(id) != null) {
            cache.clear();
            return true;
        }
        return false;
    }

    @Override
    public Iterator<Village> iterator() {
        return villages.values().iterator();
    }

    public Stream<Village> findVillages(Predicate<Village> predicate) {
        return villages.values().stream().filter(predicate);
    }

    public Optional<Village> findNearestVillage(Entity entity) {
        BlockPos p = entity.blockPosition();
        return findVillages(v -> v.isWithinBorder(entity)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    public Optional<Village> findNearestVillage(BlockPos p, int margin) {
        return findVillages(v -> v.isWithinBorder(p, margin)).min((a, b) -> (int) (a.getCenter().distSqr(p) - b.getCenter().distSqr(p)));
    }

    public boolean isWithinHorizontalBoundaries(BlockPos p) {
        return villages.values().stream().anyMatch(v -> v.getBox().expand(0, 1000, 0).isInside(p));
    }

    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        nbt.putInt("lastBuildingId", lastBuildingId);
        nbt.putInt("lastVillageId", lastVillageId);
        nbt.put("villages", NbtHelper.fromList(villages.values(), Village::save));
        nbt.put("reapers", reapers.writeNbt());
        return nbt;
    }

    /**
     * Updates all the villages in the world.
     */
    public void tick() {
        //keep track of where player are currently
        if (world.getOverworldClockTime() % 100 == 0) {
            world.players().forEach(player ->
                    PlayerSaveData.get(player).updateLastSeenVillage(this, player)
            );
        }

        //send bounty hunters
        if (world.getOverworldClockTime() % (Config.getInstance().bountyHunterInterval / 10) == 0 && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.players().forEach(player -> {
                if (world.getRandom().nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.blockPosition()) && !player.isCreative()) {
                    villages.values().stream()
                            .filter(v -> v.getPopulation() >= 3)
                            .filter(v -> v.getReputation(player) < Config.getInstance().bountyHunterHearts)
                            .min(Comparator.comparingInt(v -> v.getReputation(player)))
                            .ifPresent(buildings -> startBountyHunterWave(player, buildings));
                }
            });
        }

        long time = world.getGameTime();

        for (Village v : this) {
            v.tick(world, time);
        }

        //process a single building
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
            //the village has been wiped out, lets send one last wave
            sender.cleanReputation();

            count *= 2;
        } else {
            //slightly increase your reputation
            sender.pushHearts(player, count * heartsPerHunter / 2);
        }

        //trigger advancement
        CriterionMCA.GENERIC_EVENT.trigger(player, "bounty_hunter");

        //spawn the bois
        for (int c = 0; c < count; c++) {
            if (world.getRandom().nextBoolean()) {
                spawnBountyHunter(EntityType.PILLAGER, player);
            } else {
                spawnBountyHunter(EntityType.VINDICATOR, player);
            }
        }

        //warn the player
        player.sendSystemMessage(Component.translatable(sender.getPopulation() == 0 ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).withStyle(ChatFormatting.RED));

        //civil entry
        sender.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends AbstractIllager> void spawnBountyHunter(EntityType<T> t, ServerPlayer player) {
        AbstractIllager pillager = t.create(world, EntitySpawnReason.EVENT);
        if (pillager != null) {
            for (int attempt = 0; attempt < 32; attempt++) {
                float f = this.world.getRandom().nextFloat() * 6.2831855F;
                int x = (int) (player.getX() + Mth.cos(f) * 32.0f);
                int z = (int) (player.getZ() + Mth.sin(f) * 32.0f);
                int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (SpawnPlacements.isSpawnPositionOk(t, world, pos)) {
                    pillager.setPos(x, y, z);
                    pillager.setTarget(player);
                    WorldUtils.spawnEntity(world, pillager, EntitySpawnReason.EVENT);
                    break;
                }
            }
        }
    }

    //adds a potential block to the processing queue
    public void reportBuilding(BlockPos pos) {
        //mark in cache
        cache.add(pos);

        buildingQueue.add(pos);
    }

    public Building.validationResult processBuilding(BlockPos pos) {
        return processBuilding(pos, false, true);
    }

    //checks weather the given block contains a grouped building block, e.g., a town bell or gravestone
    private BuildingType getGroupedBuildingType(BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        Identifier blockId = BuiltInRegistries.BLOCK.getKey(block);
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.grouped() && bt.matchesBlock(blockId)) {
                return bt;
            }
        }
        return null;
    }

    //returns the scan-source blocks of all buildings, used to check for overlaps
    public Set<BlockPos> getBlockedSet(Village village) {
        return village.getBuildings().values().stream()
                .filter(b -> !b.getBuildingType().grouped())
                .map(Building::getSourceBlock)
                .collect(Collectors.toSet());
    }

    public BuildingBlockedResult getBlockedResult(BlockPos pos) {
        Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
        Set<BlockPos> blocked = new java.util.HashSet<>();
        Building existingBuilding = null;
        if (optionalVillage.isPresent()) {
            Village village = optionalVillage.get();
            blocked = getBlockedSet(village);
            for (Building b : village.getBuildings().values()) {
                if (b.containsPos(pos) && !b.getBuildingType().grouped()) {
                    existingBuilding = b;
                    break;
                }
            }
        }
        return new BuildingBlockedResult(blocked, existingBuilding, optionalVillage.orElse(null));
    }

    public BuildingScanResult analyzeBuilding(BlockPos pos, boolean strictScan) {
        BuildingBlockedResult blockResult = getBlockedResult(pos);
        Building building;
        if (blockResult.existingBuilding() != null) {
            building = new Building(blockResult.existingBuilding().getSourceBlock(), blockResult.existingBuilding().isStrictScan());
        } else {
            building = new Building(pos, strictScan);
        }
        Building.validationResult result = building.validateBuilding(world, blockResult.blocked());
        List<String> matchingTypes = new java.util.ArrayList<>();
        if (result == Building.validationResult.SUCCESS) {
            building.getVisibleMatchingTypes().forEach(bt -> matchingTypes.add(bt.name()));
        }
        return new BuildingScanResult(
            result,
            building.getSourceBlock(),
            building.isStrictScan(),
            building,
            matchingTypes,
            blockResult.village()
        );
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        if (forcedType != null && !scan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (forcedType == null && scan.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }
        return commitBuilding(scan.building(), scan.village(), forcedType);
    }

    private Building.validationResult commitBuilding(Building building, Village village, String forcedType) {
        Village targetVillage = village;
        if (targetVillage == null) {
            targetVillage = new Village(lastVillageId++, world);
        }
        Building existing = targetVillage.getBuildings().values().stream()
                .filter(b -> b.getSourceBlock().equals(building.getSourceBlock()))
                .findFirst().orElse(null);
        if (existing != null) {
            existing.getBlocks().clear();
            existing.getBlocks().putAll(building.getBlocks());
            existing.setLastScan(world.getGameTime());
            if (forcedType != null) {
                existing.setTypeForced(true);
                existing.setType(forcedType);
            } else {
                existing.setTypeForced(false);
                existing.determineType();
            }
            existing.validateBuilding(world, getBlockedSet(targetVillage));
        } else {
            if (forcedType != null) {
                building.setTypeForced(true);
                building.setType(forcedType);
            } else {
                building.setTypeForced(false);
                building.determineType();
            }
            BuildingBlockedResult blockResult = getBlockedResult(building.getSourceBlock());
            Building.validationResult result = building.validateBuilding(world, blockResult.blocked());
            if (result != Building.validationResult.SUCCESS) {
                return result;
            }
            if (targetVillage.getBuildings().values().stream().anyMatch(b -> b.isIdentical(building))) {
                return Building.validationResult.IDENTICAL;
            }
            villages.put(targetVillage.getId(), targetVillage);
            building.setId(lastBuildingId++);
            targetVillage.getBuildings().put(building.getId(), building);
        }
        targetVillage.calculateDimensions();
        Village finalVillage = targetVillage;
        villages.values().stream()
                .filter(v -> v != finalVillage)
                .filter(v -> v.getBox().inflatedBy(Village.MERGE_MARGIN).intersects(finalVillage.getBox()))
                .findAny()
                .ifPresent(v -> {
                    if (v.getPopulation() > finalVillage.getPopulation()) {
                        merge(v, finalVillage);
                        villages.remove(finalVillage.getId());
                    } else {
                        merge(finalVillage, v);
                        villages.remove(v.getId());
                    }
                });
        setDirty();
        return Building.validationResult.SUCCESS;
    }

    //processed a building at given position
    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan) {
        return processBuilding(pos, enforce, strictScan, null);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan, String forcedType) {
        BuildingType groupedBuildingType = getGroupedBuildingType(pos);
        if (groupedBuildingType != null) {
            Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
            if (optionalVillage.isPresent()) {
                Village village = optionalVillage.get();
                String name = groupedBuildingType.name();
                double range = groupedBuildingType.mergeRange() * groupedBuildingType.mergeRange();
                Optional<Building> building = village.getBuildings().values().stream()
                        .filter(b -> b.getType().equals(name))
                        .min((a, b) -> (int) (a.getCenter().distSqr(pos) - b.getCenter().distSqr(pos)))
                        .filter(b -> b.getCenter().distSqr(pos) < range);
                if (building.isPresent()) {
                    building.get().addPOI(world, pos);
                    setDirty();
                    return Building.validationResult.SUCCESS;
                }
            }
            Village village = optionalVillage.orElse(new Village(lastVillageId++, world));
            Building building = new Building(pos, strictScan);
            building.setType(groupedBuildingType.name());
            building.addPOI(world, pos);
            villages.put(village.getId(), village);
            building.setId(lastBuildingId++);
            village.getBuildings().put(building.getId(), building);
            village.calculateDimensions();
            setDirty();
            return Building.validationResult.SUCCESS;
        }
        BuildingScanResult scan = analyzeBuilding(pos, strictScan);
        if (scan.result() != Building.validationResult.SUCCESS) {
            if (enforce) {
                BuildingBlockedResult blockResult = getBlockedResult(pos);
                if (blockResult.existingBuilding() != null) {
                    Village village = blockResult.village();
                    if (village != null) {
                        village.removeBuilding(blockResult.existingBuilding().getId());
                        if (village.getBuildings().isEmpty()) {
                            villages.remove(village.getId());
                        }
                        setDirty();
                    }
                }
            }
            return scan.result();
        }
        return commitBuilding(scan, forcedType);
    }

    public void setBuildingCooldown(int buildingCooldown) {
        this.buildingCooldown = buildingCooldown;
    }

    public void merge(Village into, Village from) {
        into.merge(from);
    }
}

