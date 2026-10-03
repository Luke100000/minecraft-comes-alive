package net.conczin.mca.benchmark;

import net.conczin.mca.MCA;
import net.conczin.mca.neoforge.gametest.McaGameTestRegistration;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;

import java.util.List;

/** Register A/B fixtures only in the explicitly requested benchmark run. */
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class ABBenchmarkRegistration {
    private ABBenchmarkRegistration() {
    }

    @SubscribeEvent
    public static void registerTests(RegisterGameTestsEvent event) {
        if (Boolean.getBoolean("mca.pathSearchBenchmark")) {
            MCA.LOGGER.info("[MCA Search Probe] registering autonomous search experiments");
            McaGameTestRegistration.registerClasses(event, List.of(PathSearchBudgetGameTests.class));
            return;
        }
        if (!Boolean.getBoolean("mca.pathABBenchmark")) {
            return;
        }
        MCA.LOGGER.info("[MCA AB] registering benchmark GameTests");
        if (Boolean.getBoolean("mca.pathABOpenOnly")) {
            McaGameTestRegistration.registerClasses(event, List.of(OpenPathABGameTests.class));
        } else {
            McaGameTestRegistration.registerClasses(event, List.of(PathfindingABGameTests.class));
        }
    }
}
