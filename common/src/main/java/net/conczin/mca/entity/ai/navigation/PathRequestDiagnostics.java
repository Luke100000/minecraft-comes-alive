package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Development-only pathfinding counters used by regression and profiling fixtures. */
public final class PathRequestDiagnostics {
    private static final boolean BENCHMARK_WITHOUT_DIAGNOSTICS =
            Boolean.getBoolean("mca.pathDebug.benchmarkWithoutDiagnostics");
    private static final boolean ENABLED = shouldCollect(
            MCA.platformHelper.isDevelopmentEnvironment(), BENCHMARK_WITHOUT_DIAGNOSTICS);
    private static final ThreadLocal<Boolean> SINK_REQUEST = new ThreadLocal<>();
    private static final Map<Mob, EntityStats> ENTITY_STATS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Mob, RecomputeStats> RECOMPUTE_STATS = Collections.synchronizedMap(new WeakHashMap<>());

    private PathRequestDiagnostics() {
    }

    public static void beginSinkRequest(Mob mob) {
        if (!enabled()) {
            return;
        }
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).sinkRequests++;
        }
        SINK_REQUEST.set(Boolean.TRUE);
    }

    public static void endSinkRequest() {
        if (!enabled()) {
            return;
        }
        SINK_REQUEST.remove();
    }

    public static void recordNavigationRequest(Mob mob) {
        if (!enabled() || Boolean.TRUE.equals(SINK_REQUEST.get())) {
            return;
        }
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).directRequests++;
        }
    }

    public static void recordSearch(Mob mob, long elapsedNanos, boolean extended, int expandedNodes) {
        if (!enabled()) {
            return;
        }
        synchronized (ENTITY_STATS) {
            EntityStats stats = ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats());
            if (extended) {
                stats.extendedSearches++;
            } else {
                stats.ordinarySearches++;
            }
            stats.searchNanos += elapsedNanos;
            stats.expandedNodes += expandedNodes;
            stats.maxExpandedNodes = Math.max(stats.maxExpandedNodes, expandedNodes);
        }
    }

    public static void recordSuppressedExtendedSearch(Mob mob) {
        if (!enabled()) {
            return;
        }
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).suppressedExtended++;
        }
    }

    public static void recordDeferredProducerRetry(Mob mob) {
        if (!enabled()) {
            return;
        }
        synchronized (ENTITY_STATS) {
            ENTITY_STATS.computeIfAbsent(mob, ignored -> new EntityStats()).deferredProducerRetries++;
        }
    }

    public static SearchSnapshot snapshot(Mob mob) {
        synchronized (ENTITY_STATS) {
            EntityStats stats = ENTITY_STATS.get(mob);
            return stats == null ? new SearchSnapshot(0, 0, 0, 0, 0, 0L, 0, 0L, 0) : stats.snapshot();
        }
    }

    public record SearchSnapshot(int sinkRequests, int directRequests, int ordinarySearches,
                                 int extendedSearches, int suppressedExtended, long searchNanos,
                                 int deferredProducerRetries, long expandedNodes, int maxExpandedNodes) {
    }

    public static void recordBlockRecompute(Mob mob, BlockPos changed, Path activePath) {
        if (!enabled()) {
            return;
        }
        BlockPos pathTarget = activePath == null ? null : activePath.getTarget();
        synchronized (RECOMPUTE_STATS) {
            RecomputeStats stats = RECOMPUTE_STATS.computeIfAbsent(mob, ignored -> new RecomputeStats());
            stats.blockUpdates++;
            stats.lastChangedBlock = changed.immutable();
            stats.lastActiveTarget = pathTarget == null ? null : pathTarget.immutable();
        }
    }

    public static RecomputeSnapshot recomputeSnapshot(Mob mob) {
        synchronized (RECOMPUTE_STATS) {
            RecomputeStats stats = RECOMPUTE_STATS.get(mob);
            return stats == null ? new RecomputeSnapshot(0, null, null) : stats.snapshot();
        }
    }

    public record RecomputeSnapshot(int blockUpdates, BlockPos lastChangedBlock, BlockPos lastActiveTarget) {
    }

    static boolean enabled() {
        return ENABLED;
    }

    static boolean shouldCollect(boolean developmentEnvironment, boolean benchmarkWithoutDiagnostics) {
        return developmentEnvironment && !benchmarkWithoutDiagnostics;
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

    private static final class RecomputeStats {
        private int blockUpdates;
        private BlockPos lastChangedBlock;
        private BlockPos lastActiveTarget;

        private RecomputeSnapshot snapshot() {
            return new RecomputeSnapshot(blockUpdates, lastChangedBlock, lastActiveTarget);
        }
    }
}
