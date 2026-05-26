package net.conczin.mca.util;

import com.mojang.datafixers.util.Pair;
import net.conczin.mca.MCA;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
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

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

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
        SavedDataType<T> type = createSavedDataType(world, loader, factory, dataId);
        T data = getSavedDataWithLegacyFallback(world, type, dataId);
        if (data != null) {
            return data;
        }

        T created = factory.apply(world);
        world.getDataStorage().set(type, created);
        return created;
    }

    static <T extends SavedData> Optional<T> loadDataIfPresent(ServerLevel world, BiFunction<CompoundTag, HolderLookup.Provider, T> loader, String dataId) {
        SavedDataType<T> type = createSavedDataType(world, loader, ignored -> null, dataId);
        return Optional.ofNullable(getSavedDataWithLegacyFallback(world, type, dataId));
    }

    @SuppressWarnings("deprecation")
    static void spawnEntity(Level world, Mob entity, EntitySpawnReason reason) {
        if (world instanceof ServerLevel serverLevel) {
            entity.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(entity.blockPosition()), reason, null);
        }
        world.addFreshEntity(entity);
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
        } catch (ReflectiveOperationException ignored) {
        }
        return new CompoundTag();
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
        return isChunkLoaded(world, new BlockPos(pos));
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
            ServerLevel world,
            BiFunction<CompoundTag, HolderLookup.Provider, T> loader,
            Function<ServerLevel, T> factory,
            String dataId
    ) {
        return new SavedDataType<>(
                MCA.locate(dataId),
                () -> factory.apply(world),
                CompoundTag.CODEC.xmap(
                        tag -> loader.apply(tag, world.registryAccess()),
                        data -> serializeSavedData(data, world.registryAccess())
                ),
                null
        );
    }

    private static <T extends SavedData> @Nullable T getSavedDataWithLegacyFallback(ServerLevel world, SavedDataType<T> type, String legacyDataId) {
        SavedDataStorage storage = world.getDataStorage();
        T data = storage.get(type);
        if (data != null) {
            return data;
        }

        T legacyData = tryLoadLegacySavedData(world, storage, type, legacyDataId);
        if (legacyData != null) {
            // Cache the migrated data under the new type so later lookups stay coherent.
            storage.set(type, legacyData);
        }
        return legacyData;
    }

    @SuppressWarnings("deprecation")
    private static <T extends SavedData> @Nullable T tryLoadLegacySavedData(ServerLevel world, SavedDataStorage storage, SavedDataType<T> type, String legacyDataId) {
        try {
            Path dataFolder = getSavedDataFolder(storage);
            Path legacyFile = dataFolder.resolve(legacyDataId + ".dat").normalize();
            if (!legacyFile.startsWith(dataFolder) || !Files.exists(legacyFile)) {
                return null;
            }

            CompoundTag tag = storage.readTagFromDisk(legacyFile, type.dataFixType(), SharedConstants.getCurrentVersion().dataVersion().version());
            RegistryOps<Tag> ops = world.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return type.codec()
                    .parse(ops, tag.get("data"))
                    .resultOrPartial(error -> MCA.LOGGER.error("Failed to parse legacy saved data '{}': {}", legacyDataId, error))
                    .orElse(null);
        } catch (ReflectiveOperationException reflectiveOperationException) {
            MCA.LOGGER.error("Failed to resolve saved-data directory for '{}'", legacyDataId, reflectiveOperationException);
        } catch (Exception exception) {
            MCA.LOGGER.error("Error loading legacy saved data '{}'", legacyDataId, exception);
        }

        return null;
    }

    private static Path getSavedDataFolder(SavedDataStorage storage) throws ReflectiveOperationException {
        var field = SavedDataStorage.class.getDeclaredField("dataFolder");
        field.setAccessible(true);
        return ((Path) field.get(storage)).toAbsolutePath().normalize();
    }
}
