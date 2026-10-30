package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.server.world.data.IndoorRoomCache;
import net.conczin.mca.server.world.data.ShelterLookupProbeGameTests;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Diagnostic reproductions: room success, cold discovery and validated cache reuse are separate claims. */
@PrefixGameTestTemplate(false)
public final class ShelterLookupGameTests {
    private static final String BATCH = "mca_shelter_lookup";
    private static final String TEMPLATE = "gametest/isolated_shelter_lookup";
    private static final List<VillagerEntityMCA> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_BLOCKS = new HashMap<>();

    private ShelterLookupGameTests() {
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true)
    public static void ceilingRaySkipsLeavesWithoutExtending128BlockBudget(GameTestHelper helper) {
        ShelterLookupProbeGameTests.verifyLeavesRespectCeilingBound(helper);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 150)
    public static void savedRoomNineReusesSuccessfulLookupAfterValidation(GameTestHelper helper) throws IOException {
        // New World (6), crop (607,60,-900)..(639,96,-868), Fatos and the room's bed.
        verifySavedRoom(helper, "reported_shelter_room_9", new BlockPos(17, 15, 18));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 150)
    public static void savedRoomFourReusesSuccessfulLookupAfterValidation(GameTestHelper helper) throws IOException {
        // New World (6), crop (614,52,-961)..(646,88,-929), Arlinda and the room's bed.
        verifySavedRoom(helper, "reported_shelter_room_4", new BlockPos(16, 15, 17));
    }

    private static void verifySavedRoom(GameTestHelper helper, String fixture, BlockPos occupant) throws IOException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = placeSavedRoom(helper, fixture);
        BlockPos bed = origin.offset(16, 15, 16);
        BlockPos standing = origin.offset(occupant);
        helper.assertTrue(level.getBlockState(bed).getBlock() instanceof BedBlock, "saved bed is missing");
        VillagerEntityMCA villager = spawn(level, standing);
        IndoorRoomCache cache = VillageManager.get(level).getIndoorRooms();
        IndoorRoomCache.Room[] initial = {null};
        Object[] initialEntry = {null};
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            initial[0] = SeekIndoorShelterTask.currentRoom(villager).orElseThrow(
                    () -> new IllegalStateException(fixture + ": cold lookup did not recognize the saved indoor position"));
            helper.assertTrue(initial[0].floorCells().contains(standing), "lookup returned another room");
            helper.assertTrue(cache.resolve(bed).orElseThrow() == initial[0], "saved occupant and bed are not in the same discovered room");
            initialEntry[0] = entryFor(cache, bed);
            helper.assertTrue(field(initialEntry[0], "completeDependencies").equals(true),
                    "captured house exceeded the bounded dependency proof");
            logEntry(fixture + ":cold", initialEntry[0]);
            ShelterLookupProbeGameTests.log(cache, bed, fixture);
            for (int i = 0; i < 100; i++) assertSameRoom(helper, villager, initial[0]);
            helper.assertTrue(entryFor(cache, bed) == initialEntry[0], "immediate successful lookups repeated floor discovery");
        }).thenIdle(41).thenExecute(() -> {
            var current = SeekIndoorShelterTask.currentRoom(villager).orElseThrow();
            Object entry = entryFor(cache, bed);
            logEntry(fixture + ":after-validation", entry);
            assertValidatedRoom(helper, initial[0], initialEntry[0], current, entry);
            initial[0] = current;
            initialEntry[0] = entry;
        }).thenIdle(41).thenExecute(() -> {
            var current = SeekIndoorShelterTask.currentRoom(villager).orElseThrow();
            Object entry = entryFor(cache, bed);
            assertValidatedRoom(helper, initial[0], initialEntry[0], current, entry);
        }).thenSucceed();
    }

    private static void assertValidatedRoom(GameTestHelper helper, IndoorRoomCache.Room previous, Object previousEntry,
                                            IndoorRoomCache.Room current, Object currentEntry) {
        helper.assertTrue(current.floorCells().equals(previous.floorCells()),
                "unchanged saved room changed membership during validation");
        helper.assertTrue(field(currentEntry, "completeDependencies").equals(true)
                        && current == previous && currentEntry == previousEntry,
                "complete unchanged dependency proof failed to reuse its room");
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 280)
    public static void savedMissingRoofFailureIsCachedUntilRetry(GameTestHelper helper) throws IOException {
        // The actual exposed bed at (606,71,-911), not a roof removed from an intact test house.
        BlockPos origin = placeSavedRoom(helper, "reported_shelter_missing_roof");
        BlockPos bed = origin.offset(16, 15, 16);
        ServerLevel level = helper.getLevel();
        IndoorRoomCache cache = VillageManager.get(level).getIndoorRooms();
        Object[] initialEntry = {null};
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(level.canSeeSky(bed.above()), "captured bed does not have the reported missing roof");
            helper.assertTrue(cache.resolve(bed).isEmpty(), "missing-roof bed was accepted as a sheltered room");
            initialEntry[0] = ((Map<?, ?>) field(cache, "entries")).get(bed);
            helper.assertTrue(initialEntry[0] != null, "failed discovery was not cached");
            ShelterLookupProbeGameTests.log(cache, bed, "reported_shelter_missing_roof");
            for (int i = 0; i < 100; i++) helper.assertTrue(cache.resolve(bed).isEmpty(), "failed shelter unexpectedly succeeded");
            helper.assertTrue(((Map<?, ?>) field(cache, "entries")).get(bed) == initialEntry[0],
                    "immediate failed attempts rescanned the missing-roof house");
        }).thenIdle(41).thenExecute(() -> {
            helper.assertTrue(cache.resolve(bed).isEmpty(), "unchanged exposed bed became sheltered");
            helper.assertTrue(((Map<?, ?>) field(cache, "entries")).get(bed) == initialEntry[0],
                    "failed missing-roof entry incorrectly used the successful-room validation interval");
        }).thenIdle(160).thenExecute(() -> {
            helper.assertTrue(cache.resolve(bed).isEmpty(), "unchanged exposed bed became sheltered on retry");
            Object next = ((Map<?, ?>) field(cache, "entries")).get(bed);
            helper.assertTrue(next != initialEntry[0], "failed missing-roof entry never retried after its failure lifetime");
            MCA.LOGGER.info("[MCA Shelter Lookup] missing-roof failedQueries=100 reusedAfter41Ticks=true retriedAfter201Ticks=true");
        }).thenSucceed();
    }

    private static BlockPos placeSavedRoom(GameTestHelper helper, String fixture) throws IOException {
        prepareChunks(helper);
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(16, 0, 16));
        int sourceY = switch (fixture) {
            case "reported_shelter_room_9" -> 60;
            case "reported_shelter_room_4" -> 52;
            case "reported_shelter_missing_roof" -> 56;
            default -> throw new IllegalArgumentException("Unknown saved room: " + fixture);
        };
        // Preserve the distance to the world ceiling: open columns scan to the build-height limit.
        BlockPos origin = new BlockPos(corner.getX(), sourceY, corner.getZ());
        helper.assertTrue(helper.getBounds().contains(origin.getX(), origin.getY(), origin.getZ())
                        && helper.getBounds().contains(origin.getX() + 32, origin.getY() + 36, origin.getZ() + 32),
                "saved-world capture must stay inside its allocated arena");
        var resource = level.getServer().getResourceManager().getResourceOrThrow(MCA.locate("gametest/" + fixture + ".nbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(BuiltInRegistries.BLOCK.asLookup(), NbtIo.readCompressed(input, NbtAccounter.create(32L * 1024 * 1024)));
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(32, 36, 32))) remember(level, pos);
        helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.random,
                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE), "saved room template did not place");
        return origin;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 100)
    public static void bedlessRoomReusesTheActualRoomFromItsRememberedBedFloor(GameTestHelper helper) {
        prepareChunks(helper);
        BlockPos min = buildPartitionedHouse(helper);
        BlockPos bed = min.offset(2, 0, 2);
        BlockPos standing = min.offset(6, 0, 2);
        VillagerEntityMCA villager = spawn(helper.getLevel(), standing);
        IndoorRoomCache cache = VillageManager.get(helper.getLevel()).getIndoorRooms();
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            var room = SeekIndoorShelterTask.currentRoom(villager).orElseThrow();
            helper.assertTrue(room.floorCells().contains(standing) && !room.floorCells().contains(bed),
                    "fixture did not separate the bedroom from the bedless room");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleTypeMCA.SHELTER_BED).orElseThrow().pos().equals(bed),
                    "bedless room unexpectedly found a different bed anchor");
            Object initialEntry = entryFor(cache, bed);
            for (int i = 0; i < 100; i++) {
                helper.assertTrue(!cache.resolve(bed).orElseThrow().floorCells().contains(standing),
                        "bed-only fast lookup unexpectedly recognized the adjacent room");
                helper.assertTrue(cache.resolveRoom(bed, standing).orElseThrow() == room,
                        "occupant lookup could not reuse its bedless room from the discovered bed floor");
                assertSameRoom(helper, villager, room);
            }
            helper.assertTrue(entryFor(cache, bed) == initialEntry, "successful bedless-room queries repeated floor discovery");
            MCA.LOGGER.info("[MCA Shelter Lookup] bedless-room successfulQueries=100 occupantFastPathMatches=true floorEntryReused=true {}",
                    describe(initialEntry));
        }).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 100)
    public static void outsideLookupCanFailWhileReusingDiscoveredFloor(GameTestHelper helper) {
        prepareChunks(helper);
        BlockPos min = buildPartitionedHouse(helper);
        BlockPos bed = min.offset(2, 0, 2);
        BlockPos standing = min.offset(11, 0, 2);
        put(helper.getLevel(), standing.below(), Blocks.STONE.defaultBlockState());
        VillagerEntityMCA villager = spawn(helper.getLevel(), standing);
        IndoorRoomCache cache = VillageManager.get(helper.getLevel()).getIndoorRooms();
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(cache.resolve(bed).isPresent(), "fixture bedroom was not discovered");
            Object initialEntry = entryFor(cache, bed);
            for (int i = 0; i < 100; i++) {
                helper.assertTrue(SeekIndoorShelterTask.currentRoom(villager).isEmpty(), "outside villager was classified as indoors");
            }
            helper.assertTrue(entryFor(cache, bed) == initialEntry, "failed current-room queries repeated floor discovery");
            MCA.LOGGER.info("[MCA Shelter Lookup] outside failedQueries=100 floorEntryReused=true {}", describe(initialEntry));
        }).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = TEMPLATE, skyAccess = true, timeoutTicks = 100)
    public static void unsuccessfulIndoorWalkDoesNotRepeatRoomDiscoveryUntilRetry(GameTestHelper helper) {
        prepareChunks(helper);
        BlockPos min = buildPartitionedHouse(helper);
        BlockPos bed = min.offset(2, 0, 2), standing = min.offset(11, 0, 2);
        ServerLevel level = helper.getLevel();
        put(level, standing.below(), Blocks.STONE.defaultBlockState());
        VillagerEntityMCA villager = spawn(level, standing);
        IndoorRoomCache cache = VillageManager.get(level).getIndoorRooms();
        var walk = LocalInsideBrownianWalk.create(0.5F);
        Object[] entry = {null};
        long[] firstUse = {0};
        helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.assertTrue(!walk.tryStart(level, villager, level.getGameTime()), "outside villager unexpectedly began an indoor walk");
            entry[0] = entryFor(cache, bed);
            firstUse[0] = (long) field(entry[0], "lastUsed");
        }).thenIdle(1).thenExecute(() -> {
            helper.assertTrue(!walk.tryStart(level, villager, level.getGameTime()), "deferred outside walk unexpectedly succeeded");
            helper.assertTrue((long) field(entry[0], "lastUsed") == firstUse[0],
                    "unsuccessful indoor walk repeated its expensive room lookup on the next tick");
        }).thenIdle(40).thenExecute(() -> {
            helper.assertTrue(!walk.tryStart(level, villager, level.getGameTime()), "unchanged outside villager became sheltered");
            helper.assertTrue((long) field(entry[0], "lastUsed") > firstUse[0], "failed indoor walk never retried after its interval");
        }).thenSucceed();
    }

    private static void assertSameRoom(GameTestHelper helper, VillagerEntityMCA villager, IndoorRoomCache.Room expected) {
        helper.assertTrue(SeekIndoorShelterTask.currentRoom(villager).orElseThrow() == expected,
                "unchanged current-room lookup did not reuse discovered room");
    }

    private static VillagerEntityMCA spawn(ServerLevel level, BlockPos standing) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(standing)).spawn(MobSpawnType.STRUCTURE);
        ACTORS.add(villager);
        // These tests exercise lookup/cache ownership, not autonomous navigation or schedule selection.
        villager.setNoAi(true);
        villager.refreshBrain(level);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
        return villager;
    }

    private static BlockPos buildPartitionedHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(27, 12, 27));
        for (int x = -1; x <= 9; x++) {
            for (int z = -1; z <= 5; z++) {
                for (int y = -1; y <= 3; y++) {
                    boolean wall = x == -1 || x == 9 || z == -1 || z == 5 || x == 4;
                    put(level, min.offset(x, y, z), y == -1 || y == 3 || wall
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        put(level, min.offset(4, 0, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, min.offset(4, 1, 2), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        put(level, min.offset(1, 0, 2), bed.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, min.offset(2, 0, 2), bed.setValue(BedBlock.PART, BedPart.HEAD));
        return min;
    }

    private static void prepareChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos min = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
        ChunkPos max = new ChunkPos(helper.absolutePos(new BlockPos(63, 0, 63)));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    FORCED_CHUNKS.add(chunk);
                    level.setChunkForced(x, z, true);
                }
                level.getChunk(x, z);
            }
        }
    }

    private static void remember(ServerLevel level, BlockPos pos) {
        ORIGINAL_BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        remember(level, pos);
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    // Test-only read-only reflection avoids adding instrumentation or widening access in production.
    private static Object field(Object owner, String name) {
        try {
            Field field = owner.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(owner);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Room-cache diagnostic field unavailable: " + name, e);
        }
    }

    private static Object entryFor(IndoorRoomCache cache, BlockPos bed) {
        for (Object entry : ((Map<?, ?>) field(cache, "entries")).values()) {
            for (Object room : (List<?>) field(entry, "rooms")) {
                if (((IndoorRoomCache.Room) room).floorCells().contains(bed)) return entry;
            }
        }
        throw new IllegalStateException("No discovered floor contains fixture bed " + bed);
    }

    private static String describe(Object entry) {
        return "created=" + field(entry, "created") + " completeDependencies=" + field(entry, "completeDependencies")
                + " observed=" + ((Map<?, ?>) field(entry, "observed")).size()
                + " columns=" + ((Map<?, ?>) field(entry, "columns")).size()
                + " heightColumns=" + ((Map<?, ?>) field(entry, "ceilingHeights")).size();
    }

    private static void logEntry(String label, Object entry) {
        MCA.LOGGER.info("[MCA Shelter Lookup] {} {}", label, describe(entry));
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(VillagerEntityMCA::discard);
        ACTORS.clear();
        ORIGINAL_BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        ORIGINAL_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
    }
}
