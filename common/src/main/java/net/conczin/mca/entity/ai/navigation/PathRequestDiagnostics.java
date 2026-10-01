package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Development-only counters for correlating expensive path searches with the
 * movement request that caused them. This deliberately observes existing state
 * without changing retry or navigation behavior.
 */
public final class PathRequestDiagnostics {
    private static final long REPORT_INTERVAL_TICKS = 100L;
    /** Opt into the original per-call stack trace and log when investigating an individual villager. */
    private static final boolean TRACE_RECOMPUTE_CALLS = Boolean.getBoolean("mca.pathDebug.traceRecomputeCalls");
    private static final boolean BENCHMARK_WITHOUT_DIAGNOSTICS =
            Boolean.getBoolean("mca.pathDebug.benchmarkWithoutDiagnostics");
    private static final Accumulator ACCUMULATOR = new Accumulator();
    private static final Reporter REPORTER = new Reporter(ACCUMULATOR, MCA.LOGGER::info);
    private static final ThreadLocal<RequestContext> CURRENT_REQUEST = new ThreadLocal<>();
    private static final TargetSource UNKNOWN_SOURCE = new TargetSource("unknown", "none");
    private static final Map<WalkTarget, TargetSource> TARGET_SOURCES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, EntityStats> ENTITY_STATS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, RecomputeStats> RECOMPUTE_STATS = Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile boolean collecting;

    private PathRequestDiagnostics() {
    }

    public static void beginSinkRequest(Mob mob, WalkTarget walkTarget) {
        if (!enabled()) {
            return;
        }

        String tracker = walkTarget.getTarget().getClass().getSimpleName();
        TargetSource source = sourceFor(walkTarget);
        String producer = source.producer();
        BlockPos target = walkTarget.getTarget().currentBlockPosition();
        Path path = mob.getNavigation().getPath();
        boolean activePath = path != null && !path.isDone();
        boolean activePartial = activePath && !path.canReach();
        Optional<Long> failure = mob.getBrain()
                .getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        boolean failureEvidence = failure != null && failure.isPresent();

        ACCUMULATOR.recordRequest(
                mob.getStringUUID(),
                Origin.SINK,
                producer,
                tracker,
                source.destinationMemory(),
                mob.blockPosition().toShortString(),
                target.toShortString(),
                failureEvidence,
                activePath,
                activePartial
        );
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).sinkRequests++;
        }
        CURRENT_REQUEST.set(new RequestContext(Origin.SINK, producer, tracker, source.destinationMemory()));
    }

    public static void endSinkRequest() {
        if (enabled()) {
            CURRENT_REQUEST.remove();
        }
    }

    public static void recordNavigationRequest(Mob mob, BlockPos target) {
        if (!enabled() || CURRENT_REQUEST.get() != null) {
            return;
        }

        Optional<WalkTarget> walkTargetMemory = mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET);
        WalkTarget walkTarget = walkTargetMemory == null ? null : walkTargetMemory.orElse(null);
        String tracker = walkTarget == null
                ? "none"
                : walkTarget.getTarget().getClass().getSimpleName();
        String producer = directProducer();
        String destinationMemory = walkTarget == null ? "none" : sourceFor(walkTarget).destinationMemory();
        Path path = mob.getNavigation().getPath();
        boolean activePath = path != null && !path.isDone();
        boolean activePartial = activePath && !path.canReach();
        Optional<Long> failure = mob.getBrain()
                .getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        boolean failureEvidence = failure != null && failure.isPresent();

        ACCUMULATOR.recordRequest(
                mob.getStringUUID(),
                Origin.NAVIGATION,
                producer,
                tracker,
                destinationMemory,
                mob.blockPosition().toShortString(),
                target.toShortString(),
                failureEvidence,
                activePath,
                activePartial
        );
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).directRequests++;
        }
    }

    public static void recordSearch(Mob mob, long elapsedNanos, boolean extended, Path result, int expandedNodes) {
        if (!enabled()) {
            return;
        }

        RequestContext request = CURRENT_REQUEST.get();
        Origin origin = request == null ? Origin.NAVIGATION : request.origin();
        String producer = request == null ? directProducer() : request.producer();
        String tracker = request == null ? currentTracker(mob) : request.tracker();
        String destinationMemory = request == null ? currentDestinationMemory(mob) : request.destinationMemory();
        boolean reachable = result != null && result.canReach();
        boolean partial = result != null && !result.canReach();
        ACCUMULATOR.recordSearch(origin, producer, tracker, destinationMemory,
                elapsedNanos, reachable, partial, result == null, expandedNodes);
        synchronized (ENTITY_STATS) {
            EntityStats value = ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats());
            if (extended) {
                value.extendedSearches++;
            } else {
                value.ordinarySearches++;
            }
            value.searchNanos += elapsedNanos;
            value.expandedNodes += expandedNodes;
            value.maxExpandedNodes = Math.max(value.maxExpandedNodes, expandedNodes);
        }
    }

    public static void recordSuppressedExtendedSearch(Mob mob) {
        if (!enabled()) {
            return;
        }
        RequestContext request = CURRENT_REQUEST.get();
        ACCUMULATOR.recordSuppressedExtendedSearch(
                request == null ? Origin.NAVIGATION : request.origin(),
                request == null ? directProducer() : request.producer(),
                request == null ? currentTracker(mob) : request.tracker(),
                request == null ? currentDestinationMemory(mob) : request.destinationMemory());
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).suppressedExtended++;
        }
    }

    public static void recordDeferredProducerRetry(Mob mob, String producer, String destinationMemory,
                                                   BlockPos previousOrigin, String target, long retryAgeTicks) {
        if (!enabled()) {
            return;
        }
        BlockPos position = mob.blockPosition();
        ACCUMULATOR.recordDeferredProducerRetry(mob.getStringUUID(), producer, destinationMemory,
                previousOrigin.toShortString(), position.toShortString(), target,
                retryAgeTicks, position.distSqr(previousOrigin));
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).deferredProducerRetries++;
        }
    }

    /** Cumulative counts for a single live mob; independent of 100-tick log drains. */
    public static SearchSnapshot snapshot(Mob mob) {
        synchronized (ENTITY_STATS) {
            EntityStats value = ENTITY_STATS.get(mob);
            return value == null ? new SearchSnapshot(0, 0, 0, 0, 0, 0L, 0, 0L, 0) : value.snapshot();
        }
    }

    public record SearchSnapshot(int sinkRequests, int directRequests, int ordinarySearches,
                                 int extendedSearches, int suppressedExtended, long searchNanos,
                                 int deferredProducerRetries, long expandedNodes, int maxExpandedNodes) {
    }

    /** Captured before vanilla clears its path during recomputation. */
    public record RecomputeSnapshot(int blockUpdates, BlockPos lastChangedBlock,
                                    BlockPos lastActiveTarget, String lastCaller, int delayedCalls) {
    }

    public static RecomputeSnapshot recomputeSnapshot(Mob mob) {
        synchronized (RECOMPUTE_STATS) {
            RecomputeStats value = RECOMPUTE_STATS.get(mob);
            return value == null ? new RecomputeSnapshot(0, null, null, "none", 0) : value.snapshot();
        }
    }

    public static void recordBlockRecompute(Mob mob, BlockPos changed, BlockState before,
                                            BlockState after, Path activePath) {
        if (!enabled()) {
            return;
        }
        BlockPos pathTarget = activePath == null ? null : activePath.getTarget();
        synchronized (RECOMPUTE_STATS) {
            RecomputeStats value = RECOMPUTE_STATS.computeIfAbsent(mob, ignored -> new RecomputeStats());
            value.blockUpdates++;
            value.lastChangedBlock = changed.immutable();
            value.lastActiveTarget = pathTarget == null ? null : pathTarget.immutable();
            value.lastBlockTick = mob.level().getGameTime();
            // MixinServerWorld calls this immediately before the wrapped recomputePath().
            value.pendingBlockUpdate = true;
        }
        REPORTER.log(String.format(Locale.ROOT,
                "[MCA Path Recompute] source=block_update entity=%s tick=%d changed=%s old=%s new=%s prePathTarget=%s prePathIndex=%d/%d prePathPartial=%s",
                mob.getStringUUID(), mob.level().getGameTime(), changed.toShortString(), before, after,
                pathTarget, activePath == null ? -1 : activePath.getNextNodeIndex(),
                activePath == null ? -1 : activePath.getNodeCount(), activePath != null && !activePath.canReach()));
    }

    public static void recordRecomputeCall(Mob mob, boolean delayed, Path activePath) {
        if (!enabled()) {
            return;
        }
        long tick = mob.level().getGameTime();
        BlockPos changed;
        long blockTick;
        boolean blockUpdate;
        synchronized (RECOMPUTE_STATS) {
            RecomputeStats value = RECOMPUTE_STATS.computeIfAbsent(mob, ignored -> new RecomputeStats());
            blockUpdate = value.pendingBlockUpdate;
            value.pendingBlockUpdate = false;
            if (delayed) {
                value.delayedCalls++;
            }
            changed = value.lastChangedBlock;
            blockTick = value.lastBlockTick;
        }
        String caller = TRACE_RECOMPUTE_CALLS
                ? StackWalker.getInstance().walk(frames -> frames
                        .filter(frame -> frame.getClassName().equals("net.minecraft.server.level.ServerLevel")
                                || frame.getClassName().equals("net.minecraft.world.entity.ai.navigation.PathNavigation")
                                && frame.getMethodName().equals("tick"))
                        .findFirst().map(frame -> frame.getClassName() + '#' + frame.getMethodName())
                        .orElse("other"))
                : selectRecomputeCaller(blockUpdate, delayed);
        synchronized (RECOMPUTE_STATS) {
            RECOMPUTE_STATS.get(mob).lastCaller = caller;
        }
        String entity = mob.getStringUUID();
        String target = activePath == null ? "null" : activePath.getTarget().toString();
        int index = activePath == null ? -1 : activePath.getNextNodeIndex();
        int nodes = activePath == null ? -1 : activePath.getNodeCount();
        boolean partial = activePath != null && !activePath.canReach();
        ACCUMULATOR.recordRecompute(caller, delayed, entity, tick,
                changed == null ? "null" : changed.toShortString(), blockTick, target, index, nodes, partial);
        if (TRACE_RECOMPUTE_CALLS) {
            REPORTER.log(String.format(Locale.ROOT,
                    "[MCA Path Recompute] source=call entity=%s tick=%d caller=%s delayed=%s lastChanged=%s lastBlockTick=%d prePathTarget=%s prePathIndex=%d/%d prePathPartial=%s",
                    entity, tick, caller, delayed, changed, blockTick,
                    activePath == null ? null : activePath.getTarget(), index, nodes, partial));
        }
    }

    static String selectRecomputeCaller(boolean blockUpdate, boolean delayed) {
        if (blockUpdate) {
            return "net.minecraft.server.level.ServerLevel#sendBlockUpdated";
        }
        return delayed ? "net.minecraft.world.entity.ai.navigation.PathNavigation#tick" : "other";
    }

    private static final class RecomputeStats {
        private int blockUpdates;
        private BlockPos lastChangedBlock;
        private BlockPos lastActiveTarget;
        private String lastCaller = "none";
        private long lastBlockTick = Long.MIN_VALUE;
        private int delayedCalls;
        private boolean pendingBlockUpdate;

        private RecomputeSnapshot snapshot() {
            return new RecomputeSnapshot(blockUpdates, lastChangedBlock, lastActiveTarget,
                    lastCaller, delayedCalls);
        }
    }

    private static final class EntityStats {
        private int sinkRequests;
        private int directRequests;
        private int ordinarySearches;
        private int extendedSearches;
        private int suppressedExtended;
        private long searchNanos;
        private int deferredProducerRetries;
        private long expandedNodes;
        private int maxExpandedNodes;

        private SearchSnapshot snapshot() {
            return new SearchSnapshot(sinkRequests, directRequests, ordinarySearches,
                    extendedSearches, suppressedExtended, searchNanos, deferredProducerRetries,
                    expandedNodes, maxExpandedNodes);
        }
    }

    public static void recordWalkTargetPublication(WalkTarget walkTarget) {
        if (!enabled()) {
            return;
        }

        String producer = StackWalker.getInstance().walk(stream ->
                selectProducer(stream.map(StackWalker.StackFrame::getClassName)));
        TARGET_SOURCES.compute(walkTarget, (ignored, existing) -> new TargetSource(
                producer, existing == null ? "none" : existing.destinationMemory()));
    }

    public static void tagDestinationMemory(WalkTarget walkTarget, MemoryModuleType<?> destination) {
        if (!enabled()) {
            return;
        }
        TARGET_SOURCES.compute(walkTarget, (ignored, existing) -> new TargetSource(
                existing == null ? "unknown" : existing.producer(), destination.toString()));
    }

    static String selectProducer(Stream<String> classNames) {
        return classNames.filter(PathRequestDiagnostics::isProducerFrame)
                .findFirst()
                .map(className -> {
                    int packageSeparator = className.lastIndexOf('.');
                    String simpleName = packageSeparator >= 0 ? className.substring(packageSeparator + 1) : className;
                    int nestedSeparator = simpleName.indexOf('$');
                    return nestedSeparator >= 0 ? simpleName.substring(0, nestedSeparator) : simpleName;
                })
                .orElse("unknown");
    }

    private static boolean isProducerFrame(String className) {
        if (className.equals(PathRequestDiagnostics.class.getName())
                || className.equals("net.minecraft.world.entity.ai.Brain")
                || className.equals("net.conczin.mca.mixin.MixinBrain")
                || className.startsWith(MCAGroundPathNavigation.class.getName())
                || className.contains(".behavior.declarative.BehaviorBuilder")
                || className.contains(".behavior.declarative.MemoryAccessor")) {
            return false;
        }
        return className.startsWith("net.conczin.mca.")
                || className.startsWith("net.minecraft.world.entity.ai.behavior.");
    }

    private static TargetSource sourceFor(WalkTarget walkTarget) {
        return TARGET_SOURCES.getOrDefault(walkTarget, UNKNOWN_SOURCE);
    }

    private static String directProducer() {
        return selectNavigationSource(StackWalker.getInstance().walk(stream -> stream
                .map(frame -> frame.getClassName() + "#" + frame.getMethodName())
                .toList()));
    }

    /**
     * The next MCA class on a navigation stack is frequently VillagerEntityMCA.tick,
     * even though vanilla initiated the search. Preserve the initiating navigation
     * operation before falling back to the original project-level producer name.
     */
    static String selectNavigationSource(List<String> frames) {
        String navigation = MCAGroundPathNavigation.class.getName();
        if (frames.contains(navigation + "#chainCompletedStaticWalkTarget")) {
            return "PartialPathChain";
        }
        if (frames.contains(navigation + "#recomputePath")
                || frames.contains("net.minecraft.world.entity.ai.navigation.PathNavigation#recomputePath")) {
            return "NavigationRecompute";
        }
        return selectProducer(frames.stream()
                .map(frame -> frame.substring(0, frame.indexOf('#'))));
    }

    private static String currentTracker(Mob mob) {
        Optional<WalkTarget> walkTarget = mob.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET);
        if (walkTarget == null) {
            return "none";
        }
        return walkTarget
                .map(target -> target.getTarget().getClass().getSimpleName())
                .orElse("none");
    }

    private static String currentDestinationMemory(Mob mob) {
        Optional<WalkTarget> walkTarget = mob.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET);
        return walkTarget == null ? "none" : walkTarget.map(target -> sourceFor(target).destinationMemory()).orElse("none");
    }

    static boolean enabled() {
        return collecting;
    }

    static boolean shouldCollect(boolean developmentEnvironment, boolean benchmarkWithoutDiagnostics) {
        return developmentEnvironment && !benchmarkWithoutDiagnostics;
    }

    /** The loader owns this lifecycle; non-development servers never start a logging thread. */
    public static void onServerStarting() {
        collecting = shouldCollect(MCA.platformHelper.isDevelopmentEnvironment(), BENCHMARK_WITHOUT_DIAGNOSTICS);
        if (collecting) {
            REPORTER.start();
        }
    }

    /** Collect windows after navigation has ticked, without performing any disk logging on the server thread. */
    public static void onServerEndTick(long gameTime) {
        if (enabled()) {
            REPORTER.endTick(gameTime);
        }
    }

    /** Drain the final partial window and wait for all queued log lines at server shutdown. */
    public static void onServerStopping(long gameTime) {
        if (!collecting) {
            return;
        }
        try {
            REPORTER.stop(gameTime);
        } finally {
            collecting = false;
        }
    }

    /** One sequential writer keeps block events, optional call traces and window reports in enqueue order. */
    static final class Reporter {
        private static final String STOP = "MCA Path Debug Logger stop";

        private final Accumulator accumulator;
        private final Consumer<String> sink;
        private BlockingQueue<LogEntry> queue;
        private Thread worker;
        private long windowStart = Long.MIN_VALUE;

        Reporter(Accumulator accumulator, Consumer<String> sink) {
            this.accumulator = accumulator;
            this.sink = sink;
        }

        synchronized void start() {
            if (worker != null) {
                return;
            }
            windowStart = Long.MIN_VALUE;
            queue = new LinkedBlockingQueue<>(); // Development-only: never discard diagnostics under backpressure.
            BlockingQueue<LogEntry> sessionQueue = queue;
            worker = new Thread(() -> consume(sessionQueue), "MCA Path Debug Logger");
            worker.setDaemon(true);
            worker.start();
        }

        synchronized void log(String line) {
            if (queue == null) {
                // Outside the server lifecycle there is no navigation tick to stall.
                sink.accept(line);
            } else {
                queue.add(new LogEntry(line, false));
            }
        }

        synchronized void endTick(long gameTime) {
            if (queue != null) {
                publishWindow(gameTime, false);
            }
        }

        synchronized void stop(long gameTime) {
            if (worker == null) {
                return;
            }
            publishWindow(gameTime, true);
            queue.add(new LogEntry(STOP, true));
            boolean interrupted = false;
            while (worker.isAlive()) {
                try {
                    worker.join();
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            queue = null;
            worker = null;
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }

        private void publishWindow(long gameTime, boolean finalWindow) {
            if (windowStart == Long.MIN_VALUE) {
                windowStart = gameTime;
            }
            if (!finalWindow && gameTime - windowStart < REPORT_INTERVAL_TICKS) {
                return;
            }
            for (String line : accumulator.drainSummary()) {
                log("[MCA Path Debug] window=" + windowStart + ".." + gameTime + ' ' + line);
            }
            for (String line : accumulator.drainRecomputeSummary()) {
                log("[MCA Path Recompute] window=" + windowStart + ".." + gameTime + ' ' + line);
            }
            windowStart = gameTime;
        }

        private void consume(BlockingQueue<LogEntry> sessionQueue) {
            boolean interrupted = false;
            while (true) {
                LogEntry entry;
                try {
                    entry = sessionQueue.take();
                } catch (InterruptedException e) {
                    // Only the shutdown marker may end the writer; a stray interrupt cannot lose later events.
                    interrupted = true;
                    continue;
                }
                if (entry.stop()) {
                    if (interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    return;
                }
                try {
                    sink.accept(entry.line());
                } catch (RuntimeException e) {
                    // A broken appender must not silently discard the rest of the ordered queue.
                    System.err.println("MCA Path Debug Logger failed to write a line: " + e);
                    System.err.println(entry.line());
                }
            }
        }

        private record LogEntry(String line, boolean stop) {
        }
    }

    public enum Origin {
        SINK("sink"),
        NAVIGATION("navigation"),
        PRODUCER("producer");

        private final String label;

        Origin(String label) {
            this.label = label;
        }
    }

    private record TargetSource(String producer, String destinationMemory) {
    }

    private record RequestContext(Origin origin, String producer, String tracker, String destinationMemory) {
    }

    static final class Accumulator {
        private final Map<Key, Stats> stats = new LinkedHashMap<>();
        private final Map<RecomputeKey, RecomputeWindowStats> recomputes = new LinkedHashMap<>();
        private final Map<String, String> lastTargetByRequester = new LinkedHashMap<>();

        synchronized void recordRecompute(String caller, boolean delayed, String entity, long tick,
                                          String changed, long blockTick, String target,
                                          int pathIndex, int pathNodes, boolean partial) {
            RecomputeWindowStats value = recomputes.computeIfAbsent(
                    new RecomputeKey(caller, delayed), ignored -> new RecomputeWindowStats());
            value.calls++;
            value.lastEntity = entity;
            value.lastTick = tick;
            value.lastChanged = changed;
            value.lastBlockTick = blockTick;
            value.lastTarget = target;
            value.lastPathIndex = pathIndex;
            value.lastPathNodes = pathNodes;
            value.lastPartial = partial;
        }

        synchronized List<String> drainRecomputeSummary() {
            List<String> lines = new ArrayList<>(recomputes.size());
            for (Map.Entry<RecomputeKey, RecomputeWindowStats> entry : recomputes.entrySet()) {
                RecomputeKey key = entry.getKey();
                RecomputeWindowStats value = entry.getValue();
                lines.add(String.format(Locale.ROOT,
                        "source=call caller=%s delayed=%s calls=%d lastEntity=%s lastTick=%d lastChanged=%s lastBlockTick=%d prePathTarget=%s prePathIndex=%d/%d prePathPartial=%s",
                        key.caller, key.delayed, value.calls, value.lastEntity, value.lastTick,
                        value.lastChanged, value.lastBlockTick, value.lastTarget,
                        value.lastPathIndex, value.lastPathNodes, value.lastPartial));
            }
            recomputes.clear();
            return lines;
        }

        synchronized void recordRequest(
                String requester,
                Origin origin,
                String producer,
                String tracker,
                String destinationMemory,
                String position,
                String target,
                boolean failureEvidence,
                boolean activePath,
                boolean activePartial
        ) {
            Stats value = stats.computeIfAbsent(new Key(origin, producer, tracker, destinationMemory), unused -> new Stats());
            value.requests++;
            value.lastEntity = requester;
            value.lastPosition = position;
            value.lastTarget = target;
            String requestKey = requester + '|' + origin.name() + '|' + producer + '|' + tracker + '|' + destinationMemory;
            if (target.equals(lastTargetByRequester.put(requestKey, target))) {
                value.sameTarget++;
            }
            if (failureEvidence) {
                value.failureEvidence++;
            }
            if (activePath) {
                value.activePath++;
            }
            if (activePartial) {
                value.activePartial++;
            }
        }

        synchronized void recordSearch(
                Origin origin,
                String producer,
                String tracker,
                String destinationMemory,
                long elapsedNanos,
                boolean reachable,
                boolean partial,
                boolean nullPath,
                int expandedNodes
        ) {
            Stats value = stats.computeIfAbsent(new Key(origin, producer, tracker, destinationMemory), unused -> new Stats());
            value.searches++;
            value.searchNanos += elapsedNanos;
            value.maxSearchNanos = Math.max(value.maxSearchNanos, elapsedNanos);
            value.expandedNodes += expandedNodes;
            value.maxExpandedNodes = Math.max(value.maxExpandedNodes, expandedNodes);
            if (reachable) {
                value.reachable++;
            }
            if (partial) {
                value.partial++;
            }
            if (nullPath) {
                value.nullPath++;
            }
        }

        synchronized void recordSuppressedExtendedSearch(
                Origin origin, String producer, String tracker, String destinationMemory) {
            stats.computeIfAbsent(new Key(origin, producer, tracker, destinationMemory), unused -> new Stats())
                    .suppressedExtended++;
        }

        synchronized void recordDeferredProducerRetry(String requester, String producer, String destinationMemory,
                                                      String previousOrigin, String position, String target,
                                                      long retryAgeTicks, double movementSq) {
            Stats value = stats.computeIfAbsent(
                    new Key(Origin.PRODUCER, producer, "none", destinationMemory), ignored -> new Stats());
            value.sourceDeferred++;
            value.lastEntity = requester;
            value.lastPosition = position;
            value.lastTarget = target;
            value.previousOrigin = previousOrigin;
            value.movementSq = movementSq;
            value.retryAgeTicks = retryAgeTicks;
        }

        synchronized List<String> drainSummary() {
            List<String> lines = new ArrayList<>(stats.size());
            for (Map.Entry<Key, Stats> entry : stats.entrySet()) {
                Key key = entry.getKey();
                Stats value = entry.getValue();
                lines.add(String.format(
                        Locale.ROOT,
                        "origin=%s producer=%s tracker=%s requests=%d sameTarget=%d failureEvidence=%d activePath=%d activePartial=%d searches=%d searchMs=%.3f maxSearchMs=%.3f reachable=%d partial=%d null=%d lastEntity=%s lastPos=%s lastTarget=%s destinationMemory=%s suppressedExtended=%d sourceDeferred=%d previousOrigin=%s movementSq=%.1f retryAgeTicks=%d expandedNodes=%d maxExpandedNodes=%d",
                        key.origin.label,
                        key.producer,
                        key.tracker,
                        value.requests,
                        value.sameTarget,
                        value.failureEvidence,
                        value.activePath,
                        value.activePartial,
                        value.searches,
                        value.searchNanos / 1_000_000.0D,
                        value.maxSearchNanos / 1_000_000.0D,
                        value.reachable,
                        value.partial,
                        value.nullPath,
                        value.lastEntity,
                        value.lastPosition,
                        value.lastTarget,
                        key.destinationMemory,
                        value.suppressedExtended,
                        value.sourceDeferred,
                        value.previousOrigin,
                        value.movementSq,
                        value.retryAgeTicks,
                        value.expandedNodes,
                        value.maxExpandedNodes
                ));
            }
            stats.clear();
            lastTargetByRequester.clear();
            return lines;
        }
    }

    private record Key(Origin origin, String producer, String tracker, String destinationMemory) {
    }

    private record RecomputeKey(String caller, boolean delayed) {
    }

    private static final class RecomputeWindowStats {
        private int calls;
        private String lastEntity;
        private long lastTick;
        private String lastChanged;
        private long lastBlockTick;
        private String lastTarget;
        private int lastPathIndex;
        private int lastPathNodes;
        private boolean lastPartial;
    }

    private static final class Stats {
        private int requests;
        private int sameTarget;
        private int failureEvidence;
        private int activePath;
        private int activePartial;
        private int searches;
        private long searchNanos;
        private long maxSearchNanos;
        private long expandedNodes;
        private int maxExpandedNodes;
        private int reachable;
        private int partial;
        private int nullPath;
        private int suppressedExtended;
        private int sourceDeferred;
        private String previousOrigin = "none";
        private double movementSq;
        private long retryAgeTicks;
        private String lastEntity = "none";
        private String lastPosition = "none";
        private String lastTarget = "none";
    }
}
