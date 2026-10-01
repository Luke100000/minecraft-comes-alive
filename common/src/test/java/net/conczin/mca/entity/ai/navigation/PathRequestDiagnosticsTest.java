package net.conczin.mca.entity.ai.navigation;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathRequestDiagnosticsTest {
    @Test
    void benchmarkOptOutDoesNotDisableNormalDevelopmentDiagnostics() {
        assertTrue(PathRequestDiagnostics.shouldCollect(true, false));
        assertFalse(PathRequestDiagnostics.shouldCollect(true, true));
        assertFalse(PathRequestDiagnostics.shouldCollect(false, false));
        assertFalse(PathRequestDiagnostics.shouldCollect(false, true));
    }

    @Test
    void endTickPublishesImmutableWindowAfterEarlierEventsInOrder() {
        List<String> logged = new ArrayList<>();
        var accumulator = new PathRequestDiagnostics.Accumulator();
        var reporter = new PathRequestDiagnostics.Reporter(accumulator, logged::add);
        reporter.start();
        accumulator.recordRequest("villager-a", PathRequestDiagnostics.Origin.SINK,
                "Producer", "BlockPosTracker", "home", "0,64,0", "10,64,10", false, false, false);
        accumulator.recordRecompute("caller", true, "villager-a", 100L,
                "1,64,1", 99L, "10,64,10", 2, 3, true);
        reporter.endTick(100L);
        reporter.log("[MCA Path Recompute] source=block_update entity=villager-a");
        reporter.endTick(199L);
        reporter.endTick(200L);
        reporter.stop(200L);

        assertEquals(3, logged.size());
        assertEquals("[MCA Path Recompute] source=block_update entity=villager-a", logged.get(0));
        assertTrue(logged.get(1).startsWith("[MCA Path Debug] window=100..200 origin=sink producer=Producer"));
        assertTrue(logged.get(1).contains("requests=1"));
        assertTrue(logged.get(2).startsWith("[MCA Path Recompute] window=100..200 source=call caller=caller"));
    }

    @Test
    void slowDiskConsumerDoesNotBlockProducerAndStopDrainsEveryQueuedLine() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<String> logged = new ArrayList<>();
        var reporter = new PathRequestDiagnostics.Reporter(new PathRequestDiagnostics.Accumulator(), line -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("timed out waiting to release logging consumer");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
            logged.add(line);
        });
        reporter.start();
        try {
            reporter.log("block update first");
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofMillis(500), () -> reporter.log("trace call second"));

            FutureTask<Void> stopping = new FutureTask<>(() -> {
                reporter.stop(1L);
                return null;
            });
            Thread stoppingThread = new Thread(stopping);
            stoppingThread.start();
            assertFalse(stopping.isDone(), "shutdown must wait until queued messages are written");
            release.countDown();
            stopping.get(5, TimeUnit.SECONDS);
            assertEquals(List.of("block update first", "trace call second"), logged);
        } finally {
            release.countDown();
        }
    }

    @Test
    void stoppingFlushesPartialWindowAndRestartDoesNotReusePreviousWindow() {
        List<String> logged = new ArrayList<>();
        var accumulator = new PathRequestDiagnostics.Accumulator();
        var reporter = new PathRequestDiagnostics.Reporter(accumulator, logged::add);
        reporter.start();
        reporter.endTick(100L);
        accumulator.recordRequest("villager-a", PathRequestDiagnostics.Origin.SINK,
                "Producer", "BlockPosTracker", "home", "0,64,0", "10,64,10", false, false, false);
        reporter.stop(110L);

        reporter.start();
        reporter.endTick(3L);
        accumulator.recordRequest("villager-b", PathRequestDiagnostics.Origin.SINK,
                "Producer", "BlockPosTracker", "home", "0,64,0", "11,64,11", false, false, false);
        reporter.stop(4L);

        assertEquals(2, logged.size());
        assertTrue(logged.get(0).contains("window=100..110") && logged.get(0).contains("lastEntity=villager-a"));
        assertTrue(logged.get(1).contains("window=3..4") && logged.get(1).contains("lastEntity=villager-b"));
        assertTrue(logged.get(1).contains("requests=1 sameTarget=0"));
    }

    @Test
    void interruptedIdleWorkerStaysAliveToDrainFutureLines() throws Exception {
        List<String> logged = new ArrayList<>();
        var reporter = new PathRequestDiagnostics.Reporter(new PathRequestDiagnostics.Accumulator(), logged::add);
        reporter.start();
        Field workerField = PathRequestDiagnostics.Reporter.class.getDeclaredField("worker");
        workerField.setAccessible(true);
        Thread worker = (Thread) workerField.get(reporter);
        try {
            worker.interrupt();
            worker.join(200L);
            assertTrue(worker.isAlive(), "an unexpected interrupt must not abandon the logging queue");
            reporter.log("event after interrupt");
            reporter.stop(1L);
            assertEquals(List.of("event after interrupt"), logged);
        } finally {
            if (worker.isAlive()) {
                reporter.stop(1L);
            }
        }
    }

    @Test
    void aggregatesRepeatedRecomputesByCallerAndKeepsLatestPathContext() {
        var accumulator = new PathRequestDiagnostics.Accumulator();

        String tick = "net.minecraft.world.entity.ai.navigation.PathNavigation#tick";
        String block = "net.minecraft.server.level.ServerLevel#sendBlockUpdated";
        accumulator.recordRecompute(tick, true, "villager-a", 101L, "-283,98,-1589", 100L,
                "-287,80,-1650", 1, 42, true);
        accumulator.recordRecompute(tick, true, "villager-a", 102L, "-283,98,-1589", 100L,
                "-287,80,-1650", 2, 42, true);
        accumulator.recordRecompute(block, false, "villager-b", 103L, "-283,97,-1589", 103L,
                "-281,96,-1599", 3, 14, false);

        List<String> lines = accumulator.drainRecomputeSummary();
        assertEquals(2, lines.size());
        assertTrue(lines.stream().anyMatch(line -> line.contains("caller=" + tick + " delayed=true calls=2")
                && line.contains("lastEntity=villager-a lastTick=102")
                && line.contains("lastChanged=-283,98,-1589 lastBlockTick=100")
                && line.contains("prePathTarget=-287,80,-1650 prePathIndex=2/42 prePathPartial=true")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("caller=" + block + " delayed=false calls=1")
                && line.contains("lastEntity=villager-b lastTick=103")
                && line.contains("prePathTarget=-281,96,-1599 prePathIndex=3/14 prePathPartial=false")));
        assertTrue(accumulator.drainRecomputeSummary().isEmpty(), "window drain should clear recompute counters");
    }

    @Test
    void identifiesRoutineRecomputeSourcesWithoutStackInspection() {
        assertEquals("net.minecraft.server.level.ServerLevel#sendBlockUpdated",
                PathRequestDiagnostics.selectRecomputeCaller(true, false));
        assertEquals("net.minecraft.world.entity.ai.navigation.PathNavigation#tick",
                PathRequestDiagnostics.selectRecomputeCaller(false, true));
        assertEquals("other", PathRequestDiagnostics.selectRecomputeCaller(false, false));
    }

    @Test
    void recordsSourceRetryDeferralsWithUnchangedOriginAndAge() {
        var accumulator = new PathRequestDiagnostics.Accumulator();

        accumulator.recordDeferredProducerRetry("villager-a", "ExtendedWalkTowardsTask", "minecraft:meeting_point",
                "0,64,0", "0,64,0", "10,64,10", 20L, 0.0D);
        accumulator.recordDeferredProducerRetry("villager-a", "ExtendedWalkTowardsTask", "minecraft:meeting_point",
                "0,64,0", "1,64,0", "10,64,10", 60L, 1.0D);

        List<String> summary = accumulator.drainSummary();
        assertEquals(1, summary.size());
        assertTrue(summary.getFirst().contains("origin=producer producer=ExtendedWalkTowardsTask"));
        assertTrue(summary.getFirst().contains("sourceDeferred=2 previousOrigin=0,64,0 movementSq=1.0 retryAgeTicks=60"));
        assertTrue(accumulator.drainSummary().isEmpty());
    }

    @Test
    void aggregatesRepeatedRequestsAndSearchCostByOriginAndTracker() {
        var accumulator = new PathRequestDiagnostics.Accumulator();
        var sink = PathRequestDiagnostics.Origin.SINK;

        accumulator.recordRequest("villager-a", sink, "ExtendedWalkTowardsTask", "BlockPosTracker", "minecraft:home", "1,64,1", "10,64,10", false, false, false);
        accumulator.recordRequest("villager-a", sink, "ExtendedWalkTowardsTask", "BlockPosTracker", "minecraft:home", "2,64,1", "10,64,10", true, true, true);
        accumulator.recordSearch(sink, "ExtendedWalkTowardsTask", "BlockPosTracker", "minecraft:home", 2_000_000L, false, true, false, 18);
        accumulator.recordSearch(sink, "ExtendedWalkTowardsTask", "BlockPosTracker", "minecraft:home", 3_000_000L, true, false, false, 12);
        accumulator.recordSuppressedExtendedSearch(sink, "ExtendedWalkTowardsTask", "BlockPosTracker", "minecraft:home");

        List<String> summary = accumulator.drainSummary();

        assertEquals(1, summary.size());
        String line = summary.getFirst();
        assertTrue(line.contains("origin=sink producer=ExtendedWalkTowardsTask tracker=BlockPosTracker"));
        assertTrue(line.contains("requests=2 sameTarget=1 failureEvidence=1 activePath=1 activePartial=1"));
        assertTrue(line.contains("searches=2 searchMs=5.000 maxSearchMs=3.000 reachable=1 partial=1 null=0"));
        assertTrue(line.contains("lastEntity=villager-a lastPos=2,64,1 lastTarget=10,64,10"));
        assertTrue(line.contains("destinationMemory=minecraft:home suppressedExtended=1"));
        assertTrue(line.contains("expandedNodes=30 maxExpandedNodes=18"),
                "search reports must expose node work as well as elapsed time");
        assertTrue(accumulator.drainSummary().isEmpty(), "draining should reset the report window");
    }

    @Test
    void separatesHomeAndMourningDestinationsForTheSameProducer() {
        var accumulator = new PathRequestDiagnostics.Accumulator();
        var sink = PathRequestDiagnostics.Origin.SINK;

        accumulator.recordRequest("a", sink, "ExtendedWalkTowardsTask", "BlockPosTracker",
                "minecraft:home", "0,64,0", "10,64,10", false, false, false);
        accumulator.recordRequest("b", sink, "ExtendedWalkTowardsTask", "BlockPosTracker",
                "mca:mourning_position", "0,64,0", "10,64,10", false, false, false);

        List<String> summary = accumulator.drainSummary();
        assertEquals(2, summary.size());
        assertTrue(summary.stream().anyMatch(line -> line.contains("destinationMemory=minecraft:home")));
        assertTrue(summary.stream().anyMatch(line -> line.contains("destinationMemory=mca:mourning_position")));
    }

    @Test
    void producerSelectionSkipsBrainAndBehaviorBuilderInfrastructure() {
        assertEquals("ExtendedWalkTowardsTask", PathRequestDiagnostics.selectProducer(Stream.of(
                "net.minecraft.world.entity.ai.Brain",
                "net.conczin.mca.mixin.MixinBrain",
                "net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder$Instance",
                "net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation",
                "net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask",
                "net.minecraft.world.entity.ai.behavior.OneShot"
        )));
        assertEquals("SetWalkTargetFromBlockMemory", PathRequestDiagnostics.selectProducer(Stream.of(
                "net.minecraft.world.entity.ai.Brain",
                "net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor",
                "net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory"
        )));
    }

    @Test
    void producerSelectionStopsAtFirstMatchingFrame() {
        Stream<String> frames = Stream.concat(
                Stream.of("net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask$1"),
                Stream.generate(() -> {
                    throw new AssertionError("frames below the producer must not be inspected");
                }));

        assertEquals("ExtendedWalkTowardsTask", PathRequestDiagnostics.selectProducer(frames));
    }

    @Test
    void producerSelectionReturnsUnknownWhenNoBehaviorFrameExists() {
        assertEquals("unknown", PathRequestDiagnostics.selectProducer(Stream.of(
                "net.minecraft.world.entity.ai.Brain",
                "net.conczin.mca.mixin.MixinBrain",
                "java.lang.Thread")));
    }

    @Test
    void separatesBlockChangeRecomputationFromCompletedPartialPathChaining() {
        String navigation = "net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation";
        assertEquals("NavigationRecompute", PathRequestDiagnostics.selectNavigationSource(List.of(
                navigation + "#createPath",
                "net.minecraft.world.entity.ai.navigation.PathNavigation#recomputePath",
                navigation + "#recomputePath",
                "net.minecraft.server.level.ServerLevel#sendBlockUpdated"
        )));
        assertEquals("PartialPathChain", PathRequestDiagnostics.selectNavigationSource(List.of(
                navigation + "#createPath",
                navigation + "#chainCompletedStaticWalkTarget",
                navigation + "#tick",
                "net.conczin.mca.entity.VillagerEntityMCA#tick"
        )));
        assertEquals("PathingBlockInteraction", PathRequestDiagnostics.selectNavigationSource(List.of(
                navigation + "#createPath",
                "net.conczin.mca.entity.ai.PathingBlockInteraction#recheckPath",
                "net.conczin.mca.entity.VillagerEntityMCA#tick"
        )));
    }
}
