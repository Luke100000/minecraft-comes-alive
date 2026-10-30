package net.conczin.mca.entity.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedWeaponHelperTest {
    private static final double EPSILON = 1.0E-3D;

    @Test
    void lateralMovingSmallTargetReceivesAnInterceptingShot() {
        Vec3 launch = new Vec3(0, 1.5, 0);
        Vec3 target = new Vec3(12, 0, 0);
        Vec3 motion = new Vec3(0, 0, 0.2);
        Vec3 shot = RangedWeaponHelper.solveBowShot(launch, target, 0.9, motion).orElseThrow().direction();
        Vec3 position = launch;
        Vec3 velocity = shot.normalize().scale(1.6);
        double closest = Double.POSITIVE_INFINITY;
        for (int tick = 1; tick <= 20; tick++) {
            position = position.add(velocity);
            velocity = velocity.scale(0.99).subtract(0, 0.05, 0);
            Vec3 bodyCenter = target.add(motion.scale(tick)).add(0, 0.45, 0);
            closest = Math.min(closest, position.distanceTo(bodyCenter));
        }
        assertTrue(closest < 0.5, "nominal shot missed moving small target by " + closest);
    }

    @Test
    void impossibleShotDoesNotInventAValidTrajectory() {
        assertTrue(RangedWeaponHelper.solveBowShot(Vec3.ZERO, new Vec3(1000, 1000, 0), 2, Vec3.ZERO).isEmpty());
        assertTrue(RangedWeaponHelper.solveBowShot(Vec3.ZERO, new Vec3(Double.NaN, 0, 0), 2, Vec3.ZERO).isEmpty());
    }

    @Test
    void nominalFlightMeetsBodyAcrossShortSteepAndNearlyVerticalShots() {
        Vec3 launch = new Vec3(0, 1.5, 0);
        for (double distance : new double[]{0, 0.01, 3, 8, 12}) {
            for (double height : new double[]{-5, -2, 0, 2, 5}) {
                Vec3 target = new Vec3(distance, height, 0);
                var shot = RangedWeaponHelper.solveBowShot(launch, target, 0.9, Vec3.ZERO).orElseThrow();
                Vec3 position = launch;
                Vec3 velocity = shot.direction().normalize().scale(1.6F);
                for (int tick = 0; tick < Math.ceil(shot.flightTicks()); tick++) {
                    position = position.add(velocity.scale(Math.min(1, shot.flightTicks() - tick)));
                    velocity = velocity.scale(0.99).subtract(0, 0.05, 0);
                }
                assertTrue(position.distanceTo(target.add(0, 0.45, 0)) < 1.0E-4,
                        "nominal projectile missed body at distance=" + distance + ", height=" + height);
            }
        }
    }

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
