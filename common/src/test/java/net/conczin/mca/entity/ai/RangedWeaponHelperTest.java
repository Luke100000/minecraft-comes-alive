package net.conczin.mca.entity.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedWeaponHelperTest {
    private static final double EPSILON = 1.0E-3D;

    @Test
    void levelSkeletonUsesMidpointAndArrowPhysics() {
        Vec3 shot = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                2.0D
        );

        assertVector(shot, 10.0D, 0.365D, 0.0D);
    }

    @Test
    void uphillAndDownhillCompensateForActualTrajectory() {
        Vec3 uphill = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, 3.0D, 0.0D),
                2.0D
        );
        Vec3 downhill = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, -2.0D, 0.0D),
                2.0D
        );

        assertVector(uphill, 10.0D, 3.478D, 0.0D);
        assertVector(downhill, 10.0D, -1.611D, 0.0D);
    }

    @Test
    void spiderAndCaveSpiderUseTheirActualBodyMidpoints() {
        Vec3 spider = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                0.9D
        );
        Vec3 caveSpider = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                0.5D
        );

        assertVector(spider, 10.0D, -0.186D, 0.0D);
        assertVector(caveSpider, 10.0D, -0.385D, 0.0D);
    }

    @Test
    void zeroHorizontalDistanceProducesFiniteMidpointVector() {
        Vec3 shot = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(0.0D, 3.0D, 0.0D),
                2.0D
        );

        assertTrue(Double.isFinite(shot.y));
        assertVector(shot, 0.0D, 2.5D, 0.0D);
    }

    private static void assertVector(Vec3 actual, double x, double y, double z) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
