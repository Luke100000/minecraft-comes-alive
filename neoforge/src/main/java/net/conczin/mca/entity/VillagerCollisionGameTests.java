package net.conczin.mca.entity;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

@GameTestHolder(MCA.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillagerCollisionGameTests {
    private static final double EPSILON = 1.0E-9D;
    private static final Method DO_PUSH = findDoPush();

    private VillagerCollisionGameTests() {
    }

    @GameTest(batch = "mca_villager_collision", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void villagerCollisionSettingControlsPush(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        VillagerEntityMCA first = createVillager(helper, feet, 0.25D);
        VillagerEntityMCA second = createVillager(helper, feet, 0.75D);
        boolean previous = Config.SERVER.enableVillagerCollisions.get();

        try {
            Config.SERVER.enableVillagerCollisions.set(true);
            invokeDoPush(first, second);
            helper.assertTrue(
                    horizontalImpulse(first) > EPSILON && horizontalImpulse(second) > EPSILON,
                    "enabled villager collisions did not retain vanilla push"
            );

            first.setDeltaMovement(Vec3.ZERO);
            second.setDeltaMovement(Vec3.ZERO);
            Config.SERVER.enableVillagerCollisions.set(false);
            invokeDoPush(first, second);
            helper.assertTrue(
                    horizontalImpulse(first) <= EPSILON && horizontalImpulse(second) <= EPSILON,
                    "disabled villager collisions still applied a push impulse"
            );
        } finally {
            Config.SERVER.enableVillagerCollisions.set(previous);
        }

        helper.succeed();
    }

    private static VillagerEntityMCA createVillager(GameTestHelper helper, BlockPos feet, double xOffset) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setPos(feet.getX() + xOffset, feet.getY(), feet.getZ() + 0.5D);
        villager.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static double horizontalImpulse(Entity entity) {
        Vec3 movement = entity.getDeltaMovement();
        return movement.x * movement.x + movement.z * movement.z;
    }

    private static Method findDoPush() {
        try {
            Method method = LivingEntity.class.getDeclaredMethod("doPush", Entity.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void invokeDoPush(LivingEntity source, Entity target) {
        try {
            DO_PUSH.invoke(source, target);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new AssertionError("Could not invoke LivingEntity#doPush for collision GameTest", exception);
        }
    }
}
