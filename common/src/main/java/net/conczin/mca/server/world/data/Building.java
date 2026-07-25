package net.conczin.mca.server.world.data;

import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A registered functional Room.
 *
 * <p>Physical building ownership lives in {@link Structure}; this class intentionally has
 * no Main Room mode, Ground Floor state, or hierarchy-repair state.</p>
 */
public class Building implements VillageBuilding {
    public static final long SCAN_COOLDOWN = 4800;
    public static final int PLAYER_POSITION_HORIZONTAL_MARGIN = 1;
    public static final int PLAYER_POSITION_VERTICAL_MARGIN = 2;

    protected final Map<ResourceLocation, List<BlockPos>> blocks = new HashMap<>();
    private List<BuildingFloorRegion> floorRegions = List.of();
    private String type = "house";
    private boolean typeForced;
    /** Whether this Room contributes to and visually inherits from its Main Room. */
    private boolean inheritanceEnabled = true;
    private int size;
    private int pos0X, pos0Y, pos0Z;
    private int pos1X, pos1Y, pos1Z;
    private int posX, posY, posZ;
    private int structureId = -1;
    private int floorId = -1;
    private int id = -1;
    private long lastScan;

    public Building() {
    }

    public Building(BlockPos pos) {
        this(pos, true);
    }

    /** Kept as a source-compatible constructor; registered Buildings are always Rooms. */
    public Building(BlockPos pos, boolean ignoredStrictScan) {
        pos0X = pos1X = posX = pos.getX();
        pos0Y = pos1Y = posY = pos.getY();
        pos0Z = pos1Z = posZ = pos.getZ();
    }

    public Building(CompoundTag tag) {
        id = tag.getInt("id");
        size = tag.getInt("size");
        pos0X = tag.getInt("pos0X");
        pos0Y = tag.getInt("pos0Y");
        pos0Z = tag.getInt("pos0Z");
        pos1X = tag.getInt("pos1X");
        pos1Y = tag.getInt("pos1Y");
        pos1Z = tag.getInt("pos1Z");
        posX = tag.getInt("posX");
        posY = tag.getInt("posY");
        posZ = tag.getInt("posZ");
        floorRegions = List.copyOf(NbtHelper.toList(
                tag.getList("floorRegions", Tag.TAG_COMPOUND),
                value -> BuildingFloorRegion.load((CompoundTag) value)));
        structureId = tag.getInt("structureId");
        floorId = tag.getInt("floorId");
        typeForced = tag.getBoolean("isTypeForced");
        type = tag.getString("type");
        inheritanceEnabled = tag.getBoolean("inheritanceEnabled");
        blocks.putAll(NbtHelper.toMap(tag.getCompound("blocks2"),
                ResourceLocation::parse,
                value -> NbtHelper.toStream(value, Building::loadBlockPos)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(ArrayList::new))));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putInt("size", size);
        tag.putInt("pos0X", pos0X);
        tag.putInt("pos0Y", pos0Y);
        tag.putInt("pos0Z", pos0Z);
        tag.putInt("pos1X", pos1X);
        tag.putInt("pos1Y", pos1Y);
        tag.putInt("pos1Z", pos1Z);
        tag.putInt("posX", posX);
        tag.putInt("posY", posY);
        tag.putInt("posZ", posZ);
        tag.putInt("structureId", structureId);
        tag.putInt("floorId", floorId);
        tag.putBoolean("isTypeForced", typeForced);
        tag.putString("type", type);
        tag.putBoolean("inheritanceEnabled", inheritanceEnabled);
        tag.put("floorRegions", NbtHelper.fromList(floorRegions, BuildingFloorRegion::save));
        CompoundTag blockTag = new CompoundTag();
        NbtHelper.fromMap(blockTag, blocks, ResourceLocation::toString,
                positions -> NbtHelper.fromList(positions, NbtHelper::encodeBlockPos));
        tag.put("blocks2", blockTag);
        return tag;
    }

    private static BlockPos loadBlockPos(Tag tag) {
        return NbtHelper.decodeBlockPos(tag);
    }

    Building.validationResult applyRoomScan(Level world,
                                             BuildingRoomScanner.Result scan) {
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

        blocks.clear();
        for (BlockPos pos : scan.poiCells()) {
            recordBuildingBlock(world, pos);
        }
        BlockPos seed = scan.seed();
        posX = seed.getX();
        posY = seed.getY();
        posZ = seed.getZ();
        setGeometry(scan.min(), scan.max(), scan.footprintCells().size(),
                BuildingFloorRegion.fromFootprint(scan.floorY(), scan.footprintCells()));
        lastScan = world.getGameTime();
        return validationResult.SUCCESS;
    }

    void setGeometry(BlockPos min, BlockPos max, int size, BuildingFloorRegion footprint) {
        pos0X = min.getX();
        pos0Y = min.getY();
        pos0Z = min.getZ();
        pos1X = max.getX();
        pos1Y = max.getY();
        pos1Z = max.getZ();
        this.size = size;
        floorRegions = footprint == null ? List.of() : List.of(footprint);
    }

    public List<BuildingFloorRegion> getFloorRegions() {
        return floorRegions;
    }

    public int getFloorY() {
        return floorRegions.isEmpty() ? posY : floorRegions.getFirst().anchorY();
    }

    public int getFloorDistanceTo(Vec3i pos) {
        return Math.abs(getFloorY() - pos.getY());
    }

    public boolean containsFloorPosition(Vec3i pos) {
        return getFloorDistanceTo(pos) <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE
                && containsFloorColumn(pos.getX(), pos.getZ());
    }

    boolean containsFloorColumn(int x, int z) {
        if (floorRegions.isEmpty()) {
            return x >= pos0X && x <= pos1X && z >= pos0Z && z <= pos1Z;
        }
        return floorRegions.getFirst().containsHorizontally(x, z);
    }

    public long getFloorFootprintArea() {
        return floorRegions.isEmpty() ? getHorizontalArea() : floorRegions.getFirst().area();
    }

    public long getFloorFootprintIntersectionArea(Building other) {
        if (other == null) {
            return 0L;
        }
        if (!floorRegions.isEmpty() && !other.floorRegions.isEmpty()) {
            return floorRegions.getFirst().intersectionArea(other.floorRegions.getFirst());
        }
        int x = Math.min(pos1X, other.pos1X) - Math.max(pos0X, other.pos0X) + 1;
        int z = Math.min(pos1Z, other.pos1Z) - Math.max(pos0Z, other.pos0Z) + 1;
        return x <= 0 || z <= 0 ? 0L : (long) x * z;
    }

    public boolean sharesFloorBandWith(Building other) {
        return other != null && (floorId >= 0 && other.floorId >= 0
                ? floorId == other.floorId
                : Math.abs(getFloorY() - other.getFloorY()) <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE);
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

    public int getFloorId() {
        return floorId;
    }

    public void setFloorId(int floorId) {
        this.floorId = floorId;
    }

    public boolean isFunctionalRoom() {
        return !(this instanceof ExternalBuilding);
    }

    public boolean isStrictScan() {
        return isFunctionalRoom();
    }

    public List<BuildingType> getMatchingTypes() {
        return new ArrayList<>(matchingTypes(blocks));
    }

    static List<BuildingType> matchingTypes(Map<ResourceLocation, List<BlockPos>> availableBlocks) {
        List<BuildingType> matches = new ArrayList<>();
        for (BuildingType type : BuildingTypes.getInstance()) {
            if (!type.grouped() && matchesType(type, availableBlocks)) {
                matches.add(type);
            }
        }
        matches.sort(Comparator.comparingInt(BuildingType::priority).reversed()
                .thenComparing(BuildingType::name));
        return List.copyOf(matches);
    }

    public List<BuildingType> getVisibleMatchingTypes() {
        return new ArrayList<>(visibleMatchingTypes(blocks));
    }

    static List<BuildingType> visibleMatchingTypes(Map<ResourceLocation, List<BlockPos>> availableBlocks) {
        List<BuildingType> matches = new ArrayList<>(matchingTypes(availableBlocks).stream()
                .filter(type -> type.visible() || type.name().equals("house"))
                .filter(type -> !type.name().equals("blocked") && !type.name().equals("building"))
                .toList());
        if (matches.stream().anyMatch(type -> type.name().equals("big_house"))) {
            matches.removeIf(type -> type.name().equals("house"));
        }
        return matches;
    }

    public boolean matchesType(BuildingType type) {
        return matchesType(type, blocks);
    }

    static boolean matchesType(BuildingType type, Map<ResourceLocation, List<BlockPos>> availableBlocks) {
        Map<ResourceLocation, List<BlockPos>> available = type.getGroups(availableBlocks);
        return type.getGroups().entrySet().stream()
                .noneMatch(entry -> !available.containsKey(entry.getKey())
                        || available.get(entry.getKey()).size() < entry.getValue());
    }

    public boolean determineType() {
        List<BuildingType> matches = getMatchingTypes();
        if (matches.isEmpty()) {
            return false;
        }
        type = matches.getFirst().name();
        return true;
    }

    protected void recordBuildingBlock(Level world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        boolean relevant = false;
        for (BuildingType type : BuildingTypes.getInstance()) {
            if (type.matchesBlock(state)) {
                relevant = true;
                break;
            }
        }
        if (relevant && (!(block instanceof BedBlock) || state.getValue(BedBlock.PART) == BedPart.HEAD)) {
            addBlock(block, pos);
        }
    }

    public Stream<BlockPos> getBlockPosStream() {
        return blocks.values().stream().flatMap(Collection::stream);
    }

    public Map<ResourceLocation, List<BlockPos>> getBlocks() {
        return blocks;
    }

    public void addBlock(Block block, BlockPos pos) {
        blocks.computeIfAbsent(BuiltInRegistries.BLOCK.getKey(block), ignored -> new ArrayList<>()).add(pos);
    }

    public void removeBlock(Block block, BlockPos pos) {
        List<BlockPos> positions = blocks.get(BuiltInRegistries.BLOCK.getKey(block));
        if (positions != null) {
            positions.remove(pos);
        }
    }

    public int getBlockCount() {
        return blocks.values().stream().mapToInt(List::size).sum();
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isTypeForced() {
        return typeForced;
    }

    public void setTypeForced(boolean forced) {
        typeForced = forced;
    }

    public boolean isInheritanceEnabled() {
        return inheritanceEnabled;
    }

    public void setInheritanceEnabled(boolean enabled) {
        inheritanceEnabled = enabled;
    }

    public BuildingType getBuildingType() {
        return BuildingTypes.getInstance().getBuildingType(type);
    }

    public BlockPos getRawPos0() {
        return new BlockPos(pos0X, pos0Y, pos0Z);
    }

    public BlockPos getRawPos1() {
        return new BlockPos(pos1X, pos1Y, pos1Z);
    }

    @Override
    public BlockPos getPos0() {
        int margin = getBuildingType().getMargin();
        return getRawPos0().subtract(new Vec3i(margin, margin, margin));
    }

    @Override
    public BlockPos getPos1() {
        int margin = getBuildingType().getMargin();
        return getRawPos1().offset(new Vec3i(margin, margin, margin));
    }

    @Override
    public BlockPos getCenter() {
        return new BlockPos((pos0X + pos1X) / 2, (pos0Y + pos1Y) / 2, (pos0Z + pos1Z) / 2);
    }

    public BlockPos getSourceBlock() {
        return new BlockPos(posX, posY, posZ);
    }

    @Override
    public boolean containsPos(Vec3i pos) {
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

    public boolean containsPositionWithMargin(Vec3i pos, int horizontalMargin, int verticalMargin) {
        return pos.getX() >= pos0X - horizontalMargin && pos.getX() <= pos1X + horizontalMargin
                && pos.getY() >= pos0Y - verticalMargin && pos.getY() <= pos1Y + verticalMargin
                && pos.getZ() >= pos0Z - horizontalMargin && pos.getZ() <= pos1Z + horizontalMargin;
    }

    public boolean containsStructurePosition(Vec3i pos) {
        return containsFloorPosition(pos) || containsPositionWithMargin(pos,
                PLAYER_POSITION_HORIZONTAL_MARGIN, PLAYER_POSITION_VERTICAL_MARGIN);
    }

    public boolean overlaps(Building other) {
        return pos1X > other.pos0X && pos0X < other.pos1X
                && pos1Y > other.pos0Y && pos0Y < other.pos1Y
                && pos1Z > other.pos0Z && pos0Z < other.pos1Z;
    }

    public boolean isIdentical(Building other) {
        return other != null && floorId == other.floorId
                && getFloorFootprintArea() == other.getFloorFootprintArea()
                && getFloorFootprintIntersectionArea(other) == getFloorFootprintArea();
    }

    public int getHorizontalArea() {
        return Math.max(1, pos1X - pos0X + 1) * Math.max(1, pos1Z - pos0Z + 1);
    }

    public int getSize() {
        return size;
    }

    public int getVerticalDistanceTo(Vec3i pos) {
        if (pos.getY() < pos0Y) return pos0Y - pos.getY();
        if (pos.getY() > pos1Y) return pos.getY() - pos1Y;
        return 0;
    }

    public void copyScannedGeometryFrom(Building scanned, Level world, boolean preserveFloorClassification) {
        int oldFloorId = floorId;
        int oldStructureId = structureId;
        String oldType = type;
        boolean oldForced = typeForced;
        BlockPos oldSource = getSourceBlock();

        size = scanned.size;
        pos0X = scanned.pos0X;
        pos0Y = scanned.pos0Y;
        pos0Z = scanned.pos0Z;
        pos1X = scanned.pos1X;
        pos1Y = scanned.pos1Y;
        pos1Z = scanned.pos1Z;
        floorRegions = scanned.floorRegions;
        lastScan = scanned.lastScan;
        blocks.clear();
        scanned.blocks.forEach((key, value) -> blocks.put(key, new ArrayList<>(value)));
        structureId = oldStructureId;
        floorId = oldFloorId;
        type = oldType;
        typeForced = oldForced;

        BlockState oldSourceState = world.getBlockState(oldSource);
        if (!containsPos(oldSource)
                || !(oldSourceState.isAir() || !oldSourceState.getFluidState().isEmpty()
                || oldSourceState.getCollisionShape(world, oldSource).isEmpty())) {
            posX = scanned.posX;
            posY = scanned.posY;
            posZ = scanned.posZ;
        }
    }

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getLastScan() {
        return lastScan;
    }

    public void setLastScan(long lastScan) {
        this.lastScan = lastScan;
    }

    public boolean isComplete() {
        int minBlocks = getBuildingType().getMinBlocks();
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
