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
        return processAutoScannedBuilding(pos);
    }

    private Building.validationResult processAutoScannedBuilding(BlockPos pos) {
        // Grouped POIs (bells, grave markers, etc.) keep their existing aggregation path.
        if (getGroupedBuildingType(pos) != null) {
            return processBuilding(pos, false, true, null);
        }

        // Auto Scan first behaves like Add Room. This updates an existing room or assigns
        // a genuinely new room to exactly one unambiguous rooted structure.
        BuildingScanResult roomScan = analyzeRoom(pos);

        return switch (getAutoScanResolution(roomScan.result())) {
            case COMMIT_ROOM -> commitBuilding(roomScan, null);
            case CREATE_STRUCTURE -> {
                // No existing structure owns this room. Fall back to the same canonical
                // container + Ground Floor creation used by manual Add Building.
                InitialStructureScan initialScan = analyzeInitialStructure(pos);
                yield commitInitialStructure(initialScan, null);
            }
            case REJECT -> roomScan.result();
        };
    }

    static AutoScanResolution getAutoScanResolution(Building.validationResult roomResult) {
        return switch (roomResult) {
            case SUCCESS -> AutoScanResolution.COMMIT_ROOM;
            case NOT_IN_BUILDING -> AutoScanResolution.CREATE_STRUCTURE;
            default -> AutoScanResolution.REJECT;
        };
    }

    enum AutoScanResolution {
        COMMIT_ROOM,
        CREATE_STRUCTURE,
        REJECT
    }

    private enum ScanMode {
        STRUCTURE,
        ROOM
    }

    private enum RoomAssignment {
        MATCH_ONLY,
        ASSIGN_IF_NEW
    }

    private record ScanRequest(BlockPos pos,
                               boolean strictScan,
                               ScanMode mode,
                               Village village,
                               int preferredBuildingId,
                               RoomAssignment assignment,
                               Building explicitStructureRoot) {
        private static ScanRequest structure(BlockPos pos, boolean strictScan) {
            return new ScanRequest(pos, strictScan, ScanMode.STRUCTURE,
                    null, -1, RoomAssignment.MATCH_ONLY, null);
        }

        private static ScanRequest room(BlockPos pos,
                                        Village village,
                                        int preferredBuildingId,
                                        RoomAssignment assignment,
                                        Building explicitStructureRoot) {
            return new ScanRequest(pos, true, ScanMode.ROOM,
                    village, preferredBuildingId, assignment, explicitStructureRoot);
        }

        private static ScanRequest rescan(BlockPos pos, Village village, Building existing) {
            return new ScanRequest(pos, existing.isStrictScan(),
                    existing.isStructureRoot() ? ScanMode.STRUCTURE : ScanMode.ROOM,
                    village, existing.getId(), RoomAssignment.ASSIGN_IF_NEW, null);
        }
    }

    private record GeometryScan(BuildingScanResult scan, int preferredBuildingId) {
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
            existingBuilding = village.getStructuralLookup(world, pos).building().orElse(null);
        }
        return new BuildingBlockedResult(blocked, existingBuilding, optionalVillage.orElse(null));
    }

    private BuildingScanResult analyzeStructure(BlockPos pos, boolean strictScan) {
        return analyzeBuilding(ScanRequest.structure(pos, strictScan));
    }

    public BuildingScanResult analyzeRoom(BlockPos pos) {
        return analyzeBuilding(ScanRequest.room(pos, null, -1, RoomAssignment.ASSIGN_IF_NEW, null));
    }

    public InitialStructureScan analyzeInitialStructure(BlockPos pos) {
        BuildingScanResult root = analyzeStructure(pos, false);
        if (root.result() != Building.validationResult.SUCCESS) {
            Building emptyRoom = new Building(pos, true);
            return new InitialStructureScan(root, new BuildingScanResult(
                    root.result(), pos, true, emptyRoom, List.of(), root.village()));
        }
        return new InitialStructureScan(root, analyzeBuilding(ScanRequest.room(
                pos, root.village(), -1, RoomAssignment.MATCH_ONLY, root.building())));
    }

    public BuildingScanResult analyzeRegisteredRoom(Village village, int buildingId, BlockPos pos) {
        return analyzeBuilding(ScanRequest.room(
                pos, village, buildingId, RoomAssignment.ASSIGN_IF_NEW, null));
    }

    public RoomUpdatePlan analyzeRegisteredRoomUpdate(Village village, int buildingId, BlockPos pos) {
        if (village == null) {
            return RoomUpdatePlan.failure(Building.validationResult.NOT_IN_BUILDING, null, buildingId);
        }
        Building expected = village.getBuilding(buildingId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return RoomUpdatePlan.conflict(Building.validationResult.OVERLAP, null, null, buildingId);
        }

        GeometryScan requestedGeometry = scanBuildingGeometry(ScanRequest.room(
                pos, village, buildingId, RoomAssignment.MATCH_ONLY, null));
        BuildingScanResult requestedRaw = requestedGeometry.scan();
        if (requestedRaw.result() != Building.validationResult.SUCCESS) {
            return RoomUpdatePlan.failure(requestedRaw.result(), requestedRaw, buildingId);
        }

        Building structureRoot = BuildingStructureManager.root(
                village, expected.getEffectiveStructureId()).orElse(null);
        if (structureRoot == null) {
            return RoomUpdatePlan.failure(Building.validationResult.NOT_IN_BUILDING, requestedRaw, buildingId);
        }

        Building requestedBuilding = requestedRaw.building();
        requestedBuilding.setStructureId(expected.getEffectiveStructureId());
        requestedBuilding.setStructureRoot(false);
        requestedBuilding.canonicalizeFloor(structureRoot);

        // Detect a remodel split before ordinary identity matching. Otherwise a large
        // anchor-side component (or a component retaining >80% of the old footprint)
        // can look like a normal resize and silently discard the other room.
        long requestedArea = requestedBuilding.getFloorFootprintArea();
        boolean requestedInsideOldFootprint =
                requestedBuilding.getHorizontalFootprintIntersectionArea(expected) == requestedArea;
        if (requestedInsideOldFootprint
                && !BuildingStructureManager.sameRoomGeometry(expected, requestedBuilding, village)) {
            List<BuildingScanResult> components = new ArrayList<>();
            addDistinctSplitComponent(components, requestedRaw, village);
            discoverSplitComponents(village, expected, structureRoot, components);

            if (components.size() > 2) {
                return RoomUpdatePlan.conflict(
                        Building.validationResult.OVERLAP, requestedRaw, null, buildingId);
            }
            if (components.size() == 2) {
                BuildingScanResult first = components.get(0);
                BuildingScanResult second = components.get(1);
                Building retainedBuilding = BuildingStructureManager.selectSplitRetainedSide(
                        expected, first.building(), second.building()).orElse(null);
                if (retainedBuilding == null) {
                    return RoomUpdatePlan.conflict(
                            Building.validationResult.OVERLAP, requestedRaw, null, buildingId);
                }

                BuildingScanResult retained = retainedBuilding == first.building() ? first : second;
                BuildingScanResult added = retained == first ? second : first;
                Building.validationResult splitResult = BuildingStructureManager.validateRoomSplit(
                        expected, retained.building(), added.building(), village);
                return splitResult == Building.validationResult.SUCCESS
                        ? RoomUpdatePlan.split(requestedRaw, retained, added, buildingId)
                        : RoomUpdatePlan.conflict(splitResult, requestedRaw, retained, buildingId);
            }
        }

        BuildingScanResult requested = BuildingStructureManager.resolveScanIdentity(
                requestedRaw, requestedGeometry.preferredBuildingId(), false);
        if (requested.result() != Building.validationResult.SUCCESS) {
            return RoomUpdatePlan.failure(requested.result(), requested, buildingId);
        }
        if (requested.existingBuildingId() == buildingId && requested.mergedBuildingIds().isEmpty()) {
            return RoomUpdatePlan.update(requested, buildingId);
        }
        return RoomUpdatePlan.conflict(Building.validationResult.OVERLAP, requested, null, buildingId);
    }

    private void discoverSplitComponents(Village village,
                                         Building expected,
                                         Building structureRoot,
                                         List<BuildingScanResult> components) {
        int canonicalFloorY = BuildingStructureManager.canonicalFloorY(village, expected);
        int probeY = canonicalFloorY + BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        BlockPos min = expected.getRawPos0();
        BlockPos max = expected.getRawPos1();

        for (int x = min.getX(); x <= max.getX() && components.size() <= 2; x++) {
            for (int z = min.getZ(); z <= max.getZ() && components.size() <= 2; z++) {
                if (!expected.containsFloorColumn(x, z)
                        || containsSplitComponentColumn(components, x, z)) {
                    continue;
                }

                BlockPos column = new BlockPos(x, probeY, z);
                BuildingFloorResolver.ResolvedFloor floor =
                        BuildingFloorResolver.resolve(world, column, structureRoot).orElse(null);
                if (floor == null
                        || floor.semanticY() != canonicalFloorY
                        || !BuildingRoomScanner.hasOpenCellInColumn(
                        world, x, z, floor.physicalY(), floor.ceilingY())) {
                    continue;
                }

                BuildingScanResult component = scanRegisteredRoomGeometry(
                        village, expected, structureRoot,
                        new BlockPos(x, floor.physicalY(), z));
                if (component != null) {
                    addDistinctSplitComponent(components, component, village);
                }
            }
        }
    }

    private static boolean containsSplitComponentColumn(List<BuildingScanResult> components,
                                                        int x,
                                                        int z) {
        for (BuildingScanResult component : components) {
            if (component.building().containsFloorColumn(x, z)) {
                return true;
            }
        }
        return false;
    }

    private BuildingScanResult scanRegisteredRoomGeometry(Village village,
                                                          Building expected,
                                                          Building structureRoot,
                                                          BlockPos probe) {
        GeometryScan geometry = scanBuildingGeometry(ScanRequest.room(
                probe, village, expected.getId(), RoomAssignment.MATCH_ONLY, structureRoot));
        BuildingScanResult scan = geometry.scan();
        if (scan.result() != Building.validationResult.SUCCESS) {
            return null;
        }

        Building room = scan.building();
        room.setStructureId(expected.getEffectiveStructureId());
        room.setStructureRoot(false);
        room.canonicalizeFloor(structureRoot);
        if (room.getHorizontalFootprintIntersectionArea(expected) != room.getFloorFootprintArea()) {
            return null;
        }
        return scan;
    }

    private static void addDistinctSplitComponent(List<BuildingScanResult> components,
                                                  BuildingScanResult candidate,
                                                  Village village) {
        boolean duplicate = components.stream().anyMatch(existing ->
                BuildingStructureManager.sameRoomGeometry(
                        existing.building(), candidate.building(), village));
        if (!duplicate) {
            components.add(candidate);
        }
    }

    private BuildingScanResult analyzeBuilding(ScanRequest request) {
        GeometryScan geometry = scanBuildingGeometry(request);
        return BuildingStructureManager.resolveScanIdentity(
                geometry.scan(),
                geometry.preferredBuildingId(),
                request.mode() == ScanMode.ROOM
                        && request.assignment() == RoomAssignment.ASSIGN_IF_NEW);
    }

    private GeometryScan scanBuildingGeometry(ScanRequest request) {
        BlockPos pos = request.pos();
        boolean roomScan = request.mode() == ScanMode.ROOM;
        Village knownVillage = request.village();
        int preferredBuildingId = request.preferredBuildingId();
        Building explicitStructureRoot = request.explicitStructureRoot();
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
                ? request.strictScan()
                : preferred.isStrictScan();
        BlockPos scanSource = !roomScan && preferred != null && preferredBuildingId < 0
                ? preferred.getSourceBlock()
                : pos;
        Building building = new Building(scanSource, effectiveStrictScan);
        Building roomScanRoot = roomScan
                ? resolveRoomScanRoot(village, preferred, scanSource, explicitStructureRoot)
                : null;

        boolean allowMissingEntrance = roomScan
                || (preferred != null && preferred.hasStructure() && !preferred.isStructureRoot());

        Building.validationResult result = building.validateBuilding(
                world, blocked, allowMissingEntrance, roomScanRoot);
        List<String> matchingTypes = result == Building.validationResult.SUCCESS
                ? building.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : List.of();

        BuildingScanResult geometry = new BuildingScanResult(
                result,
                building.getSourceBlock(),
                building.isStrictScan(),
                building,
                matchingTypes,
                village,
                -1,
                List.of()
        );
        return new GeometryScan(geometry, effectivePreferredId);
    }


    private static Building resolveRoomScanRoot(Village village,
                                                Building preferred,
                                                BlockPos source,
                                                Building explicitStructureRoot) {
        if (explicitStructureRoot != null && !explicitStructureRoot.isStrictScan()) {
            return explicitStructureRoot;
        }

        if (preferred != null) {
            if (preferred.isStructureRoot()) {
                return preferred;
            }
            if (preferred.hasStructure()) {
                return BuildingStructureManager.root(village, preferred.getEffectiveStructureId()).orElse(null);
            }
        }
        return BuildingStructureManager.containingRawRoot(village, source).orElse(null);
    }

    public Building.validationResult commitBuilding(BuildingScanResult scan, String forcedType) {
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

        Building pendingGroundRoom = null;
        Building pendingGroundRoot = null;
        if (existing == null && building.isFunctionalRoom() && building.hasStructure()) {
            pendingGroundRoot = BuildingStructureManager.root(targetVillage, building.getStructureId()).orElse(null);
            if (pendingGroundRoot == null) {
                return Building.validationResult.NOT_IN_BUILDING;
            }

            if (!BuildingStructureManager.isGroundFloor(pendingGroundRoot, building.getFloorY())
                    && !hasRegisteredGroundRoom(targetVillage, pendingGroundRoot)) {
                GroundRoomScan groundScan = scanGroundRoom(pendingGroundRoot);
                if (groundScan.result() != Building.validationResult.SUCCESS) {
                    return groundScan.result();
                }
                pendingGroundRoom = groundScan.room();
            }
        }

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
            if (existing.isFunctionalRoom()) {
                BuildingStructureManager.root(targetVillage, structureId)
                        .ifPresent(existing::canonicalizeFloor);
            }
            if (existing.isStructureContainer()) {
                existing.makeStructureContainer();
            } else if (forcedType != null) {
                existing.setTypeForced(true);
                existing.setType(forcedType);
            } else {
                existing.setTypeForced(false);
                existing.setType(building.getType());
            }

            if (!scan.mergedBuildingIds().isEmpty()) {
                targetVillage.removeBuildings(scan.mergedBuildingIds().stream()
                        .filter(id -> id != existing.getId())
                        .toList());
            }
        } else {
            if (forcedType != null) {
                building.setTypeForced(true);
                building.setType(forcedType);
            } else {
                // validateBuilding already resolved a valid unambiguous type.
                building.setTypeForced(false);
            }

            // Strict scans are functional rooms, never structure roots. New standalone
            // structures must be created through commitInitialStructure so they always
            // receive a non-strict container root plus a strict Ground Floor room.
            if (!building.hasStructure() && building.isStrictScan()) {
                return Building.validationResult.NOT_IN_BUILDING;
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

            if (pendingGroundRoom != null) {
                registerGroundRoom(targetVillage, pendingGroundRoot, pendingGroundRoom);
            }
        }

        finalizeVillageMutation(targetVillage);
        return Building.validationResult.SUCCESS;
    }

    public Building.validationResult commitRegisteredRoomUpdate(RoomUpdatePlan update, String forcedType) {
        if (update == null) {
            return Building.validationResult.TOO_SMALL;
        }
        return switch (update.kind()) {
            case CONFLICT, FAILURE -> update.result();
            case UPDATE -> commitBuilding(update.requested(), forcedType);
            case SPLIT -> commitRoomSplit(update, forcedType);
        };
    }

    private Building.validationResult commitRoomSplit(RoomUpdatePlan update, String forcedType) {
        BuildingScanResult addedScan = update.added();
        BuildingScanResult retainedScan = update.retained();
        Village village = addedScan == null ? null : addedScan.village();
        if (village == null || retainedScan == null || retainedScan.village() != village) {
            return Building.validationResult.NOT_IN_BUILDING;
        }

        Building existing = village.getBuilding(update.expectedRoomId()).orElse(null);
        if (existing == null || !existing.isFunctionalRoom()) {
            return Building.validationResult.TOO_SMALL;
        }

        Building retained = retainedScan.building();
        Building added = addedScan.building();
        Building.validationResult validation = BuildingStructureManager.validateRoomSplit(
                existing, retained, added, village);
        if (validation != Building.validationResult.SUCCESS) {
            return validation;
        }
        if (forcedType != null && !addedScan.matchesType(forcedType)) {
            return Building.validationResult.INVALID_TYPE;
        }
        if (forcedType == null && addedScan.isAmbiguous()) {
            return Building.validationResult.INVALID_TYPE;
        }

        int structureId = existing.getEffectiveStructureId();
        Building structureRoot = BuildingStructureManager.root(village, structureId).orElse(null);
        if (structureRoot == null) {
            return Building.validationResult.NOT_IN_BUILDING;
        }

        existing.copyScannedGeometryFrom(retained, world, true);
        existing.canonicalizeFloor(structureRoot);

        added.setId(lastBuildingId++);
        added.setStructureId(structureId);
        added.setStructureRoot(false);
        added.setTypeForced(forcedType != null);
        if (forcedType != null) {
            added.setType(forcedType);
        }
        village.getBuildings().put(added.getId(), added);

        finalizeVillageMutation(village);
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
            if (targetVillage.getStructuralPosition(world, scan.root().source()) != Village.StructuralPosition.OUTSIDE) {
                return Building.validationResult.IDENTICAL;
            }
        } else {
            targetVillage = new Village(lastVillageId++, world);
        }

        Building root = scan.root().building();
        Building room = scan.room().building();

        GroundRoomScan groundScan = resolveGroundRoom(root, room);
        if (groundScan.result() != Building.validationResult.SUCCESS) {
            return groundScan.result();
        }
        Building groundRoom = groundScan.room();

        Building finalGroundRoom = groundRoom;
        if (targetVillage.getBuildings().values().stream()
                .anyMatch(existing -> existing.isIdentical(root)
                        || existing.isIdentical(room)
                        || (finalGroundRoom != room && existing.isIdentical(finalGroundRoom)))) {
            return Building.validationResult.IDENTICAL;
        }

        root.setId(lastBuildingId++);
        root.setStructureId(root.getId());
        root.makeStructureContainer();
        targetVillage.getBuildings().put(root.getId(), root);

        if (groundRoom != room) {
            registerGroundRoom(targetVillage, root, groundRoom);
        }

        room.setId(lastBuildingId++);
        room.setStructureId(root.getId());
        room.setStructureRoot(false);
        room.setTypeForced(forcedRoomType != null);
        if (forcedRoomType != null) {
            room.setType(forcedRoomType);
        }
        targetVillage.getBuildings().put(room.getId(), room);

        villages.put(targetVillage.getId(), targetVillage);
        finalizeVillageMutation(targetVillage);
        return Building.validationResult.SUCCESS;
    }

    private GroundRoomScan resolveGroundRoom(Building root, Building room) {
        return BuildingStructureManager.isGroundFloor(root, room.getFloorY())
                ? new GroundRoomScan(Building.validationResult.SUCCESS, room)
                : scanGroundRoom(root);
    }

    private GroundRoomScan scanGroundRoom(Building root) {
        Building.validationResult lastFailure = Building.validationResult.TOO_SMALL;
        List<BlockPos> sources = getGroundRoomSources(root);

        for (BlockPos source : sources) {
            Building candidate = new Building(source, true);
            Building.validationResult result = candidate.validateBuilding(world, Set.of(), true, root);
            if (result != Building.validationResult.SUCCESS) {
                lastFailure = result;
                continue;
            }

            if (!BuildingStructureManager.isGroundFloor(root, candidate.getFloorY())
                    || !root.containsRawPos(candidate.getSourceBlock())) {
                continue;
            }
            return new GroundRoomScan(Building.validationResult.SUCCESS, candidate);
        }

        MCA.LOGGER.warn(
                "[GroundRoomInvariant] stage=ground-room-missing structure={} rootGroundFloorY={} rootBounds={}..{} candidates={} lastFailure={}",
                root.getEffectiveStructureId(), root.getGroundFloorY(), root.getRawPos0(), root.getRawPos1(),
                sources.size(), lastFailure);
        return new GroundRoomScan(lastFailure, null);
    }

    private static List<BlockPos> getGroundRoomSources(Building root) {
        int groundY = root.getGroundFloorY();
        LinkedHashSet<BlockPos> sources = new LinkedHashSet<>();

        root.getFloorRegions().stream()
                .filter(region -> BuildingStructureManager.isGroundFloor(root, region.anchorY()))
                .sorted(Comparator.comparingInt(BuildingFloorRegion::area).reversed())
                .forEach(region -> region.components().stream()
                        .sorted(Comparator.comparingInt(BuildingFloorRegion.Component::area).reversed()
                                .thenComparingInt(BuildingFloorRegion.Component::minX)
                                .thenComparingInt(BuildingFloorRegion.Component::minZ)
                                .thenComparingInt(BuildingFloorRegion.Component::maxX)
                                .thenComparingInt(BuildingFloorRegion.Component::maxZ))
                        .map(component -> getGroundRoomSource(component, groundY))
                        .forEach(sources::add));

        BlockPos center = root.getCenter();
        sources.add(new BlockPos(center.getX(), groundY, center.getZ()));
        BlockPos originalSource = root.getSourceBlock();
        sources.add(new BlockPos(originalSource.getX(), groundY, originalSource.getZ()));
        return List.copyOf(sources);
    }

    private static BlockPos getGroundRoomSource(BuildingFloorRegion.Component component, int groundY) {
        int centerX = component.minX() + (component.maxX() - component.minX()) / 2;
        int centerZ = component.minZ() + (component.maxZ() - component.minZ()) / 2;

        return component.spans().stream()
                .min(Comparator
                        .comparingInt((BuildingFloorRegion.Span span) -> Math.abs(span.z() - centerZ))
                        .thenComparingInt(span -> horizontalDistance(centerX, span))
                        .thenComparingInt(BuildingFloorRegion.Span::z)
                        .thenComparingInt(BuildingFloorRegion.Span::minX)
                        .thenComparingInt(BuildingFloorRegion.Span::maxX))
                .map(span -> new BlockPos(
                        Math.max(span.minX(), Math.min(centerX, span.maxX())),
                        groundY,
                        span.z()
                ))
                .orElseGet(() -> new BlockPos(centerX, groundY, centerZ));
    }

    private static int horizontalDistance(int x, BuildingFloorRegion.Span span) {
        if (x < span.minX()) {
            return span.minX() - x;
        }
        if (x > span.maxX()) {
            return x - span.maxX();
        }
        return 0;
    }

    private static boolean hasRegisteredGroundRoom(Village village, Building root) {
        return BuildingStructureManager.members(village, root.getEffectiveStructureId()).stream()
                .filter(Building::isFunctionalRoom)
                .anyMatch(room -> BuildingStructureManager.isGroundFloor(root, room.getFloorY()));
    }

    private void registerGroundRoom(Village village, Building root, Building groundRoom) {
        groundRoom.setId(lastBuildingId++);
        groundRoom.setStructureId(root.getEffectiveStructureId());
        groundRoom.setStructureRoot(false);
        groundRoom.setTypeForced(false);
        village.getBuildings().put(groundRoom.getId(), groundRoom);
    }

    private record GroundRoomScan(Building.validationResult result, Building room) {
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

    public void ensureStructureHierarchy(Village village) {
        if (BuildingStructureManager.ensureHierarchy(village)) {
            village.calculateDimensions();
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

    public BuildingEditResult forceRoomType(BlockPos pos, String type) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        ensureStructureHierarchy(village);
        Building room = village.getFunctionalRoomAt(world, pos).orElse(null);
        if (room == null) {
            return BuildingEditResult.NO_BUILDING;
        }

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
        if (village == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        ensureStructureHierarchy(village);
        Building room = village.getFunctionalRoomAt(world, pos).orElse(null);
        if (room == null) {
            return village.hasStructuralBuildingAt(world, pos)
                    ? BuildingEditResult.NO_ROOM
                    : BuildingEditResult.NO_BUILDING;
        }
        if (village.isStructuralGroundFloor(room)) {
            return BuildingEditResult.GROUND_FLOOR;
        }

        village.removeBuilding(room.getId());
        ensureStructureHierarchy(village);
        if (village.getBuildings().isEmpty()) {
            removeVillage(village.getId());
            setDirty();
        }
        return BuildingEditResult.SUCCESS;
    }

    public BuildingEditResult removeBuilding(BlockPos pos) {
        Village village = findNearestVillage(pos, Village.PLAYER_BORDER_MARGIN).orElse(null);
        if (village == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        ensureStructureHierarchy(village);
        Building building = village.getBuildingTarget(pos).orElse(null);
        if (building == null) {
            return BuildingEditResult.NO_BUILDING;
        }
        if (building.getBuildingType().grouped()) {
            village.removeBuilding(building.getId());
        } else {
            removeStructure(village, building.getEffectiveStructureId());
        }
        return BuildingEditResult.SUCCESS;
    }

    public enum BuildingEditResult {
        SUCCESS,
        NO_BUILDING,
        NO_ROOM,
        GROUND_FLOOR
    }

    public record RoomUpdatePlan(
            Kind kind,
            Building.validationResult result,
            BuildingScanResult requested,
            BuildingScanResult retained,
            BuildingScanResult added,
            int expectedRoomId
    ) {
        public enum Kind {
            UPDATE,
            SPLIT,
            CONFLICT,
            FAILURE
        }

        static RoomUpdatePlan update(BuildingScanResult requested, int expectedRoomId) {
            return new RoomUpdatePlan(
                    Kind.UPDATE, Building.validationResult.SUCCESS,
                    requested, null, null, expectedRoomId);
        }

        static RoomUpdatePlan split(BuildingScanResult requested,
                                    BuildingScanResult retained,
                                    BuildingScanResult added,
                                    int expectedRoomId) {
            return new RoomUpdatePlan(
                    Kind.SPLIT, Building.validationResult.SUCCESS,
                    requested, retained, added, expectedRoomId);
        }

        static RoomUpdatePlan conflict(Building.validationResult result,
                                       BuildingScanResult requested,
                                       BuildingScanResult retained,
                                       int expectedRoomId) {
            return new RoomUpdatePlan(
                    Kind.CONFLICT, result, requested, retained, null, expectedRoomId);
        }

        static RoomUpdatePlan failure(Building.validationResult result,
                                      BuildingScanResult requested,
                                      int expectedRoomId) {
            return new RoomUpdatePlan(
                    Kind.FAILURE, result, requested, null, null, expectedRoomId);
        }

        public BuildingScanResult typeSelectionScan() {
            return kind == Kind.SPLIT ? added : requested;
        }

        public boolean isAmbiguous() {
            BuildingScanResult scan = typeSelectionScan();
            return scan != null && scan.isAmbiguous();
        }
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
            BuildingScanResult scan = analyzeBuilding(ScanRequest.rescan(probe, village, existing));
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
        BuildingScanResult scan = analyzeStructure(pos, strictScan);
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
