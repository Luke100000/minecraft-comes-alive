package net.mca.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Optional;
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

    static <T extends SavedData> T loadData(ServerLevel world, Function<CompoundTag, T> loader, Function<ServerLevel, T> factory, String dataId) {
        return world.getDataStorage().computeIfAbsent(loader, () -> factory.apply(world), dataId);
    }

    static void spawnEntity(Level world, Mob entity, MobSpawnType reason) {
        entity.finalizeSpawn((ServerLevelAccessor) world, world.getCurrentDifficultyAt(entity.blockPosition()), reason, null, null);
        world.addFreshEntity(entity);
    }

    //a wrapper for the unnecessary complex query provided by minecraft
    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, ResourceLocation structure, int radius) {
        Registry<Structure> registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Structure feature = registry.get(structure);
        Optional<Holder.Reference<Structure>> entry = registry.getHolder(registry.getId(feature));
        if (entry.isPresent()) {
            HolderSet.Direct<Structure> of = HolderSet.direct(entry.get());
            Pair<BlockPos, Holder<Structure>> pair = world.getChunkSource().getGenerator().findNearestMapStructure(world, of, center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
    }

    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, TagKey<Structure> tag, int radius) {
        Registry<Structure> registry = world.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var entryList = registry.getTag(tag);
        if (entryList.isPresent()) {
            var chunkGenerator = world.getChunkSource().getGenerator();
            Pair<BlockPos, Holder<Structure>> pair = chunkGenerator.findNearestMapStructure(world, entryList.get(), center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
    }

    static boolean isChunkLoaded(Level world, Vec3i pos) {
        if (world instanceof ServerLevel serverWorld) {
            return isChunkLoaded(serverWorld, pos);
        }
        return false;
    }

    static boolean isChunkLoaded(ServerLevel world, Vec3i pos) {
        return isChunkLoaded(world, new BlockPos(pos));
    }

    static boolean isChunkLoaded(ServerLevel world, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        LevelChunk worldChunk = world.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (worldChunk != null) {
            return worldChunk.getFullStatus() == FullChunkStatus.ENTITY_TICKING && world.areEntitiesLoaded(chunkPos.toLong());
        }
        return false;
    }

    static boolean isAreaLoaded(ServerLevel world, ChunkPos pos, int radius) {
        ServerChunkCache chunkManager = world.getChunkSource();
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (!chunkManager.hasChunk(pos.x + x, pos.z + z)) {
                    return false;
                }
            }
        }
        return true;
    }
}
