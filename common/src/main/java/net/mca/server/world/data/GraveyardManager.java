package net.mca.server.world.data;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArraySet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

import java.util.*;
import java.util.function.LongFunction;

/**
 * Tracks the positions where a tombstone may be found and whether it is filled or empty.
 * <p>
 * Because this structure can potentially be very large, we have to take special considerations into account.
 * Besides already using fast-collections we also try not to create or store any BlockPos instances without need.
 */
public class GraveyardManager extends SavedData {

    private final Map<TombstoneState, Long2ObjectMap<ChunkBase>> tombstones = new EnumMap<>(TombstoneState.class);

    public static GraveyardManager get(ServerLevel world) {
        return WorldUtils.loadData(world, GraveyardManager::new, GraveyardManager::new, "mca_graveyard");
    }

    public GraveyardManager(ServerLevel world) { }

    public GraveyardManager(CompoundTag nbt) {
        tombstones.putAll(NbtHelper.toMap(nbt, TombstoneState::valueOf, v -> {
            CompoundTag vv = (CompoundTag)v;
            Long2ObjectMap<ChunkBase> map = new Long2ObjectOpenHashMap<>();
            vv.getAllKeys().forEach(key -> {
                map.put(Long.parseLong(key), new Chunk((ListTag)vv.get(key)));
            });
            return map;
        }));
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        CompoundTag tag = new CompoundTag();
        synchronized (tombstones) {
            tombstones.forEach((state, chunks) -> {
                CompoundTag chunkList = new CompoundTag();

                chunks.long2ObjectEntrySet().forEach(entry -> {
                    if (!entry.getValue().isEmpty()) {
                        chunkList.put(String.valueOf(entry.getLongKey()), entry.getValue().toNbt());
                    }
                });

                if (!chunkList.isEmpty()) {
                    tag.put(state.name(), chunkList);
                }
            });
        }
        return tag;
    }

    public void setTombstoneState(BlockPos pos, TombstoneState state) {
        synchronized (tombstones) {
            long l = getChunkPos(pos);
            getChunk(state.opposite(), l, ChunkBase::empty).removePos(pos);
            getChunk(state, l, Chunk::new).addPos(pos);
            setDirty();
        }
    }

    public void removeTombstoneState(BlockPos pos) {
        synchronized (tombstones) {
            long l = getChunkPos(pos);
            getChunk(TombstoneState.EMPTY, l, ChunkBase::empty).removePos(pos);
            getChunk(TombstoneState.FILLED, l, ChunkBase::empty).removePos(pos);
            setDirty();
        }
    }

    public List<BlockPos> findAll(AABB box, boolean includeEmpty, boolean includeFilled) {
        List<BlockPos> positions = new ArrayList<>();

        if (includeEmpty || includeFilled) {
            int minX = Mth.floor((box.minX - 2) / 16D);
            int maxX = Mth.ceil((box.maxX + 2) / 16D);
            int minZ = Mth.floor((box.minZ - 2) / 16D);
            int maxZ = Mth.ceil((box.maxZ + 2) / 16D);

            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            synchronized (tombstones) {
                for (int x = minX; x < maxX; x++) {
                    for (int z = minZ; z < maxZ; z++) {
                        long l = ChunkPos.asLong(x, z);

                        if (includeEmpty) {
                            getChunk(TombstoneState.EMPTY, l, ChunkBase::empty).appendAll(box, mutable, positions);
                        }
                        if (includeFilled) {
                            getChunk(TombstoneState.FILLED, l, ChunkBase::empty).appendAll(box, mutable, positions);
                        }
                    }
                }
            }
        }

        return positions;
    }

    public Optional<BlockPos> findNearest(BlockPos pos, TombstoneState state, int maxChunkRange) {
        synchronized (tombstones) {
            BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

            // first we check the immediate chunk
            return getChunk(state, getChunkPos(pos), ChunkBase::empty).findNearest(pos, mutable).or(() -> {
                // then we iterate outwards checking surrounding chunks
                BlockPos center = new BlockPos(SectionPos.blockToSectionCoord(pos.getX()), 0, SectionPos.blockToSectionCoord(pos.getZ()));
                // luckily BlockPos has a useful utility for this already
                return BlockPos.withinManhattanStream(center, maxChunkRange, 0, maxChunkRange)
                    .map(p -> ChunkPos.asLong(p.getX(), p.getZ()))
                    .map(l -> getChunk(state, l, ChunkBase::empty).findNearest(pos, mutable))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .min(Comparator.comparing(a -> a.distSqr(pos)));
            });
        }
    }

    private static long getChunkPos(BlockPos pos) {
        return ChunkPos.asLong(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }

    private ChunkBase getChunk(TombstoneState state, long pos, LongFunction<ChunkBase> fallback) {

        Long2ObjectMap<ChunkBase> chunks = tombstones.computeIfAbsent(state, n -> new Long2ObjectOpenHashMap<>());

        ChunkBase chunk = chunks.get(pos);

        if (chunk == null) {
            chunk = fallback.apply(pos);
            if (chunk != ChunkBase.EMPTY) {
                chunks.put(pos, chunk);
            }
        }

        return chunk;
    }

    public void reportToVillageManager(Entity entity) {
        VillageManager manager = VillageManager.get((ServerLevel)entity.level());
        GraveyardManager.get((ServerLevel)entity.level())
                .findAll(entity.getBoundingBox().inflate(24D), true, true)
                .stream()
                .filter(p -> !manager.cache.contains(p))
                .forEach(manager::processBuilding);
    }

    private static class ChunkBase {
        static final ChunkBase EMPTY = new ChunkBase();

        static ChunkBase empty(long l) {
            return EMPTY;
        }

        public boolean isEmpty() {
            return true;
        }

        public ListTag toNbt() {
            return new ListTag();
        }

        public void removePos(BlockPos pos) {
        }

        public void addPos(BlockPos pos) {
        }

        public Optional<BlockPos> findNearest(BlockPos pos, BlockPos.MutableBlockPos mutable) {
            return Optional.empty();
        }

        public void appendAll(AABB box, BlockPos.MutableBlockPos mutable, List<BlockPos> positions) {

        }
    }

    private static class Chunk extends ChunkBase {
        private final LongSet tombstones = new LongArraySet();

        Chunk(long l) {}

        Chunk(ListTag list) {
            list.forEach(l -> tombstones.add(((NumericTag)l).getAsLong()));
        }

        @Override
        public boolean isEmpty() {
            return tombstones.isEmpty();
        }

        @Override
        public ListTag toNbt() {
            ListTag list = new ListTag();
            tombstones.forEach((long l) -> list.add(LongTag.valueOf(l)));
            return list;
        }

        @Override
        public void removePos(BlockPos pos) {
            tombstones.remove(pos.asLong());
        }

        @Override
        public void addPos(BlockPos pos) {
            tombstones.add(pos.asLong());
        }

        @Override
        public Optional<BlockPos> findNearest(BlockPos pos, BlockPos.MutableBlockPos mutable) {
            double distance = Double.MAX_VALUE;
            long nearest = -1;
            boolean found = false;

            // we do it oldschool to preserve thread safety
            for (long l : tombstones) {
                mutable.set(l);
                double d = pos.distSqr(mutable);
                if (d < distance) {
                    distance = d;
                    nearest = l;
                    found = true;
                }
            }

            return found ? Optional.of(BlockPos.of(nearest)) : Optional.empty();
        }

        @Override
        public void appendAll(AABB box, BlockPos.MutableBlockPos mutable, List<BlockPos> positions) {
            for (long l : tombstones) {
                mutable.set(l);
                if (box.contains(mutable.getX(), mutable.getY(), mutable.getZ())) {
                    positions.add(mutable.immutable());
                }
            }
        }
    }

    public enum TombstoneState {
        EMPTY,
        FILLED;

        TombstoneState opposite() {
            return this == EMPTY ? FILLED : EMPTY;
        }
    }
}
