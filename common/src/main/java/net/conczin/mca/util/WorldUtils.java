package net.conczin.mca.util;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.datafix.DataFixTypes;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.StreamSupport;

public interface WorldUtils {
    Field TAG_VALUE_INPUT_FIELD = findTagValueInputField();

    interface NbtSavedData {
        CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider);
    }

    static Field findTagValueInputField() {
        try {
            Field field = TagValueInput.class.getDeclaredField("input");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to access TagValueInput backing tag", e);
        }
    }

    static CompoundTag getCompoundTag(ValueInput input) {
        if (input instanceof TagValueInput tagValueInput) {
            try {
                return ((CompoundTag) TAG_VALUE_INPUT_FIELD.get(tagValueInput)).copy();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Unable to read TagValueInput backing tag", e);
            }
        }

        return new CompoundTag();
    }

    static CompoundTag getCompoundTag(ValueOutput output) {
        if (output instanceof TagValueOutput tagValueOutput) {
            return tagValueOutput.buildResult();
        }

        return new CompoundTag();
    }

    static ValueInput createValueInput(CompoundTag nbt, HolderLookup.Provider provider) {
        return TagValueInput.create(ProblemReporter.DISCARDING, provider, nbt);
    }

    static TagValueOutput createValueOutput(HolderLookup.Provider provider) {
        return TagValueOutput.createWithContext(ProblemReporter.DISCARDING, provider);
    }

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
    static <T extends SavedData & NbtSavedData> T loadData(ServerLevel world, BiFunction<CompoundTag, HolderLookup.Provider, T> loader, Function<ServerLevel, T> factory, String dataId) {
        return world.getDataStorage().computeIfAbsent(
                new SavedDataType<>(
                        dataId,
                        () -> factory.apply(world),
                        CompoundTag.CODEC.xmap(
                                nbt -> loader.apply(nbt, world.registryAccess()),
                                data -> data.save(new CompoundTag(), world.registryAccess())
                        ),
                        DataFixTypes.LEVEL
                )
        );
    }

    static void spawnEntity(Level world, Mob entity, EntitySpawnReason reason) {
        ServerLevelAccessor levelAccessor = (ServerLevelAccessor) world;
        entity.finalizeSpawn(levelAccessor, levelAccessor.getCurrentDifficultyAt(entity.blockPosition()), reason, null);
        world.addFreshEntity(entity);
    }

    //a wrapper for the unnecessary complex query provided by minecraft
    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, Identifier structure, int radius) {
        Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Optional<Holder.Reference<Structure>> entry = registry.get(structure);
        if (entry.isPresent()) {
            HolderSet.Direct<Structure> of = HolderSet.direct(entry.get());
            Pair<BlockPos, Holder<Structure>> pair = world.getChunkSource().getGenerator().findNearestMapStructure(world, of, center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
    }

    static Optional<BlockPos> getClosestStructurePosition(ServerLevel world, BlockPos center, TagKey<Structure> tag, int radius) {
        Registry<Structure> registry = world.registryAccess().lookupOrThrow(Registries.STRUCTURE);
        List<Holder<Structure>> entryList = StreamSupport.stream(registry.getTagOrEmpty(tag).spliterator(), false).toList();
        if (!entryList.isEmpty()) {
            var chunkGenerator = world.getChunkSource().getGenerator();
            Pair<BlockPos, Holder<Structure>> pair = chunkGenerator.findNearestMapStructure(world, HolderSet.direct(entryList), center, radius, false);
            return pair == null ? Optional.empty() : Optional.ofNullable(pair.getFirst());
        } else {
            return Optional.empty();
        }
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
