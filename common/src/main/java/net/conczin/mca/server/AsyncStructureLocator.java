package net.conczin.mca.server;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.chunk.storage.ChunkScanAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheck;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.*;

/** Server-owned locator. Call locate/cancel/close on the server thread; results complete there too. */
public final class AsyncStructureLocator implements AutoCloseable {
    // Includes active searches, queued virtual-thread tasks, and results waiting for server-thread delivery.
    private static final int MAX_PENDING_SEARCHES = 64;
    private static final int MAX_CONCURRENT_SEARCHES = 4;
    private static final TicketType<UUID> SEARCH_TICKET = TicketType.create("mca_structure_search", UUID::compareTo);
    private final MinecraftServer server;
    private final ExecutorService executor;
    private final Semaphore concurrencyPermits = new Semaphore(MAX_CONCURRENT_SEARCHES);
    private final ConcurrentHashMap<SearchOwner, Request> requests = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public AsyncStructureLocator(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
        this.executor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("MCA-Structure-Locator-", 0).factory()
        );
    }

    private void ensureServerThread() {
        if (!server.isSameThread()) {
            throw new IllegalStateException("AsyncStructureLocator must be called on the server thread");
        }
    }

    public CompletableFuture<Optional<BlockPos>> locate(UUID owner, ServerLevel level,
            BlockPos origin, HolderSet<Structure> structures, int radius) {
        return locate(owner, level, origin, structures, radius, false);
    }

    public CompletableFuture<Optional<BlockPos>> locateRumor(UUID villager, UUID player, ServerLevel level,
            BlockPos origin, HolderSet<Structure> structures, int radius) {
        return locate(new SearchOwner(player, villager), level, origin, structures, radius, false);
    }

    public CompletableFuture<Optional<BlockPos>> locate(UUID owner, ServerLevel level,
            BlockPos origin, HolderSet<Structure> structures, int radius, boolean loadDestinationChunk) {
        return locate(new SearchOwner(owner, null), level, origin, structures, radius, loadDestinationChunk);
    }

    private CompletableFuture<Optional<BlockPos>> locate(SearchOwner owner, ServerLevel level,
            BlockPos origin, HolderSet<Structure> structures, int radius, boolean loadDestinationChunk) {
        ensureServerThread();
        cancel(owner);
        if (closed) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        if (requests.size() >= MAX_PENDING_SEARCHES) {
            // Overflow is cancellation, not "structure not found": do not run gameplay callbacks.
            CompletableFuture<Optional<BlockPos>> rejected = new CompletableFuture<>();
            rejected.cancel(false);
            return rejected;
        }

        // Resolve lazy placement maps on the owning thread before publishing them to a worker.
        ServerChunkCache chunks = level.getChunkSource();
        ChunkGeneratorStructureState state = chunks.getGeneratorState();
        Map<StructurePlacement, List<Holder<Structure>>> placements = new LinkedHashMap<>();
        for (Holder<Structure> structure : structures) {
            for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
                placements.computeIfAbsent(placement, ignored -> new ArrayList<>()).add(structure);
            }
        }
        if (placements.isEmpty()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        // Vanilla StructureCheck has mutable caches also updated by chunk loading. Give this
        // request its own check, sharing only the native asynchronous storage/generation inputs.
        Request request = new Request();
        ChunkScanAccess scanner = chunks.chunkScanner();
        StructureCheck check = new StructureCheck(
                (position, visitor) -> scanChunk(request, scanner, position, visitor), level.registryAccess(),
                server.getStructureManager(), level.dimension(), chunks.getGenerator(),
                chunks.randomState(), LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()),
                chunks.getGenerator().getBiomeSource(), server.getWorldData().worldGenOptions().seed(),
                server.getFixerUpper());
        requests.put(owner, request);
        BlockPos searchOrigin = origin.immutable();
        try {
            long seed = state.getLevelSeed();
            request.task = executor.submit(() -> runSearch(
                    owner, request, level, chunks, state, check, placements,
                    searchOrigin, radius, seed, loadDestinationChunk
            ));
        } catch (RejectedExecutionException e) {
            requests.remove(owner, request);
            request.result.cancel(false);
        }
        return request.result;
    }

    private void runSearch(SearchOwner owner, Request request, ServerLevel level, ServerChunkCache chunks,
            ChunkGeneratorStructureState state, StructureCheck check,
            Map<StructurePlacement, List<Holder<Structure>>> placements, BlockPos searchOrigin,
            int radius, long seed, boolean loadDestinationChunk) {
        boolean permitAcquired = false;
        try {
            checkActive(request);
            concurrencyPermits.acquire();
            permitAcquired = true;
            checkActive(request);

            Optional<BlockPos> result = search(request, level, chunks, state, check, placements,
                    searchOrigin, radius, seed);
            checkActive(request);
            if (loadDestinationChunk && result.isPresent()) {
                ChunkPos destination = new ChunkPos(result.get());
                if (!loadChunk(request, chunks, destination, ChunkStatus.FULL).isSuccess()) {
                    result = Optional.empty();
                }
            }
            checkActive(request);
            deliver(owner, request, result);
        } catch (InterruptedException e) {
            request.aborted = true;
            Thread.currentThread().interrupt();
            deliver(owner, request, Optional.empty());
        } catch (CancellationException e) {
            request.aborted = true;
            deliver(owner, request, Optional.empty());
        } catch (Exception e) {
            request.aborted = true;
            MCA.LOGGER.error("Failed to locate structure", e);
            deliver(owner, request, Optional.empty());
        } finally {
            if (permitAcquired) {
                concurrencyPermits.release();
            }
        }
    }

    private void deliver(SearchOwner owner, Request request, Optional<BlockPos> result) {
        if (closed) {
            return;
        }
        server.execute(() -> {
            try {
                if (!closed && requests.remove(owner, request)) {
                    // Keep the destination ticket until callbacks have applied the teleport.
                    request.result.complete(result);
                }
            } finally {
                releaseTicket(request);
            }
        });
    }

    public void cancel(UUID owner) {
        cancel(new SearchOwner(owner, null));
    }

    private void cancel(SearchOwner owner) {
        ensureServerThread();
        Request request = requests.remove(owner);
        if (request != null) {
            request.aborted = true;
            request.result.cancel(false);
            releaseTicket(request);
            Future<?> task = request.task;
            if (task != null) {
                task.cancel(true);
            }
        }
    }

    @Override
    public void close() {
        ensureServerThread();
        closed = true;
        cancelAll();
        executor.shutdownNow();
    }

    public void cancelAll() {
        ensureServerThread();
        for (SearchOwner owner : List.copyOf(requests.keySet())) {
            cancel(owner);
        }
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    /** Called only from the loader's SERVER_STOPPED event, after live ticking has ended. */
    public boolean finishStopping(MinecraftServer stoppedServer) {
        if (server != stoppedServer) {
            return false;
        }
        if (!closed) {
            close();
        }
        try {
            if (executor.awaitTermination(5, TimeUnit.SECONDS)) {
                return true;
            }
            MCA.LOGGER.warn("Structure locator did not terminate after server shutdown; "
                    + "structure lookups on the next server will be unavailable until this worker exits");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            MCA.LOGGER.warn("Interrupted while awaiting structure locator shutdown", e);
        }
        return false;
    }

    private void checkActive(Request request) {
        if (closed || request.aborted || request.result.isCancelled() || Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Structure search cancelled");
        }
    }

    private <T> T await(Request request, Future<T> future)
            throws InterruptedException, ExecutionException {
        checkActive(request);
        return future.get();
    }

    private CompletableFuture<Void> scanChunk(Request request, ChunkScanAccess scanner,
            ChunkPos position, StreamTagVisitor visitor) {
        try {
            checkActive(request);
            // StructureCheck.join() receives an already completed future, so its internal
            // uninterruptible wait cannot monopolize our worker after cancellation/shutdown.
            await(request, scanner.scanChunk(position, visitor));
            return CompletableFuture.completedFuture(null);
        } catch (InterruptedException e) {
            request.aborted = true;
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        } catch (CancellationException e) {
            request.aborted = true;
            return CompletableFuture.failedFuture(e);
        } catch (ExecutionException e) {
            // Keep vanilla's handling of actual storage failures, including the original cause.
            return CompletableFuture.failedFuture(e.getCause());
        }
    }

    private ChunkResult<ChunkAccess> loadChunk(Request request, ServerChunkCache chunks,
            ChunkPos position, ChunkStatus status) throws Exception {
        checkActive(request);
        var chunkFuture = await(request, server.submit(() -> {
            if (closed || request.aborted || request.result.isCancelled()) {
                return CompletableFuture.<ChunkResult<ChunkAccess>>failedFuture(
                        new CancellationException("Structure search cancelled"));
            }
            int ticketLevel = ChunkLevel.byStatus(status);
            acquireTicket(request, chunks, position, ticketLevel);
            // Guard, ticket acquisition and native scheduling are one server-thread action.
            // The public getChunkFuture() would managedBlock here; this native scheduler does not.
            return chunks.getChunkFutureMainThread(position.x, position.z, status, true);
        }));
        return await(request, chunkFuture);
    }

    private void acquireTicket(Request request, ServerChunkCache chunks, ChunkPos position, int ticketLevel) {
        releaseTicket(request);
        chunks.chunkMap.getDistanceManager().addTicket(SEARCH_TICKET, position, ticketLevel, request.ticketId);
        request.ticketChunks = chunks;
        request.ticketPosition = position;
        request.ticketLevel = ticketLevel;
    }

    private void releaseTicket(Request request) {
        if (request.ticketChunks != null) {
            request.ticketChunks.chunkMap.getDistanceManager().removeTicket(
                    SEARCH_TICKET, request.ticketPosition, request.ticketLevel, request.ticketId);
            request.ticketChunks = null;
            request.ticketPosition = null;
        }
    }

    private Optional<BlockPos> search(Request request, ServerLevel level, ServerChunkCache chunks,
            ChunkGeneratorStructureState state, StructureCheck check,
            Map<StructurePlacement, List<Holder<Structure>>> placements, BlockPos origin,
            int radius, long seed) throws Exception {
        List<Map.Entry<ConcentricRingsStructurePlacement, List<Holder<Structure>>>> ringPlacements = new ArrayList<>();
        List<Map.Entry<RandomSpreadStructurePlacement, List<Holder<Structure>>>> spreadPlacements = new ArrayList<>();
        for (var entry : placements.entrySet()) {
            switch (entry.getKey()) {
                case ConcentricRingsStructurePlacement rings ->
                        ringPlacements.add(Map.entry(rings, entry.getValue()));
                case RandomSpreadStructurePlacement spread ->
                        spreadPlacements.add(Map.entry(spread, entry.getValue()));
                default -> {
                }
            }
        }

        BlockPos nearest = null;
        double distance = Double.MAX_VALUE;
        // Preserve vanilla's ring/random-spread ordering and radius semantics. Candidate math
        // uses the vanilla placement APIs; chunk generation is requested through its futures.
        for (var entry : ringPlacements) {
            ConcentricRingsStructurePlacement rings = entry.getKey();
            checkActive(request);
            var positionsFuture = state.ringPositions.get(rings);
            if (positionsFuture == null) {
                throw new IllegalStateException("Missing positions for concentric structure placement");
            }
            List<ChunkPos> positions = await(request, positionsFuture);
            double ringDistance = Double.MAX_VALUE;
            BlockPos ringNearest = null;
            for (ChunkPos candidate : positions) {
                double candidateDistance = new BlockPos(candidate.getMiddleBlockX(), 32,
                        candidate.getMiddleBlockZ()).distSqr(origin);
                if (ringNearest == null || candidateDistance < ringDistance) {
                    Optional<BlockPos> found = checkCandidate(request, level, chunks, check,
                            entry.getValue(), rings, candidate);
                    if (found.isPresent()) {
                        ringNearest = found.get();
                        ringDistance = candidateDistance;
                    }
                }
            }
            if (ringNearest != null && ringNearest.distSqr(origin) < distance) {
                nearest = ringNearest;
                distance = nearest.distSqr(origin);
            }
        }
        if (!spreadPlacements.isEmpty()) {
            int originX = SectionPos.blockToSectionCoord(origin.getX());
            int originZ = SectionPos.blockToSectionCoord(origin.getZ());
            for (int ring = 0; ring <= radius; ring++) {
                boolean foundInRing = false;
                for (var entry : spreadPlacements) {
                    RandomSpreadStructurePlacement spread = entry.getKey();
                    Optional<BlockPos> found = Optional.empty();
                    for (int x = -ring; x <= ring && found.isEmpty(); x++) {
                        // Interior columns only need the two edge candidates.
                        int step = x == -ring || x == ring ? 1 : ring * 2;
                        for (int z = -ring; z <= ring && found.isEmpty(); z += step) {
                            checkActive(request);
                            ChunkPos candidate = spread.getPotentialStructureChunk(seed,
                                    originX + spread.spacing() * x, originZ + spread.spacing() * z);
                            found = checkCandidate(request, level, chunks, check, entry.getValue(), spread, candidate);
                        }
                    }
                    if (found.isPresent()) {
                        foundInRing = true;
                        double candidateDistance = found.get().distSqr(origin);
                        if (candidateDistance < distance) {
                            nearest = found.get();
                            distance = candidateDistance;
                        }
                    }
                }
                if (foundInRing) {
                    break;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    private Optional<BlockPos> checkCandidate(Request request, ServerLevel level, ServerChunkCache chunks,
            StructureCheck check, List<Holder<Structure>> structures, StructurePlacement placement,
            ChunkPos candidate) throws Exception {
        for (Holder<Structure> holder : structures) {
            checkActive(request);
            StructureCheckResult presence = check.checkStart(candidate, holder.value(), placement, false);
            checkActive(request);
            if (presence == StructureCheckResult.START_NOT_PRESENT) {
                continue;
            }
            if (presence == StructureCheckResult.START_PRESENT) {
                return Optional.of(placement.getLocatePos(candidate));
            }
            var chunkResult = loadChunk(request, chunks, candidate, ChunkStatus.STRUCTURE_STARTS);
            checkActive(request);
            Optional<BlockPos> found = await(request, server.submit(() -> {
                try {
                    if (closed || request.aborted || request.result.isCancelled()) {
                        return Optional.<BlockPos>empty();
                    }
                    var chunk = chunkResult.orElse(null);
                    if (chunk == null) {
                        return Optional.<BlockPos>empty();
                    }
                    StructureStart start = level.structureManager().getStartForStructure(
                            SectionPos.bottomOf(chunk), holder.value(), chunk);
                    return start != null && start.isValid()
                            ? Optional.of(placement.getLocatePos(start.getChunkPos())) : Optional.<BlockPos>empty();
                } finally {
                    releaseTicket(request);
                }
            }));
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private record SearchOwner(UUID player, @Nullable UUID villager) {
    }

    private static final class Request {
        private final CompletableFuture<Optional<BlockPos>> result = new CompletableFuture<>();
        private final UUID ticketId = UUID.randomUUID();
        private volatile boolean aborted;
        private volatile Future<?> task;
        // Ticket state is read and changed only on the server thread.
        private ServerChunkCache ticketChunks;
        private ChunkPos ticketPosition;
        private int ticketLevel;
    }
}
