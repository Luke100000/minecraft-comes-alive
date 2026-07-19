package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Building {
    public static final long SCAN_COOLDOWN = 4800;
    public static final int PLAYER_POSITION_HORIZONTAL_MARGIN = 1;
    public static final int PLAYER_POSITION_VERTICAL_MARGIN = 2;
    /** Tolerance used for semantic floor identity, not broad room matching. */
    public static final int SEMANTIC_FLOOR_TOLERANCE = 1;
    private static final int FLOOR_MATCH_TOLERANCE = BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
    private static final Direction[] directions = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private final Map<ResourceLocation, List<BlockPos>> blocks = new HashMap<>();
    private List<BuildingFloorRegion> floorRegions = List.of();

    private String type = "building";
    private boolean isTypeForced = false;

    private int size;
    private int pos0X, pos0Y, pos0Z;
    private int pos1X, pos1Y, pos1Z;
    private int posX, posY, posZ;
    private int floorY;
    private int groundFloorY;
    private boolean hasGroundFloorAnchor;
    private int structureId = -1;
    private boolean structureRoot;
    private int id;
    private boolean strictScan;
    private long lastScan;

    public Building() {
    }

    public Building(BlockPos pos) {
        this(pos, false);
    }

    public Building(BlockPos pos, boolean strictScan) {
        this();

        pos0X = pos.getX();
        pos0Y = pos.getY();
        pos0Z = pos.getZ();

        pos1X = pos0X;
        pos1Y = pos0Y;
        pos1Z = pos0Z;

        posX = pos0X;
        posY = pos0Y;
        posZ = pos0Z;
        floorY = pos0Y;
        groundFloorY = floorY;

        this.strictScan = strictScan;
    }

    public Building(CompoundTag v) {
        id = v.getInt("id");
        size = v.getInt("size");
        pos0X = v.getInt("pos0X");
        pos0Y = v.getInt("pos0Y");
        pos0Z = v.getInt("pos0Z");
        pos1X = v.getInt("pos1X");
        pos1Y = v.getInt("pos1Y");
        pos1Z = v.getInt("pos1Z");
        if (v.contains("posX")) {
            posX = v.getInt("posX");
            posY = v.getInt("posY");
            posZ = v.getInt("posZ");
        } else {
            BlockPos center = getCenter();
            posX = center.getX();
            posY = center.getY();
            posZ = center.getZ();
        }

        floorY = v.contains("floorY") ? v.getInt("floorY") : pos0Y + 1;
        groundFloorY = v.contains("groundFloorY") ? v.getInt("groundFloorY") : floorY;
        hasGroundFloorAnchor = v.contains("groundFloorY");
        if (v.contains("floorRegions", Tag.TAG_LIST)) {
            floorRegions = List.copyOf(NbtHelper.toList(
                    v.getList("floorRegions", Tag.TAG_COMPOUND),
                    tag -> BuildingFloorRegion.load((CompoundTag) tag)
            ));
        }
        structureId = v.contains("structureId") ? v.getInt("structureId") : -1;
        structureRoot = v.contains("structureRoot") && v.getBoolean("structureRoot");

        isTypeForced = v.getBoolean("isTypeForced");
        type = v.getString("type");

        strictScan = v.getBoolean("strictScan");

        blocks.putAll(NbtHelper.toMap(v.getCompound("blocks2"),
                ResourceLocation::parse,
                l -> NbtHelper.toStream(l, Building::loadBlockPos)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new))));
    }

    public CompoundTag save() {
        CompoundTag v = new CompoundTag();
        v.putInt("id", id);
        v.putInt("size", size);
        v.putInt("pos0X", pos0X);
        v.putInt("pos0Y", pos0Y);
        v.putInt("pos0Z", pos0Z);
        v.putInt("pos1X", pos1X);
        v.putInt("pos1Y", pos1Y);
        v.putInt("pos1Z", pos1Z);
        v.putInt("posX", posX);
        v.putInt("posY", posY);
        v.putInt("posZ", posZ);
        v.putInt("floorY", floorY);
        if (hasGroundFloorAnchor) {
            v.putInt("groundFloorY", groundFloorY);
        }
        v.put("floorRegions", NbtHelper.fromList(floorRegions, BuildingFloorRegion::save));
        v.putInt("structureId", structureId);
        v.putBoolean("structureRoot", structureRoot);
        v.putBoolean("isTypeForced", isTypeForced);
        v.putString("type", type);
        v.putBoolean("strictScan", strictScan);

        CompoundTag b = new CompoundTag();
        NbtHelper.fromMap(b, blocks, ResourceLocation::toString,
                positions -> NbtHelper.fromList(positions, NbtHelper::encodeBlockPos));
        v.put("blocks2", b);

        return v;
    }

    private static BlockPos loadBlockPos(Tag tag) {
        if (tag instanceof CompoundTag legacy && legacy.contains("x")) {
            return new BlockPos(legacy.getInt("x"), legacy.getInt("y"), legacy.getInt("z"));
        }
        return NbtHelper.decodeBlockPos(tag);
    }

    public BlockPos getRawPos0() {
        return new BlockPos(pos0X, pos0Y, pos0Z);
    }

    public BlockPos getRawPos1() {
        return new BlockPos(pos1X, pos1Y, pos1Z);
    }

    public BlockPos getPos0() {
        int margin = getBuildingType().getMargin();
        return getRawPos0().subtract(new Vec3i(margin, margin, margin));
    }

    public BlockPos getPos1() {
        int margin = getBuildingType().getMargin();
        return getRawPos1().offset(new Vec3i(margin, margin, margin));
    }

    public BlockPos getCenter() {
        return new BlockPos(
                (pos0X + pos1X) / 2,
                (pos0Y + pos1Y) / 2,
                (pos0Z + pos1Z) / 2
        );
    }

    public BlockPos getSourceBlock() {
        return new BlockPos(posX, posY, posZ);
    }

    public int getFloorY() {
        return floorY;
    }

    /**
     * The persistent semantic ground floor for a structure root. New roots derive this
     * from their entrance during the whole-building scan; existing roots retain it when
     * rescanned so room changes cannot relabel the structure's floors.
     */
    public int getGroundFloorY() {
        return groundFloorY;
    }

    void setGroundFloorY(int groundFloorY) {
        this.groundFloorY = groundFloorY;
        this.hasGroundFloorAnchor = true;
    }

    public List<BuildingFloorRegion> getFloorRegions() {
        return floorRegions;
    }

    /**
     * Canonicalizes a persisted or sampled floor Y against this structure's detected
     * floor regions without mutating either the structure or a room record.
     */
    public int getCanonicalFloorY(int floorY) {
        return resolveFloorBand(floorY)
                .map(FloorBand::anchorY)
                .orElse(floorY);
    }

    /**
     * Maps a known physical floor Y to the canonical semantic floor owned by this
     * structure. Player-position resolution is deliberately handled elsewhere: this
     * method never searches upward/downward and never decides which floor a player is on.
     */
    Optional<FloorBand> resolveFloorBand(int physicalY) {
        int bestAnchor = 0;
        int bestDistance = Integer.MAX_VALUE;
        boolean found = false;
        for (BuildingFloorRegion region : floorRegions) {
            int anchorY = region.anchorY();
            int distance = Math.abs(anchorY - physicalY);
            if (distance < bestDistance
                    || (distance == bestDistance && (!found || anchorY < bestAnchor))) {
                bestAnchor = anchorY;
                bestDistance = distance;
                found = true;
            }
        }
        if (!found || bestDistance > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
            return Optional.empty();
        }

        int nextAnchor = Integer.MAX_VALUE;
        for (BuildingFloorRegion region : floorRegions) {
            int anchorY = region.anchorY();
            if (anchorY > bestAnchor) {
                nextAnchor = Math.min(nextAnchor, anchorY);
            }
        }
        int ceilingY = nextAnchor == Integer.MAX_VALUE
                ? Math.max(bestAnchor + 1, pos1Y + 1)
                : nextAnchor;
        return Optional.of(new FloorBand(bestAnchor, ceilingY));
    }

    record FloorBand(int anchorY, int ceilingY) {
    }

    void canonicalizeFloor(Building root) {
        if (root == null || !strictScan || structureRoot) {
            return;
        }

        FloorBand band = root.resolveFloorBand(floorY).orElse(null);
        if (band == null) {
            return;
        }

        int canonicalFloorY = band.anchorY();
        boolean regionsAlreadyCanonical = floorRegions.stream()
                .allMatch(region -> region.anchorY() == canonicalFloorY);
        if (floorY == canonicalFloorY && regionsAlreadyCanonical) {
            return;
        }

        floorY = canonicalFloorY;
        groundFloorY = canonicalFloorY;
        hasGroundFloorAnchor = true;
        floorRegions = floorRegions.stream()
                .map(region -> region.withAnchorY(canonicalFloorY))
                .toList();
    }

    void retainFloorClosestTo(int y) {
        if (floorRegions.size() <= 1) {
            return;
        }

        BuildingFloorRegion closest = floorRegions.stream()
                .min(Comparator.comparingInt(region -> Math.abs(region.anchorY() - y)))
                .orElseThrow();
        floorRegions = List.of(closest);
        floorY = closest.anchorY();
    }

    public int getFloorDistanceTo(Vec3i pos) {
        return floorRegions.stream()
                .mapToInt(region -> Math.abs(region.anchorY() - pos.getY()))
                .min()
                .orElseGet(() -> Math.abs(floorY - pos.getY()));
    }

    public boolean containsFloorPosition(Vec3i pos) {
        if (floorRegions.isEmpty()) {
            return containsPos(pos);
        }
        return floorRegions.stream().anyMatch(region ->
                Math.abs(region.anchorY() - pos.getY()) <= FLOOR_MATCH_TOLERANCE
                        && region.containsHorizontally(pos.getX(), pos.getZ()));
    }

    boolean containsFloorColumn(int x, int z) {
        if (floorRegions.isEmpty()) {
            return x >= pos0X && x <= pos1X && z >= pos0Z && z <= pos1Z;
        }
        return floorRegions.stream().anyMatch(region -> region.containsHorizontally(x, z));
    }

    long getHorizontalFootprintIntersectionArea(Building other) {
        if (other == null) {
            return 0L;
        }
        if (floorRegions.isEmpty() || other.floorRegions.isEmpty()) {
            return getHorizontalBoundsIntersectionArea(other);
        }

        long intersection = 0L;
        for (BuildingFloorRegion region : floorRegions) {
            for (BuildingFloorRegion otherRegion : other.floorRegions) {
                intersection += region.intersectionArea(otherRegion);
            }
        }
        return intersection;
    }

    private long getHorizontalBoundsIntersectionArea(Building other) {
        int x = Math.min(pos1X, other.pos1X) - Math.max(pos0X, other.pos0X) + 1;
        int z = Math.min(pos1Z, other.pos1Z) - Math.max(pos0Z, other.pos0Z) + 1;
        return x <= 0 || z <= 0 ? 0L : (long) x * z;
    }

    public boolean sharesFloorBandWith(Building other) {
        if (floorRegions.isEmpty() || other.floorRegions.isEmpty()) {
            return Math.abs(floorY - other.floorY) <= FLOOR_MATCH_TOLERANCE;
        }
        return floorRegions.stream().anyMatch(region -> other.floorRegions.stream().anyMatch(otherRegion ->
                Math.abs(region.anchorY() - otherRegion.anchorY()) <= FLOOR_MATCH_TOLERANCE));
    }

    public int getStructureId() {
        return structureId;
    }

    public int getEffectiveStructureId() {
        return structureId >= 0 ? structureId : id;
    }

    public boolean hasStructure() {
        return structureId >= 0;
    }

    public void setStructureId(int structureId) {
        this.structureId = structureId;
    }

    public boolean isStructureRoot() {
        return structureRoot;
    }

    public boolean isStructureContainer() {
        return structureRoot && !strictScan;
    }

    public boolean isFunctionalRoom() {
        // Canonical structures always separate the hidden non-strict root container
        // from strict functional room records. A root is never itself a room.
        return strictScan && !structureRoot && !getBuildingType().grouped();
    }

    public void setStructureRoot(boolean structureRoot) {
        this.structureRoot = structureRoot;
    }

    void makeStructureContainer() {
        strictScan = false;
        structureRoot = true;
        isTypeForced = false;
        type = "building";
        blocks.clear();
    }

    public void validateBlocks(Level world) {
        setLastScan(world.getGameTime());

        //remove all invalid blocks
        for (Map.Entry<ResourceLocation, List<BlockPos>> positions : blocks.entrySet()) {
            List<BlockPos> mask = positions.getValue().stream()
                    .filter(p -> !BuiltInRegistries.BLOCK.getKey(world.getBlockState(p).getBlock()).equals(positions.getKey()))
                    .toList();
            positions.getValue().removeAll(mask);
        }
    }

    public Stream<BlockPos> getBlockPosStream() {
        return blocks.values().stream().flatMap(Collection::stream);
    }

    public void addPOI(Level world, BlockPos pos) {
        Block block = world.getBlockState(pos).getBlock();
        removeBlock(block, pos);
        addBlock(block, pos);

        //validate grouped buildings
        validateBlocks(world);

        //mean center
        int n = (int) getBlockPosStream().count();
        if (n > 0) {
            BlockPos center = getBlockPosStream().reduce(BlockPos.ZERO, BlockPos::offset);
            pos0X = center.getX() / n;
            pos0Y = center.getY() / n;
            pos0Z = center.getZ() / n;
            pos1X = pos0X;
            pos1Y = pos0Y;
            pos1Z = pos0Z;
        }
    }

    public validationResult validateBuilding(Level world, Set<BlockPos> blocked) {
        return validateBuilding(world, blocked, false);
    }

    public validationResult validateBuilding(Level world, Set<BlockPos> blocked, boolean allowMissingEntrance) {
        return validateBuilding(world, blocked, allowMissingEntrance, null);
    }

    validationResult validateBuilding(Level world,
                                      Set<BlockPos> blocked,
                                      boolean allowMissingEntrance,
                                      Building structureRoot) {
        //validate grouped buildings differently
        if (getBuildingType().grouped()) {
            validateBlocks(world);
            return getBlockPosStream().findAny().isEmpty() ? validationResult.TOO_SMALL : validationResult.SUCCESS;
        }

        if (strictScan) {
            return validateStrictRoom(world, blocked, allowMissingEntrance, structureRoot);
        }

        //clear old building
        blocks.clear();
        size = 0;

        setLastScan(world.getGameTime());

        //temp data for flood fill
        Set<BlockPos> done = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();
        Map<Long, Integer> lowestInteriorY = new HashMap<>();
        Set<BuildingFloorRegionDetector.SupportedCell> supportedCells = new HashSet<>();
        Set<BlockPos> reachableInteriorCells = new HashSet<>();
        Set<BlockPos> doorBlocks = new HashSet<>();
        Set<BlockPos> trapDoorBlocks = new HashSet<>();

        //start point
        BlockPos center = getSourceBlock();
        queue.add(center);
        done.add(center);
        BlockState centerState = world.getBlockState(center);
        if (centerState.isAir() || !centerState.getFluidState().isEmpty()) {
            reachableInteriorCells.add(center);
            recordInteriorColumn(lowestInteriorY, center);
            recordSupportedInteriorCell(world, supportedCells, center);
        }

        //const
        final int minSize = Config.getInstance().minBuildingSize;
        final int maxSize = Config.getInstance().maxBuildingSize;
        final int maxRadius = Config.getInstance().maxBuildingRadius;

        //fill the building
        int scanSize = 0;
        int interiorSize = 0;
        boolean hasDoor = false;
        Map<BlockPos, Boolean> roofCache = new HashMap<>();
        while (!queue.isEmpty() && scanSize < maxSize) {
            BlockPos p = queue.removeLast();

            //this block is marked as blocked, indicating an overlap
            if (blocked.contains(p) && scanSize > 0) {
                return validationResult.OVERLAP;
            }

            //as long the max radius is not reached
            if (p.distManhattan(center) < maxRadius) {
                for (Direction d : directions) {
                    BlockPos n = p.relative(d);

                    //and the block is not already checked
                    if (!done.contains(n)) {
                        BlockState state = world.getBlockState(n);

                        //mark it
                        done.add(n);

                        //if not solid, continue
                        if (state.isAir()) {
                            if (!roofCache.containsKey(n)) {
                                BlockPos n2 = n;
                                int maxScanHeight = 16;
                                for (int i = 0; i < maxScanHeight; i++) {
                                    roofCache.put(n2, false);
                                    n2 = n2.above();

                                    //found valid block
                                    BlockState block = world.getBlockState(n2);
                                    if (!block.isAir() || roofCache.containsKey(n2)) {
                                        if (!(roofCache.containsKey(n2) && !roofCache.get(n2)) && !block.is(BlockTags.LEAVES)) {
                                            for (int i2 = i; i2 >= 0; i2--) {
                                                n2 = n2.below();
                                                roofCache.put(n2, true);
                                            }
                                        }
                                        break;
                                    }
                                }
                            }
                            if (roofCache.get(n)) {
                                interiorSize++;
                                queue.add(n);
                                reachableInteriorCells.add(n);
                                recordInteriorColumn(lowestInteriorY, n);
                                recordSupportedInteriorCell(world, supportedCells, n);
                            }
                        } else if (!state.getFluidState().isEmpty()) {
                            //fluid blocks (water, lava, etc.) are treated as passable interior
                            interiorSize++;
                            queue.add(n);
                            reachableInteriorCells.add(n);
                            recordInteriorColumn(lowestInteriorY, n);
                            recordSupportedInteriorCell(world, supportedCells, n);
                        } else if (state.getBlock() instanceof DoorBlock) {
                            if (!strictScan) {
                                queue.add(n);
                                doorBlocks.add(state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                                        ? n.below()
                                        : n);
                            }
                            hasDoor = true;
                        } else if (state.getBlock() instanceof TrapDoorBlock) {
                            if (!strictScan) {
                                queue.add(n);
                                trapDoorBlocks.add(n);
                            }
                            hasDoor = true;
                        } else if (state.getBlock() instanceof LadderBlock) {
                            // Ladders connect whole-building scans but keep strict room scans separate.
                            if (!strictScan) {
                                queue.add(n);
                            }
                        }
                    }
                }
            } else {
                return validationResult.SIZE_LIMIT;
            }

            scanSize++;
        }

        // min size is 32 by default, which equals an 8 block big cube with 6 times 4 sides
        if (!queue.isEmpty()) {
            return validationResult.BLOCK_LIMIT;
        } else if (done.size() <= minSize) {
            return validationResult.TOO_SMALL;
        } else if (!hasDoor && !allowMissingEntrance) {
            return validationResult.NO_DOOR;
        } else {
            //dimensions
            int sx = center.getX();
            int sy = center.getY();
            int sz = center.getZ();
            int ex = sx;
            int ey = sy;
            int ez = sz;

            for (BlockPos p : done) {
                sx = Math.min(sx, p.getX());
                sy = Math.min(sy, p.getY());
                sz = Math.min(sz, p.getZ());
                ex = Math.max(ex, p.getX());
                ey = Math.max(ey, p.getY());
                ez = Math.max(ez, p.getZ());

                recordBuildingBlock(world, p);
            }

            //adjust building dimensions
            pos0X = sx;
            pos0Y = sy;
            pos0Z = sz;

            pos1X = ex;
            pos1Y = ey;
            pos1Z = ez;

            size = interiorSize;
            int legacyFloorY = determineDominantFloorY(lowestInteriorY, pos0Y + 1);
            floorRegions = BuildingFloorRegionDetector.detect(supportedCells);
            floorY = floorRegions.stream()
                    .max(Comparator.comparingInt(BuildingFloorRegion::area)
                            .thenComparing(Comparator.comparingInt(BuildingFloorRegion::anchorY).reversed()))
                    .map(BuildingFloorRegion::anchorY)
                    .orElse(legacyFloorY);

            EntranceCells normalDoors = classifyNormalDoorEntrances(world, doorBlocks, reachableInteriorCells);
            EntranceCells trapDoors = classifyTrapDoorEntrances(world, trapDoorBlocks, reachableInteriorCells);
            Collection<BlockPos> entranceInteriorCells;
            Set<ExteriorEntrance> exteriorEntrances;
            String entranceSource;
            if (!normalDoors.exterior().isEmpty()) {
                entranceInteriorCells = normalDoors.exteriorInteriorCells();
                exteriorEntrances = normalDoors.exterior();
                entranceSource = "exterior-door";
            } else if (!normalDoors.all().isEmpty()) {
                // Normal doors are stronger evidence of a building's intended entrance than trapdoors.
                // If none of them can be proven exterior, fall back to the dominant floor instead of
                // allowing decorative or secondary trapdoors to promote an upper floor to ground level.
                entranceInteriorCells = List.of();
                exteriorEntrances = Set.of();
                entranceSource = "floor-fallback-internal-doors";
            } else if (!trapDoors.all().isEmpty()) {
                // Trapdoors are vertical connectors, not reliable ground-floor evidence. Vanilla only
                // stores their current OPEN/HALF/FACING state, so an open real hatch is indistinguishable
                // from a vertically displayed decorative trapdoor. Keep trapdoors valid for building
                // discovery, but use the dominant floor unless a normal exterior door anchors the structure.
                entranceInteriorCells = List.of();
                exteriorEntrances = Set.of();
                entranceSource = "floor-fallback-trapdoors-only";
            } else {
                entranceInteriorCells = List.of();
                exteriorEntrances = Set.of();
                entranceSource = "floor-fallback";
            }
            List<TerrainEntranceSample> terrainSamples = sampleTerrainEntrances(
                    world,
                    exteriorEntrances
            );
            List<Integer> perimeterTerrainYs = entranceInteriorCells.isEmpty()
                    ? sampleTerrainPerimeter(world, pos0X, pos0Z, pos1X, pos1Z)
                    : List.of();
            GroundFloorSelection groundFloorSelection = determineGroundFloorY(
                    entranceInteriorCells,
                    terrainSamples,
                    perimeterTerrainYs,
                    floorRegions,
                    floorY,
                    entranceSource
            );
            groundFloorY = groundFloorSelection.floorY();
            hasGroundFloorAnchor = true;
            MCA.LOGGER.debug(
                    "[BuildingGroundFloor] source={} normalDoors={} exteriorNormalCells={} trapdoors={} exteriorTrapdoorCells={} selection={} floorY={} groundFloorY={} terrainSamples={}",
                    center, doorBlocks.size(), normalDoors.exteriorInteriorCells().size(), trapDoorBlocks.size(),
                    trapDoors.exteriorInteriorCells().size(), groundFloorSelection.source(), floorY, groundFloorY,
                    terrainSamples);

            //determine type
            if (isTypeForced()) {
                return matchesType(getBuildingType()) ? validationResult.SUCCESS : validationResult.INVALID_TYPE;
            }
            return determineType() ? validationResult.SUCCESS : validationResult.INVALID_TYPE;
        }
    }


    private validationResult validateStrictRoom(Level world,
                                                Set<BlockPos> blocked,
                                                boolean allowMissingEntrance,
                                                Building structureRoot) {
        prepareStrictRoomScan(world);
        BuildingRoomScanner.Result scan = BuildingRoomScanner.scan(
                world,
                getSourceBlock(),
                blocked,
                Config.getInstance().maxBuildingSize,
                Config.getInstance().maxBuildingRadius,
                structureRoot
        );
        return applyStrictRoomScanResult(world, scan, allowMissingEntrance);
    }

    /**
     * Applies already-discovered strict-room geometry without scanning the world again.
     * Split analysis uses this to materialize scanner-owned components exactly once.
     */
    validationResult applyStrictRoomScan(Level world,
                                         BuildingRoomScanner.Result scan,
                                         boolean allowMissingEntrance) {
        prepareStrictRoomScan(world);
        return applyStrictRoomScanResult(world, scan, allowMissingEntrance);
    }

    private void prepareStrictRoomScan(Level world) {
        blocks.clear();
        size = 0;
        setLastScan(world.getGameTime());
    }

    private validationResult applyStrictRoomScanResult(Level world,
                                                       BuildingRoomScanner.Result scan,
                                                       boolean allowMissingEntrance) {
        validationResult failure = switch (scan.status()) {
            case SUCCESS -> validationResult.SUCCESS;
            case OVERLAP -> validationResult.OVERLAP;
            case BLOCK_LIMIT -> validationResult.BLOCK_LIMIT;
            case SIZE_LIMIT -> validationResult.SIZE_LIMIT;
            case TOO_SMALL -> validationResult.TOO_SMALL;
        };
        if (failure != validationResult.SUCCESS) {
            return failure;
        }
        if (!scan.hasEntrance() && !allowMissingEntrance) {
            return validationResult.NO_DOOR;
        }

        for (BlockPos p : scan.poiCells()) {
            recordBuildingBlock(world, p);
        }

        BlockPos seed = scan.seed();
        posX = seed.getX();
        posY = seed.getY();
        posZ = seed.getZ();

        pos0X = scan.min().getX();
        pos0Y = scan.min().getY();
        pos0Z = scan.min().getZ();
        pos1X = scan.max().getX();
        pos1Y = scan.max().getY();
        pos1Z = scan.max().getZ();

        size = scan.footprintCells().size();
        floorY = scan.floorY();
        floorRegions = List.of(BuildingFloorRegion.fromFootprint(floorY, scan.footprintCells()));

        // Rooms retain a local anchor for save compatibility. The semantic structure
        // ground floor is owned exclusively by the non-strict structure root.
        groundFloorY = floorY;
        hasGroundFloorAnchor = true;

        if (isTypeForced()) {
            return matchesType(getBuildingType()) ? validationResult.SUCCESS : validationResult.INVALID_TYPE;
        }
        return determineType() ? validationResult.SUCCESS : validationResult.INVALID_TYPE;
    }

    private static EntranceCells classifyNormalDoorEntrances(Level world,
                                                              Collection<BlockPos> doorBlocks,
                                                              Set<BlockPos> reachableInteriorCells) {
        Set<BlockPos> all = new HashSet<>();
        Set<ExteriorEntrance> exterior = new HashSet<>();

        for (BlockPos doorPos : doorBlocks) {
            BlockState state = world.getBlockState(doorPos);
            if (!(state.getBlock() instanceof DoorBlock)) {
                continue;
            }
            Direction facing = state.getValue(DoorBlock.FACING);
            List<BlockPos> neighbours = new ArrayList<>(4);
            for (Direction side : List.of(facing, facing.getOpposite())) {
                BlockPos neighbour = doorPos.relative(side);
                neighbours.add(neighbour);
                neighbours.add(neighbour.above());
            }
            classifyEntranceSides(
                    world,
                    neighbours,
                    reachableInteriorCells,
                    all,
                    exterior
            );
        }
        return new EntranceCells(all, exterior);
    }

    private static EntranceCells classifyTrapDoorEntrances(
            Level world,
            Collection<BlockPos> trapDoorBlocks,
            Set<BlockPos> reachableInteriorCells
    ) {
        Set<BlockPos> all = new HashSet<>();
        Set<ExteriorEntrance> exterior = new HashSet<>();

        for (BlockPos trapDoorPos : trapDoorBlocks) {
            BlockState state = world.getBlockState(trapDoorPos);
            if (!(state.getBlock() instanceof TrapDoorBlock)) {
                continue;
            }

            List<BlockPos> neighbours = List.of(
                    trapDoorPos.above(),
                    trapDoorPos.below()
            );

            classifyEntranceSides(
                    world,
                    neighbours,
                    reachableInteriorCells,
                    all,
                    exterior
            );
        }

        return new EntranceCells(all, exterior);
    }

    private static void classifyEntranceSides(Level world,
                                              Collection<BlockPos> neighbours,
                                              Set<BlockPos> reachableInteriorCells,
                                              Set<BlockPos> allInteriorCells,
                                              Set<ExteriorEntrance> exteriorEntrances) {
        List<BlockPos> interiorNeighbours = neighbours.stream()
                .filter(reachableInteriorCells::contains)
                .toList();
        if (interiorNeighbours.isEmpty()) {
            return;
        }

        allInteriorCells.addAll(interiorNeighbours);
        List<BlockPos> exteriorNeighbours = neighbours.stream()
                .filter(pos -> !reachableInteriorCells.contains(pos))
                .filter(pos -> {
                    BlockState state = world.getBlockState(pos);
                    return state.isAir() || !state.getFluidState().isEmpty();
                })
                .toList();
        for (BlockPos interior : interiorNeighbours) {
            for (BlockPos exterior : exteriorNeighbours) {
                exteriorEntrances.add(new ExteriorEntrance(interior, exterior));
            }
        }
    }

    private record EntranceCells(Set<BlockPos> all, Set<ExteriorEntrance> exterior) {
        private EntranceCells {
            all = Set.copyOf(all);
            exterior = Set.copyOf(exterior);
        }

        private Set<BlockPos> exteriorInteriorCells() {
            return exterior.stream().map(ExteriorEntrance::interior).collect(Collectors.toUnmodifiableSet());
        }
    }

    private record ExteriorEntrance(BlockPos interior, BlockPos exterior) {
    }

    private static GroundFloorSelection determineGroundFloorY(Collection<BlockPos> entranceInteriorCells,
                                                               Collection<TerrainEntranceSample> terrainSamples,
                                                               Collection<Integer> perimeterTerrainYs,
                                                               List<BuildingFloorRegion> regions,
                                                               int fallbackY,
                                                               String fallbackSource) {
        OptionalInt terrainEntranceY = terrainSamples.stream()
                .filter(sample -> Math.abs(sample.terrainY() - sample.interior().getY())
                        <= SEMANTIC_FLOOR_TOLERANCE)
                .mapToInt(sample -> sample.interior().getY())
                .max();
        if (terrainEntranceY.isPresent()) {
            return new GroundFloorSelection(terrainEntranceY.getAsInt(), "terrain-" + fallbackSource);
        }
        if (!entranceInteriorCells.isEmpty() && !regions.isEmpty()) {
            int entranceFloorY = regions.stream()
                    .min(Comparator
                            .comparingInt((BuildingFloorRegion region) -> minimumEntranceDistance(
                                    entranceInteriorCells, region, false))
                            .thenComparingInt(region -> minimumEntranceDistance(entranceInteriorCells, region, true))
                            .thenComparingInt(BuildingFloorRegion::anchorY))
                    .map(BuildingFloorRegion::anchorY)
                    .orElse(fallbackY);
            return new GroundFloorSelection(entranceFloorY, fallbackSource);
        }

        if (!regions.isEmpty() && !perimeterTerrainYs.isEmpty()) {
            int terrainY = medianTerrainY(perimeterTerrainYs);
            int terrainFloorY = regions.stream()
                    .min(Comparator
                            .comparingInt((BuildingFloorRegion region) -> Math.abs(region.anchorY() - terrainY))
                            .thenComparing(Comparator.comparingInt(
                                    BuildingFloorRegion::anchorY).reversed()))
                    .map(BuildingFloorRegion::anchorY)
                    .orElse(fallbackY);
            return new GroundFloorSelection(
                    terrainFloorY, "terrain-perimeter-" + fallbackSource);
        }

        return new GroundFloorSelection(fallbackY, fallbackSource);
    }

    /**
     * Only terrain immediately outside a detected entrance can validate a ground
     * floor. Sampling the whole building perimeter lets unrelated slopes and lower
     * exposed walls redefine an otherwise unambiguous entrance floor.
     */
    private static List<TerrainEntranceSample> sampleTerrainEntrances(Level world,
                                                                      Collection<ExteriorEntrance> exteriorEntrances) {
        return exteriorEntrances.stream()
                .map(entrance -> new TerrainEntranceSample(
                        entrance.interior(),
                        entrance.exterior(),
                        world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                entrance.exterior().getX(), entrance.exterior().getZ())
                ))
                .toList();
    }

    /**
     * Samples the terrain immediately outside the scanned structure bounds. This is only
     * used when no reliable exterior normal-door entrance exists, so slopes elsewhere on
     * the perimeter cannot override explicit entrance evidence.
     */
    private static List<Integer> sampleTerrainPerimeter(Level world,
                                                        int minX,
                                                        int minZ,
                                                        int maxX,
                                                        int maxZ) {
        int outerMinX = minX - 1;
        int outerMaxX = maxX + 1;
        int outerMinZ = minZ - 1;
        int outerMaxZ = maxZ + 1;
        List<Integer> terrainYs = new ArrayList<>();

        for (int x = outerMinX; x <= outerMaxX; x++) {
            terrainYs.add(world.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, outerMinZ));
            if (outerMaxZ != outerMinZ) {
                terrainYs.add(world.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, outerMaxZ));
            }
        }
        for (int z = outerMinZ + 1; z < outerMaxZ; z++) {
            terrainYs.add(world.getHeight(
                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outerMinX, z));
            if (outerMaxX != outerMinX) {
                terrainYs.add(world.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, outerMaxX, z));
            }
        }
        return List.copyOf(terrainYs);
    }

    private static int medianTerrainY(Collection<Integer> terrainYs) {
        int[] sorted = terrainYs.stream().mapToInt(Integer::intValue).sorted().toArray();
        if (sorted.length == 0) {
            throw new IllegalArgumentException("terrainYs must not be empty");
        }
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : Math.floorDiv(sorted[middle - 1] + sorted[middle], 2);
    }

    private record GroundFloorSelection(int floorY, String source) {
    }

    private record TerrainEntranceSample(BlockPos interior, BlockPos exterior, int terrainY) {
    }

    private static int minimumEntranceDistance(Collection<BlockPos> entranceInteriorCells,
                                                BuildingFloorRegion region,
                                                boolean requireHorizontalOverlap) {
        return entranceInteriorCells.stream()
                .filter(pos -> !requireHorizontalOverlap || region.containsHorizontally(pos.getX(), pos.getZ()))
                .mapToInt(pos -> Math.abs(region.anchorY() - pos.getY()))
                .min()
                .orElse(Integer.MAX_VALUE);
    }

    private static void recordSupportedInteriorCell(Level world,
                                                    Set<BuildingFloorRegionDetector.SupportedCell> supportedCells,
                                                    BlockPos interiorPos) {
        BlockPos supportPos = interiorPos.below();
        BlockState supportState = world.getBlockState(supportPos);
        var collisionShape = supportState.getCollisionShape(world, supportPos);
        if (collisionShape.isEmpty()) {
            return;
        }

        double width = collisionShape.max(Direction.Axis.X) - collisionShape.min(Direction.Axis.X);
        double depth = collisionShape.max(Direction.Axis.Z) - collisionShape.min(Direction.Axis.Z);
        if (width * depth < 0.25D) {
            return;
        }

        supportedCells.add(new BuildingFloorRegionDetector.SupportedCell(
                interiorPos.getX(), interiorPos.getY(), interiorPos.getZ()));
    }

    private static void recordInteriorColumn(Map<Long, Integer> lowestInteriorY, BlockPos pos) {
        long key = ((long) pos.getX() << 32) ^ (pos.getZ() & 0xffffffffL);
        lowestInteriorY.merge(key, pos.getY(), Math::min);
    }

    /**
     * Picks one structural floor plane for the room. Only the lowest reachable interior
     * position in each X/Z column contributes, so tall rooms do not manufacture extra
     * floors. The most common plane wins, which makes stairs, slabs and small raised
     * platforms harmless unless they actually make up most of the room.
     */
    private static int determineDominantFloorY(Map<Long, Integer> lowestInteriorY, int fallbackY) {
        if (lowestInteriorY.isEmpty()) {
            return fallbackY;
        }

        Map<Integer, Integer> counts = new HashMap<>();
        lowestInteriorY.values().forEach(y -> counts.merge(y, 1, Integer::sum));

        int bestY = fallbackY;
        int bestCount = -1;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            int y = entry.getKey();
            int count = entry.getValue();
            if (count > bestCount
                    || (count == bestCount && Math.abs(y - fallbackY) < Math.abs(bestY - fallbackY))
                    || (count == bestCount && Math.abs(y - fallbackY) == Math.abs(bestY - fallbackY) && y > bestY)) {
                bestY = y;
                bestCount = count;
            }
        }
        return bestY;
    }

    public boolean matchesType(BuildingType bt) {
        Map<ResourceLocation, List<BlockPos>> available = bt.getGroups(blocks);
        return bt.getGroups().entrySet().stream()
                .noneMatch(e -> !available.containsKey(e.getKey()) || available.get(e.getKey()).size() < e.getValue());
    }

    public List<BuildingType> getMatchingTypes() {
        List<BuildingType> matches = new ArrayList<>();
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.grouped()) {
                continue;
            }
            if (matchesType(bt)) {
                matches.add(bt);
            }
        }
        matches.sort(Comparator
                .comparingInt(BuildingType::priority).reversed()
                .thenComparing(BuildingType::name));
        return matches;
    }

    public List<BuildingType> getVisibleMatchingTypes() {
        List<BuildingType> matches = new ArrayList<>(getMatchingTypes().stream()
                .filter(bt -> bt.visible() || bt.name().equals("house"))
                .filter(bt -> !bt.name().equals("blocked") && !bt.name().equals("building"))
                .toList());

        boolean hasBigHouse = matches.stream().anyMatch(bt -> bt.name().equals("big_house"));
        if (hasBigHouse) {
            matches.removeIf(bt -> bt.name().equals("house"));
        }
        return matches;
    }

    private boolean isBuildingBlock(BlockState state) {
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.matchesBlock(state)) {
                return true;
            }
        }
        return false;
    }

    private void recordBuildingBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (!isBuildingBlock(state)) {
            return;
        }

        Block block = state.getBlock();
        if (!(block instanceof BedBlock)
                || state.getValue(BedBlock.PART) == BedPart.HEAD) {
            addBlock(block, pos);
        }
    }

    public boolean determineType() {
        List<BuildingType> matches = getMatchingTypes();
        if (matches.isEmpty()) {
            return false;
        }
        type = matches.getFirst().name();
        return true;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isTypeForced() {
        return isTypeForced;
    }

    public void setTypeForced(boolean forced) {
        this.isTypeForced = forced;
    }

    public BuildingType getBuildingType() {
        return BuildingTypes.getInstance().getBuildingType(type);
    }

    public Map<ResourceLocation, List<BlockPos>> getBlocks() {
        return blocks;
    }

    public void addBlock(Block block, BlockPos p) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        blocks.computeIfAbsent(key, k -> new ArrayList<>());
        blocks.get(key).add(p);
    }

    public void removeBlock(Block block, BlockPos p) {
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
        if (blocks.containsKey(key)) {
            blocks.get(key).remove(p);
        }
    }

    public int getBlockCount() {
        return blocks.values().stream().mapToInt(List::size).sum();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean overlaps(Building b) {
        return pos1X > b.pos0X && pos0X < b.pos1X && pos1Y > b.pos0Y && pos0Y < b.pos1Y && pos1Z > b.pos0Z && pos0Z < b.pos1Z;
    }

    public boolean containsPos(Vec3i pos) {
        if (getBuildingType().grouped()) {
            return pos.closerThan(getCenter(), getBuildingType().getMargin());
        }
        return pos.getX() >= pos0X && pos.getX() <= pos1X
               && pos.getY() >= pos0Y && pos.getY() <= pos1Y
               && pos.getZ() >= pos0Z && pos.getZ() <= pos1Z;
    }

    boolean containsHorizontalPosition(Vec3i pos) {
        return pos.getX() >= pos0X - PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getX() <= pos1X + PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getZ() >= pos0Z - PLAYER_POSITION_HORIZONTAL_MARGIN
                && pos.getZ() <= pos1Z + PLAYER_POSITION_HORIZONTAL_MARGIN;
    }

    /**
     * Player-position lookup used by both blueprint controls and server validation.
     * The small margin covers a room immediately beside or above an existing scan
     * without allowing an unrelated building elsewhere in the village to match.
     */
    public boolean containsStructurePosition(Vec3i pos) {
        if (getBuildingType().grouped()) {
            return containsPos(pos);
        }
        return containsFloorPosition(pos)
                || containsPositionWithMargin(pos,
                PLAYER_POSITION_HORIZONTAL_MARGIN,
                PLAYER_POSITION_VERTICAL_MARGIN);
    }

    public boolean containsPositionWithMargin(Vec3i pos, int horizontalMargin, int verticalMargin) {
        return pos.getX() >= pos0X - horizontalMargin && pos.getX() <= pos1X + horizontalMargin
                && pos.getY() >= pos0Y - verticalMargin && pos.getY() <= pos1Y + verticalMargin
                && pos.getZ() >= pos0Z - horizontalMargin && pos.getZ() <= pos1Z + horizontalMargin;
    }

    public boolean isIdentical(Building b) {
        boolean sameBounds = pos0X == b.pos0X && pos1X == b.pos1X
                && pos0Y == b.pos0Y && pos1Y == b.pos1Y
                && pos0Z == b.pos0Z && pos1Z == b.pos1Z;
        if (!sameBounds || strictScan != b.strictScan) {
            return false;
        }
        if (!strictScan) {
            return true;
        }

        long footprintArea = getFloorFootprintArea();
        return sharesFloorBandWith(b)
                && footprintArea == b.getFloorFootprintArea()
                && getFloorFootprintIntersectionArea(b) == footprintArea;
    }

    public int getSize() {
        return size;
    }

    public int getHorizontalArea() {
        return Math.max(1, pos1X - pos0X + 1) * Math.max(1, pos1Z - pos0Z + 1);
    }

    public long getFloorFootprintArea() {
        long area = floorRegions.stream()
                .filter(region -> Math.abs(region.anchorY() - floorY) <= SEMANTIC_FLOOR_TOLERANCE)
                .mapToLong(BuildingFloorRegion::area)
                .sum();
        return area > 0L ? area : getHorizontalArea();
    }

    public long getFloorFootprintIntersectionArea(Building other) {
        if (other == null) {
            return 0L;
        }
        if (floorRegions.isEmpty() || other.floorRegions.isEmpty()) {
            int x = Math.min(pos1X, other.pos1X) - Math.max(pos0X, other.pos0X) + 1;
            int z = Math.min(pos1Z, other.pos1Z) - Math.max(pos0Z, other.pos0Z) + 1;
            return x <= 0 || z <= 0 ? 0L : (long) x * z;
        }

        long intersection = 0L;
        for (BuildingFloorRegion region : floorRegions) {
            for (BuildingFloorRegion otherRegion : other.floorRegions) {
                if (Math.abs(region.anchorY() - otherRegion.anchorY()) <= SEMANTIC_FLOOR_TOLERANCE) {
                    intersection += region.intersectionArea(otherRegion);
                }
            }
        }
        return intersection;
    }

    public long getRawVolume() {
        return (long) Math.max(1, pos1X - pos0X + 1)
                * Math.max(1, pos1Y - pos0Y + 1)
                * Math.max(1, pos1Z - pos0Z + 1);
    }

    public long getIntersectionVolume(Building other) {
        int x = Math.min(pos1X, other.pos1X) - Math.max(pos0X, other.pos0X) + 1;
        int y = Math.min(pos1Y, other.pos1Y) - Math.max(pos0Y, other.pos0Y) + 1;
        int z = Math.min(pos1Z, other.pos1Z) - Math.max(pos0Z, other.pos0Z) + 1;
        if (x <= 0 || y <= 0 || z <= 0) {
            return 0L;
        }
        return (long) x * y * z;
    }

    public boolean containsRawPos(Vec3i pos) {
        return pos.getX() >= pos0X && pos.getX() <= pos1X
                && pos.getY() >= pos0Y && pos.getY() <= pos1Y
                && pos.getZ() >= pos0Z && pos.getZ() <= pos1Z;
    }

    public boolean containsRawBounds(Building other) {
        return other.pos0X >= pos0X && other.pos1X <= pos1X
                && other.pos0Y >= pos0Y && other.pos1Y <= pos1Y
                && other.pos0Z >= pos0Z && other.pos1Z <= pos1Z;
    }

    private static int axisGap(int minA, int maxA, int minB, int maxB) {
        if (maxA < minB) {
            return minB - maxA - 1;
        }
        if (maxB < minA) {
            return minA - maxB - 1;
        }
        return 0;
    }

    /**
     * Face/stack attachment, deliberately excluding diagonal corner contact.
     */
    public boolean isStructurallyAttachedTo(Building other, int maxVerticalGap) {
        int gapX = axisGap(pos0X, pos1X, other.pos0X, other.pos1X);
        int gapY = axisGap(pos0Y, pos1Y, other.pos0Y, other.pos1Y);
        int gapZ = axisGap(pos0Z, pos1Z, other.pos0Z, other.pos1Z);

        boolean verticalStack = gapX == 0 && gapZ == 0 && gapY <= maxVerticalGap;
        boolean sideBySideX = gapY == 0 && gapZ == 0 && gapX <= 1;
        boolean sideBySideZ = gapY == 0 && gapX == 0 && gapZ <= 1;
        return verticalStack || sideBySideX || sideBySideZ;
    }

    public int getVerticalDistanceTo(Vec3i pos) {
        if (pos.getY() < pos0Y) {
            return pos0Y - pos.getY();
        }
        if (pos.getY() > pos1Y) {
            return pos.getY() - pos1Y;
        }
        return 0;
    }

    /**
     * Updates mutable scan geometry while preserving persistent room/structure identity.
     * A registered room keeps its semantic floor assignment while the newly scanned
     * region components, POIs, and type are refreshed. The old source anchor is retained
     * only while it remains passable and inside the new room; otherwise the successful
     * scan source becomes the new persistent anchor.
     */
    public void copyScannedGeometryFrom(Building scanned, Level world, boolean preserveFloorClassification) {
        BlockPos oldSource = getSourceBlock();
        int previousFloorY = floorY;
        int previousGroundFloorY = groundFloorY;

        size = scanned.size;
        pos0X = scanned.pos0X;
        pos0Y = scanned.pos0Y;
        pos0Z = scanned.pos0Z;
        pos1X = scanned.pos1X;
        pos1Y = scanned.pos1Y;
        pos1Z = scanned.pos1Z;
        floorY = preserveFloorClassification ? previousFloorY : scanned.floorY;
        floorRegions = preserveFloorClassification
                ? scanned.floorRegions.stream().map(region -> region.withAnchorY(previousFloorY)).toList()
                : scanned.floorRegions;
        groundFloorY = structureRoot && hasGroundFloorAnchor ? previousGroundFloorY : scanned.groundFloorY;
        hasGroundFloorAnchor = true;
        lastScan = scanned.lastScan;

        blocks.clear();
        scanned.blocks.forEach((key, value) -> blocks.put(key, new ArrayList<>(value)));

        BlockState oldSourceState = world.getBlockState(oldSource);
        boolean oldSourceStillPassable = oldSourceState.isAir() || !oldSourceState.getFluidState().isEmpty();
        if (!containsRawPos(oldSource) || !oldSourceStillPassable) {
            posX = scanned.posX;
            posY = scanned.posY;
            posZ = scanned.posZ;
        }
    }

    public long getLastScan() {
        return lastScan;
    }

    public void setLastScan(long lastScan) {
        this.lastScan = lastScan;
    }

    public boolean isStrictScan() {
        return strictScan;
    }

    /**
     * @return true if the group is large enough to be considered complete (e.g., Graveyard appears on map)
     */
    public boolean isComplete() {
        BuildingType bt = getBuildingType();
        int minBlocks = bt.getMinBlocks();
        return minBlocks == 0 || getBlockCount() >= minBlocks;
    }

    public enum validationResult {
        OVERLAP,
        BLOCK_LIMIT,
        SIZE_LIMIT,
        NO_DOOR,
        TOO_SMALL,
        IDENTICAL,
        SUCCESS,
        INVALID_TYPE,
        NOT_IN_BUILDING,
        AMBIGUOUS_STRUCTURE
    }
}
