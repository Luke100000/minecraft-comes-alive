package net.mca.entity.ai.pathfinder;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import net.mca.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.EnumSet;

public class VillagerLandPathNodeMaker extends NodeEvaluator {
    protected float waterPathNodeTypeWeight;
    private final Long2ObjectMap<ExtendedPathNodeType> nodeTypes = new Long2ObjectOpenHashMap<>();
    private final Object2BooleanMap<AABB> collidedBoxes = new Object2BooleanOpenHashMap<>();

    @Override
    public void prepare(PathNavigationRegion cachedWorld, Mob entity) {
        super.prepare(cachedWorld, entity);
        this.waterPathNodeTypeWeight = getPenalty(entity, ExtendedPathNodeType.WATER);
    }

    @Override
    public void done() {
        this.mob.setPathfindingMalus(ExtendedPathNodeType.WATER.toVanilla(), this.waterPathNodeTypeWeight);
        this.nodeTypes.clear();
        this.collidedBoxes.clear();
        super.done();
    }

    @Override
    public Node getStart() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        int i = this.mob.getBlockY();
        BlockState blockState = this.level.getBlockState(mutable.set(this.mob.getX(), i, this.mob.getZ()));
        if (this.mob.canStandOnFluid(blockState.getFluidState())) {
            while (this.mob.canStandOnFluid(blockState.getFluidState())) {
                blockState = this.level.getBlockState(mutable.set(this.mob.getX(), ++i, this.mob.getZ()));
            }
            --i;
        } else if (this.canFloat() && this.mob.isInWater()) {
            while (blockState.is(Blocks.WATER) || blockState.getFluidState() == Fluids.WATER.getSource(false)) {
                blockState = this.level.getBlockState(mutable.set(this.mob.getX(), ++i, this.mob.getZ()));
            }
            --i;
        } else if (this.mob.onGround()) {
            i = Mth.floor(this.mob.getY() + 0.5);
        } else {
            BlockPos blockPos = this.mob.blockPosition();
            while ((this.level.getBlockState(blockPos).isAir() || this.level.getBlockState(blockPos).isPathfindable(this.level, blockPos, PathComputationType.LAND)) && blockPos.getY() > this.mob.level().getMinBuildHeight()) {
                blockPos = blockPos.below();
            }
            i = blockPos.above().getY();
        }

        BlockPos blockPos = this.mob.blockPosition();
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.mob, blockPos.getX(), i, blockPos.getZ());
        if (getPenalty(pathNodeType) < 0.0f) {
            AABB box = this.mob.getBoundingBox();
            if (this.canPathThrough(mutable.set(box.minX, i, box.minZ)) || this.canPathThrough(mutable.set(box.minX, i, box.maxZ)) || this.canPathThrough(mutable.set(box.maxX, i, box.minZ)) || this.canPathThrough(mutable.set(box.maxX, i, box.maxZ))) {
                Node pathNode = this.getNode(mutable);
                ExtendedPathNodeType type = this.getNodeType(this.mob, pathNode.asBlockPos());
                pathNode.type = type.toVanilla();
                pathNode.costMalus = getPenalty(type);
                return pathNode;
            }
        }

        Node pathNode2 = this.getNode(blockPos.getX(), i, blockPos.getZ());
        ExtendedPathNodeType type = this.getNodeType(this.mob, pathNode2.asBlockPos());
        pathNode2.type = type.toVanilla();
        pathNode2.costMalus = getPenalty(type);
        return pathNode2;
    }

    private boolean canPathThrough(BlockPos pos) {
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.mob, pos);
        return getPenalty(pathNodeType) >= 0.0f;
    }

    @Override
    public Target getGoal(double x, double y, double z) {
        return new Target(this.getNode(Mth.floor(x), Mth.floor(y), Mth.floor(z)));
    }

    @Override
    public int getNeighbors(Node[] successors, Node node) {
        ExtendedPathNodeType pathNodeTypeHead = this.getNodeType(this.mob, node.x, node.y + 1, node.z);
        ExtendedPathNodeType pathNodeType = this.getNodeType(this.mob, node.x, node.y, node.z);

        double feetY = this.getFeetY(new BlockPos(node.x, node.y, node.z));
        int maxYStep = 0;
        if (getPenalty(pathNodeTypeHead) >= 0.0f && pathNodeType != ExtendedPathNodeType.STICKY_HONEY) {
            maxYStep = Mth.floor(Math.max(1.0f, this.mob.maxUpStep()));
        }

        Node pathNode1 = this.getPathNode(node.x, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);
        Node pathNode2 = this.getPathNode(node.x - 1, node.y, node.z, maxYStep, feetY, Direction.WEST, pathNodeType);
        Node pathNode3 = this.getPathNode(node.x + 1, node.y, node.z, maxYStep, feetY, Direction.EAST, pathNodeType);
        Node pathNode4 = this.getPathNode(node.x, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        Node pathNode5 = this.getPathNode(node.x - 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        Node pathNode6 = this.getPathNode(node.x + 1, node.y, node.z - 1, maxYStep, feetY, Direction.NORTH, pathNodeType);
        Node pathNode7 = this.getPathNode(node.x - 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);
        Node pathNode8 = this.getPathNode(node.x + 1, node.y, node.z + 1, maxYStep, feetY, Direction.SOUTH, pathNodeType);

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

    protected boolean isValidAdjacentSuccessor( Node node, Node successor1) {
        return node != null && !node.closed && (node.costMalus >= 0.0f || successor1.costMalus < 0.0f);
    }

    protected boolean isValidDiagonalSuccessor(Node xNode,  Node zNode,  Node xDiagNode,  Node zDiagNode) {
        if (zDiagNode == null || xDiagNode == null || zNode == null) {
            return false;
        }
        if (zDiagNode.closed) {
            return false;
        }
        if (xDiagNode.y > xNode.y || zNode.y > xNode.y) {
            return false;
        }
        if (zNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla() || xDiagNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla() || zDiagNode.type == ExtendedPathNodeType.WALKABLE_DOOR.toVanilla()) {
            return false;
        }
        boolean bl = xDiagNode.type == ExtendedPathNodeType.FENCE.toVanilla() && zNode.type == ExtendedPathNodeType.FENCE.toVanilla() && (double) this.mob.getBbWidth() < 0.5;
        return zDiagNode.costMalus >= 0.0f && (xDiagNode.y < xNode.y || xDiagNode.costMalus >= 0.0f || bl) && (zNode.y < xNode.y || zNode.costMalus >= 0.0f || bl);
    }

    private boolean isBlocked(Node node) {
        Vec3 vec3d = new Vec3((double) node.x - this.mob.getX(), (double) node.y - this.mob.getY(), (double) node.z - this.mob.getZ());
        AABB box = this.mob.getBoundingBox();
        int i = Mth.ceil(vec3d.length() / box.getSize());
        vec3d = vec3d.scale(1.0f / (float) i);
        for (int j = 1; j <= i; ++j) {
            if (!this.checkBoxCollision(box = box.move(vec3d))) continue;
            return false;
        }
        return true;
    }

    protected double getFeetY(BlockPos pos) {
        return VillagerLandPathNodeMaker.getFeetY(this.level, pos);
    }

    public static double getFeetY(BlockGetter world, BlockPos pos) {
        BlockPos blockPos = pos.below();
        VoxelShape voxelShape = world.getBlockState(blockPos).getCollisionShape(world, blockPos);
        return (double) blockPos.getY() + (voxelShape.isEmpty() ? 0.0 : voxelShape.max(Direction.Axis.Y));
    }

    float getPenalty(ExtendedPathNodeType pathNodeType) {
        return getPenalty(mob, pathNodeType);
    }

    private float getPenalty(Mob mob, ExtendedPathNodeType type) {
        return mob.getPathfindingMalus(type.toVanilla()) + type.getBonusPenalty();
    }

    
    protected Node getPathNode(int x, int y, int z, int maxYStep, double prevFeetY, Direction direction, ExtendedPathNodeType nodeType) {
        double h;
        double g;

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double step = this.getFeetY(mutable.set(x, y, z));
        if (step - prevFeetY > 1.125) {
            return null;
        }

        ExtendedPathNodeType pathNodeType = this.getNodeType(this.mob, x, y, z);
        float penalty = getPenalty(pathNodeType);
        double e = (double) this.mob.getBbWidth() / 2.0;

        Node pathNode = null;
        if (penalty >= 0.0f) {
            pathNode = this.getNode(x, y, z);
            pathNode.type = pathNodeType.toVanilla();
            pathNode.costMalus = Math.max(pathNode.costMalus, penalty);
        }

        if (nodeType == ExtendedPathNodeType.FENCE && pathNode != null && pathNode.costMalus >= 0.0f && !this.isBlocked(pathNode)) {
            pathNode = null;
        }

        if (pathNodeType.isWalkable()) {
            return pathNode;
        }

        // Step
        if ((pathNode == null || pathNode.costMalus < 0.0f) &&
                maxYStep > 0 &&
                pathNodeType != ExtendedPathNodeType.FENCE &&
                pathNodeType != ExtendedPathNodeType.UNPASSABLE_RAIL &&
                pathNodeType != ExtendedPathNodeType.TRAPDOOR &&
                pathNodeType != ExtendedPathNodeType.POWDER_SNOW) {
            pathNode = this.getPathNode(x, y + 1, z, maxYStep - 1, prevFeetY, direction, nodeType);
            if (pathNode != null &&
                    (pathNode.type == ExtendedPathNodeType.OPEN.toVanilla() || pathNode.type == ExtendedPathNodeType.WALKABLE.toVanilla()) &&
                    this.mob.getBbWidth() < 1.0f &&
                    this.checkBoxCollision(new AABB((g = (double) (x - direction.getStepX()) + 0.5) - e,
                            VillagerLandPathNodeMaker.getFeetY(this.level, mutable.set(g, y + 1, h = (double) (z - direction.getStepZ()) + 0.5)) + 0.001,
                            h - e,
                            g + e,
                            (double) this.mob.getBbHeight() + VillagerLandPathNodeMaker.getFeetY(this.level, mutable.set(pathNode.x, pathNode.y, (double) pathNode.z)) - 0.002,
                            h + e)
                    )) {
                pathNode = null;
            }
        }

        if (pathNodeType == ExtendedPathNodeType.WATER && !this.canFloat()) {
            if (this.getNodeType(this.mob, x, y - 1, z) != ExtendedPathNodeType.WATER) {
                return pathNode;
            }
            while (y > this.mob.level().getMinBuildHeight()) {
                if ((pathNodeType = this.getNodeType(this.mob, x, --y, z)) == ExtendedPathNodeType.WATER) {
                    pathNode = this.getNode(x, y, z);
                    pathNode.type = pathNodeType.toVanilla();
                    pathNode.costMalus = Math.max(pathNode.costMalus, getPenalty(pathNodeType));
                    continue;
                }
                return pathNode;
            }
        }

        if (pathNodeType == ExtendedPathNodeType.OPEN) {
            int i = 0;
            int j = y;
            while (pathNodeType == ExtendedPathNodeType.OPEN) {
                if (--y < this.mob.level().getMinBuildHeight()) {
                    Node pathNode2 = this.getNode(x, j, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.costMalus = -1.0f;
                    return pathNode2;
                }
                if (i++ >= this.mob.getMaxFallDistance()) {
                    Node pathNode2 = this.getNode(x, y, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.costMalus = -1.0f;
                    return pathNode2;
                }
                pathNodeType = this.getNodeType(this.mob, x, y, z);
                penalty = getPenalty(pathNodeType);
                if (pathNodeType != ExtendedPathNodeType.OPEN && penalty >= 0.0f) {
                    pathNode = this.getNode(x, y, z);
                    pathNode.type = pathNodeType.toVanilla();
                    pathNode.costMalus = Math.max(pathNode.costMalus, penalty);
                    break;
                }
                if (penalty < 0.0f) {
                    Node pathNode2 = this.getNode(x, y, z);
                    pathNode2.type = ExtendedPathNodeType.BLOCKED.toVanilla();
                    pathNode2.costMalus = -1.0f;
                    return pathNode2;
                }
            }
        }

        if (pathNodeType == ExtendedPathNodeType.FENCE) {
            pathNode = this.getNode(x, y, z);
            pathNode.closed = true;
            pathNode.type = pathNodeType.toVanilla();
            pathNode.costMalus = pathNodeType.getDefaultPenalty();
        }

        return pathNode;
    }

    private boolean checkBoxCollision(AABB box) {
        return this.collidedBoxes.computeIfAbsent(box, object -> !this.level.noCollision(this.mob, box));
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z, Mob mob) {
        // Placeholder, not used
        return getExtendedNodeType(world, x, y, z, mob, entityWidth, entityHeight, entityDepth, canOpenDoors, canPassDoors).toVanilla();
    }

    public ExtendedPathNodeType getExtendedNodeType(BlockGetter world, int x, int y, int z, Mob mob, int sizeX, int sizeY, int sizeZ, boolean canOpenDoors, boolean canEnterOpenDoors) {
        EnumSet<ExtendedPathNodeType> enumSet = EnumSet.noneOf(ExtendedPathNodeType.class);
        ExtendedPathNodeType centerPathNodeType = this.findNearbyNodeTypes(world, x, y, z, sizeX, sizeY, sizeZ, canOpenDoors, canEnterOpenDoors, enumSet, mob.blockPosition());
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
    public ExtendedPathNodeType findNearbyNodeTypes(BlockGetter world, int x, int y, int z, int sizeX, int sizeY, int sizeZ, boolean canOpenDoors, boolean canEnterOpenDoors, EnumSet<ExtendedPathNodeType> nearbyTypes, BlockPos pos) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(x, y, z);
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
                    if (Config.getServerConfig().useSmarterDoorAI && blockState.is(BlockTags.FENCE_GATES, state -> state.getBlock() instanceof FenceGateBlock)) {
                        pathNodeType = ExtendedPathNodeType.WALKABLE_DOOR;
                    }

                    if (pathNodeType != ExtendedPathNodeType.DOOR_OPEN) {
                        if (blockState.getBlock() instanceof DoorBlock) {
                            // if we find a door, check that it's adjacent to any of the previously found pressure plates.
                            for (BlockPos adjacent : BlockPos.betweenClosed(l - 1, m - 1, n - 1, l + 1, m + 1, n + 1)) {
                                if (world.getBlockState(adjacent).is(BlockTags.PRESSURE_PLATES)) {
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

    protected ExtendedPathNodeType adjustNodeType(BlockGetter world, boolean canOpenDoors, boolean canEnterOpenDoors, BlockPos pos, ExtendedPathNodeType type) {
        if (type == ExtendedPathNodeType.DOOR_WOOD_CLOSED && canOpenDoors && canEnterOpenDoors) {
            type = ExtendedPathNodeType.WALKABLE_DOOR;
        }
        if (type == ExtendedPathNodeType.DOOR_OPEN && !canEnterOpenDoors) {
            type = ExtendedPathNodeType.BLOCKED;
        }
        if (type == ExtendedPathNodeType.RAIL && !(world.getBlockState(pos).getBlock() instanceof BaseRailBlock) && !(world.getBlockState(pos.below()).getBlock() instanceof BaseRailBlock)) {
            type = ExtendedPathNodeType.UNPASSABLE_RAIL;
        }
        if (type == ExtendedPathNodeType.LEAVES) {
            type = ExtendedPathNodeType.BLOCKED;
        }
        return type;
    }

    private ExtendedPathNodeType getNodeType(Mob entity, BlockPos pos) {
        return this.getNodeType(entity, pos.getX(), pos.getY(), pos.getZ());
    }

    protected ExtendedPathNodeType getNodeType(Mob entity, int x, int y, int z) {
        return this.nodeTypes.computeIfAbsent(BlockPos.asLong(x, y, z), l -> this.getExtendedNodeType(this.level, x, y, z, entity, this.entityWidth, this.entityHeight, this.entityDepth, this.canOpenDoors(), this.canPassDoors()));
    }

    @Override
    public BlockPathTypes getBlockPathType(BlockGetter world, int x, int y, int z) {
        return getExtendedDefaultNodeType(world, x, y, z).toVanilla();
    }

    public ExtendedPathNodeType getExtendedDefaultNodeType(BlockGetter world, int x, int y, int z) {
        return VillagerLandPathNodeMaker.getLandNodeType(world, new BlockPos.MutableBlockPos(x, y, z));
    }

    public static ExtendedPathNodeType getLandNodeType(BlockGetter world, BlockPos.MutableBlockPos pos) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ExtendedPathNodeType pathNodeType = VillagerLandPathNodeMaker.getCommonNodeType(world, pos);
        if (pathNodeType == ExtendedPathNodeType.OPEN && y >= world.getMinBuildHeight() + 1) {
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

    public static ExtendedPathNodeType getNodeTypeFromNeighbors(BlockGetter world, BlockPos.MutableBlockPos pos, ExtendedPathNodeType nodeType) {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        for (int l = -1; l <= 1; ++l) {
            for (int m = -1; m <= 1; ++m) {
                for (int n = -1; n <= 1; ++n) {
                    if (l == 0 && n == 0) continue;
                    pos.set(x + l, y + m, z + n);
                    BlockState blockState = world.getBlockState(pos);
                    if (blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
                        return ExtendedPathNodeType.DANGER_OTHER;
                    }
                    if (VillagerLandPathNodeMaker.inflictsFireDamage(blockState)) {
                        return ExtendedPathNodeType.DANGER_FIRE;
                    }
                    if (!world.getFluidState(pos).is(FluidTags.WATER)) continue;
                    return ExtendedPathNodeType.WATER_BORDER;
                }
            }
        }
        return nodeType;
    }

    protected static ExtendedPathNodeType getCommonNodeType(BlockGetter world, BlockPos pos) {
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
        if (blockState.is(BlockTags.TRAPDOORS) || blockState.is(Blocks.LILY_PAD) || blockState.is(Blocks.BIG_DRIPLEAF)) {
            return ExtendedPathNodeType.TRAPDOOR;
        }
        if (blockState.is(Blocks.POWDER_SNOW)) {
            return ExtendedPathNodeType.POWDER_SNOW;
        }
        if (blockState.is(Blocks.CACTUS) || blockState.is(Blocks.SWEET_BERRY_BUSH)) {
            return ExtendedPathNodeType.DAMAGE_OTHER;
        }
        if (blockState.is(Blocks.HONEY_BLOCK)) {
            return ExtendedPathNodeType.STICKY_HONEY;
        }
        if (blockState.is(Blocks.COCOA)) {
            return ExtendedPathNodeType.COCOA;
        }
        FluidState fluidState = world.getFluidState(pos);
        if (fluidState.is(FluidTags.LAVA)) {
            return ExtendedPathNodeType.LAVA;
        }
        if (VillagerLandPathNodeMaker.inflictsFireDamage(blockState)) {
            return ExtendedPathNodeType.DAMAGE_FIRE;
        }
        if (block instanceof DoorBlock doorBlock) {
            if (blockState.getValue(DoorBlock.OPEN)) {
                return ExtendedPathNodeType.DOOR_OPEN;
            } else {
                return doorBlock.type().canOpenByHand() ? ExtendedPathNodeType.DOOR_WOOD_CLOSED : ExtendedPathNodeType.DOOR_IRON_CLOSED;
            }
        }
        if (block instanceof BaseRailBlock) {
            return ExtendedPathNodeType.RAIL;
        }
        if (block instanceof LeavesBlock) {
            return ExtendedPathNodeType.LEAVES;
        }
        if (blockState.is(BlockTags.FENCES) || blockState.is(BlockTags.WALLS) || (Config.getServerConfig().useSmarterDoorAI && block instanceof FenceGateBlock && !blockState.getValue(FenceGateBlock.OPEN))) {
            return ExtendedPathNodeType.FENCE;
        }
        if (!blockState.isPathfindable(world, pos, PathComputationType.LAND)) {
            return ExtendedPathNodeType.BLOCKED;
        }
        if (fluidState.is(FluidTags.WATER)) {
            return ExtendedPathNodeType.WATER;
        }

        return ExtendedPathNodeType.OPEN;
    }

    public static boolean inflictsFireDamage(BlockState state) {
        return WalkNodeEvaluator.isBurningBlock(state); // todo add custom tag
    }
}