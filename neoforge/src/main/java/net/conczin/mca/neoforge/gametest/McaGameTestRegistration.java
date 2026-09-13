package net.conczin.mca.neoforge.gametest;

import com.mojang.serialization.MapCodec;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensorGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherCombatMovementGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.HarvestingTaskGameTests;
import net.conczin.mca.server.world.data.FloorScannerGameTests;
import net.conczin.mca.server.world.data.VillageMourningGameTests;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Locale;

/** Converts the 1.21 annotation-based MCA GameTests to the 26.x registry model. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class McaGameTestRegistration {
    private static final List<Class<?>> TEST_CLASSES = List.of(
            GuardEnemiesSensorGameTests.class,
            ArcherCombatMovementGameTests.class,
            FishingTaskGameTests.class,
            HarvestingTaskGameTests.class,
            FloorScannerGameTests.class,
            VillageMourningGameTests.class
    );

    private McaGameTestRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(
                MCA.locate("ported_1_21_1"), new TestEnvironmentDefinition.AllOf());

        for (Class<?> testClass : TEST_CLASSES) {
            for (Method method : testClass.getDeclaredMethods()) {
                GameTest metadata = method.getAnnotation(GameTest.class);
                if (metadata == null) continue;
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("MCA GameTest must be static: " + method);
                }

                Identifier structure = Identifier.fromNamespaceAndPath(
                        metadata.templateNamespace(), metadata.template());
                TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                        environment,
                        structure,
                        metadata.timeoutTicks(),
                        Math.toIntExact(metadata.setupTicks()),
                        metadata.required(),
                        rotation(metadata.rotationSteps()),
                        metadata.manualOnly(),
                        metadata.attempts(),
                        metadata.requiredSuccesses(),
                        metadata.skyAccess(),
                        0
                );
                String path = testClass.getSimpleName().toLowerCase(Locale.ROOT)
                        + "/" + method.getName().toLowerCase(Locale.ROOT);
                event.registerTest(MCA.locate(path), new PortedGameTest(data, method));
            }
        }
    }

    private static Rotation rotation(int steps) {
        return switch (Math.floorMod(steps, 4)) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static final class PortedGameTest extends GameTestInstance {
        private final Method method;

        private PortedGameTest(TestData<Holder<TestEnvironmentDefinition<?>>> info, Method method) {
            super(info);
            this.method = method;
        }

        @Override
        public void run(GameTestHelper helper) {
            try {
                method.invoke(null, helper);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) throw runtimeException;
                if (cause instanceof Error error) throw error;
                throw new RuntimeException(cause);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return FunctionGameTestInstance.CODEC;
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("MCA ported GameTest");
        }
    }
}
