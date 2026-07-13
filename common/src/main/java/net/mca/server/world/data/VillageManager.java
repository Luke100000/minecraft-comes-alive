package net.mca.server.world.data;

import net.mca.Config;
import net.mca.MCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.mca.resources.BuildingTypes;
import net.mca.resources.data.BuildingType;
import net.mca.server.ReaperSpawner;
import net.mca.server.SpawnQueue;
import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.IllagerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.PersistentState;
import net.minecraft.world.SpawnHelper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VillageManager extends PersistentState implements Iterable<Village> {
    private final Map<Integer, Village> villages = new HashMap<>();

    public final Set<BlockPos> cache = ConcurrentHashMap.newKeySet();

    private final List<BlockPos> buildingQueue = new LinkedList<>();

    private int lastBuildingId;
    private int lastVillageId;

    private final ServerWorld world;

    private final ReaperSpawner reapers;

    private int buildingCooldown = 21;

    public static VillageManager get(ServerWorld world) {
        return WorldUtils.loadData(world, nbt -> new VillageManager(world, nbt), VillageManager::new, "mca_villages");
    }

    VillageManager(ServerWorld world) {
        this.world = world;
        reapers = new ReaperSpawner(this);
    }

    VillageManager(ServerWorld world, NbtCompound nbt) {
        this.world = world;
        lastBuildingId = nbt.getInt("lastBuildingId");
        lastVillageId = nbt.getInt("lastVillageId");
        reapers = nbt.contains("reapers", NbtElement.COMPOUND_TYPE) ? new ReaperSpawner(this, nbt.getCompound("reapers")) : new ReaperSpawner(this);

        NbtList villageList = nbt.getList("villages", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i), world);
            if (village.getBuildings().isEmpty()) {
                MCA.LOGGER.warn("Empty village detected (" + village.getName() + "), removing...");
                markDirty();
            } else {
                villages.put(village.getId(), village);
            }
        }
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
        BlockPos p = entity.getBlockPos();
        return findVillages(v -> v.isWithinBorder(entity)).min((a, b) -> (int)(a.getCenter().getSquaredDistance(p) - b.getCenter().getSquaredDistance(p)));
    }

    public Optional<Village> findNearestVillage(BlockPos p, int margin) {
        return findVillages(v -> v.isWithinBorder(p, margin)).min((a, b) -> (int)(a.getCenter().getSquaredDistance(p) - b.getCenter().getSquaredDistance(p)));
    }

    public boolean isWithinHorizontalBoundaries(BlockPos p) {
        return villages.values().stream().anyMatch(v -> v.getBox().expand(0, 1000, 0).contains(p));
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
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
        if (world.getTimeOfDay() % 100 == 0) {
            world.getPlayers().forEach(player ->
                    PlayerSaveData.get(player).updateLastSeenVillage(this, player)
            );
        }

        //send bounty hunters
        int bountyHunterInterval = Config.getInstance().bountyHunterInterval;
        if (bountyHunterInterval > 0
                && world.getTimeOfDay() % Math.max(1, bountyHunterInterval / 10) == 0
                && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.getPlayers().forEach(player -> {
                if (world.random.nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.getBlockPos()) && !player.isCreative()) {
                    villages.values().stream()
                            .filter(v -> v.getPopulation() >= 3)
                            .filter(v -> v.getReputation(player) < Config.getInstance().bountyHunterHearts)
                            .min(Comparator.comparingInt(v -> v.getReputation(player)))
                            .ifPresent(buildings -> startBountyHunterWave(player, buildings));
                }
            });
        }

        long time = world.getTime();

        for (Village v : this) {
            v.tick(world, time);
        }

        //process a single building
        if (time % buildingCooldown == 0 && !buildingQueue.isEmpty()) {
            processBuilding(buildingQueue.remove(0));
        }

        reapers.tick(world);
        SpawnQueue.getInstance().tick();
    }

    private void startBountyHunterWave(ServerPlayerEntity player, Village sender) {
        int count = Math.min(30, -sender.getReputation(player) / 100 + 2);

        if (sender.getPopulation() == 0) {
            //the village has been wiped out, lets send one last wave
            sender.cleanReputation();
            sender.resetHearts(player);

            count *= 2;
        } else {
            //slightly increase your reputation
            sender.pushHearts(player, count * 50);
        }

        //trigger advancement
        CriterionMCA.GENERIC_EVENT_CRITERION.trigger(player, "bounty_hunter");

        //spawn the bois
        for (int c = 0; c < count; c++) {
            if (world.random.nextBoolean()) {
                spawnBountyHunter(EntityType.PILLAGER, player);
            } else {
                spawnBountyHunter(EntityType.VINDICATOR, player);
            }
        }

        //warn the player
        player.sendMessage(Text.translatable(sender.getPopulation() == 0 ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).formatted(Formatting.RED), false);

        //civil entry
        sender.getCivilRegistry().ifPresent(r -> r.addText(Text.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends IllagerEntity> void spawnBountyHunter(EntityType<T> t, ServerPlayerEntity player) {
        IllagerEntity pillager = t.create(world);
        if (pillager != null) {
            for (int attempt = 0; attempt < 32; attempt++) {
                float f = this.world.random.nextFloat() * 6.2831855F;
                int x = (int)(player.getX() + MathHelper.cos(f) * 32.0f);
                int z = (int)(player.getZ() + MathHelper.sin(f) * 32.0f);
                int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (SpawnHelper.canSpawn(SpawnRestriction.Location.ON_GROUND, world, pos, t)) {
                    pillager.setPosition(x, y, z);
                    pillager.setTarget(player);
                    WorldUtils.spawnEntity(world, pillager, SpawnReason.EVENT);
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
        Identifier blockId = Registries.BLOCK.getId(block);
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

    /**
     * Resolves the village context, occupied scan sources and any existing non-grouped
     * building that contains the supplied position. This is shared by the preview and
     * commit paths so both operate on the same overlap information.
     */
    public BuildingBlockedResult getBlockedResult(BlockPos pos) {
        Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
        Set<BlockPos> blocked = new HashSet<>();
        Building existingBuilding = null;
        if (optionalVillage.isPresent()) {
            Village village = optionalVillage.get();
            blocked = getBlockedSet(village);
            for (Building building : village.getBuildings().values()) {
                if (building.containsPos(pos) && !building.getBuildingType().grouped()) {
                    existingBuilding = building;
                    break;
                }
            }
        }
        return new BuildingBlockedResult(blocked, existingBuilding, optionalVillage.orElse(null));
    }

    /**
     * Scans a potential building without mutating village data. The result can be sent
     * to the client when more than one visible building type matches and committed after
     * the player chooses a type.
     */
    public BuildingScanResult analyzeBuilding(BlockPos pos, boolean strictScan) {
        BuildingBlockedResult blockResult = getBlockedResult(pos);
        Building building;
        if (blockResult.existingBuilding() != null) {
            building = new Building(blockResult.existingBuilding().getSourceBlock(), blockResult.existingBuilding().isStrictScan());
        } else {
            building = new Building(pos, strictScan);
        }
        Building.validationResult result = building.validateBuilding(world, blockResult.blocked());
        List<String> matchingTypes = new ArrayList<>();
        if (result == Building.validationResult.SUCCESS) {
            building.getVisibleMatchingTypes().forEach(type -> matchingTypes.add(type.name()));
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
                .filter(entry -> entry.getSourceBlock().equals(building.getSourceBlock()))
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.getBlocks().clear();
            existing.getBlocks().putAll(building.getBlocks());
            existing.setLastScan(world.getTime());
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

            // The building is valid, but it might be identical to an existing one.
            if (targetVillage.getBuildings().values().stream().anyMatch(entry -> entry.isIdentical(building))) {
                return Building.validationResult.IDENTICAL;
            }

            villages.put(targetVillage.getId(), targetVillage);
            building.setId(lastBuildingId++);
            targetVillage.getBuildings().put(building.getId(), building);
        }

        targetVillage.calculateDimensions();
        Village finalVillage = targetVillage;

        // Attempt to merge villages whose expanded borders now overlap.
        villages.values().stream()
                .filter(candidate -> candidate != finalVillage)
                .filter(candidate -> candidate.getBox().expand(Village.MERGE_MARGIN).intersects(finalVillage.getBox()))
                .findAny()
                .ifPresent(candidate -> {
                    if (candidate.getPopulation() > finalVillage.getPopulation()) {
                        merge(candidate, finalVillage);
                        villages.remove(finalVillage.getId());
                    } else {
                        merge(finalVillage, candidate);
                        villages.remove(candidate.getId());
                    }
                });

        markDirty();
        return Building.validationResult.SUCCESS;
    }

    //processed a building at given position
    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan) {
        return processBuilding(pos, enforce, strictScan, null);
    }

    public Building.validationResult processBuilding(BlockPos pos, boolean enforce, boolean strictScan, String forcedType) {
        // Check first whether this is a grouped building, e.g. a town bell or gravestone.
        BuildingType groupedBuildingType = getGroupedBuildingType(pos);
        if (groupedBuildingType != null) {
            Optional<Village> optionalVillage = findNearestVillage(pos, Village.MERGE_MARGIN);
            if (optionalVillage.isPresent()) {
                Village village = optionalVillage.get();
                String name = groupedBuildingType.name();
                double range = groupedBuildingType.mergeRange() * groupedBuildingType.mergeRange();

                // Add the POI to the nearest compatible grouped building.
                Optional<Building> building = village.getBuildings().values().stream()
                        .filter(entry -> entry.getType().equals(name))
                        .min((a, b) -> Double.compare(a.getCenter().getSquaredDistance(pos), b.getCenter().getSquaredDistance(pos)))
                        .filter(entry -> entry.getCenter().getSquaredDistance(pos) < range);
                if (building.isPresent()) {
                    building.get().addPOI(world, pos);
                    markDirty();
                    return Building.validationResult.SUCCESS;
                }
            }

            // No nearby grouped building exists, so create one in the nearest/new village.
            Village village = optionalVillage.orElse(new Village(lastVillageId++, world));
            Building building = new Building(pos, strictScan);
            building.setType(groupedBuildingType.name());
            building.addPOI(world, pos);
            villages.put(village.getId(), village);
            building.setId(lastBuildingId++);
            village.getBuildings().put(building.getId(), building);
            village.calculateDimensions();
            markDirty();
            return Building.validationResult.SUCCESS;
        }

        BuildingScanResult scan = analyzeBuilding(pos, strictScan);
        if (scan.result() != Building.validationResult.SUCCESS) {
            // Enforced rescans remove a now-invalid existing building just like the legacy path.
            if (enforce) {
                BuildingBlockedResult blockResult = getBlockedResult(pos);
                if (blockResult.existingBuilding() != null) {
                    Village village = blockResult.village();
                    if (village != null) {
                        village.removeBuilding(blockResult.existingBuilding().getId());
                        if (village.getBuildings().isEmpty()) {
                            villages.remove(village.getId());
                        }
                        markDirty();
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
