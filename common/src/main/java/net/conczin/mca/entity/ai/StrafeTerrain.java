package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.ai.navigation.PathNodeGeometry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/** Local footing for lateral movement; vanilla still owns travel, stepping and jumps. */
public final class StrafeTerrain {
    private static final double EPSILON = 1.0E-5;
    private static final double MAX_DROP = 1.0;
    private static final double AIRBORNE_FLOOR_SEARCH = 2.5;

    private StrafeTerrain() { }

    @Nullable
    public static Vec3 findLanding(Mob mob, Vec3 offset) {
        AABB body = mob.getBoundingBox();
        double floor = findSupport(mob, body, mob.getY() - AIRBORNE_FLOOR_SEARCH, mob.getY() + EPSILON);
        if (!Double.isFinite(floor)) return null;

        AABB destination = body.move(offset.x, 0, offset.z);
        double height = findSupport(mob, destination, floor - MAX_DROP - EPSILON,
                floor + PathNodeGeometry.jumpHeight(mob) + EPSILON);
        if (!Double.isFinite(height)) return null;
        Vec3 feet = new Vec3(mob.getX() + offset.x, height, mob.getZ() + offset.z);
        PathType type = mob.getNavigation().getNodeEvaluator().getPathType(mob,
                BlockPos.containing(feet.x, Math.ceil(height - EPSILON), feet.z));
        if (type != PathType.WALKABLE && type != PathType.WALKABLE_DOOR && type != PathType.DOOR_OPEN && type != PathType.OPEN) return null;

        double rise = Math.max(0, height - mob.getY());
        // Check the space above the current body and across the raised movement,
        // not through the step's solid face. Physics supplies the actual jump arc.
        if (!mob.level().noCollision(mob, body.expandTowards(0, rise, 0))
                || !mob.level().noCollision(mob, body.move(0, rise, 0).expandTowards(offset.x, 0, offset.z))
                || !mob.level().noCollision(mob, destination.move(0, height - mob.getY(), 0))) return null;
        return feet;
    }

    private static double findSupport(Mob mob, AABB footprint, double minY, double maxY) {
        AABB search = new AABB(footprint.minX + EPSILON, minY - EPSILON, footprint.minZ + EPSILON,
                footprint.maxX - EPSILON, maxY + EPSILON, footprint.maxZ - EPSILON);
        double[] highest = {Double.NEGATIVE_INFINITY};
        Shapes.DoubleLineConsumer support = (x0, y0, z0, x1, y1, z1) -> {
            if (y1 >= minY && y1 <= maxY && y1 > highest[0]
                    && x1 > search.minX && x0 < search.maxX
                    && z1 > search.minZ && z0 < search.maxZ) {
                highest[0] = y1;
            }
        };
        for (VoxelShape shape : mob.level().getBlockCollisions(mob, search)) {
            shape.forAllBoxes(support);
        }
        return highest[0];
    }
}
