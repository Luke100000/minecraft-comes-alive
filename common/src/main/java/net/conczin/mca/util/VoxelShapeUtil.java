package net.conczin.mca.util;

import java.util.function.Function;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public interface VoxelShapeUtil {
    Vec3 CENTER = new Vec3(0.5, 0, 0.5);

    static Function<Direction, VoxelShape> rotator(VoxelShape base) {
        return d -> rotate(base, d);
    }

    static VoxelShape rotate(VoxelShape shape, Direction direction) {
        if (direction.toYRot() == 0) {
            return shape;
        }
        float angle = (float)(-direction.toYRot() * Math.PI / 180.0);
        return Shapes.or(Shapes.empty(), shape.toAabbs().stream()
            .map(box -> {
              //These first two are enough for orthogonal rotations
                Vec3 a = rotate(box.minX, box.minZ, angle);
                Vec3 b = rotate(box.maxX, box.maxZ, angle);
                //These cover odd angles
                Vec3 c = rotate(box.minX, box.maxZ, angle);
                Vec3 d = rotate(box.maxX, box.minZ, angle);

                return Shapes.create(new AABB(
                        Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)),
                        box.minY,
                        Math.min(Math.min(a.z, b.z), Math.min(c.z, d.z)),
                        Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)),
                        box.maxY,
                        Math.max(Math.max(a.z, b.z), Math.max(c.z, d.z))
                ));
            })
            .toArray(VoxelShape[]::new));
    }

    static Vec3 rotate(double x, double z, float angle) {
        return new Vec3(x, 0, z).subtract(CENTER).yRot(angle).add(CENTER);
    }
}
