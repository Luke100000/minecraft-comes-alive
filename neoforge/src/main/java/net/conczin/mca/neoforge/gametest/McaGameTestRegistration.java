package net.conczin.mca.neoforge.gametest;

import com.mojang.serialization.MapCodec;
import net.conczin.mca.MCA;
import net.conczin.mca.block.TombstoneBlockGameTests;
import net.conczin.mca.entity.VillagerBedAlignmentGameTests;
import net.conczin.mca.entity.VillagerCarryPositionGameTests;
import net.conczin.mca.entity.VillagerChildSuffocationGameTests;
import net.conczin.mca.entity.VillagerConversionGameTests;
import net.conczin.mca.entity.VillagerRecoveryFoodGameTests;
import net.conczin.mca.entity.VillagerVoicePitchGameTests;
import net.conczin.mca.entity.ai.ChoreToolMatchingGameTests;
import net.conczin.mca.entity.ai.ResidencyGoHomeGameTests;
import net.conczin.mca.entity.ai.ResidencySetHomeGameTests;
import net.conczin.mca.entity.ai.brain.ForcedHomeOccupiedBedGameTests;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensorGameTests;
import net.conczin.mca.entity.ai.brain.sensor.VillagerMCABabiesSensorGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherArrowFriendlyFireGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherBowTrajectoryGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherCombatMovementGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ArcherSpiderCombatGameTests;
import net.conczin.mca.entity.ai.brain.tasks.DoorInteractionGameTests;
import net.conczin.mca.entity.ai.brain.tasks.EnterBuildingGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedFindPointOfInterestTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.GuardEquipmentGameTests;
import net.conczin.mca.entity.ai.brain.tasks.HomelessShelterGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ValidateNearbyPoiGameTests;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.AbstractChoreTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.ChoppingTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.ChorePathfindingGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.FishingTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.chore.HarvestingTaskGameTests;
import net.conczin.mca.entity.ai.navigation.AutonomousPathfindingGameTests;
import net.conczin.mca.entity.ai.navigation.FenceGateInteractionGameTests;
import net.conczin.mca.entity.ai.navigation.HomeArrivalGameTests;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigationGameTests;
import net.conczin.mca.entity.ai.navigation.MCAWalkNodeEvaluatorLookupGameTests;
import net.conczin.mca.entity.ai.navigation.McaSparkFullBrainProfileGameTests;
import net.conczin.mca.entity.ai.navigation.McaSparkPathProfileGameTests;
import net.conczin.mca.entity.ai.navigation.NavigationRecoveryGameTests;
import net.conczin.mca.entity.ai.navigation.PathRetryLifecycleGameTests;
import net.conczin.mca.entity.ai.navigation.StairWallPathfindingGameTests;
import net.conczin.mca.server.DestinyLocationResolverGameTests;
import net.conczin.mca.server.world.data.CopiedOpenHouseGameTests;
import net.conczin.mca.server.world.data.FloorScannerGameTests;
import net.conczin.mca.server.world.data.ReportedFloorInteractionGameTests;
import net.conczin.mca.server.world.data.ReportedStairHouseGameTests;
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
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/** Converts the 1.21 annotation-based MCA GameTests to the 26.x registry model. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class McaGameTestRegistration {
    private static final String SELECTOR_PROPERTY = "mca.gametest.select";
    private static final Identifier LEGACY_AIR_TEMPLATE = Identifier.withDefaultNamespace("bastion/blocks/air");
    private static final Identifier ISOLATED_AIR_TEMPLATE = MCA.locate("ported_1_21_1/isolated_air");
    private static final List<Class<?>> TEST_CLASSES = List.of(
            DestinyLocationResolverGameTests.class,
            TombstoneBlockGameTests.class,
            VillagerBedAlignmentGameTests.class,
            VillagerCarryPositionGameTests.class,
            VillagerChildSuffocationGameTests.class,
            VillagerConversionGameTests.class,
            VillagerRecoveryFoodGameTests.class,
            VillagerVoicePitchGameTests.class,
            ChoreToolMatchingGameTests.class,
            ResidencyGoHomeGameTests.class,
            ResidencySetHomeGameTests.class,
            ForcedHomeOccupiedBedGameTests.class,
            GuardEnemiesSensorGameTests.class,
            VillagerMCABabiesSensorGameTests.class,
            ArcherArrowFriendlyFireGameTests.class,
            ArcherBowTrajectoryGameTests.class,
            ArcherCombatMovementGameTests.class,
            ArcherSpiderCombatGameTests.class,
            DoorInteractionGameTests.class,
            EnterBuildingGameTests.class,
            ExtendedFindPointOfInterestTaskGameTests.class,
            ExtendedWalkTowardsTaskGameTests.class,
            GuardEquipmentGameTests.class,
            HomelessShelterGameTests.class,
            ValidateNearbyPoiGameTests.class,
            WanderOrTeleportToTargetTaskGameTests.class,
            AbstractChoreTaskGameTests.class,
            ChoppingTaskGameTests.class,
            ChorePathfindingGameTests.class,
            FenceGateInteractionGameTests.class,
            AutonomousPathfindingGameTests.class,
            HomeArrivalGameTests.class,
            MCAGroundPathNavigationGameTests.class,
            MCAWalkNodeEvaluatorLookupGameTests.class,
            McaSparkFullBrainProfileGameTests.class,
            McaSparkPathProfileGameTests.class,
            NavigationRecoveryGameTests.class,
            PathRetryLifecycleGameTests.class,
            StairWallPathfindingGameTests.class,
            FishingTaskGameTests.class,
            HarvestingTaskGameTests.class,
            CopiedOpenHouseGameTests.class,
            FloorScannerGameTests.class,
            ReportedFloorInteractionGameTests.class,
            ReportedStairHouseGameTests.class,
            VillageMourningGameTests.class
    );

    private McaGameTestRegistration() {
    }

    @SubscribeEvent
    public static void registerEnvironmentDefinitionTypes(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.TEST_ENVIRONMENT_DEFINITION_TYPE) {
            event.register(
                    Registries.TEST_ENVIRONMENT_DEFINITION_TYPE,
                    MCA.locate("batch_teardown"),
                    () -> BatchTeardownEnvironment.CODEC
            );
        }
    }

    @SubscribeEvent
    public static void register(RegisterGameTestsEvent event) {
        String selector = System.getProperty(SELECTOR_PROPERTY);
        if (selector == null || selector.isBlank()) {
            registerClasses(event, TEST_CLASSES);
            return;
        }

        Selection selection = parseSelection(selector);
        Class<?> testClass = findClass(selection.className());
        if (selection.methodName() != null) {
            findGameTestMethod(testClass, selection.methodName());
        }
        MCA.LOGGER.info("[MCA GameTest] registering focused selection {}", selector);
        registerClasses(event, List.of(testClass), selection.methodName());
    }

    public static void registerClasses(RegisterGameTestsEvent event, Collection<Class<?>> testClasses) {
        registerClasses(event, testClasses, null);
    }

    private static void registerClasses(RegisterGameTestsEvent event, Collection<Class<?>> testClasses,
                                        String selectedMethodName) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        Map<String, List<Method>> teardownMethods = new HashMap<>();

        for (Class<?> testClass : testClasses) {
            for (Method method : testClass.getDeclaredMethods()) {
                AfterBatch afterBatch = method.getAnnotation(AfterBatch.class);
                if (afterBatch == null) continue;
                if (!Modifier.isStatic(method.getModifiers())) {
                    throw new IllegalStateException("MCA AfterBatch callback must be static: " + method);
                }
                teardownMethods.computeIfAbsent(afterBatch.batch(), ignored -> new ArrayList<>()).add(method);
            }
        }

        for (Class<?> testClass : testClasses) {
            for (Method method : testClass.getDeclaredMethods()) {
                GameTest metadata = method.getAnnotation(GameTest.class);
                if (metadata == null) continue;
                if (selectedMethodName != null && !method.getName().equalsIgnoreCase(selectedMethodName)) continue;
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
                                teardownMethods.containsKey(batch)
                                        ? new BatchTeardownEnvironment(teardownMethods.get(batch))
                                        : new TestEnvironmentDefinition.AllOf()
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
                        Level.OVERWORLD,
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

    private static Selection parseSelection(String selector) {
        String value = selector.trim();
        int separator = value.indexOf('#');
        if (separator < 0) {
            if (value.isEmpty()) {
                throw invalidSelector(selector);
            }
            return new Selection(value, null);
        }
        if (separator == 0 || separator == value.length() - 1 || separator != value.lastIndexOf('#')) {
            throw invalidSelector(selector);
        }
        return new Selection(value.substring(0, separator), value.substring(separator + 1));
    }

    private static Class<?> findClass(String requestedName) {
        Class<?> selected = null;
        for (Class<?> testClass : TEST_CLASSES) {
            boolean matches = testClass.getSimpleName().equalsIgnoreCase(requestedName)
                    || testClass.getName().equalsIgnoreCase(requestedName);
            if (!matches) continue;
            if (selected != null) {
                throw new IllegalArgumentException("Ambiguous MCA GameTest class '" + requestedName + "'");
            }
            selected = testClass;
        }
        if (selected == null) {
            throw new IllegalArgumentException("Unknown MCA GameTest class '" + requestedName + "'");
        }
        return selected;
    }

    private static Method findGameTestMethod(Class<?> testClass, String requestedName) {
        Method selected = null;
        for (Method method : testClass.getDeclaredMethods()) {
            if (method.getAnnotation(GameTest.class) == null || !method.getName().equalsIgnoreCase(requestedName)) continue;
            if (selected != null) {
                throw new IllegalArgumentException(
                        "Ambiguous MCA GameTest method '" + requestedName + "' in " + testClass.getSimpleName());
            }
            selected = method;
        }
        if (selected == null) {
            throw new IllegalArgumentException(
                    "Unknown MCA GameTest method '" + requestedName + "' in " + testClass.getSimpleName());
        }
        return selected;
    }

    private static IllegalArgumentException invalidSelector(String selector) {
        return new IllegalArgumentException(
                "Invalid mcaGameTest selector '" + selector + "'; expected ClassName or ClassName#methodName");
    }

    private record Selection(String className, String methodName) {
    }

    private static void invokeBatchCallback(Method method, ServerLevel level) {
        try {
            method.invoke(null, level);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new RuntimeException(cause);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private static final class BatchTeardownEnvironment implements TestEnvironmentDefinition<Unit> {
        private static final MapCodec<BatchTeardownEnvironment> CODEC =
                MapCodec.unit(() -> new BatchTeardownEnvironment(List.of()));

        private final List<Method> callbacks;

        private BatchTeardownEnvironment(List<Method> callbacks) {
            this.callbacks = List.copyOf(callbacks);
        }

        @Override
        public Unit setup(ServerLevel level) {
            return Unit.INSTANCE;
        }

        @Override
        public void teardown(ServerLevel level, Unit saveData) {
            callbacks.forEach(callback -> invokeBatchCallback(callback, level));
        }

        @Override
        public MapCodec<BatchTeardownEnvironment> codec() {
            return CODEC;
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
