package net.conczin.mca.gametest;

import net.conczin.mca.MCA;
import net.conczin.mca.block.TombstoneBlockGameTests;
import net.conczin.mca.dialogue.DialogueConditionGameTests;
import net.conczin.mca.dialogue.DialogueActionGameTests;
import net.conczin.mca.dialogue.DialogueEngineGameTests;
import net.conczin.mca.entity.VillagerBedAlignmentGameTests;
import net.conczin.mca.entity.VillagerCarryPositionGameTests;
import net.conczin.mca.entity.VillagerChildSuffocationGameTests;
import net.conczin.mca.entity.VillagerVoicePitchGameTests;
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
import net.conczin.mca.entity.ai.brain.tasks.GuardEquipmentGameTests;
import net.conczin.mca.entity.ai.brain.tasks.HomelessShelterGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ShelterDistributionGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedFindPointOfInterestTaskGameTests;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTaskGameTests;
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
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.lang.reflect.Method;
import java.util.List;

/** Central GameTest registration with an optional strict class or method selector for development runs. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class McaGameTestsRegistration {
    private static final String SELECTOR_PROPERTY = "mca.gametest.select";

    private static final List<Class<?>> GAME_TEST_CLASSES = List.of(
            TombstoneBlockGameTests.class,
            DialogueConditionGameTests.class,
            DialogueActionGameTests.class,
            DialogueEngineGameTests.class,
            VillagerBedAlignmentGameTests.class,
            VillagerCarryPositionGameTests.class,
            VillagerChildSuffocationGameTests.class,
            VillagerVoicePitchGameTests.class,
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
            GuardEquipmentGameTests.class,
            HomelessShelterGameTests.class,
            ShelterDistributionGameTests.class,
            ExtendedFindPointOfInterestTaskGameTests.class,
            ExtendedWalkTowardsTaskGameTests.class,
            ValidateNearbyPoiGameTests.class,
            WanderOrTeleportToTargetTaskGameTests.class,
            AbstractChoreTaskGameTests.class,
            ChoppingTaskGameTests.class,
            ChorePathfindingGameTests.class,
            FishingTaskGameTests.class,
            HarvestingTaskGameTests.class,
            AutonomousPathfindingGameTests.class,
            FenceGateInteractionGameTests.class,
            HomeArrivalGameTests.class,
            MCAGroundPathNavigationGameTests.class,
            MCAWalkNodeEvaluatorLookupGameTests.class,
            McaSparkFullBrainProfileGameTests.class,
            McaSparkPathProfileGameTests.class,
            NavigationRecoveryGameTests.class,
            PathRetryLifecycleGameTests.class,
            StairWallPathfindingGameTests.class,
            DestinyLocationResolverGameTests.class,
            CopiedOpenHouseGameTests.class,
            FloorScannerGameTests.class,
            ReportedFloorInteractionGameTests.class,
            ReportedStairHouseGameTests.class,
            VillageMourningGameTests.class
    );

    private McaGameTestsRegistration() {
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        if (!Boolean.getBoolean("mca.gametest.enabled")) {
            return;
        }

        String selector = System.getProperty(SELECTOR_PROPERTY);
        if (selector == null || selector.isBlank()) {
            GAME_TEST_CLASSES.forEach(event::register);
            return;
        }

        Selection selection = parseSelection(selector);
        Class<?> testClass = findClass(selection.className());
        if (selection.methodName() == null) {
            MCA.LOGGER.info("[MCA GameTest] registering focused class {}", testClass.getSimpleName());
            event.register(testClass);
            return;
        }

        Method testMethod = findGameTestMethod(testClass, selection.methodName());
        registerBatchHooks(event, testClass, testMethod.getAnnotation(GameTest.class).batch());
        event.register(testMethod);
        MCA.LOGGER.info("[MCA GameTest] registering focused test {}#{}", testClass.getSimpleName(), testMethod.getName());
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
        for (Class<?> testClass : GAME_TEST_CLASSES) {
            boolean matches = testClass.getSimpleName().equalsIgnoreCase(requestedName)
                    || testClass.getName().equalsIgnoreCase(requestedName);
            if (!matches) {
                continue;
            }
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
            if (!method.isAnnotationPresent(GameTest.class) || !method.getName().equalsIgnoreCase(requestedName)) {
                continue;
            }
            if (selected != null) {
                throw new IllegalArgumentException(
                        "Ambiguous MCA GameTest method '" + requestedName + "' in " + testClass.getSimpleName()
                );
            }
            selected = method;
        }
        if (selected == null) {
            throw new IllegalArgumentException(
                    "Unknown MCA GameTest method '" + requestedName + "' in " + testClass.getSimpleName()
            );
        }
        return selected;
    }

    private static void registerBatchHooks(RegisterGameTestsEvent event, Class<?> testClass, String batch) {
        if (batch.isBlank()) {
            return;
        }
        for (Method method : testClass.getDeclaredMethods()) {
            BeforeBatch beforeBatch = method.getAnnotation(BeforeBatch.class);
            if (beforeBatch != null && beforeBatch.batch().equals(batch)) {
                event.register(method);
            }
            AfterBatch afterBatch = method.getAnnotation(AfterBatch.class);
            if (afterBatch != null && afterBatch.batch().equals(batch)) {
                event.register(method);
            }
        }
    }

    private static IllegalArgumentException invalidSelector(String selector) {
        return new IllegalArgumentException(
                "Invalid mcaGameTest selector '" + selector + "'; expected ClassName or ClassName#methodName"
        );
    }

    private record Selection(String className, String methodName) {
    }
}
