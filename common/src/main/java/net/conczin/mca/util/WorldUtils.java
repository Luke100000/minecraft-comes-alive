package net.conczin.mca.util;

import com.mojang.datafixers.util.Pair;
import net.conczin.mca.MCA;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.storage.SavedDataStorage;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public interface WorldUtils {
    static List<Entity> getCloseEntities(Level world, Entity e, double range) {
        Vec3 pos = e.position();
        return world.getEntities(e, new AABB(pos, pos).inflate(range));
    }

    static <T extends Entity> List<T> getCloseEntities(Level world, Entity e, double range, Class<T> c) {
        return getCloseEntities(world, e.position(), range, c);
    }

    static <T extends Entity> List<T> getCloseEntities(Level world, Vec3 pos, double range, Class<T> c) {
        return world.getEntitiesOfClass(c, new AABB(pos, pos).inflate(range));
    }

    @SuppressWarnings("DataFlowIssue")
    static <T extends SavedData> T loadData(ServerLevel world, BiFunction<CompoundTag, HolderLookup.Provider, T> loader, Function<ServerLevel, T> factory, String dataId) {
        return loadData(world, loader, factory, dataId, new String[0]);
    }

    @SuppressWarnings("DataFlowIssue")
    static <T extends SavedData> T loadData(
            ServerLevel world,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            Function<ServerLevel, T> factory,
            String dataId,
            String... legacyDataIds
    ) {
        return loadDataSafely(world, world.getDataStorage(), getSavedDataFolder(world.getDataStorage()), dataId,
                factory, loader, (data, path) -> {}, legacyDataIds);
    }

    static <T extends SavedData> Optional<T> loadDataIfPresent(ServerLevel world, BiFunction<CompoundTag, HolderLookup.Provider, T> loader, String dataId) {
        SavedDataStorage storage = world.getDataStorage();
        return loadDataIfPresentSafely(world, storage, getSavedDataFolder(storage), dataId,
                ignored -> null, loader, (data, path) -> {});
    }

    static List<String> savedDataLookupIds(String currentId, String... legacyDataIds) {
        return Stream.concat(Stream.of(currentId), Arrays.stream(legacyDataIds))
                .distinct()
                .toList();
    }

    @FunctionalInterface
    interface SavedDataPayloadMigration {
        boolean migrate(CompoundTag payload, @Nullable Tag storedVersion, boolean canonicalPath,
                        HolderLookup.Provider registries);
    }

    SavedDataPayloadMigration NO_PAYLOAD_MIGRATION = (payload, version, canonical, registries) -> false;

    static <T extends SavedData> T loadDataSafely(
            ServerLevel world,
            SavedDataStorage storage,
            Path dataFolder,
            String dataId,
            Function<ServerLevel, T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable,
            String... legacyDataIds
    ) {
        return loadDataSafely(storage, dataFolder, world.registryAccess(), dataId,
                () -> emptyFactory.apply(world), loader, markUnreadable, legacyDataIds);
    }

    static <T extends SavedData> T loadDataSafely(
            ServerLevel world, SavedDataStorage storage, Path dataFolder, String dataId,
            Function<ServerLevel, T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, SavedDataPayloadMigration migration, String... legacyDataIds
    ) {
        return loadDataSafely(storage, dataFolder, world.registryAccess(), dataId,
                () -> emptyFactory.apply(world), loader, markUnreadable, migration, legacyDataIds);
    }

    static <T extends SavedData> T loadDataSafely(
            SavedDataStorage storage,
            Path dataFolder,
            HolderLookup.Provider provider,
            String dataId,
            Supplier<T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable,
            String... legacyDataIds
    ) {
        return loadDataSafely(storage, dataFolder, provider, dataId, emptyFactory, loader,
                markUnreadable, NO_PAYLOAD_MIGRATION, legacyDataIds);
    }

    static <T extends SavedData> T loadDataSafely(
            SavedDataStorage storage, Path dataFolder, HolderLookup.Provider provider, String dataId,
            Supplier<T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, BiConsumer<T, Path> loadedFrom, String... legacyDataIds
    ) {
        return loadDataSafely(storage, dataFolder, provider, dataId, emptyFactory, loader,
                markUnreadable, NO_PAYLOAD_MIGRATION, loadedFrom, legacyDataIds);
    }

    private static <T extends SavedData> T loadDataSafely(
            SavedDataStorage storage, Path dataFolder, HolderLookup.Provider provider, String dataId,
            Supplier<T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, SavedDataPayloadMigration migration, String... legacyDataIds
    ) {
        return loadDataSafely(storage, dataFolder, provider, dataId, emptyFactory, loader,
                markUnreadable, migration, (data, path) -> {}, legacyDataIds);
    }

    private static <T extends SavedData> T loadDataSafely(
            SavedDataStorage storage, Path dataFolder, HolderLookup.Provider provider, String dataId,
            Supplier<T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, SavedDataPayloadMigration migration,
            BiConsumer<T, Path> loadedFrom, String... legacyDataIds
    ) {
        Optional<T> existing = loadDataIfPresentSafely(storage, dataFolder, provider, dataId,
                emptyFactory, loader, markUnreadable, migration, loadedFrom, legacyDataIds);
        if (existing.isPresent()) {
            return existing.get();
        }
        SavedDataType<T> type = createSavedDataType(provider, loader, emptyFactory, dataId);
        T created = emptyFactory.get();
        storage.set(type, created);
        return created;
    }

    static <T extends SavedData> Optional<T> loadDataIfPresentSafely(
            ServerLevel world, SavedDataStorage storage, Path dataFolder, String dataId,
            Function<ServerLevel, T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, String... legacyDataIds
    ) {
        return loadDataIfPresentSafely(world, storage, dataFolder, dataId, emptyFactory, loader,
                markUnreadable, NO_PAYLOAD_MIGRATION, legacyDataIds);
    }

    static <T extends SavedData> Optional<T> loadDataIfPresentSafely(
            ServerLevel world, SavedDataStorage storage, Path dataFolder, String dataId,
            Function<ServerLevel, T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, SavedDataPayloadMigration migration, String... legacyDataIds
    ) {
        return loadDataIfPresentSafely(storage, dataFolder, world.registryAccess(), dataId,
                () -> emptyFactory.apply(world), loader, markUnreadable, migration, (data, path) -> {}, legacyDataIds);
    }

    private static <T extends SavedData> Optional<T> loadDataIfPresentSafely(
            SavedDataStorage storage, Path dataFolder, HolderLookup.Provider provider, String dataId,
            Supplier<T> emptyFactory,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            BiConsumer<T, Path> markUnreadable, SavedDataPayloadMigration migration,
            BiConsumer<T, Path> loadedFrom, String... legacyDataIds
    ) {
        SavedDataType<T> type = createSavedDataType(provider, loader, emptyFactory, dataId);
        Optional<T> cached = getCachedSavedData(storage, type);
        if (cached != null) {
            return cached;
        }

        Path folder = dataFolder.toAbsolutePath().normalize();
        Path current = type.id().withSuffix(".dat").resolveAgainst(folder).normalize();
        List<String> ids = savedDataLookupIds(dataId, legacyDataIds);
        Stream<Path> flatCandidates = flatSavedDataCandidates(folder, ids);
        Path legacyFolder = legacySavedDataFolder(folder);
        if (legacyFolder != null) {
            flatCandidates = Stream.concat(flatCandidates, flatSavedDataCandidates(legacyFolder, ids));
        }
        if (!current.startsWith(folder)) {
            throw new IllegalArgumentException("Saved data path outside storage directory: " + current);
        }
        List<Path> candidates = Stream.concat(Stream.of(current), flatCandidates).distinct().toList();
        Path unreadable = null;
        for (Path candidate : candidates) {
            if (Files.notExists(candidate)) {
                continue;
            }
            try {
                CompoundTag root = readRawSavedData(candidate);
                if (!(root.get("data") instanceof CompoundTag payload)) {
                    throw new IOException("Missing saved data compound");
                }
                boolean migrated = migration.migrate(payload, root.get("DataVersion"), candidate.equals(current), provider);
                RegistryOps<Tag> ops = provider.createSerializationContext(NbtOps.INSTANCE);
                T data = type.codec().parse(ops, payload).getOrThrow();
                loadedFrom.accept(data, candidate);
                if (migrated) {
                    data.setDirty();
                }
                if (unreadable != null) {
                    markUnreadable.accept(data, unreadable);
                }
                boolean dirty = data.isDirty();
                storage.set(type, data);
                if (candidate.equals(current) && !dirty) {
                    data.setDirty(false);
                }
                return Optional.of(data);
            } catch (Exception exception) {
                MCA.LOGGER.error("Error loading saved data '{}'", candidate, exception);
                if (unreadable == null) {
                    unreadable = candidate;
                }
            }
        }
        if (unreadable != null) {
            T data = emptyFactory.get();
            if (data != null) {
                markUnreadable.accept(data, unreadable);
                storage.set(type, data);
                return Optional.of(data);
            }
        }
        return Optional.empty();
    }

    private static Stream<Path> flatSavedDataCandidates(Path folder, List<String> ids) {
        return ids.stream().map(id -> {
            Path candidate = folder.resolve(id + ".dat").normalize();
            if (!candidate.startsWith(folder)) {
                throw new IllegalArgumentException("Saved data path outside storage directory: " + candidate);
            }
            return candidate;
        });
    }

    /** The 26.1 file fixer relocates known vanilla files, leaving old mod data in these directories. */
    static @Nullable Path legacySavedDataFolder(Path dataFolder) {
        Path folder = dataFolder.toAbsolutePath().normalize();
        String legacyDirectory;
        if (folder.endsWith(Path.of("dimensions", "minecraft", "overworld", "data"))) {
            legacyDirectory = "data";
        } else if (folder.endsWith(Path.of("dimensions", "minecraft", "the_nether", "data"))) {
            legacyDirectory = "DIM-1/data";
        } else if (folder.endsWith(Path.of("dimensions", "minecraft", "the_end", "data"))) {
            legacyDirectory = "DIM1/data";
        } else {
            return null;
        }
        Path worldRoot = folder.getParent().getParent().getParent().getParent();
        return worldRoot.resolve(legacyDirectory);
    }

    @SuppressWarnings("deprecation")
    static void spawnEntity(Level world, Mob entity, EntitySpawnReason reason) {
        trySpawnEntity(world, entity, reason);
    }

    @SuppressWarnings("deprecation")
    static boolean trySpawnEntity(Level world, Mob entity, EntitySpawnReason reason) {
        if (world instanceof ServerLevel serverLevel) {
            entity.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(entity.blockPosition()), reason, null);
        }
        return world.addFreshEntity(entity);
    }

    //a wrapper for the unnecessary complex query provided by minecraft
    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, Identifier structure, int radius) {
        HolderLookup.RegistryLookup<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> entry = registry.get(ResourceKey.create(Registries.STRUCTURE, structure));
        if (entry.isPresent()) {
            HolderSet.Direct<Structure> of = HolderSet.direct(entry.get());
            Pair<BlockPos, Holder<Structure>> pair = world.getChunkSource().getGenerator().findNearestMapStructure(world, of, center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
    }

    private static CompoundTag serializeSavedData(SavedData data, HolderLookup.Provider provider) {
        try {
            Method save = data.getClass().getMethod("save", CompoundTag.class, HolderLookup.Provider.class);
            Object encoded = save.invoke(data, new CompoundTag(), provider);
            if (encoded instanceof CompoundTag tag) {
                return tag;
            }
            throw new IllegalStateException("Unexpected saved data encoder result for " + data.getClass().getName());
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not encode " + data.getClass().getName(), exception);
        }
    }

    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, TagKey<Structure> tag, int radius) {
        HolderLookup.RegistryLookup<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        var entryList = registry.get(tag);
        if (entryList.isPresent()) {
            var chunkGenerator = world.getChunkSource().getGenerator();
            Pair<BlockPos, Holder<Structure>> pair = chunkGenerator.findNearestMapStructure(world, entryList.get(), center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
    }

    static boolean isChunkLoaded(ServerLevel world, Vec3i pos) {
        return isChunkLoaded(world, new BlockPos(pos.getX(), pos.getY(), pos.getZ()));
    }

    static boolean isChunkLoaded(ServerLevel world, BlockPos pos) {
        ChunkPos chunkPos = ChunkPos.containing(pos);
        LevelChunk worldChunk = world.getChunkSource().getChunkNow(chunkPos.x(), chunkPos.z());
        if (worldChunk != null) {
            return worldChunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && world.areEntitiesLoaded(chunkPos.pack());
        }
        return false;
    }

    static boolean isAreaLoaded(ServerLevel world, ChunkPos pos, int radius) {
        ServerChunkCache chunkManager = world.getChunkSource();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!chunkManager.hasChunk(pos.x() + x, pos.z() + z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static <T extends SavedData> SavedDataType<T> createSavedDataType(
            HolderLookup.Provider provider,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            Supplier<T> factory,
            String dataId
    ) {
        return new SavedDataType<>(
                MCA.locate(dataId),
                factory,
                CompoundTag.CODEC.xmap(
                        tag -> loader.apply(tag, provider),
                        data -> serializeSavedData(data, provider)
                ),
                null
        );
    }

    private static CompoundTag readRawSavedData(Path path) throws IOException {
        // MCA owns this schema and has no vanilla DataFixTypes entry.
        try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(path))) {
            input.mark(2);
            boolean gzip = input.read() == 0x1f && input.read() == 0x8b;
            input.reset();
            return gzip
                    ? NbtIo.readCompressed(input, NbtAccounter.unlimitedHeap())
                    : NbtIo.read(new DataInputStream(input));
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends SavedData> @Nullable Optional<T> getCachedSavedData(SavedDataStorage storage, SavedDataType<T> type) {
        try {
            var field = SavedDataStorage.class.getDeclaredField("cache");
            field.setAccessible(true);
            return (Optional<T>) ((Map<?, ?>) field.get(storage)).get(type);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access saved data cache", exception);
        }
    }

    static Path getSavedDataFolder(SavedDataStorage storage) {
        try {
            var field = SavedDataStorage.class.getDeclaredField("dataFolder");
            field.setAccessible(true);
            return ((Path) field.get(storage)).toAbsolutePath().normalize();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not resolve saved data directory", exception);
        }
    }
}
