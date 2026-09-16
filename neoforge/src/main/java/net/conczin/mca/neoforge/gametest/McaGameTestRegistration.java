package net.conczin.mca.neoforge.gametest;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerRecoveryFoodGameTests;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensorGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherArrowFriendlyFireGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherCombatMovementGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.HarvestingTaskGameTests;
import net.conczin.mca.entity.ai.navigation.FenceGateInteractionGameTests;
import net.conczin.mca.server.world.data.FloorScannerGameTests;
import net.conczin.mca.server.world.data.VillageMourningGameTests;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Converts the 1.21 annotation-based MCA GameTests to the 26.x registry model. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class McaGameTestRegistration {
    private static final Identifier LEGACY_AIR_TEMPLATE = Identifier.withDefaultNamespace("bastion/blocks/air");
    private static final Identifier ISOLATED_AIR_TEMPLATE = MCA.locate("ported_1_21_1/isolated_air");
    private static final List<Class<?>> TEST_CLASSES = List.of(
            VillagerRecoveryFoodGameTests.class,
            GuardEnemiesSensorGameTests.class,
            ArcherArrowFriendlyFireGameTests.class,
            ArcherCombatMovementGameTests.class,
            FenceGateInteractionGameTests.class,
            FishingTaskGameTests.class,
            HarvestingTaskGameTests.class,
            FloorScannerGameTests.class,
            VillageMourningGameTests.class
    );

    private McaGameTestRegistration() {
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();

        for (Class<?> testClass : TEST_CLASSES) {
            for (Method method : testClass.getDeclaredMethods()) {
                GameTest metadata = method.getAnnotation(GameTest.class);
                if (metadata == null) continue;
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("MCA GameTest must be static: " + method);
                }

                // 1.21 grouped tests by @GameTest.batch. In 26.x that grouping moved
                // into TestData.environment, so preserve it instead of running every
                // ported test concurrently in one environment.
                Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(
                        metadata.batch(),
                        batch -> event.registerEnvironment(
                                MCA.locate("ported_1_21_1/" + batch.toLowerCase(Locale.ROOT)),
                                new TestEnvironmentDefinition.AllOf()
                        )
                );

                Identifier sourceStructure = Identifier.fromNamespaceAndPath(
                        metadata.templateNamespace(), metadata.template());
                // The legacy one-block air template only described the annotation fixture,
                // while these MCA tests construct much larger worlds around it. 26.x places
                // batches from the registered template bounds, so give the ported air tests
                // a wider empty footprint instead of using isotropic TestData padding (which
                // also shifts the fixture vertically in 26.x).
                Identifier structure = sourceStructure.equals(LEGACY_AIR_TEMPLATE)
                        ? ISOLATED_AIR_TEMPLATE
                        : sourceStructure;
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
                Identifier testId = MCA.locate(path);
                ResourceKey<Consumer<GameTestHelper>> functionKey = ResourceKey.create(Registries.TEST_FUNCTION, testId);
                event.registerTest(testId, new PortedGameTest(functionKey, data, method));
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

    private static final class PortedGameTest extends FunctionGameTestInstance {
        private final Method method;

        private PortedGameTest(ResourceKey<Consumer<GameTestHelper>> functionKey,
                               TestData<Holder<TestEnvironmentDefinition<?>>> info,
                               Method method) {
            super(functionKey, info);
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
        protected MutableComponent typeDescription() {
            return Component.literal("MCA ported GameTest");
        }
    }
}
