package net.mca.server;

import net.mca.Config;
import net.mca.MCA;
import net.mca.SoundsMCA;
import net.mca.block.BlocksMCA;
import net.mca.entity.EntitiesMCA;
import net.mca.entity.GrimReaperEntity;
import net.mca.server.world.data.VillageManager;
import net.mca.util.WorldUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ReaperSpawner {
    private static final Direction[] HORIZONTALS = new Direction[]{
            Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
    };

    private final Object lock = new Object();

    private final Map<Long, ActiveSummon> activeSummons = new ConcurrentHashMap<>();

    private final VillageManager manager;

    public ReaperSpawner(VillageManager manager) {
        this.manager = manager;
    }

    public ReaperSpawner(VillageManager manager, NbtCompound nbt) {
        this.manager = manager;
        net.mca.util.NbtHelper.toList(nbt.getList("summons", NbtElement.COMPOUND_TYPE), n -> new ActiveSummon((NbtCompound) n)).forEach(summon ->
                activeSummons.put(summon.position.spawnPosition.asLong(), summon)
        );
    }

    private void warn(World world, BlockPos pos, String phrase) {
        world.getPlayers().stream()
                .min(Comparator.comparingInt(a -> a.getBlockPos().getManhattanDistance(pos)))
                .ifPresent(p -> p.sendMessage(Text.translatable(phrase).formatted(Formatting.RED), true));
    }

    public void trySpawnReaper(ServerWorld world, BlockPos pos) {
        if (!Config.getInstance().allowGrimReaper) {
            return;
        }

        ChunkPos chunkPos = new ChunkPos(pos);

        // Make sure the neighboring chunks are loaded
        if (!WorldUtils.isAreaLoaded(world, chunkPos, 1)) {
            return;
        }

        if (world.getBlockState(pos).getBlock() != Blocks.EMERALD_BLOCK) {
            return;
        }

        MCA.LOGGER.info("Attempting to spawn reaper at {} in {}", pos, world.getRegistryKey().getValue());

        if (!isNightTime(world)) {
            warn(world, pos, "reaper.day");
            return;
        }

        Set<BlockPos> totems = getTotemsFires(world, pos);

        MCA.LOGGER.info("It is night time, found {} totems", totems.size());

        if (totems.size() < 3) {
            warn(world, pos, "reaper.totems");
            return;
        }

        start(new SummonPosition(pos.up(), totems));

        EntityType.LIGHTNING_BOLT.spawn(world, pos, SpawnReason.TRIGGERED);

        world.setBlockState(pos, Blocks.SOUL_SOIL.getDefaultState(), Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
        world.setBlockState(pos.up(), BlocksMCA.INFERNAL_FLAME.get().getDefaultState(), Block.NOTIFY_NEIGHBORS | Block.NOTIFY_LISTENERS);
        totems.forEach(totem ->
                world.setBlockState(totem, BlocksMCA.INFERNAL_FLAME.get().getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE)
        );
    }

    private void start(SummonPosition pos) {
        activeSummons.computeIfAbsent(pos.spawnPosition.asLong(), ActiveSummon::new).start(pos);
        manager.markDirty();
    }

    public void tick(ServerWorld world) {
        boolean empty = activeSummons.isEmpty();
        activeSummons.values().removeIf(summon -> {
            try {
                return summon.tick(world);
            } catch (Exception e) {
                MCA.LOGGER.error("Exception ticking summon", e);
                return true;
            }
        });
        if (!empty) {
            manager.markDirty();
        }
    }

    private boolean isNightTime(World world) {
        long time = world.getTimeOfDay() % 24000;
        MCA.LOGGER.info("Current time is {}", time);
        return time >= 13000 && time <= 23000;
    }

    private Set<BlockPos> getTotemsFires(World world, BlockPos pos) {
        int groundY = pos.getY() - 1;
        int leftSkyHeight = world.getTopY() - groundY;
        int minPillarHeight = Math.min(Config.getInstance().minPillarHeight, leftSkyHeight);
        BlockPos.Mutable target = new BlockPos.Mutable();
        return Stream.of(HORIZONTALS).map(d -> target.set(pos).setY(groundY).move(d, 3)).filter(pillarPos -> {
            for (int height = 1; height <= leftSkyHeight; height++) {
                pillarPos.setY(groundY + height);
                if (world.getBlockState(pillarPos).isOf(Blocks.OBSIDIAN)) {
                    continue;
                } else if (world.getBlockState(pillarPos).isIn(BlockTags.FIRE)) {
                    return height - 1 >= minPillarHeight; // except fire one height
                } else {
                    return false;
                }
            }
            return false;
        }).map(BlockPos::toImmutable).collect(Collectors.toSet());
    }

    public NbtCompound writeNbt() {
        synchronized (lock) {
            NbtCompound nbt = new NbtCompound();
            nbt.put("summons", net.mca.util.NbtHelper.fromList(activeSummons.values(), ActiveSummon::write));
            return nbt;
        }
    }

    static class SummonPosition {
        public final BlockPos spawnPosition;
        public final BlockPos fire;
        public final Set<BlockPos> totems;

        public SummonPosition(NbtCompound tag) {
            if (tag.contains("fire") || tag.contains("totems") || tag.contains("spawnPosition")) {
                fire = NbtHelper.toBlockPos(tag.getCompound("fire"));
                totems = new HashSet<>(net.mca.util.NbtHelper.toList(tag.getCompound("totems"), v -> NbtHelper.toBlockPos((NbtCompound) v)));
                spawnPosition = NbtHelper.toBlockPos(tag.getCompound("spawnPosition"));
            } else {
                totems = new HashSet<>();
                spawnPosition = NbtHelper.toBlockPos(tag);
                fire = spawnPosition.down(10);
            }
        }

        public SummonPosition(BlockPos fire, Set<BlockPos> totems) {
            this.fire = fire;
            this.spawnPosition = fire.up(10);
            this.totems = totems;
        }

        public boolean isCancelled(World world) {
            return !check(fire, world);
        }

        private boolean check(BlockPos pos, World world) {
            return world.getBlockState(pos).isOf(BlocksMCA.INFERNAL_FLAME.get());
        }

        public NbtCompound toNbt() {
            NbtCompound tag = new NbtCompound();
            tag.put("fire", NbtHelper.fromBlockPos(fire));
            tag.put("totems", net.mca.util.NbtHelper.fromList(totems, NbtHelper::fromBlockPos));
            tag.put("spawnPosition", NbtHelper.fromBlockPos(spawnPosition));
            return tag;
        }
    }

    static class ActiveSummon {
        private int ticks;
        private SummonPosition position;

        ActiveSummon(long l) {
            // nop
        }

        ActiveSummon(NbtCompound nbt) {
            ticks = nbt.getInt("ticks");
            position = new SummonPosition(nbt.getCompound("position"));
        }

        public void start(SummonPosition pos) {
            if (ticks <= 0) {
                position = pos;
                ticks = 100;
            }
        }

        /**
         * Updates this summoning instance. Returns true once complete.
         */
        public boolean tick(ServerWorld world) {
            if (ticks <= 0 || position == null) {
                return true;
            }

            if (position.isCancelled(world)) {
                position.totems.forEach(totem -> {
                    if (position.check(totem, world)) {
                        world.setBlockState(totem, Blocks.FIRE.getDefaultState());
                    }
                });
                position = null;
                ticks = 0;
                return true;
            }

            if (--ticks % 20 == 0) {
                EntityType.LIGHTNING_BOLT.spawn(world, position.spawnPosition, SpawnReason.TRIGGERED);
            }

            if (ticks == 0) {
                GrimReaperEntity reaper = EntitiesMCA.GRIM_REAPER.get().spawn(world, position.spawnPosition, SpawnReason.TRIGGERED);
                if (reaper != null) {
                    reaper.playSound(SoundsMCA.REAPER_SUMMON.get(), 1.0F, 1.0F);
                }

                return true;
            }

            return false;
        }

        public NbtCompound write() {
            NbtCompound nbt = new NbtCompound();
            nbt.putInt("ticks", ticks);
            nbt.put("position", position.toNbt());
            return nbt;
        }
    }
}
