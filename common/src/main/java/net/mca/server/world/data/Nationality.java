package net.mca.server.world.data;

import net.mca.util.NbtHelper;
import net.mca.util.WorldUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtInt;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;

public class Nationality extends PersistentState {
    private static final int CHUNK_SIZE = 128;
    private Map<Long, Integer> map = new HashMap<>();
    final Random random = Random.create();

    public static Nationality get(ServerWorld world) {
        return WorldUtils.loadData(world.getServer().getOverworld(), Nationality::new, w -> new Nationality(), "mca_nationality");
    }

    Nationality() {

    }

    Nationality(NbtCompound nbt) {
        map = NbtHelper.toMap(nbt, Long::valueOf, e -> ((NbtInt)e).intValue());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtHelper.fromMap(nbt, map, String::valueOf, NbtInt::of);
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
            {-1, 1},
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
            markDirty();
        }
        return id;
    }
}
