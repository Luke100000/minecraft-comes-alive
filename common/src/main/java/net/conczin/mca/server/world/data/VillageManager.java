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
        lastBuildingId = nbt.getInt("lastBuildingId");
        lastVillageId = nbt.getInt("lastVillageId");
        reapers = nbt.contains("reapers", Tag.TAG_COMPOUND) ? new ReaperSpawner(this, nbt.getCompound("reapers")) : new ReaperSpawner(this);

        ListTag villageList = nbt.getList("villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < villageList.size(); i++) {
            Village village = new Village(villageList.getCompound(i), world);
            if (village.getBuildings().isEmpty()) {
                MCA.LOGGER.warn("Empty village detected ({}), removing...", village.getName());
                setDirty();
            } else {
                villages.put(village.getId(), village);
                if (BuildingStructureManager.ensureHierarchy(village)) {
                    setDirty();
                }
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

    @Override
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
        if (world.getDayTime() % 100 == 0) {
            world.players().forEach(player ->
                    PlayerSaveData.get(player).updateLastSeenVillage(this, player)
            );
        }

        //send bounty hunters
        int bountyHunterInterval = Config.getInstance().bountyHunterInterval;
        if (bountyHunterInterval > 0 && world.getDayTime() % Math.max(1, bountyHunterInterval / 10) == 0 && world.getDifficulty() != Difficulty.PEACEFUL) {
            world.players().forEach(player -> {
                if (world.random.nextInt(10) == 0 && !isWithinHorizontalBoundaries(player.blockPosition()) && !player.isCreative()) {
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
            if (world.random.nextBoolean()) {
                spawnBountyHunter(EntityType.PILLAGER, player);
            } else {
                spawnBountyHunter(EntityType.VINDICATOR, player);
            }
        }

        //warn the player
        player.displayClientMessage(Component.translatable(sender.getPopulation() == 0 ? "events.bountyHuntersFinal" : "events.bountyHunters", sender.getName()).withStyle(ChatFormatting.RED), false);

        //civil entry
        sender.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("civil_registry.bounty_hunters", player.getName())));
    }

    private <T extends AbstractIllager> void spawnBountyHunter(EntityType<T> t, ServerPlayer player) {
        AbstractIllager pillager = t.create(world);
        if (pillager != null) {
            for (int attempt = 0; attempt < 32; attempt++) {
                float f = this.world.random.nextFloat() * 6.2831855F;
                int x = (int) (player.getX() + Mth.cos(f) * 32.0f);
                int z = (int) (player.getZ() + Mth.sin(f) * 32.0f);
                int y = world.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                BlockPos pos = new BlockPos(x, y, z);
                if (SpawnPlacements.isSpawnPositionOk(t, world, pos)) {
                    pillager.setPos(x, y, z);
                    pillager.setTarget(player);
                    WorldUtils.spawnEntity(world, pillager, MobSpawnType.EVENT);
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
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(block);
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
        Set<BlockPos> blocked = new HashSet<>();
        Building existingBuilding = null;
        if (optionalVillage.isPresent()) {
            Village village = optionalVillage.get();
            blocked = getBlockedSet(village);
            existingBuilding = village.getStructuralLookup(pos).building().orElse(null);
        }
        return new BuildingBlockedResult(blocked, existingBuilding, optionalVillage.orElse(null));
    }

    private BuildingScanResult analyzeBuilding(BlockPos pos, boolean strictScan) {
        return analyzeBuilding(pos, strictScan, false, null, -1);
    }

    public BuildingScanResult analyzeRoom(BlockPos pos) {
        return analyzeBuilding(pos, true, true, null, -1);
    }

    public InitialStructureScan analyzeInitialStructure(BlockPos pos) {
        BuildingScanResult root = analyzeBuilding(pos, false);
        if (root.result() != Building.validationResult.SUCCESS) {
            Building emptyRoom = new Building(pos, true);
            return new InitialStructureScan(root, new BuildingScanResult(
                    root.result(), pos, true, emptyRoom, List.of(), root.village()));
        }
        return new InitialStructureScan(root, analyzeBuilding(pos, true, true, root.village(), -1, false));
    }

    public BuildingScanResult analyzeRegisteredRoom(Village village, int buildingId, BlockPos pos) {
        return analyzeBuilding(pos, true, true, village, buildingId);
    }

    private BuildingScanResult analyzeBuilding(BlockPos pos,
                                               boolean strictScan,
                                               boolean roomScan,
                                               Village knownVillage,
                                               int preferredBuildingId) {
        return analyzeBuilding(pos, strictScan, roomScan, knownVillage, preferredBuildingId, true);
    }

    private BuildingScanResult analyzeBuilding(BlockPos pos,
                                               boolean strictScan,
                                               boolean roomScan,
                                               Village knownVillage,
                                               int preferredBuildingId,
                                               boolean assignRoom) {
        BuildingBlockedResult blockResult = knownVillage == null ? getBlockedResult(pos) : null;
        Village village = knownVillage != null ? knownVillage : blockResult.village();

        ensureStructureHierarchy(village);

        Building locatedExisting = blockResult == null ? null : blockResult.existingBuilding();
        Building preferred = village == null
                ? null
                : preferredBuildingId >= 0
                ? village.getBuilding(preferredBuildingId).orElse(null)
                : locatedExisting;
        int effectivePreferredId = preferred == null ? preferredBuildingId : preferred.getId();
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=analyze-start source={} strict={} roomScan={} assignRoom={} knownVillageId={} resolvedVillageId={} locatedExisting={} preferred={} preferredId={}",
                pos, strictScan, roomScan, assignRoom,
                knownVillage == null ? -1 : knownVillage.getId(), village == null ? -1 : village.getId(),
                debugBuilding(locatedExisting), debugBuilding(preferred), effectivePreferredId);

        Set<BlockPos> blocked = village == null
                ? new HashSet<>()
                : new HashSet<>(getBlockedSet(village));

        /*
         * Room discovery must be able to cross the old source anchor so expansions,
         * shrinks and splits can be identified after the new geometry is known.
         * Cross-structure conflicts are rejected by the identity matcher.
         */
        if (roomScan) {
            blocked.clear();
        } else if (preferred != null && preferred.hasStructure()) {
            int structureId = preferred.getEffectiveStructureId();
            BuildingStructureManager.members(village, structureId)
                    .forEach(member -> blocked.remove(member.getSourceBlock()));
        }

        boolean effectiveStrictScan = (roomScan
                || preferred == null
                || preferredBuildingId >= 0)
                ? strictScan
                : preferred.isStrictScan();
        BlockPos scanSource = !roomScan && preferred != null && preferredBuildingId < 0
                ? preferred.getSourceBlock()
                : pos;
        Building building = new Building(scanSource, effectiveStrictScan);

        boolean allowMissingEntrance = roomScan
                || (preferred != null && preferred.hasStructure() && !preferred.isStructureRoot());

        Building.validationResult result = building.validateBuilding(world, blocked, allowMissingEntrance);
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=validate-result source={} result={} roomScan={} allowMissingEntrance={} blockedCount={} candidate={}",
                scanSource, result, roomScan, allowMissingEntrance, blocked.size(), debugBuilding(building));
        int existingBuildingId = -1;
        List<Integer> mergedBuildingIds = List.of();

        if (result == Building.validationResult.SUCCESS) {
            if (roomScan) {
                building.retainFloorClosestTo(scanSource.getY());
            }

            BuildingStructureManager.MatchResult match =
                    BuildingStructureManager.matchExistingRoom(building, village, effectivePreferredId);
            MCA.LOGGER.info("[FloorRoomDebug] side=server stage=match-existing-room source={} matchResult={} matched={} mergedIds={} candidateFloorY={} candidateRegions={}",
                    scanSource, match.result(), debugBuilding(match.primary()), match.mergedBuildingIds(),
                    building.getFloorY(), building.getFloorRegions().stream().map(BuildingFloorRegion::anchorY).toList());
            MCA.LOGGER.debug(
                    "[BuildingRoomScan] source={} roomScan={} matchResult={} matchedId={} mergedIds={} floorY={} groundFloorY={} floorRegions={}",
                    scanSource, roomScan, match.result(),
                    match.primary() == null ? -1 : match.primary().getId(), match.mergedBuildingIds(),
                    building.getFloorY(), building.getGroundFloorY(),
                    building.getFloorRegions().stream().map(BuildingFloorRegion::anchorY).toList());

            if (match.result() != Building.validationResult.SUCCESS) {
                result = match.result();
            } else if (match.hasMatch()) {
                Building existing = match.primary();
                building.setStructureId(existing.getStructureId());
                building.setStructureRoot(existing.isStructureRoot());
                existingBuildingId = existing.getId();
                mergedBuildingIds = match.mergedBuildingIds();
            } else if (roomScan && assignRoom) {
                result = BuildingStructureManager.assignNewRoom(building, village);
                MCA.LOGGER.info("[FloorRoomDebug] side=server stage=assign-new-room-result source={} result={} candidate={}",
                        scanSource, result, debugBuilding(building));
                MCA.LOGGER.debug("[BuildingRoomScan] source={} assignmentResult={} structureId={}",
                        scanSource, result, building.getStructureId());
            }
        }

        List<String> matchingTypes = result == Building.validationResult.SUCCESS
                ? building.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : List.of();

        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=analyze-result source={} result={} existing={} merged={} matchingTypes={} candidate={}",
                scanSource, result, existingBuildingId, mergedBuildingIds, matchingTypes, debugBuilding(building));

        return new BuildingScanResult(
                result,
                building.getSourceBlock(),
                building.isStrictScan(),
                building,
                matchingTypes,
                village,
                existingBuildingId,
                mergedBuildingIds
        );
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=commit-building-start scanResult={} source={} scanVillageId={} existing={} merged={} forcedType={} candidate={}",
                scan.result(), scan.source(), scan.village() == null ? -1 : scan.village().getId(),
                scan.existingBuildingId(), scan.mergedBuildingIds(), forcedType, debugBuilding(scan.building()));
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        Building existingForValidation = scan.hasExistingBuilding() && scan.village() != null
                ? scan.village().getBuilding(scan.existingBuildingId()).orElse(null)
                : null;
        boolean updatingStructureContainer = existingForValidation != null
                && existingForValidation.isStructureContainer();
        if (forcedType != null && !scan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (forcedType == null && scan.isAmbiguous() && !updatingStructureContainer) {
            return Building.validationResult.INVALID_TYPE;
        }

        Building building = scan.building();
        Village targetVillage = scan.village();
        if (targetVillage == null) {
            targetVillage = new Village(lastVillageId++, world);
        } else {
            ensureStructureHierarchy(targetVillage);
        }

        Building existing = scan.hasExistingBuilding()
                ? targetVillage.getBuilding(scan.existingBuildingId()).orElse(null)
                : null;

        if (existing != null) {
            int structureId = existing.getEffectiveStructureId();
            boolean mergedAcrossStructures = scan.mergedBuildingIds().stream()
                    .map(targetVillage::getBuilding)
                    .flatMap(Optional::stream)
                    .anyMatch(candidate -> candidate.getEffectiveStructureId() != structureId);
            if (mergedAcrossStructures) {
                return Building.validationResult.AMBIGUOUS_STRUCTURE;
            }

            // A strict non-root record is a registered room. Its floor is an assigned
            // semantic identity, so every rescan (including Full Scan) refreshes its
            // geometry without moving it between Basement, Ground Floor, and upper-floor
            // groups. Moving floors requires remove/re-add or a future explicit operation.
            existing.copyScannedGeometryFrom(
                    building,
                    world,
                    existing.isStrictScan() && !existing.isStructureRoot()
            );
            if (existing.isStructureContainer()) {
                existing.makeStructureContainer();
            } else if (forcedType != null) {
                existing.setTypeForced(true);
                existing.setType(forcedType);
            } else {
                existing.setTypeForced(false);
                existing.setType(building.getType());
            }

            for (int mergedId : scan.mergedBuildingIds()) {
                if (mergedId != existing.getId()) {
                    targetVillage.removeBuilding(mergedId);
                }
            }
        } else {
            if (forcedType != null) {
                building.setTypeForced(true);
                building.setType(forcedType);
            } else {
                // validateBuilding already resolved a valid unambiguous type.
                building.setTypeForced(false);
            }

            if (targetVillage.getBuildings().values().stream().anyMatch(b -> b.isIdentical(building))) {
                return Building.validationResult.IDENTICAL;
            }

            villages.put(targetVillage.getId(), targetVillage);
            building.setId(lastBuildingId++);
            if (!building.hasStructure()) {
                building.setStructureId(building.getId());
                building.setStructureRoot(true);
            }
            targetVillage.getBuildings().put(building.getId(), building);
        }

        finalizeVillageMutation(targetVillage);
        Village committedVillage = targetVillage;
        MCA.LOGGER.info("[FloorRoomDebug] side=server stage=commit-building-finished source={} villageId={} buildingCount={} candidateNow={}",
                scan.source(), committedVillage.getId(), committedVillage.getBuildings().size(), debugBuilding(building));
        committedVillage.getBuildings().values().stream()
                .sorted(Comparator.comparingInt(Building::getId))
                .forEach(persisted -> MCA.LOGGER.info(
                        "[FloorRoomDebug] side=server stage=commit-building-state villageId={} {}",
                        committedVillage.getId(), debugBuilding(persisted)));
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitInitialStructure(InitialStructureScan scan, String forcedRoomType) {
        if (scan.result() != Building.validationResult.SUCCESS) {
            return scan.result();
        }
        if (scan.root().hasExistingBuilding() || scan.room().hasExistingBuilding()) {
            return Building.validationResult.IDENTICAL;
        }
        if (forcedRoomType != null && !scan.room().matchesType(forcedRoomType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (forcedRoomType == null && scan.room().isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }

        Village targetVillage = scan.root().village();
        if (targetVillage != null) {
            ensureStructureHierarchy(targetVillage);
            if (targetVillage.getStructuralPosition(scan.root().source()) != Village.StructuralPosition.OUTSIDE) {
                return Building.validationResult.IDENTICAL;
            }
        } else {
            targetVillage = new Village(lastVillageId++, world);
        }

        Building root = scan.root().building();
        Building room = scan.room().building();
        if (targetVillage.getBuildings().values().stream()
                .anyMatch(existing -> existing.isIdentical(root) || existing.isIdentical(room))) {
            return Building.validationResult.IDENTICAL;
        }

        root.setId(lastBuildingId++);
        root.setStructureId(root.getId());
        root.makeStructureContainer();

        room.setId(lastBuildingId++);
        room.setStructureId(root.getId());
        room.setStructureRoot(false);
        room.setTypeForced(forcedRoomType != null);
        if (forcedRoomType != null) {
            room.setType(forcedRoomType);
        }

        targetVillage.getBuildings().put(root.getId(), root);
        targetVillage.getBuildings().put(room.getId(), room);
        villages.put(targetVillage.getId(), targetVillage);
        finalizeVillageMutation(targetVillage);
        return Building.validationResult.SUCCESS;
    }

    private void finalizeVillageMutation(Village targetVillage) {
        BuildingStructureManager.ensureHierarchy(targetVillage);
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
    }

    private static String debugBuilding(Building building) {
        if (building == null) {
            return "none";
        }
        return "id=" + building.getId()
                + ",structure=" + building.getEffectiveStructureId()
                + ",root=" + building.isStructureRoot()
                + ",strict=" + building.isStrictScan()
                + ",functional=" + building.isFunctionalRoom()
                + ",floorY=" + building.getFloorY()
                + ",groundFloorY=" + building.getGroundFloorY()
                + ",floorRegions=" + building.getFloorRegions().stream().map(BuildingFloorRegion::anchorY).toList()
                + ",source=" + building.getSourceBlock()
                + ",bounds=" + building.getPos0() + ".." + building.getPos1();
    }

    public void ensureStructureHierarchy(Village village) {
        if (BuildingStructureManager.ensureHierarchy(village)) {
            setDirty();
        }
    }

    public void removeStructure(Village village, int structureId) {
        BuildingStructureManager.removeStructure(village, structureId);
        if (village != null && village.getBuildings().isEmpty()) {
            removeVillage(village.getId());
        }
        setDirty();
    }

    /**
     * Rescans an existing room by persistent identity. Source anchor is tried first;
     * center is only a fallback. Ambiguous scans are intentionally non-destructive.
     */
    public Building.validationResult rescanBuilding(Village village, int buildingId) {
        ensureStructureHierarchy(village);

        Building existing = village == null ? null : village.getBuilding(buildingId).orElse(null);
        if (existing == null) {
            return Building.validationResult.TOO_SMALL;
        }
        if (existing.getBuildingType().grouped()) {
            return processBuilding(existing.getCenter(), true, existing.isStrictScan());
        }

        List<BlockPos> probes = new ArrayList<>();
        probes.add(existing.getSourceBlock());
        if (!existing.getCenter().equals(existing.getSourceBlock())) {
            probes.add(existing.getCenter());
        }

        BuildingScanResult lastScan = null;
        for (BlockPos probe : probes) {
            BuildingScanResult scan = analyzeBuilding(
                    probe,
                    existing.isStrictScan(),
                    !existing.isStructureRoot(),
                    village,
                    existing.getId()
            );
            lastScan = scan;

            if (scan.result() == Building.validationResult.SUCCESS && scan.hasExistingBuilding()) {
                return commitBuilding(scan, null);
            }
            if (scan.result() == Building.validationResult.AMBIGUOUS_STRUCTURE
                    || scan.result() == Building.validationResult.OVERLAP) {
                return scan.result();
            }
        }

        // Refresh is deliberately non-destructive. A blocked source, removed door,
        // open-floor remodel, or temporarily ambiguous scan must not erase persistent
        // room identities or the entire Building -> Rooms structure hierarchy.
        return lastScan == null
                ? Building.validationResult.TOO_SMALL
                : lastScan.result();
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
                        ensureStructureHierarchy(village);
                        Building existing = blockResult.existingBuilding();
                        if (existing.isStructureRoot()) {
                            removeStructure(village, existing.getEffectiveStructureId());
                        } else {
                            village.removeBuilding(existing.getId());
                            if (village.getBuildings().isEmpty()) {
                                villages.remove(village.getId());
                            }
                            setDirty();
                        }
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
