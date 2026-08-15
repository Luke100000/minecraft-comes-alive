package net.conczin.mca.server.world.data;

import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.HashMap;
import java.util.Map;

public class Nationality extends SavedData {
    private static final int CHUNK_SIZE = 128;
    private Map<Long, Integer> map = new HashMap<>();
    final RandomSource random = RandomSource.create();

    public static Nationality get(ServerLevel world) {
        return WorldUtils.loadData(world.getServer().overworld(), Nationality::new, w -> new Nationality(), "mca_nationality");
    }

    Nationality() {

    }

    Nationality(CompoundTag nbt) {
        map = NbtHelper.toMap(nbt, Long::valueOf, e -> ((IntTag)e).getAsInt());
    }

    @Override
    public CompoundTag save(CompoundTag nbt) {
        NbtHelper.fromMap(nbt, map, String::valueOf, IntTag::valueOf);
        return nbt;
    }

    private static final int[][] neighbours = {
            {0, 0},
            {-1, 0},
            {1, 0},
            {0, -1},
            {0, 1},
            {-1, 1},
            {1, 1},
            {-1, -1},
            {1, -1},
    };

    private static long toId(long x, long z) {
        return x / CHUNK_SIZE * (long)Integer.MAX_VALUE + z / CHUNK_SIZE;
    }

    public int getRegionId(BlockPos pos) {
        int id = -1;
        for (int[] neighbour : neighbours) {
            int x = pos.getX() + neighbour[0] * CHUNK_SIZE;
            int z = pos.getZ() + neighbour[1] * CHUNK_SIZE;
            long rid = toId(x, z);
            if (map.containsKey(rid)) {
                id = map.get(rid);
                break;
            }
        }
        if (id == -1) {
            id = random.nextInt();
        }

        long rid = toId(pos.getX(), pos.getZ());
        if (!map.containsKey(rid)) {
            map.put(rid, id);
            setDirty();
        }
        return id;
    }
}
