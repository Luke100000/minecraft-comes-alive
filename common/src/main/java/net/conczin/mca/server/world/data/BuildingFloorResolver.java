package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/**
 * Resolves player/query positions onto physical floors without changing X/Z.
 * Floor selection is gravity-like: current Y first, then downward only.
 */
final class BuildingFloorResolver {
    private static final int MAX_LOCAL_DROP = 4;
    private static final int MAX_LOCAL_CEILING_SEARCH = 16;

    private BuildingFloorResolver() {
    }

    static Optional<ResolvedFloor> resolve(Level world, BlockPos source, Building structureRoot) {
        BlockPos.MutableBlockPos supportCursor = new BlockPos.MutableBlockPos();
        if (structureRoot != null && !structureRoot.getFloorRegions().isEmpty()) {
            int minY = structureRoot.getRawPos0().getY();
            for (int y = source.getY(); y >= minY; y--) {
                Building.FloorBand band = structureRoot.resolveFloorBand(y).orElse(null);
                if (band != null && isSupported(
                        world, source.getX(), y, source.getZ(), supportCursor)) {
                    return Optional.of(new ResolvedFloor(y, band.anchorY(), band.ceilingY()));
                }
            }
            return Optional.empty();
        }

        for (int drop = 0; drop <= MAX_LOCAL_DROP; drop++) {
            int floorY = source.getY() - drop;
            if (isSupported(world, source.getX(), floorY, source.getZ(), supportCursor)) {
                return Optional.of(new ResolvedFloor(
                        floorY, floorY, floorY + MAX_LOCAL_CEILING_SEARCH));
            }
        }
        return Optional.empty();
    }

    static boolean isSupported(Level world, int x, int floorY, int z) {
        return isSupported(world, x, floorY, z, new BlockPos.MutableBlockPos());
    }

    static boolean isSupported(Level world,
                               int x,
                               int floorY,
                               int z,
                               BlockPos.MutableBlockPos cursor) {
        cursor.set(x, floorY - 1, z);
        BlockState supportState = world.getBlockState(cursor);
        var collisionShape = supportState.getCollisionShape(world, cursor);
        if (collisionShape.isEmpty()) {
            return false;
        }

        double width = collisionShape.max(Direction.Axis.X) - collisionShape.min(Direction.Axis.X);
        double depth = collisionShape.max(Direction.Axis.Z) - collisionShape.min(Direction.Axis.Z);
        return width * depth >= 0.25D;
    }

    record ResolvedFloor(int physicalY, int semanticY, int ceilingY) {
    }
}
