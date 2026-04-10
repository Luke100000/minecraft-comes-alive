package net.conczin.mca.server;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.GrimReaperEntity;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.registry.SoundsMCA;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    public ReaperSpawner(VillageManager manager, CompoundTag nbt) {
        this.manager = manager;
        NbtHelper.toList(nbt.getList("summons").orElseGet(ListTag::new), n -> new ActiveSummon((CompoundTag) n)).forEach(summon ->
                activeSummons.put(summon.position.spawnPosition.asLong(), summon)
        );
    }

    private void warn(Level world, BlockPos pos, String phrase) {
        world.players().stream()
                .min(Comparator.comparingInt(a -> a.blockPosition().distManhattan(pos)))
                .ifPresent(p -> p.displayClientMessage(Component.translatable(phrase).withStyle(ChatFormatting.RED), true));
    }

    public void trySpawnReaper(ServerLevel world, BlockPos pos) {
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

        MCA.LOGGER.info("Attempting to spawn reaper at {} in {}", pos, world.dimension().identifier());

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

        start(new SummonPosition(pos.above(), totems));

        EntityType.LIGHTNING_BOLT.spawn(world, pos, EntitySpawnReason.TRIGGERED);

        world.setBlock(pos, Blocks.SOUL_SOIL.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        world.setBlock(pos.above(), BlocksMCA.INFERNAL_FLAME.defaultBlockState(), Block.UPDATE_NEIGHBORS | Block.UPDATE_CLIENTS);
        totems.forEach(totem ->
                world.setBlock(totem, BlocksMCA.INFERNAL_FLAME.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE)
        );
    }

    private void start(SummonPosition pos) {
        activeSummons.computeIfAbsent(pos.spawnPosition.asLong(), ActiveSummon::new).start(pos);
        manager.setDirty();
    }

    public void tick(ServerLevel world) {
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
            manager.setDirty();
        }
    }

    private boolean isNightTime(Level world) {
        long time = world.getDayTime() % 24000;
        MCA.LOGGER.info("Current time is {}", time);
        return time >= 13000 && time <= 23000;
    }

    private Set<BlockPos> getTotemsFires(Level world, BlockPos pos) {
        int groundY = pos.getY() - 1;
        int leftSkyHeight = world.getMaxY() - groundY;
        int minPillarHeight = Math.min(Config.getInstance().minPillarHeight, leftSkyHeight);
        BlockPos.MutableBlockPos target = new BlockPos.MutableBlockPos();
        return Stream.of(HORIZONTALS).map(d -> target.set(pos).setY(groundY).move(d, 3)).filter(pillarPos -> {
            for (int height = 1; height <= leftSkyHeight; height++) {
                pillarPos.setY(groundY + height);
                if (world.getBlockState(pillarPos).is(Blocks.OBSIDIAN)) {
                    continue;
                } else if (world.getBlockState(pillarPos).is(BlockTags.FIRE)) {
                    return height - 1 >= minPillarHeight; // except fire one height
                } else {
                    return false;
                }
            }
            return false;
        }).map(BlockPos::immutable).collect(Collectors.toSet());
    }

    public CompoundTag writeNbt() {
        synchronized (lock) {
            CompoundTag nbt = new CompoundTag();
            nbt.put("summons", NbtHelper.fromList(activeSummons.values(), ActiveSummon::write));
            return nbt;
        }
    }

    static class SummonPosition {
        public final BlockPos spawnPosition;
        public final BlockPos fire;
        public final Set<BlockPos> totems;

        public SummonPosition(CompoundTag tag) {
            spawnPosition = readBlockPos(tag.get("spawnPosition"));
            fire = readBlockPos(tag.get("fire"));
            totems = new HashSet<>(NbtHelper.toList(tag.getList("totems").orElseGet(ListTag::new), v -> readBlockPos(((IntArrayTag) v).getAsIntArray())));
        }

        public SummonPosition(BlockPos fire, Set<BlockPos> totems) {
            this.fire = fire;
            this.spawnPosition = fire.above(10);
            this.totems = totems;
        }

        public static BlockPos readBlockPos(Tag tag) {
            return tag instanceof IntArrayTag array ? readBlockPos(array.getAsIntArray()) : BlockPos.ZERO;
        }

        public static BlockPos readBlockPos(int[] aint) {
            return aint.length == 3 ? new BlockPos(aint[0], aint[1], aint[2]) : BlockPos.ZERO;
        }

        public static IntArrayTag writeBlockPos(BlockPos pos) {
            return new IntArrayTag(new int[]{pos.getX(), pos.getY(), pos.getZ()});
        }

        public boolean isCancelled(Level world) {
            return !check(fire, world);
        }

        private boolean check(BlockPos pos, Level world) {
            return world.getBlockState(pos).is(BlocksMCA.INFERNAL_FLAME);
        }

        public CompoundTag toNbt() {
            CompoundTag tag = new CompoundTag();
            tag.put("fire", writeBlockPos(fire));
            tag.put("totems", NbtHelper.fromList(totems, SummonPosition::writeBlockPos));
            tag.put("spawnPosition", writeBlockPos(spawnPosition));
            return tag;
        }
    }

    static class ActiveSummon {
        private int ticks;
        private SummonPosition position;

        ActiveSummon(long l) {
            // nop
        }

        ActiveSummon(CompoundTag nbt) {
            ticks = nbt.getInt("ticks").orElse(0);
            position = new SummonPosition(nbt.getCompound("position").orElseGet(CompoundTag::new));
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
        public boolean tick(ServerLevel world) {
            if (ticks <= 0 || position == null) {
                return true;
            }

            if (position.isCancelled(world)) {
                position.totems.forEach(totem -> {
                    if (position.check(totem, world)) {
                        world.setBlockAndUpdate(totem, Blocks.FIRE.defaultBlockState());
                    }
                });
                position = null;
                ticks = 0;
                return true;
            }

            if (--ticks % 20 == 0) {
                EntityType.LIGHTNING_BOLT.spawn(world, position.spawnPosition, EntitySpawnReason.TRIGGERED);
            }

            if (ticks == 0) {
                GrimReaperEntity reaper = EntitiesMCA.GRIM_REAPER.spawn(world, position.spawnPosition, EntitySpawnReason.TRIGGERED);
                if (reaper != null) {
                    reaper.playSound(SoundsMCA.REAPER_SUMMON, 1.0F, 1.0F);
                }

                return true;
            }

            return false;
        }

        public CompoundTag write() {
            CompoundTag nbt = new CompoundTag();
            nbt.putInt("ticks", ticks);
            nbt.put("position", position.toNbt());
            return nbt;
        }
    }
}
