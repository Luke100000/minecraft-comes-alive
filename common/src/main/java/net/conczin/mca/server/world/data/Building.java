package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import java.util.*;
import java.util.stream.Stream;

public class Building {
    public static final long SCAN_COOLDOWN = 4800;
    private static final Direction[] directions = {
            Direction.UP, Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };

    private final Map<Identifier, List<BlockPos>> blocks = new HashMap<>();

    private String type = "building";
    private boolean isTypeForced = false;

    private int size;
    private int pos0X, pos0Y, pos0Z;
    private int pos1X, pos1Y, pos1Z;
    private int posX, posY, posZ;
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

        this.strictScan = strictScan;
    }

    public Building(CompoundTag v) {
        id = v.getInt("id").orElse(0);
        size = v.getInt("size").orElse(0);
        pos0X = v.getInt("pos0X").orElse(0);
        pos0Y = v.getInt("pos0Y").orElse(0);
        pos0Z = v.getInt("pos0Z").orElse(0);
        pos1X = v.getInt("pos1X").orElse(0);
        pos1Y = v.getInt("pos1Y").orElse(0);
        pos1Z = v.getInt("pos1Z").orElse(0);
        if (v.contains("posX")) {
            posX = v.getInt("posX").orElse(0);
            posY = v.getInt("posY").orElse(0);
            posZ = v.getInt("posZ").orElse(0);
        } else {
            BlockPos center = getCenter();
            posX = center.getX();
            posY = center.getY();
            posZ = center.getZ();
        }

        isTypeForced = v.getBoolean("isTypeForced").orElse(false);
        type = v.getString("type").orElse(type);

        strictScan = v.getBoolean("strictScan").orElse(false);

        blocks.putAll(NbtHelper.toMap(v.getCompound("blocks2").orElseGet(CompoundTag::new),
                Identifier::parse,
                l -> NbtHelper.toList(l, e -> {
                    CompoundTag c = (CompoundTag) e;
                    return new BlockPos(c.getInt("x").orElse(0), c.getInt("y").orElse(0), c.getInt("z").orElse(0));
                })));
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
        v.putBoolean("isTypeForced", isTypeForced);
        v.putString("type", type);
        v.putBoolean("strictScan", strictScan);

        CompoundTag b = new CompoundTag();
        NbtHelper.fromMap(
                b,
                blocks,
                Identifier::toString,
                e -> NbtHelper.fromList(e, p -> {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("x", p.getX());
                    entry.putInt("y", p.getY());
                    entry.putInt("z", p.getZ());
                    return entry;
                })
        );
        v.put("blocks2", b);

        return v;
    }

    public BlockPos getPos0() {
        int margin = getBuildingType().getMargin();
        return new BlockPos(pos0X, pos0Y, pos0Z).subtract(new Vec3i(margin, margin, margin));
    }

    public BlockPos getPos1() {
        int margin = getBuildingType().getMargin();
        return new BlockPos(pos1X, pos1Y, pos1Z).offset(new Vec3i(margin, margin, margin));
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

    public void validateBlocks(Level world) {
        setLastScan(world.getGameTime());

        //remove all invalid blocks
        for (Map.Entry<Identifier, List<BlockPos>> positions : blocks.entrySet()) {
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
        //validate grouped buildings differently
        if (getBuildingType().grouped()) {
            validateBlocks(world);
            return getBlockPosStream().findAny().isEmpty() ? validationResult.TOO_SMALL : validationResult.SUCCESS;
        }

        //clear old building
        blocks.clear();
        size = 0;

        setLastScan(world.getGameTime());

        //temp data for flood fill
        Set<BlockPos> done = new HashSet<>();
        LinkedList<BlockPos> queue = new LinkedList<>();

        //start point
        BlockPos center = getSourceBlock();
        queue.add(center);
        done.add(center);

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
                            }
                        } else if (state.getBlock() instanceof DoorBlock) {
                            //skip door and start a new room
                            if (!strictScan) {
                                queue.add(n);
                            }
                            hasDoor = true;
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
        } else if (!hasDoor) {
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

                //count blocks types
                BlockState blockState = world.getBlockState(p);
                Block block = blockState.getBlock();
                if (isBuildingBlock(BuiltInRegistries.BLOCK.getKey(block))) {
                    if (block instanceof BedBlock) {
                        // TODO: look for better solution for 7.4.0
                        if (blockState.getValue(BedBlock.PART) == BedPart.HEAD) {
                            addBlock(block, p);
                        }
                    } else {
                        addBlock(block, p);
                    }
                }
            }

            //adjust building dimensions
            pos0X = sx;
            pos0Y = sy;
            pos0Z = sz;

            pos1X = ex;
            pos1Y = ey;
            pos1Z = ez;

            size = interiorSize;

            //determine type
            return isTypeForced() || determineType() ? validationResult.SUCCESS : validationResult.INVALID_TYPE;
        }
    }

    private boolean isBuildingBlock(Identifier blockId) {
        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.matchesBlock(blockId)) {
                return true;
            }
        }
        return false;
    }

    public boolean determineType() {
        int bestPriority = -1;
        boolean assignedType = false;

        for (BuildingType bt : BuildingTypes.getInstance()) {
            if (bt.priority() > bestPriority) {
                //get an overview of the satisfied blocks
                Map<Identifier, List<BlockPos>> available = bt.getGroups(blocks);
                boolean valid = bt.getGroups().entrySet().stream().noneMatch(e -> !available.containsKey(e.getKey()) || available.get(e.getKey()).size() < e.getValue());
                if (valid) {
                    bestPriority = bt.priority();
                    type = bt.name();
                    assignedType = true;
                }
            }
        }
        return assignedType;
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

    public Map<Identifier, List<BlockPos>> getBlocks() {
        return blocks;
    }

    public void addBlock(Block block, BlockPos p) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
        blocks.computeIfAbsent(key, k -> new ArrayList<>());
        blocks.get(key).add(p);
    }

    public void removeBlock(Block block, BlockPos p) {
        Identifier key = BuiltInRegistries.BLOCK.getKey(block);
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

    public boolean isIdentical(Building b) {
        return pos0X == b.pos0X && pos1X == b.pos1X && pos0Y == b.pos0Y && pos1Y == b.pos1Y && pos0Z == b.pos0Z && pos1Z == b.pos1Z;
    }

    public int getSize() {
        return size;
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
        INVALID_TYPE
    }
}
