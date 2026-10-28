package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Physical node poses shared by search clearance, movement targets, and arrival. */
public final class PathNodeGeometry {
    static final double FLOOR_EPSILON = 1.0E-3D;

    private PathNodeGeometry() { }

    static Vec3 walkingPosition(BlockGetter level, BlockPos node, Mob mob, double fallbackFloorY) {
        return new Vec3(node.getX() + 0.5D, walkingFloorY(level, node, mob, fallbackFloorY), node.getZ() + 0.5D);
    }

    /** A suspended climber must reach the node itself rather than a walking floor below it. */
    static Vec3 climbingPosition(BlockPos node) {
        return Vec3.atBottomCenterOf(node);
    }

    static AABB bodyAt(Mob mob, Vec3 feet) {
        AABB box = mob.getBoundingBox();
        return box.move(feet.x - (box.minX + box.maxX) * 0.5D,
                feet.y - box.minY, feet.z - (box.minZ + box.maxZ) * 0.5D);
    }

    public static double walkingFloorY(BlockGetter level, BlockPos pos, Mob mob, double floorY) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isPathfindable(PathComputationType.LAND)) return floorY;

        VoxelShape collisionShape = state.getCollisionShape(level, pos, CollisionContext.of(mob));
        if (collisionShape.isEmpty()) return floorY;

        double surfaceY = collisionShape.max(Direction.Axis.Y);
        if (surfaceY <= 0.0D || surfaceY > 1.0D) return floorY;

        AABB mobBox = mob.getBoundingBox();
        double halfWidth = mobBox.getXsize() * 0.5D;
        double halfDepth = mobBox.getZsize() * 0.5D;
        // Keep the support slice thicker than Shapes.EPSILON: an empty slice
        // otherwise makes a vertical door panel appear to cover the whole footprint.
        VoxelShape footprint = Shapes.box(0.5D - halfWidth, Math.max(0.0D, surfaceY - FLOOR_EPSILON),
                0.5D - halfDepth, 0.5D + halfWidth, surfaceY, 0.5D + halfDepth);
        return Shapes.joinIsNotEmpty(footprint, collisionShape, BooleanOp.ONLY_FIRST)
                ? floorY : Math.max(floorY, pos.getY() + surfaceY);
    }
}
