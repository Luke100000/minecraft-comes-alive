package net.mca.entity.ai.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.mca.Config;
import net.minecraft.block.*;
import net.minecraft.entity.ai.pathing.*;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.ChunkCache;

import java.util.EnumSet;

public class VillagerLandPathNodeMaker extends PathNodeMaker {
    protected float waterPathNodeTypeWeight;
    private final Long2ObjectMap<ExtendedPathNodeType> nodeTypes = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<Box> collidedBoxes = new Object2BooleanOpenHashMap<>();

    @Override
    public void init(ChunkCache cachedWorld, MobEntity entity) {
        super.init(cachedWorld, entity);
        this.waterPathNodeTypeWeight = getPenalty(entity, ExtendedPathNodeType.WATER);
    }

    @Override
    public void clear() {
        this.entity.setPathfindingPenalty(ExtendedPathNodeType.WATER.toVanilla(), this.waterPathNodeTypeWeight);
        this.nodeTypes.clear();
        this.collidedBoxes.clear();
        super.clear();
    }

    @Override
    public PathNode getStart() {
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        int i = this.entity.getBlockY();
        BlockState blockState = this.cachedWorld.getBlockState(mutable.set(this.entity.getX(), i, this.entity.getZ()));
        if (this.entity.canWalkOnFluid(blockState.getFluidState())) {
            while (this.entity.canWalkOnFluid(blockState.getFluidState())) {
                blockState = this.cachedWorld.getBlockState(mutable.set(this.entity.getX(), ++i, this.entity.getZ()));
            }
            --i;
        } else if (this.canSwim() && this.entity.isTouchingWater()) {
            while (blockState.isOf(Blocks.WATER) || blockState.getFluidState() == Fluids.WATER.getStill(false)) {
                blockState = this.cachedWorld.getBlockState(mutable.set(this.entity.getX(), ++i, this.entity.getZ()));
            }
            --i;
        } else if (this.entity.isOnGround()) {
            i = MathHelper.floor(this.entity.getY() + 0.5);
        } else {
            BlockPos blockPos = this.entity.getBlockPos();
            while ((this.cachedWorld.getBlockState(blockPos).isAir() || this.cachedWorld.getBlockState(blockPos).canPathfindThrough(this.cachedWorld, blockPos, NavigationType.LAND)) && blockPos.getY() > this.entity.getWorld().getBottomY()) {
                blockPos = blockPos.down();
            }
            i = blockPos.up().getY();
        }

        BlockPos blockPos = this.entity.getBlockPos();
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.entity, blockPos.getX(), i, blockPos.getZ());
        if (getPenalty(pathNodeType) < 0.0f) {
            Box box = this.entity.getBoundingBox();
            if (this.canPathThrough(mutable.set(box.minX, i, box.minZ)) || this.canPathThrough(mutable.set(box.minX, i, box.maxZ)) || this.canPathThrough(mutable.set(box.maxX, i, box.minZ)) || this.canPathThrough(mutable.set(box.maxX, i, box.maxZ))) {
                PathNode pathNode = this.getNode(mutable);
                ExtendedPathNodeType type = this.getNodeType(this.entity, pathNode.getBlockPos());
                pathNode.type = type.toVanilla();
                pathNode.penalty = getPenalty(type);
                return pathNode;
            }
        }

        PathNode pathNode2 = this.getNode(blockPos.getX(), i, blockPos.getZ());
        ExtendedPathNodeType type = this.getNodeType(this.entity, pathNode2.getBlockPos());
        pathNode2.type = type.toVanilla();
        pathNode2.penalty = getPenalty(type);
        return pathNode2;
    }

    private boolean canPathThrough(BlockPos pos) {
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.entity, pos);
        return getPenalty(pathNodeType) >= 0.0f;
    }

    @Override
    public TargetPathNode getNode(double x, double y, double z) {
        return new TargetPathNode(this.getNode(MathHelper.floor(x), MathHelper.floor(y), MathHelper.floor(z)));
    }

    @Override
    public int getSuccessors(PathNode[] successors, PathNode node) {
        ExtendedPathNodeType pathNodeTypeHead = this.getNodeType(this.entity, node.x, node.y + 1, node.z);
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.entity, node.x, node.y, node.z);

        double feetY = this.getFeetY(new BlockPos(node.x, node.y, node.z));
        int maxYStep = 0;
        if (getPenalty(pathNodeTypeHead) >= 0.0f && pathNodeType != ExtendedPathNodeType.STICKY_HONEY) {
            maxYStep = MathHelper.floor(Math.max(1.0f, this.entity.getStepHeight()));
        }

        PathNode pathNode1 = this.getPathNode(node.x, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);
        PathNode pathNode2 = this.getPathNode(node.x - 1, node.y, node.z, maxYStep, feetY, Direction.WEST, pathNodeType);
        PathNode pathNode3 = this.getPathNode(node.x + 1, node.y, node.z, maxYStep, feetY, Direction.EAST, pathNodeType);
        PathNode pathNode4 = this.getPathNode(node.x, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        PathNode pathNode5 = this.getPathNode(node.x - 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        PathNode pathNode6 = this.getPathNode(node.x + 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        PathNode pathNode7 = this.getPathNode(node.x - 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);
        PathNode pathNode8 = this.getPathNode(node.x + 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);

        int i = 0;
        if (this.isValidAdjacentSuccessor(pathNode1, node)) {
            successors[i++] = pathNode1;
        }
        if (this.isValidAdjacentSuccessor(pathNode2, node)) {
            successors[i++] = pathNode2;
        }
        if (this.isValidAdjacentSuccessor(pathNode3, node)) {
            successors[i++] = pathNode3;
        }
        if (this.isValidAdjacentSuccessor(pathNode4, node)) {
            successors[i++] = pathNode4;
        }
        if (this.isValidDiagonalSuccessor(node, pathNode2, pathNode4, pathNode5)) {
            successors[i++] = pathNode5;
        }
        if (this.isValidDiagonalSuccessor(node, pathNode3, pathNode4, pathNode6)) {
            successors[i++] = pathNode6;
        }
        if (this.isValidDiagonalSuccessor(node, pathNode2, pathNode1, pathNode7)) {
            successors[i++] = pathNode7;
        }
        if (this.isValidDiagonalSuccessor(node, pathNode3, pathNode1, pathNode8)) {
            successors[i++] = pathNode8;
        }
        return i;
    }

    protected boolean isValidAdjacentSuccessor( PathNode node, PathNode successor1) {
        return node != null && !node.visited && (node.penalty >= 0.0f || successor1.penalty < 0.0f);
    }

    protected boolean isValidDiagonalSuccessor(PathNode xNode,  PathNode zNode,  PathNode xDiagNode,  PathNode zDiagNode) {
        if (zDiagNode == null || xDiagNode == null || zNode == null) {
            return false;
        }
        if (zDiagNode.visited) {
            return false;
        }
        if (xDiagNode.y > xNode.y || zNode.y > xNode.y) {
            return false;
        }
        if (zNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla() || xDiagNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla() || zDiagNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla()) {
            return false;
        }
        boolean bl = xDiagNode.type == ExtendedPathNodeType.FENCE.toVanilla() && zNode.type == ExtendedPathNodeType.FENCE.toVanilla() && (double) this.entity.getWidth() < 0.5;
        return zDiagNode.penalty >= 0.0f && (xDiagNode.y < xNode.y || xDiagNode.penalty >= 0.0f || bl) && (zNode.y < xNode.y || zNode.penalty >= 0.0f || bl);
    }

    private boolean isBlocked(PathNode node) {
        Vec3d vec3d = new Vec3d((double) node.x - this.entity.getX(), (double) node.y - this.entity.getY(), (double) node.z - this.entity.getZ());
        Box box = this.entity.getBoundingBox();
        int i = MathHelper.ceil(vec3d.length() / box.getAverageSideLength());
        vec3d = vec3d.multiply(1.0f / (float) i);
        for (int j = 1; j <= i; ++j) {
            if (!this.checkBoxCollision(box = box.offset(vec3d))) continue;
            return false;
        }
        return true;
    }

    protected double getFeetY(BlockPos pos) {
        return VillagerLandPathNodeMaker.getFeetY(this.cachedWorld, pos);
    }

    public static double getFeetY(BlockView world, BlockPos pos) {
        BlockPos blockPos = pos.down();
        VoxelShape voxelShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
        return (double) blockPos.getY() + (voxelShape.isEmpty() ? 0.0 : voxelShape.getMax(Direction.Axis.Y));
    }

    float getPenalty(ExtendedPathNodeType pathNodeType) {
        return getPenalty(entity, pathNodeType);
    }

    private float getPenalty(MobEntity mob, ExtendedPathNodeType type) {
        return mob.getPathfindingPenalty(type.toVanilla()) + type.getBonusPenalty();
    }

    
    protected PathNode getPathNode(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, ExtendedPathNodeType nodeType) {
        double h;
        double g;

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double step = this.getFeetY(mutable.set(x, y, z));
        if (step - prevFeetY > 1.125) {
            return null;
        }

        ExtendedPathNodeType pathNodeType = this.getNodeType(this.entity, x, y, z);
        float penalty = getPenalty(pathNodeType);
        double e = (double) this.entity.getWidth() / 2.0;

        PathNode pathNode = null;
        if (penalty >= 0.0f) {
            pathNode = this.getNode(x, y, z);
            pathNode.type = pathNodeType.toVanilla();
            pathNode.penalty = Math.max(pathNode.penalty, penalty);
        }

        if (nodeType == ExtendedPathNodeType.FENCE && pathNode != null && pathNode.penalty >= 0.0f && !this.isBlocked(pathNode)) {
            pathNode = null;
        }

        if (pathNodeType.isWalkable()) {
            return pathNode;
        }

        // Step
        if ((pathNode == null || pathNode.penalty < 0.0f) &&
                maxYStep > 0 &&
                pathNodeType != ExtendedPathNodeType.FENCE &&
                pathNodeType != ExtendedPathNodeType.UNPASSABLE_RAIL &&
                pathNodeType != ExtendedPathNodeType.TRAPDOOR &&
                pathNodeType != ExtendedPathNodeType.POWDER_SNOW) {
            pathNode = this.getPathNode(x, y + 1, z, maxYStep - 1, prevFeetY, direction, nodeType);
            if (pathNode != null &&
                    (pathNode.type == ExtendedPathNodeType.OPEN.toVanilla() || pathNode.type == ExtendedPathNodeType.WALKABLE.toVanilla()) &&
                    this.entity.getWidth() < 1.0f &&
                    this.checkBoxCollision(new Box((g = (double) (x - direction.getOffsetX()) + 0.5) - e,
                            VillagerLandPathNodeMaker.getFeetY(this.cachedWorld, mutable.set(g, y + 1, h = (double) (z - direction.getOffsetZ()) + 0.5)) + 0.001,
                            h - e,
                            g + e,
                            (double) this.entity.getHeight() + VillagerLandPathNodeMaker.getFeetY(this.cachedWorld, mutable.set(pathNode.x, pathNode.y, (double) pathNode.z)) - 0.002,
                            h + e)
                    )) {
                pathNode = null;
            }
        }

        if (pathNodeType == ExtendedPathNodeType.WATER && !this.canSwim()) {
            if (this.getNodeType(this.entity, x, y - 1, z) != ExtendedPathNodeType.WATER) {
                return pathNode;
            }
            while (y > this.entity.getWorld().getBottomY()) {
                if ((pathNodeType = this.getNodeType(this.entity, x, --y, z)) == ExtendedPathNodeType.WATER) {
                    pathNode = this.getNode(x, y, z);
                    pathNode.type = pathNodeType.toVanilla();
                    pathNode.penalty = Math.max(pathNode.penalty, getPenalty(pathNodeType));
                    continue;
                }
                return pathNode;
            }
        }

        if (pathNodeType == ExtendedPathNodeType.OPEN) {
            int i = 0;
            int j = y;
            while (pathNodeType == ExtendedPathNodeType.OPEN) {
                if (--y < this.entity.getWorld().getBottomY()) {
                    PathNode pathNode2 = this.getNode(x, j, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.penalty = -1.0f;
                    return pathNode2;
                }
                if (i++ >= this.entity.getSafeFallDistance()) {
                    PathNode pathNode2 = this.getNode(x, y, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.penalty = -1.0f;
                    return pathNode2;
                }
                pathNodeType = this.getNodeType(this.entity, x, y, z);
                penalty = getPenalty(pathNodeType);
                if (pathNodeType != ExtendedPathNodeType.OPEN && penalty >= 0.0f) {
                    pathNode = this.getNode(x, y, z);
                    pathNode.type = pathNodeType.toVanilla();
                    pathNode.penalty = Math.max(pathNode.penalty, penalty);
                    break;
                }
                if (penalty < 0.0f) {
                    PathNode pathNode2 = this.getNode(x, y, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.penalty = -1.0f;
                    return pathNode2;
                }
            }
        }

        if (pathNodeType == ExtendedPathNodeType.FENCE) {
            pathNode = this.getNode(x, y, z);
            pathNode.visited = true;
            pathNode.type = pathNodeType.toVanilla();
            pathNode.penalty = pathNodeType.getDefaultPenalty();
        }

        return pathNode;
    }

    private boolean checkBoxCollision(Box box) {
        return this.collidedBoxes.computeIfAbsent(box, object -> !this.cachedWorld.isSpaceEmpty(this.entity, box));
    }

    @Override
    public PathNodeType getNodeType(BlockView world, int x, int y, int z, MobEntity mob) {
        // Placeholder, not used
        return getExtendedNodeType(world, x, y, z, mob, entityBlockXSize, entityBlockYSize, entityBlockZSize, canOpenDoors, canEnterOpenDoors).toVanilla();
    }

    public ExtendedPathNodeType getExtendedNodeType(BlockView world, int x, int y, int z, MobEntity mob, int sizeX, int sizeY, int sizeZ, boolean canOpenDoors, boolean canEnterOpenDoors) {
        EnumSet<ExtendedPathNodeType> enumSet = EnumSet.noneOf(ExtendedPathNodeType.class);
        ExtendedPathNodeType centerPathNodeType = this.findNearbyNodeTypes(world, x, y, z, sizeX, sizeY, sizeZ, canOpenDoors, canEnterOpenDoors, enumSet, mob.getBlockPos());
        if (enumSet.contains(ExtendedPathNodeType.FENCE)) {
            return ExtendedPathNodeType.FENCE;
        }
        if (enumSet.contains(ExtendedPathNodeType.UNPASSABLE_RAIL)) {
            return ExtendedPathNodeType.UNPASSABLE_RAIL;
        }
        ExtendedPathNodeType worstPathNode = ExtendedPathNodeType.BLOCKED;
        for (ExtendedPathNodeType touchedPathNodeType : enumSet) {
            if (getPenalty(mob, touchedPathNodeType) < 0.0f) {
                return touchedPathNodeType;
            }
            if (getPenalty(mob, touchedPathNodeType) >= getPenalty(mob, worstPathNode)) {
                worstPathNode = touchedPathNodeType;
            }
        }
        if (sizeX <= 1 && centerPathNodeType == ExtendedPathNodeType.OPEN && getPenalty(mob, worstPathNode) == 0.0f) {
            return ExtendedPathNodeType.OPEN;
        }
        return worstPathNode;
    }

    /**
     * Adds the node types in the box with the given size to the input EnumSet.
     */
    public ExtendedPathNodeType findNearbyNodeTypes(BlockView world, int x, int y, int z, int sizeX, int sizeY, int sizeZ, boolean canOpenDoors, boolean canEnterOpenDoors, EnumSet<ExtendedPathNodeType> nearbyTypes, BlockPos pos) {
        BlockPos.Mutable p = new BlockPos.Mutable(x, y, z);
        ExtendedPathNodeType type = ExtendedPathNodeType.BLOCKED;

        for (int i = 0; i < sizeX; ++i) {
            for (int j = 0; j < sizeY; ++j) {
                for (int k = 0; k < sizeZ; ++k) {
                    int l = i + x;
                    int m = j + y;
                    int n = k + z;

                    p.set(l, m, n);

                    BlockState blockState = world.getBlockState(p);

                    ExtendedPathNodeType pathNodeType = getExtendedDefaultNodeType(world, l, m, n);
                    pathNodeType = adjustNodeType(world, canOpenDoors, canEnterOpenDoors, pos, pathNodeType);

                    // Villager can also open gates
                    if (Config.getServerConfig().useSmarterDoorAI && blockState.isIn(BlockTags.FENCE_GATES, state -> state.getBlock() instanceof FenceGateBlock)) {
                        pathNodeType = ExtendedPathNodeType.WALKABLE_DOOR;
                    }

                    if (pathNodeType != ExtendedPathNodeType.DOOR_OPEN) {
                        if (blockState.getBlock() instanceof DoorBlock) {
                            // if we find a door, check that it's adjacent to any of the previously found pressure plates.
                            for (BlockPos adjacent : BlockPos.iterate(l - 1, m - 1, n - 1, l + 1, m + 1, n + 1)) {
                                if (world.getBlockState(adjacent).isIn(BlockTags.PRESSURE_PLATES)) {
                                    pathNodeType = ExtendedPathNodeType.DOOR_OPEN;
                                    break;
                                }
                            }
                        }
                    }

                    if (i == 0 && j == 0 && k == 0) {
                        type = pathNodeType;
                    }

                    nearbyTypes.add(pathNodeType);
                }
            }
        }

        return type;
    }

    protected ExtendedPathNodeType adjustNodeType(BlockView world, boolean canOpenDoors, boolean canEnterOpenDoors, BlockPos pos, ExtendedPathNodeType type) {
        if (type == ExtendedPathNodeType.DOOR_WOOD_CLOSED && canOpenDoors && canEnterOpenDoors) {
            type = ExtendedPathNodeType.WALKABLE_DOOR;
        }
        if (type == ExtendedPathNodeType.DOOR_OPEN && !canEnterOpenDoors) {
            type = ExtendedPathNodeType.BLOCKED;
        }
        if (type == ExtendedPathNodeType.RAIL && !(world.getBlockState(pos).getBlock() instanceof AbstractRailBlock) && !(world.getBlockState(pos.down()).getBlock() instanceof AbstractRailBlock)) {
            type = ExtendedPathNodeType.UNPASSABLE_RAIL;
        }
        if (type == ExtendedPathNodeType.LEAVES) {
            type = ExtendedPathNodeType.BLOCKED;
        }
        return type;
    }

    private ExtendedPathNodeType getNodeType(MobEntity entity, BlockPos pos) {
        return this.getNodeType(entity, pos.getX(), pos.getY(), pos.getZ());
    }

    protected ExtendedPathNodeType getNodeType(MobEntity entity, int x, int y, int z) {
        return this.nodeTypes.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getExtendedNodeType(this.cachedWorld, x, y, z, entity, this.entityBlockXSize, this.entityBlockYSize, this.entityBlockZSize, this.canOpenDoors(), this.canEnterOpenDoors()));
    }

    @Override
    public PathNodeType getDefaultNodeType(BlockView world, int x, int y, int z) {
        return getExtendedDefaultNodeType(world, x, y, z).toVanilla();
    }

    public ExtendedPathNodeType getExtendedDefaultNodeType(BlockView world, int x, int y, int z) {
        return VillagerLandPathNodeMaker.getLandNodeType(world, new BlockPos.Mutable(x, y, z));
    }

    public static ExtendedPathNodeType getLandNodeType(BlockView world, BlockPos.Mutable pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ExtendedPathNodeType pathNodeType = VillagerLandPathNodeMaker.getCommonNodeType(world, pos);
        if (pathNodeType == ExtendedPathNodeType.OPEN && y >= world.getBottomY() + 1) {
            ExtendedPathNodeType floorType = VillagerLandPathNodeMaker.getCommonNodeType(world, pos.set(x, y - 1, z));
            pathNodeType = floorType.isWalkable() || floorType == ExtendedPathNodeType.OPEN || floorType == ExtendedPathNodeType.WATER || floorType == ExtendedPathNodeType.LAVA ? ExtendedPathNodeType.OPEN : ExtendedPathNodeType.WALKABLE;

            // Start of MCA
            if (floorType == ExtendedPathNodeType.PATH) {
                pathNodeType = ExtendedPathNodeType.WALKABLE_PATH;
            }
            if (floorType == ExtendedPathNodeType.GRASS) {
                pathNodeType = ExtendedPathNodeType.WALKABLE_GRASS;
            }

            // Start of vanilla
            if (floorType == ExtendedPathNodeType.DAMAGE_FIRE) {
                pathNodeType = ExtendedPathNodeType.DAMAGE_FIRE;
            }
            if (floorType == ExtendedPathNodeType.DAMAGE_OTHER) {
                pathNodeType = ExtendedPathNodeType.DAMAGE_OTHER;
            }
            if (floorType == ExtendedPathNodeType.STICKY_HONEY) {
                pathNodeType = ExtendedPathNodeType.STICKY_HONEY;
            }
            if (floorType == ExtendedPathNodeType.POWDER_SNOW) {
                pathNodeType = ExtendedPathNodeType.DANGER_POWDER_SNOW;
            }
        }
        if (pathNodeType.isWalkable()) {
            pathNodeType = VillagerLandPathNodeMaker.getNodeTypeFromNeighbors(world, pos.set(x, y, z), pathNodeType);
        }
        return pathNodeType;
    }

    public static ExtendedPathNodeType getNodeTypeFromNeighbors(BlockView world, BlockPos.Mutable pos, ExtendedPathNodeType nodeType) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int l = -1; l <= 1; ++l) {
            for (int m = -1; m <= 1; ++m) {
                for (int n = -1; n <= 1; ++n) {
                    if (l == 0 && n == 0) continue;
                    pos.set(x + l, y + m, z + n);
                    BlockState blockState = world.getBlockState(pos);
                    if (blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
                        return ExtendedPathNodeType.DANGER_OTHER;
                    }
                    if (VillagerLandPathNodeMaker.inflictsFireDamage(blockState)) {
                        return ExtendedPathNodeType.DANGER_FIRE;
                    }
                    if (!world.getFluidState(pos).isIn(FluidTags.WATER)) continue;
                    return ExtendedPathNodeType.WATER_BORDER;
                }
            }
        }
        return nodeType;
    }

    protected static ExtendedPathNodeType getCommonNodeType(BlockView world, BlockPos pos) {
        BlockState blockState = world.getBlockState(pos);
        Block block = blockState.getBlock();

        // Here starts mca custom types
        if (block instanceof DirtPathBlock) {
            return ExtendedPathNodeType.PATH;
        }
        if (block instanceof GrassBlock) {
            return ExtendedPathNodeType.GRASS;
        }

        // Here starts vanilla types
        if (blockState.isAir()) {
            return ExtendedPathNodeType.OPEN;
        }
        if (blockState.isIn(BlockTags.TRAPDOORS) || blockState.isOf(Blocks.LILY_PAD) || blockState.isOf(Blocks.BIG_DRIPLEAF)) {
            return ExtendedPathNodeType.TRAPDOOR;
        }
        if (blockState.isOf(Blocks.POWDER_SNOW)) {
            return ExtendedPathNodeType.POWDER_SNOW;
        }
        if (blockState.isOf(Blocks.CACTUS) || blockState.isOf(Blocks.SWEET_BERRY_BUSH)) {
            return ExtendedPathNodeType.DAMAGE_OTHER;
        }
        if (blockState.isOf(Blocks.HONEY_BLOCK)) {
            return ExtendedPathNodeType.STICKY_HONEY;
        }
        if (blockState.isOf(Blocks.COCOA)) {
            return ExtendedPathNodeType.COCOA;
        }
        FluidState fluidState = world.getFluidState(pos);
        if (fluidState.isIn(FluidTags.LAVA)) {
            return ExtendedPathNodeType.LAVA;
        }
        if (VillagerLandPathNodeMaker.inflictsFireDamage(blockState)) {
            return ExtendedPathNodeType.DAMAGE_FIRE;
        }
        if (block instanceof DoorBlock doorBlock) {
            if (blockState.get(DoorBlock.OPEN)) {
                return ExtendedPathNodeType.DOOR_OPEN;
            } else {
                return doorBlock.getBlockSetType().canOpenByHand() ? ExtendedPathNodeType.DOOR_WOOD_CLOSED : ExtendedPathNodeType.DOOR_IRON_CLOSED;
            }
        }
        if (block instanceof AbstractRailBlock) {
            return ExtendedPathNodeType.RAIL;
        }
        if (block instanceof LeavesBlock) {
            return ExtendedPathNodeType.LEAVES;
        }
        if (blockState.isIn(BlockTags.FENCES) || blockState.isIn(BlockTags.WALLS) || (Config.getServerConfig().useSmarterDoorAI && block instanceof FenceGateBlock && !blockState.get(FenceGateBlock.OPEN))) {
            return ExtendedPathNodeType.FENCE;
        }
        if (!blockState.canPathfindThrough(world, pos, NavigationType.LAND)) {
            return ExtendedPathNodeType.BLOCKED;
        }
        if (fluidState.isIn(FluidTags.WATER)) {
            return ExtendedPathNodeType.WATER;
        }

        return ExtendedPathNodeType.OPEN;
    }

    public static boolean inflictsFireDamage(BlockState state) {
        return LandPathNodeMaker.inflictsFireDamage(state); // todo add custom tag
    }
}