package net.conczin.mca.server.world.data;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class ReportedStairHouseGameTests {
    private ReportedStairHouseGameTests() {
    }

    @GameTest(batch = "mca_reported_doorless_house", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void reportedDoorlessHouseRegistersAndRefreshes(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 8, 40));
        // Read-only capture of MCA Floor NBT Test, Overworld DataVersion 3955.
        // Crop (-320,71,-1646)..(-310,81,-1636); template origin is (-320,71,-1646).
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_doorless_house.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        Map<BlockPos, net.minecraft.world.level.block.state.BlockState> original = new java.util.LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(10, 10, 10))) {
            original.put(pos.immutable(), level.getBlockState(pos));
        }
        try {
            template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
            BlockPos source = origin.offset(4, 3, 4), doorway = origin.offset(4, 3, 3);
            helper.assertTrue(level.getBlockState(doorway).isAir() && level.getBlockState(doorway.above()).isAir(),
                    "saved house does not contain the reported removed entrance");
            helper.assertTrue(!level.getBlockState(source.below()).getCollisionShape(level, source.below()).isEmpty(),
                    "reported room lost its support");
            helper.assertTrue(new FloorCeilingResolver(level).ceilingY(source).isPresent(),
                    "reported room lost its roof");
            RoomWorkflow workflow = new RoomWorkflow(new VillageManager(level), level);
            helper.assertTrue(workflow.analyzeBuildingAddition(source).result() == Building.validationResult.SUCCESS,
                    "reported framed house could not register without a door");
            var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
            level.setBlock(doorway, state, 2);
            level.setBlock(doorway.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            VillageManager manager = new VillageManager(level);
            workflow = new RoomWorkflow(manager, level);
            BuildingScanResult initial = workflow.analyzeBuildingAddition(source);
            helper.assertTrue(initial.result() == Building.validationResult.SUCCESS, "reported door fixture failed");
            helper.assertTrue(workflow.commitAddition(initial,
                            initial.isAmbiguous() ? initial.matchingTypes().getFirst() : null).status()
                            == RoomWorkflow.Status.COMMITTED, "reported room did not register with its door");
            Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
            int roomId = village.findInteractionRoomAt(source).orElseThrow().getId();
            level.setBlock(doorway, Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(doorway.above(), Blocks.AIR.defaultBlockState(), 2);
            helper.assertTrue(workflow.scanRoom(source, roomId, null).status() == RoomWorkflow.Status.COMMITTED,
                    "reported room could not refresh after door removal");
            helper.assertTrue(village.findInteractionRoomAt(source).orElseThrow().getId() == roomId,
                    "reported room identity changed");
        } finally {
            original.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_slab_tower", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void reportedSlabTowerAddsTopFloorWithBoundedOpenBalcony(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 8, 40));
        // Geometry-only reconstruction of house_roof_corrected_v2.schem, cropped to
        // (5043,72,-1017)..(5066,103,-997). Cobblestone slab states are unchanged.
        // Modded wood/stone shapes use vanilla equivalents; furnishings are omitted.
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_slab_tower_probe.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        try {
            verifyTowerStoreyOwnership(helper, origin);
            verifyRemovedTowerBalconyDoors(helper, origin);
            verifySlabTowerFloors(helper, origin);
        } finally {
            // The arena reserves horizontal space, but this tower rises above its template.
            // Clear its owned volume on either success or a synchronous assertion failure.
            for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(23, 31, 20))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }
        helper.succeed();
    }

    private static void verifyTowerStoreyOwnership(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        var lower = SelectedFloorScanner.scan(level, origin.offset(18, 2, 11), 2048, 32);
        var middle = SelectedFloorScanner.scan(level, origin.offset(17, 12, 13), 2048, 32);
        var upper = SelectedFloorScanner.scan(level, origin.offset(17, 18, 11), 2048, 32);
        for (var scan : List.of(lower, middle, upper)) {
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "tower deck did not scan: " + scan.result());
        }
        var turn = SelectedFloorScanner.scan(level, origin.offset(15, 9, 10), 2048, 32);
        helper.assertTrue(turn.result() == Building.validationResult.SUCCESS
                        && (turn.floor().sameExactGeometry(lower.floor())
                        || turn.floor().sameExactGeometry(middle.floor())),
                "slab stair turn invented a fourth storey: anchor=" + (turn.anchorY() - origin.getY())
                        + " area=" + (turn.floor() == null ? 0 : turn.floor().cells().size()));
        // Include the embedded light's full-block top, not just slab-supported entry points.
        for (BlockPos relative : List.of(new BlockPos(14, 10, 12), new BlockPos(16, 11, 12),
                new BlockPos(18, 12, 12))) {
            var scan = SelectedFloorScanner.scan(level, origin.offset(relative), 2048, 32);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS
                            && scan.anchorY() == middle.anchorY()
                            && scan.floor().sameExactGeometry(middle.floor()),
                    "middle deck ownership changes with the approaching slab at " + relative);
        }
        for (int first = 0; first < 3; first++) {
            for (int second = first + 1; second < 3; second++) {
                var a = List.of(lower, middle, upper).get(first).floor();
                var b = List.of(lower, middle, upper).get(second).floor();
                helper.assertTrue(a.cells().stream().noneMatch(cell -> b.cellAt(cell.feet()).isPresent()),
                        "tower storeys claim the same slab cells");
            }
        }
        FloorCeilingResolver ceilings = new FloorCeilingResolver(level);
        for (BlockPos relative : BlockPos.betweenClosed(new BlockPos(14, 2, 8), new BlockPos(21, 20, 15))) {
            BlockPos source = origin.offset(relative);
            if (!(level.getBlockState(source.below()).getBlock() instanceof SlabBlock)
                    || !level.getBlockState(source).getCollisionShape(level, source).isEmpty()
                    || SelectedFloorScanner.inspectSurfaceCell(level, source, ceilings).isEmpty()) continue;
            var scan = SelectedFloorScanner.scan(level, source, 2048, 32);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS
                            && List.of(lower, middle, upper).stream().anyMatch(deck ->
                            deck.anchorY() == scan.anchorY() && deck.floor().sameExactGeometry(scan.floor())),
                    "slab step invented another tower storey at " + relative
                            + ": anchor=" + (scan.anchorY() - origin.getY())
                            + " area=" + (scan.floor() == null ? 0 : scan.floor().cells().size()));
            var support = level.getBlockState(source.below()).getCollisionShape(level, source.below());
            if (support.max(Direction.Axis.Y) < 1.0D) {
                var onSlab = SelectedFloorScanner.scan(level, source.below(), 2048, 32);
                helper.assertTrue(onSlab.result() == Building.validationResult.SUCCESS
                                && onSlab.anchorY() == scan.anchorY()
                                && onSlab.floor().sameExactGeometry(scan.floor()),
                        "standing within the bottom slab changed tower ownership at " + relative);
            }
        }
        // Reusing one physical observation must not let the first query choose later ownership.
        var selections = List.of(Map.entry(new BlockPos(18, 2, 11), lower),
                Map.entry(new BlockPos(15, 9, 10), middle), Map.entry(new BlockPos(14, 10, 12), middle),
                Map.entry(new BlockPos(17, 12, 13), middle), Map.entry(new BlockPos(18, 12, 12), middle),
                Map.entry(new BlockPos(17, 15, 13), upper), Map.entry(new BlockPos(17, 18, 11), upper));
        for (boolean reversed : List.of(false, true)) {
            var observation = new SelectedFloorScanner.Observation(level, 2048, 32);
            for (int index = 0; index < selections.size(); index++) {
                var selected = selections.get(reversed ? selections.size() - 1 - index : index);
                var scan = observation.selected(origin.offset(selected.getKey()));
                helper.assertTrue(scan.result() == Building.validationResult.SUCCESS
                                && scan.anchorY() == selected.getValue().anchorY()
                                && scan.floor().sameExactGeometry(selected.getValue().floor()),
                        "shared observation changed tower ownership at " + selected.getKey()
                                + " reversed=" + reversed);
            }
        }
    }

    @GameTest(batch = "mca_reported_saved_tower_intermediate", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void savedTowerIntermediateRoomsCanBeAdded(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 8, 40));
        CompoundTag snapshot;
        try (var input = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_slab_tower_live_snapshot.nbt")).open()) {
            snapshot = NbtIo.readCompressed(input, NbtAccounter.create(32 * 1024 * 1024));
        }
        StructureTemplate template = new StructureTemplate();
        try (var input = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_slab_tower_probe.snbt")).open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        StructureTemplate changes = new StructureTemplate();
        // This binary snapshot is intentionally preserved as captured in 1.21.1.
        // The 26.3 StructureTemplate loader expects the renamed block-state
        // palette fields even when reading a legacy snapshot directly.
        snapshot.getListOrEmpty("palette").compoundStream().forEach(state -> {
            Tag legacyId = state.remove("Name");
            if (legacyId != null) state.put("id", legacyId);
            Tag legacyProperties = state.remove("Properties");
            if (legacyProperties != null) state.put("properties", legacyProperties);
        });
        changes.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), snapshot);
        changes.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        helper.runAfterDelay(5, () -> {
            var config = net.conczin.mca.Config.getInstance();
            int previousSize = config.maxBuildingSize, previousRadius = config.maxBuildingRadius;
            try {
                config.maxBuildingSize = 8192;
                config.maxBuildingRadius = 320;
                BlockPos delta = origin.offset(0, -72, 0);
                CompoundTag savedManager = (CompoundTag) translateTowerState(snapshot.getCompound("manager").orElseThrow(), delta);
                VillageManager manager = new VillageManager(level, savedManager);
                Village village = manager.getOrEmpty(4).orElseThrow();
                helper.assertTrue(village.getStructures().size() == 3 && village.getStructureCount() == 1,
                        "snapshot must contain exactly one logical building and three registered sections");
                var ordinary = SelectedFloorScanner.scan(level, origin.offset(17, 2, 9), 8192, 320);
                helper.assertTrue(ordinary.result() == Building.validationResult.SUCCESS,
                        "ordinary downstairs surface must scan");
                for (BlockPos reported : List.of(new BlockPos(3, 76, 15), new BlockPos(4, 76, 15))) {
                    var selected = SelectedFloorScanner.scan(level, reported.offset(delta), 8192, 320);
                    helper.assertTrue(selected.result() == Building.validationResult.SUCCESS
                                    && selected.anchorY() == delta.getY() + 73
                                    && ordinary.floor().sameExactGeometry(selected.floor()),
                            "the saved two-block stair turn invented another Floor at " + reported);
                }

                RoomWorkflow workflow = new RoomWorkflow(manager, level);
                BlockPos downstairs = new BlockPos(7, 74, 14).offset(delta);
                var update = workflow.scanRoom(downstairs, 21, null);
                helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED,
                        "updating the saved downstairs Room must adopt its missing stair cells: " + update);
                var turnPlan = RoomScanPlanner.analyze(village, level, new BlockPos(3, 76, 15).offset(delta)).plan();
                helper.assertTrue(turnPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                                && turnPlan.currentRoom().orElseThrow().getId() == 21,
                        "refreshed stair turn lost its existing downstairs Room");

                List<Structure> before = village.getBuildingStructures(14).stream().map(Structure::copy).toList();
                var roomsBefore = village.getRooms().map(Building::copy).toList();
                BlockPos missingFloor = new BlockPos(5, 79, 9).offset(delta);
                var analysis = RoomScanPlanner.analyze(village, level, missingFloor);
                helper.assertTrue(analysis.result() == Building.validationResult.SUCCESS
                                && analysis.plan().mode() == Village.RoomScanMode.ADD_ATTACHMENT
                                && analysis.plan().targetBuildingId() == 14
                                && analysis.plan().prospectiveFloorNumber() == 1,
                        "same-building intermediate Floor must be insertable: " + analysis.result()
                                + "; plan=" + analysis.plan()
                                + "; observation=" + (analysis.observation() == null
                                ? "none" : analysis.observation().scan().result()
                                + "/" + analysis.observation().scan().anchorY()));
                BuildingScanResult addition = workflow.analyzeAttachedRoom(village, analysis, 14);
                helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                        "intermediate Floor could not materialize: " + addition.result());
                helper.assertTrue(village.getStructure(16).orElseThrow().getFloor(0).orElseThrow().floorNumber() == 1,
                        "preview must not renumber registered Floors");
                helper.assertTrue(manager.commitRoomAddition(addition,
                                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null)
                                == Building.validationResult.SUCCESS,
                        "intermediate Floor did not commit");
                helper.assertTrue(village.getStructures().size() == 4 && village.getStructureCount() == 1,
                        "insertion created another logical Building");
                for (Structure previous : before) {
                    Structure current = village.getStructure(previous.getId()).orElseThrow();
                    StructureFloor original = previous.getFloor(0).orElseThrow();
                    StructureFloor committed = current.getFloor(0).orElseThrow();
                    helper.assertTrue(committed.floorNumber() == (original.floorNumber() == 0
                                    ? 0 : original.floorNumber() + 1)
                                    && committed.anchorY() == original.anchorY()
                                    && committed.geometry().sameExactGeometry(original.geometry()),
                            "insertion must shift labels only, not saved Floor identity or geometry");
                }
                for (Building previous : roomsBefore) {
                    Building current = village.getBuilding(previous.getId()).orElseThrow();
                    helper.assertTrue(current.getStructureId() == previous.getStructureId()
                                    && current.getFloorId() == previous.getFloorId()
                                    && current.getFloorCells().equals(previous.getFloorCells()),
                            "insertion changed registered Room ownership");
                }
                CompoundTag committed = village.save();
                helper.assertTrue(manager.commitRoomAddition(addition,
                                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null)
                                == Building.validationResult.IDENTICAL && committed.equals(village.save()),
                        "repeating an addition must not shift labels again");
                Village reloaded = new Village(committed, level);
                helper.assertTrue(reloaded.getStructure(16).orElseThrow().getFloor(0).orElseThrow().floorNumber() == 2
                                && reloaded.getStructure(18).orElseThrow().getFloor(0).orElseThrow().floorNumber() == 3
                                && reloaded.getLogicalBuilding(14).orElseThrow().mainRoomId() == 15,
                        "save/reload changed inserted Floor labels or the main Room");
            } finally {
                config.maxBuildingSize = previousSize;
                config.maxBuildingRadius = previousRadius;
                for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(23, 31, 20))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.succeed();
        });
    }

    /** Translate captured block coordinates only; saved room IDs and storey labels stay unchanged. */
    private static Tag translateTowerState(Tag value, BlockPos delta) {
        if (value instanceof IntArrayTag array && array.getAsIntArray().length == 3) {
            int[] position = array.getAsIntArray();
            return new IntArrayTag(new int[]{position[0] + delta.getX(), position[1] + delta.getY(),
                    position[2] + delta.getZ()});
        }
        if (value instanceof ListTag list) {
            ListTag translated = new ListTag();
            list.forEach(element -> translated.add(translateTowerState(element, delta)));
            return translated;
        }
        if (value instanceof CompoundTag compound) {
            CompoundTag translated = compound.copy();
            for (String key : compound.keySet()) {
                int offset = switch (key) {
                    case "anchorY", "ceilingY", "posY", "pos0Y", "pos1Y" -> delta.getY();
                    case "posX", "pos0X", "pos1X" -> delta.getX();
                    case "posZ", "pos0Z", "pos1Z" -> delta.getZ();
                    default -> 0;
                };
                if (offset != 0 && compound.getInt(key).isPresent()) {
                    translated.putInt(key, compound.getInt(key).orElseThrow() + offset);
                } else {
                    translated.put(key, translateTowerState(compound.get(key), delta));
                }
            }
            return translated;
        }
        return value.copy();
    }

    @GameTest(batch = "mca_reported_slab_tower_refresh", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void reportedSlabTowerUnchangedRefreshPreservesGeometry(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        var level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 8, 40));
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_slab_tower_probe.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        // Exercise settled blocks, not only the tick in which the template was placed.
        helper.runAfterDelay(5, () -> {
            var config = net.conczin.mca.Config.getInstance();
            int previousSize = config.maxBuildingSize, previousRadius = config.maxBuildingRadius;
            try {
                for (int[] limits : List.of(new int[]{2048, 32}, new int[]{8192, 320})) {
                    config.maxBuildingSize = limits[0];
                    config.maxBuildingRadius = limits[1];
                    for (BlockPos registration : List.of(new BlockPos(18, 2, 11),
                            new BlockPos(18, 2, 10), new BlockPos(17, 2, 9))) {
                        VillageManager manager = new VillageManager(level);
                        RoomWorkflow workflow = new RoomWorkflow(manager, level);
                        BlockPos source = origin.offset(registration);
                        BuildingScanResult initial = workflow.analyzeBuildingAddition(source);
                        helper.assertTrue(initial.result() == Building.validationResult.SUCCESS,
                                "lower tower registration failed: " + initial.result());
                        var registrationOutcome = workflow.commitAddition(initial,
                                initial.isAmbiguous() ? initial.matchingTypes().getFirst() : null);
                        helper.assertTrue(registrationOutcome.status() == RoomWorkflow.Status.COMMITTED,
                                "lower tower did not register: " + registrationOutcome);
                        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
                        Building room = village.getRooms().findFirst().orElseThrow();
                        // Exercise the Blueprint's world-aware selection, including
                        // supported-surface handoffs, rather than the raw position lookup.
                        helper.assertTrue(RoomScanPlanner.analyze(village, level, source).plan()
                                        .currentRoom().map(Building::getId).orElse(-1) == room.getId(),
                                "registered tower lost its selection before update at " + registration
                                        + " limits=" + Arrays.toString(limits));
                        Structure structure = village.getStructureFor(room).orElseThrow();
                        StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
                        FloorGeometry originalGeometry = floor.geometry();
                        var originalRoomCells = java.util.Set.copyOf(room.getFloorCells());
                        for (int repetition = 0; repetition < 2; repetition++) {
                            for (BlockPos relative : List.of(registration, new BlockPos(19, 2, 13),
                                    new BlockPos(16, 3, 11), new BlockPos(16, 6, 11), new BlockPos(16, 10, 11))) {
                                BlockPos interaction = origin.offset(relative);
                                var fresh = StructureScanner.scanExistingFloor(
                                        level, structure, floor, interaction, village.getStructures().values());
                                helper.assertTrue(fresh.result() == Building.validationResult.SUCCESS
                                                && originalGeometry.sameExactGeometry(fresh.scannedFloor()),
                                        "unchanged tower refresh changes physical Floor from " + registration
                                                + " at " + relative + " limits=" + Arrays.toString(limits)
                                                + ": " + fresh.result() + " cells=" + originalGeometry.cells().size()
                                                + "->" + (fresh.scannedFloor() == null ? 0 : fresh.scannedFloor().cells().size())
                                                + " seed=" + (fresh.scan() == null ? null : fresh.scan().seed().subtract(origin)));
                                var update = workflow.scanRoom(interaction, room.getId(), null);
                                helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED,
                                        "unchanged tower update failed at " + relative + ": " + update);
                                Building updatedRoom = village.getBuilding(room.getId()).orElseThrow();
                                structure = village.getStructureFor(updatedRoom).orElseThrow();
                                floor = structure.getFloor(updatedRoom.getFloorId()).orElseThrow();
                                helper.assertTrue(originalGeometry.sameExactGeometry(floor.geometry())
                                                && originalRoomCells.equals(java.util.Set.copyOf(updatedRoom.getFloorCells()))
                                                && updatedRoom.getStructureId() == room.getStructureId()
                                                && updatedRoom.getFloorId() == room.getFloorId(),
                                        "unchanged update rewrote tower geometry or identity at " + relative);
                            }
                        }
                    }
                }
            } finally {
                config.maxBuildingSize = previousSize;
                config.maxBuildingRadius = previousRadius;
                for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(23, 31, 20))) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
                }
            }
            helper.succeed();
        });
    }

    private static void verifyRemovedTowerBalconyDoors(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        for (int x = 17; x <= 18; x++) {
            // Mount the doors on the full interior deck, before the raised window sill.
            BlockPos lower = origin.offset(x, 18, 9);
            level.setBlock(lower, door, 2);
            level.setBlock(lower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        }
        // Trace 9 registered (5060,90,-1006), then refreshed at (5059,90,-1006).
        BlockPos registration = origin.offset(17, 18, 11);
        BlockPos source = origin.offset(16, 18, 11);
        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BuildingScanResult initial = workflow.analyzeBuildingAddition(registration);
        helper.assertTrue(initial.result() == Building.validationResult.SUCCESS,
                "tower bedroom with balcony doors did not scan: " + initial.result());
        helper.assertTrue(workflow.commitAddition(initial,
                        initial.isAmbiguous() ? initial.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED, "tower bedroom did not register with balcony doors");
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
        Building room = village.findInteractionRoomAt(source).orElseThrow();
        Structure structure = village.getStructureFor(room).orElseThrow();
        helper.assertTrue(structure.getFloor(room.getFloorId()).orElseThrow().anchorY() == origin.getY() + 18,
                "balcony-door registration selected another storey");
        helper.assertTrue(workflow.analyzeRegisteredRoomUpdate(village, room.getId(), source).result()
                        == Building.validationResult.SUCCESS, "tower bedroom could not refresh with doors present");
        for (int x = 17; x <= 18; x++) {
            level.setBlock(origin.offset(x, 18, 9), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(origin.offset(x, 19, 9), Blocks.AIR.defaultBlockState(), 2);
        }
        // A small, physically bounded balcony must not invalidate an existing roofed Room.
        helper.assertTrue(RoomScanPlanner.analyze(village, level, source).plan().mode()
                        == Village.RoomScanMode.UPDATE_ROOM, "removed doors lost the saved Update Room target");
        RoomWorkflow.Outcome update = workflow.scanRoom(source, room.getId(), null);
        helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED
                        && update.result() == Building.validationResult.SUCCESS
                        && update.expectedTargetId() == room.getId(),
                "tower door removal still rejects the bounded balcony: " + update.status() + "/" + update.result());
        helper.assertTrue(village.findInteractionRoomAt(source).orElseThrow().getId() == room.getId(),
                "balcony refresh changed the saved room identity");
        Village reloaded = new Village(village.save(), level);
        helper.assertTrue(reloaded.findInteractionRoomAt(source).orElseThrow().getId() == room.getId(),
                "balcony refresh lost the room identity after reload");
    }

    private static void verifySlabTowerFloors(GameTestHelper helper, BlockPos origin) {
        var level = helper.getLevel();
        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos lowerSeed = origin.offset(18, 2, 11);
        BuildingScanResult lower = workflow.analyzeBuildingAddition(lowerSeed);
        helper.assertTrue(lower.result() == Building.validationResult.SUCCESS,
                "tower lower room failed: " + lower.result());
        helper.assertTrue(workflow.commitAddition(lower,
                        lower.isAmbiguous() ? lower.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED, "tower lower room did not register");
        Village village = manager.findNearestVillage(lowerSeed, Village.MERGE_MARGIN).orElseThrow();
        Building main = village.findInteractionRoomAt(lowerSeed).orElseThrow();
        int buildingId = village.getStructure(main.getStructureId()).orElseThrow().getLogicalBuildingId();
        // The intermediate stair turn is not a fourth storey; register the actual middle deck.
        for (BlockPos relative : List.of(new BlockPos(17, 12, 13))) {
            BlockPos source = origin.offset(relative);
            var analysis = RoomScanPlanner.analyze(village, level, source);
            helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.ADD_ATTACHMENT
                            && analysis.plan().targetBuildingId() == buildingId,
                    "tower floor " + relative + " chose " + analysis.plan().mode());
            BuildingScanResult addition = workflow.analyzeAttachedRoom(village, analysis, buildingId);
            helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                    "tower floor " + relative + " failed: " + addition.result());
            helper.assertTrue(workflow.commitAddition(addition,
                            addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null).status()
                            == RoomWorkflow.Status.COMMITTED, "tower floor did not register at " + relative);
        }
        // Retain the original open window and balcony: no glazing or replacement doors.
        for (BlockPos relative : List.of(new BlockPos(17, 15, 13), new BlockPos(16, 15, 13),
                new BlockPos(15, 9, 11), new BlockPos(16, 19, 13), new BlockPos(17, 19, 13))) {
            BlockPos source = origin.offset(relative);
            var scan = SelectedFloorScanner.scan(level, source, 2048, 32);
            var analysis = RoomScanPlanner.analyze(village, level, source);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS
                            && analysis.plan().mode() != Village.RoomScanMode.ADD_BUILDING,
                    "enclosed tower loses its building at " + relative + ": "
                            + scan.result() + "/" + analysis.plan().mode());
            if ((relative.getY() == 15 && relative.getX() == 17) || relative.getY() == 19) {
                helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.ADD_ATTACHMENT
                                && analysis.plan().targetBuildingId() == buildingId,
                        "top Room did not offer Add Floor within the existing building at " + relative);
                helper.assertTrue(workflow.analyzeAttachedRoom(village, analysis, buildingId).result()
                                == Building.validationResult.SUCCESS,
                        "top Floor attachment failed at " + relative);
            }
        }
        BlockPos upperStep = origin.offset(17, 15, 13);
        BuildingScanResult upstairs = workflow.analyzeAttachedRoom(upperStep, buildingId);
        helper.assertTrue(workflow.commitAddition(upstairs,
                        upstairs.isAmbiguous() ? upstairs.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED, "top Floor did not commit");
        Building upperRoom = village.findInteractionRoomAt(upperStep).orElseThrow();
        Village reloaded = new Village(village.save(), level);
        var registeredFloors = reloaded.getBuildingStructures(buildingId).stream()
                .flatMap(member -> member.getFloors().stream()).toList();
        var floorNumbers = registeredFloors.stream().map(StructureFloor::floorNumber)
                .collect(java.util.stream.Collectors.toSet());
        helper.assertTrue(registeredFloors.size() == 3 && floorNumbers.equals(java.util.Set.of(0, 1, 2)),
                "tower should have lower, middle and upper storeys, not staircase floors: " + floorNumbers);
        for (BlockPos relative : List.of(new BlockPos(17, 15, 13), new BlockPos(16, 19, 13),
                new BlockPos(17, 19, 13))) {
            RoomScanPlan plan = reloaded.getRoomScanPlan(level, origin.offset(relative));
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.currentRoom().orElseThrow().getId() == upperRoom.getId(),
                    "registered top Floor lost its Room after reload at " + relative);
        }
        helper.assertTrue(reloaded.findInteractionRoomAt(lowerSeed).orElseThrow().getId() == main.getId(),
                "top Floor registration replaced the main Room");
        // Refresh with all three registered storeys present: overlap checks must remain
        // strict, but an unchanged lower stairwell must not collide with the upper Floor.
        Map<Integer, java.util.Set<BlockPos>> roomCells = village.getRooms().collect(
                java.util.stream.Collectors.toMap(Building::getId, room -> java.util.Set.copyOf(room.getFloorCells())));
        Map<Integer, FloorGeometry> floorGeometry = village.getStructures().values().stream().collect(
                java.util.stream.Collectors.toMap(Structure::getId, member -> member.getFloors().getFirst().geometry()));
        for (int repetition = 0; repetition < 2; repetition++) {
            for (BlockPos source : List.of(lowerSeed, origin.offset(16, 10, 11),
                    origin.offset(17, 12, 13), upperStep)) {
                Building selected = village.findInteractionRoomAt(source).orElseThrow();
                var update = workflow.scanRoom(source, selected.getId(), null);
                helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED,
                        "unchanged stacked tower refresh failed at " + source.subtract(origin) + ": " + update);
            }
            helper.assertTrue(village.getRooms().allMatch(room ->
                            java.util.Set.copyOf(room.getFloorCells()).equals(roomCells.get(room.getId())))
                            && village.getStructures().values().stream().allMatch(member ->
                            member.getFloors().getFirst().geometry().sameExactGeometry(floorGeometry.get(member.getId()))),
                    "refresh repartitioned the unchanged three-storey tower");
        }
        Village updatedReload = new Village(village.save(), level);
        helper.assertTrue(updatedReload.getBuildingStructures(buildingId).size() == 3
                        && updatedReload.findInteractionRoomAt(lowerSeed).orElseThrow().getId() == main.getId()
                        && updatedReload.findInteractionRoomAt(upperStep).orElseThrow().getId() == upperRoom.getId(),
                "stacked tower refresh lost storey/room identity after reload");
    }

    @GameTest(batch = "mca_reported_quartz_stairs", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void reportedQuartzStairsResolveUpperRoom(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        var level = helper.getLevel();
        // Reconstruction of worldnewv3 (5066,74,-1052)..(5077,96,-1032).
        // Vanilla stairs/roof geometry is retained. Modded logs/planks use vanilla wood,
        // windows use glass, railings use bars and unmodelled furnishings are omitted.
        // This probe is not an exact reproduction of the server's modded collisions.
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_quartz_house_probe.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos lowerSeed = origin.offset(6, 2, 6);
        BuildingScanResult downstairs = workflow.analyzeBuildingAddition(lowerSeed);
        helper.assertTrue(downstairs.result() == Building.validationResult.SUCCESS,
                "reconstructed ground Room failed to scan: " + downstairs.result());
        helper.assertTrue(workflow.commitAddition(downstairs,
                        downstairs.isAmbiguous() ? downstairs.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED,
                "reconstructed ground Room failed to register");
        Village village = manager.findNearestVillage(lowerSeed, Village.MERGE_MARGIN).orElseThrow();
        Building main = village.findInteractionRoomAt(lowerSeed).orElseThrow();
        int buildingId = village.getStructure(main.getStructureId()).orElseThrow().getLogicalBuildingId();
        for (BlockPos relative : List.of(new BlockPos(8, 5, 12), new BlockPos(8, 6, 13),
                new BlockPos(8, 7, 14), new BlockPos(8, 8, 16))) {
            BlockPos source = origin.offset(relative);
            var scan = SelectedFloorScanner.scan(level, source, 2048, 32);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "reported quartz staircase rejected " + relative + ": " + scan.result());
            RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, level, source);
            if (relative.getY() == 5) {
                helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.UPDATE_ROOM,
                        "lower step lost the registered main Room");
            } else {
                helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.ADD_ATTACHMENT
                                && analysis.plan().targetBuildingId() == buildingId,
                        "upper stair " + relative + " chose " + analysis.plan().mode());
                helper.assertTrue(workflow.analyzeAttachedRoom(village, analysis, buildingId).result()
                                == Building.validationResult.SUCCESS,
                        "upper stair attachment analysis failed at " + relative);
            }
        }
        BuildingScanResult upstairs = workflow.analyzeAttachedRoom(origin.offset(8, 8, 16), buildingId);
        helper.assertTrue(workflow.commitAddition(upstairs,
                        upstairs.isAmbiguous() ? upstairs.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED,
                "upper Room did not commit to the existing building");
        helper.assertTrue(village.findInteractionRoomAt(lowerSeed).orElseThrow().getId() == main.getId(),
                "upper Room registration replaced the main Room");
        helper.succeed();
    }

    @GameTest(batch = "mca_uneven_basement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void descendingBasementContinuationAddsRoomOnExistingFloor(GameTestHelper helper) {
        // Minimal stone/door geometry from (-200, 98, 134) in the reported New World.
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        var level = helper.getLevel();
        VillageManager manager = createBasementVillage(helper, origin, false);
        Village village = manager.findNearestVillage(origin, Village.MERGE_MARGIN).orElseThrow();
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos bottom = origin.offset(0, 0, 1);
        BlockPos nextStep = origin.offset(1, 1, 1);
        for (BlockPos source : List.of(nextStep, bottom, bottom.above(), origin.offset(0, 1, 2))) {
            var analysis = RoomScanPlanner.analyze(village, level, source);
            helper.assertTrue(analysis.plan().mode() == Village.RoomScanMode.ADD_ROOM
                            && analysis.plan().targetStructureId() == 69 && analysis.plan().targetFloorId() == 0,
                    "descending basement selected " + analysis.plan().mode() + " at " + source.subtract(origin)
                            + "; scanSeed=" + (analysis.observation() == null ? null
                            : analysis.observation().seed().subtract(origin))
                            + "; sourceCell=" + (analysis.observation() == null ? null
                            : analysis.observation().scan().floor().cellAt(source))
                            + "; cells=" + (analysis.observation() == null ? null
                            : analysis.observation().scan().floor().cells().stream()
                            .map(cell -> cell.feet().subtract(origin)).toList()));
            helper.assertTrue(analysis.observation() != null
                            && analysis.observation().scan().floor().anchorY() == origin.getY() + 3,
                    "descending basement changed its canonical anchor");
            BuildingScanResult addition = workflow.analyzeRoom(source);
            helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                    "descending basement room addition failed: " + addition.result());
            StructureScanner.Result initial = StructureScanner.scanNewStructure(level, source, List.of());
            helper.assertTrue(initial.result() == Building.validationResult.SUCCESS
                            && initial.scannedFloor().anchorY() == origin.getY() + 3,
                    "initial registration from a basement step or edge failed: " + initial.result());
        }
        var grounded = StructureScanner.observeFloor(level, bottom, List.of()).orElseThrow();
        var airborne = StructureScanner.observeFloor(level, bottom.above(2), List.of()).orElse(null);
        helper.assertTrue(airborne != null && airborne.seed().equals(grounded.seed())
                        && airborne.scan().floor().sameExactGeometry(grounded.scan().floor()),
                "airborne basement selection did not resolve the same floor below");
        helper.assertTrue(StructureScanner.observeFloor(level, bottom.above(3), List.of()).isEmpty(),
                "standing normalization searched through a solid ceiling");
        var addition = workflow.analyzeRoom(bottom.above());
        var committed = workflow.commitAddition(addition,
                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null);
        helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                "descending basement room did not commit: " + committed.result());
        Building newRoom = village.findInteractionRoomAt(bottom).orElseThrow();
        helper.assertTrue(newRoom.getStructureId() == 69 && newRoom.getFloorId() == 0
                        && newRoom.getId() != 70 && village.getStructures().size() == 2,
                "descending basement created a new floor instead of a room");
        Village reloaded = new Village(village.save(), level);
        helper.assertTrue(reloaded.getStructure(69).orElseThrow().getFloor(0).orElseThrow().floorNumber() == -1,
                "room addition changed the existing basement number");
        for (BlockPos source : List.of(nextStep, bottom, bottom.above(), origin.offset(0, 1, 2))) {
            RoomScanPlan plan = reloaded.getRoomScanPlan(null, source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.currentRoom().orElseThrow().getId() == newRoom.getId(),
                    "descending basement lost its room identity after save/load");
        }
        helper.assertTrue(reloaded.findInteractionRoomAt(origin.offset(5, 3, 1)).orElseThrow().getId() == 70,
                "basement continuation replaced the registered room across the door");
        helper.succeed();
    }

    @GameTest(batch = "mca_basement_ladder_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void basementLadderKeepsFloorCellAndUpdatesItsRoomAfterExpansion(GameTestHelper helper) {
        BlockPos origin = helper.absolutePos(new BlockPos(5, 2, 5));
        var level = helper.getLevel();
        VillageManager manager = createBasementVillage(helper, origin, true);
        Village village = manager.findNearestVillage(origin, Village.MERGE_MARGIN).orElseThrow();
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BuildingScanResult addition = workflow.analyzeRoom(origin.offset(0, 0, 1));
        helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                "expanded basement did not scan: " + addition.result());
        helper.assertTrue(workflow.commitAddition(addition,
                        addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null).status()
                        == RoomWorkflow.Status.COMMITTED,
                "expanded basement did not register");
        BlockPos ladder = origin.offset(4, 3, 3);
        var floor = village.getStructure(69).orElseThrow().getFloor(0).orElseThrow();
        var lowerScan = SelectedFloorScanner.scan(level, origin.offset(0, 0, 1), 2048, 32);
        var upperScan = SelectedFloorScanner.scan(level, origin.offset(5, 3, 1), 2048, 32);
        helper.assertTrue(lowerScan.result() == Building.validationResult.SUCCESS
                        && upperScan.result() == Building.validationResult.SUCCESS
                        && lowerScan.floor().sameExactGeometry(upperScan.floor()),
                "fresh scans from both basement Rooms disagree about their physical Floor");
        helper.assertTrue(floor.geometry().cellAt(ladder).isPresent(),
                "supported basement ladder entry is not a floor cell after expansion");
        helper.assertTrue(floor.geometry().cellAt(ladder.above()).isEmpty(),
                "unsupported intermediate ladder rung became a floor cell");
        RoomScanPlan ladderPlan = village.getRoomScanPlan(level, ladder);
        helper.assertTrue(ladderPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && ladderPlan.currentRoom().orElseThrow().getId() == 70,
                "newly observed ladder entry lost its persisted connector room");
        var fresh = StructureScanner.scanExistingFloor(level, village.getStructure(69).orElseThrow(),
                floor, ladder, village.getStructures().values());
        helper.assertTrue(fresh.result() == Building.validationResult.SUCCESS
                        && floor.geometry().cells().stream().allMatch(cell ->
                        fresh.scannedFloor().cellAt(cell.feet()).isPresent()),
                "ladder refresh dropped unchanged cells from the lower room");
        var updated = workflow.scanRoom(ladder, 70, null);
        helper.assertTrue(updated.status() == RoomWorkflow.Status.COMMITTED,
                "basement ladder update returned " + updated.result());
        helper.assertTrue(village.getStructure(69).orElseThrow().getFloor(0).orElseThrow()
                        .geometry().cellAt(ladder).isPresent(),
                "supported basement ladder entry is not a floor cell");
        helper.assertTrue(village.findInteractionRoomAt(ladder).orElseThrow().getId() == 70,
                "basement ladder changed its registered room");
        Village reloaded = new Village(village.save(), level);
        RoomScanPlan reloadedPlan = reloaded.getRoomScanPlan(null, ladder);
        helper.assertTrue(reloadedPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && reloadedPlan.currentRoom().orElseThrow().getId() == 70,
                "basement ladder lost room lookup on reload");
        helper.succeed();
    }

    private static VillageManager createBasementVillage(GameTestHelper helper, BlockPos origin, boolean expanded) {
        var level = helper.getLevel();
        for (BlockPos relative : BlockPos.betweenClosed(-2, -1, -1, 6, 8, 4)) {
            level.setBlock(origin.offset(relative), Blocks.STONE.defaultBlockState(), 2);
        }
        int[][] air = {
                {0, 0, 1}, {-1, 1, 1}, {0, 1, 1}, {1, 1, 0}, {1, 1, 1}, {0, 1, 2}, {0, 2, 2},
                {-1, 2, 1}, {0, 2, 1}, {1, 2, 0}, {1, 2, 1}, {2, 2, 0}, {2, 2, 1}, {1, 2, 2},
                {0, 3, 0}, {1, 3, 0}, {1, 3, 1}, {2, 3, 0}, {2, 3, 1},
                {2, 4, 0}, {1, 4, 1}, {2, 4, 1},
                {4, 3, 0}, {4, 3, 1}, {4, 3, 2}, {5, 3, 0}, {5, 3, 1}, {5, 3, 2}, {5, 3, 3},
                {4, 4, 0}, {4, 4, 1}, {4, 4, 2}, {5, 4, 0}, {5, 4, 1}, {5, 4, 2}, {5, 4, 3},
                {4, 6, 0}, {4, 6, 1}, {5, 6, 0}, {5, 6, 1},
                {4, 7, 0}, {4, 7, 1}, {5, 7, 0}, {5, 7, 1}
        };
        for (int[] pos : air) {
            level.setBlock(origin.offset(pos[0], pos[1], pos[2]), Blocks.AIR.defaultBlockState(), 2);
        }
        if (expanded) {
            for (BlockPos feet : List.of(new BlockPos(-1, 1, 2), new BlockPos(1, 1, 2),
                    new BlockPos(0, 1, 3), new BlockPos(1, 1, 3))) {
                level.setBlock(origin.offset(feet), Blocks.AIR.defaultBlockState(), 2);
                level.setBlock(origin.offset(feet).above(), Blocks.AIR.defaultBlockState(), 2);
            }
            var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
            level.setBlock(origin.offset(4, 3, 3), ladder, 2);
            level.setBlock(origin.offset(4, 4, 3), ladder, 2);
            level.setBlock(origin.offset(4, 5, 3), Blocks.AIR.defaultBlockState(), 2);
        }
        BlockPos doorPos = origin.offset(3, 3, 0);
        var door = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.WEST)
                .setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        level.setBlock(doorPos, door, 2);
        level.setBlock(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);

        Village village = new Village(0, level);
        List<FloorGeometry.Cell> groundCells = List.of(new BlockPos(4, 6, 0), new BlockPos(4, 6, 1),
                        new BlockPos(5, 6, 0), new BlockPos(5, 6, 1)).stream()
                .map(pos -> new FloorGeometry.Cell(origin.offset(pos), origin.getY() + 8)).toList();
        registerBasementFixtureFloor(village, 67, 68, 0, new FloorGeometry(groundCells, List.of()), groundCells);
        int[][] saved = {
                {1, 1, 0, 4}, {1, 1, 1, 5}, {2, 2, 0, 5}, {2, 2, 1, 5}, {3, 3, 0, 4},
                {4, 3, 0, 5}, {4, 3, 1, 5}, {4, 3, 2, 5}, {5, 3, 0, 5}, {5, 3, 1, 5}, {5, 3, 2, 5}, {5, 3, 3, 5}
        };
        List<FloorGeometry.Cell> cells = Arrays.stream(saved)
                .map(pos -> new FloorGeometry.Cell(origin.offset(pos[0], pos[1], pos[2]), origin.getY() + pos[3]))
                .toList();
        var markers = new java.util.ArrayList<FloorConnector.Marker>();
        markers.add(new FloorConnector.Marker(doorPos, FloorConnector.Type.DOOR, doorPos, Direction.EAST));
        if (expanded) {
            markers.add(new FloorConnector.Marker(origin.offset(4, 3, 3),
                    FloorConnector.Type.LADDER, origin.offset(5, 3, 3)));
        }
        FloorGeometry basement = new FloorGeometry(cells, markers);
        List<FloorGeometry.Cell> existingRoom = cells.stream().filter(cell ->
                cell.feet().getX() >= origin.getX() + 4 && cell.feet().getY() == origin.getY() + 3).toList();
        registerBasementFixtureFloor(village, 69, 70, -1, basement, existingRoom);
        var villages = new ListTag();
        villages.add(village.save());
        var managerTag = new CompoundTag();
        managerTag.put("villages", villages);
        managerTag.putInt("lastBuildingId", 100);
        return new VillageManager(level, managerTag);
    }

    private static void registerBasementFixtureFloor(Village village, int structureId, int roomId, int number,
                                                     FloorGeometry geometry, List<FloorGeometry.Cell> roomCells) {
        BlockPos source = roomCells.getFirst().feet();
        Structure structure = new Structure(structureId, source, List.of(new StructureFloor(0, number, geometry)));
        structure.setLogicalBuildingId(67);
        Building room = new Building(source);
        room.setId(roomId);
        room.setStructureId(structureId);
        room.setFloorId(0);
        room.setType("house");
        room.setGeometry(structure.getRawPos0(), structure.getRawPos1(),
                roomCells.stream().map(FloorGeometry.Cell::feet).toList());
        village.registerStructure(structure, room);
    }

    @GameTest(batch = "mca_reported_stair_initial_registration", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedMiddleStairCanRegisterItsSelectedFloor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        BlockPos stair = origin.offset(4, 4, 2);
        for (BlockPos source : List.of(stair, stair.above())) {
            VillageManager manager = new VillageManager(helper.getLevel());
            RoomWorkflow workflow = new RoomWorkflow(manager, helper.getLevel());
            BuildingScanResult scan = workflow.analyzeBuildingAddition(source);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
                    "initial stair registration failed at " + source.subtract(origin) + ": " + scan.result());
            var committed = workflow.commitAddition(scan,
                    scan.isAmbiguous() ? scan.matchingTypes().getFirst() : null);
            helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                    "initial stair registration did not commit: " + committed.result());
            Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
            Building room = village.findInteractionRoomAt(origin.offset(3, 6, 2)).orElseThrow();
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.currentRoom().orElseThrow().getId() == room.getId(),
                    "initial stair registration did not resolve back to its selected Room");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_stair_house", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedStaircaseUpdatesTheLowerRoomAndAddsTheUpperFloor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        var level = helper.getLevel();

        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos lowerSeed = origin.offset(7, 2, 4);
        BuildingScanResult lowerScan = workflow.analyzeBuildingAddition(lowerSeed);
        helper.assertTrue(lowerScan.result() == Building.validationResult.SUCCESS,
                "saved lower room did not scan: " + lowerScan.result());
        var outcome = workflow.commitAddition(lowerScan,
                lowerScan.isAmbiguous() ? lowerScan.matchingTypes().getFirst() : null);
        helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED,
                "saved lower room did not register: " + outcome.result());
        Village village = manager.findNearestVillage(lowerSeed, Village.MERGE_MARGIN).orElseThrow();
        Building lowerRoom = village.findInteractionRoomAt(lowerSeed).orElseThrow();
        BlockPos source = origin.offset(4, 4, 2);
        BlockPos lowerStair = origin.offset(5, 4, 2);
        for (BlockPos position : List.of(origin.offset(6, 3, 2), lowerStair, lowerStair.below())) {
            RoomScanPlan plan = village.getRoomScanPlan(level, position);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                    "lower stair " + position.subtract(origin) + " selected " + plan.mode());
            helper.assertTrue(plan.currentRoom().orElseThrow().getId() == lowerRoom.getId(),
                    "lower stair lost its Room identity");
        }
        var update = workflow.scanRoom(lowerStair, lowerRoom.getId(), null);
        helper.assertTrue(update.status() == RoomWorkflow.Status.COMMITTED,
                "saved lower stair update failed: " + update.result());

        BlockPos upperLanding = origin.offset(3, 6, 2);
        SelectedFloorScanner.Result lowerGeometry = SelectedFloorScanner.scan(level, lowerSeed, 256, 24);
        SelectedFloorScanner.Result upperGeometry = SelectedFloorScanner.scan(level, upperLanding, 256, 24);
        helper.assertTrue(lowerGeometry.result() == Building.validationResult.SUCCESS
                        && upperGeometry.result() == Building.validationResult.SUCCESS,
                "saved staircase floor ownership scans failed");
        for (BlockPos feet : List.of(origin.offset(6, 3, 2), lowerStair)) {
            helper.assertTrue(lowerGeometry.floor().cellAt(feet).isPresent()
                            && upperGeometry.floor().cellAt(feet).isEmpty(),
                    "saved lower half was not exclusively owned downstairs");
        }
        for (BlockPos feet : List.of(source.above(), upperLanding)) {
            helper.assertTrue(upperGeometry.floor().cellAt(feet).isPresent()
                            && lowerGeometry.floor().cellAt(feet).isEmpty(),
                    "saved upper half was not exclusively owned upstairs");
        }
        RoomScanPlan additionPlan = village.getRoomScanPlan(level, upperLanding);
        for (BlockPos position : List.of(source, source.above(), upperLanding, origin.offset(2, 6, 2))) {
            RoomScanPlan plan = village.getRoomScanPlan(level, position);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT,
                    "upper landing selected " + plan.mode() + " instead of Add Floor");
            helper.assertTrue(plan.targetBuildingId() == village.getLogicalBuildingId(lowerRoom.getStructureId()),
                    "upper landing targeted another building");
            helper.assertTrue(plan.prospectiveFloorNumber() == 1,
                    "upper landing did not select floor 1");
        }
        BuildingScanResult addition = workflow.analyzeAttachedRoom(upperLanding, additionPlan.targetBuildingId());
        helper.assertTrue(addition.result() == Building.validationResult.SUCCESS,
                "saved upper floor attachment failed: " + addition.result());
        var added = workflow.commitAddition(addition,
                addition.isAmbiguous() ? addition.matchingTypes().getFirst() : null);
        helper.assertTrue(added.status() == RoomWorkflow.Status.COMMITTED,
                "saved upper floor did not commit: " + added.result());
        Building upperRoom = village.findInteractionRoomAt(upperLanding).orElseThrow();
        for (Village persisted : List.of(village, new Village(village.save(), level))) {
            for (BlockPos position : List.of(origin.offset(6, 3, 2), lowerStair)) {
                Building owner = persisted.findInteractionRoomAt(position).orElseThrow();
                helper.assertTrue(owner.getId() == lowerRoom.getId(),
                        "registered lower stair was reassigned to the upper Room");
            }
            for (BlockPos position : List.of(source, source.above(), upperLanding, origin.offset(2, 6, 2))) {
                RoomScanPlan plan = persisted.getRoomScanPlan(level, position);
                helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                        "registered upper staircase selected " + plan.mode());
                helper.assertTrue(plan.currentRoom().orElseThrow().getId() == upperRoom.getId(),
                        "registered upper staircase lost its Room identity");
            }
        }
        int logicalBuildingId = village.getLogicalBuildingId(upperRoom.getStructureId());
        int upperStructureId = upperRoom.getStructureId();
        int upperFloorId = upperRoom.getFloorId();
        var landingState = level.getBlockState(upperLanding.below());
        helper.assertTrue(manager.removeFloor(upperLanding, 1, logicalBuildingId, upperStructureId, upperFloorId)
                        == VillageManager.BuildingEditResult.NO_FLOOR,
                "occupied upper floor should reject removal");
        helper.assertTrue(manager.removeRoom(upperLanding, upperRoom.getId())
                        == VillageManager.BuildingEditResult.SUCCESS,
                "upper Room could not be removed before removing its Floor");
        RoomScanPlan emptyFloorPlan = village.getRoomScanPlan(level, upperLanding);
        helper.assertTrue(emptyFloorPlan.mode() == Village.RoomScanMode.ADD_ROOM
                        && emptyFloorPlan.targetStructureId() == upperStructureId
                        && emptyFloorPlan.targetFloorId() == upperFloorId,
                "empty upper landing lost its persisted Floor identity");
        helper.assertTrue(manager.removeFloor(upperLanding, 1, logicalBuildingId, upperStructureId, upperFloorId)
                        == VillageManager.BuildingEditResult.SUCCESS,
                "empty upper Floor could not be removed without a Room");
        helper.assertTrue(village.getStructure(upperStructureId)
                        .flatMap(structure -> structure.getFloor(upperFloorId)).isEmpty(),
                "removed upper Floor is still registered");
        helper.assertTrue(village.getBuilding(lowerRoom.getId()).isPresent(),
                "removing upper Floor removed the lower Room");
        helper.assertTrue(level.getBlockState(upperLanding.below()).equals(landingState),
                "floor registration removal changed staircase blocks");
        helper.succeed();
    }

    @GameTest(batch = "mca_reported_short_ladder_hole", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void savedHouseShortLadderHoleUpdatesItsExistingRoom(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        BlockPos origin = placeSavedHouse(helper);
        var level = helper.getLevel();
        // Saved-world opening (-196, 104, 136), interaction (-196, 103, 136), top ladder Y=102.
        BlockPos opening = origin.offset(2, 2, 4);
        level.setBlock(opening.below(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(opening.below(2), Blocks.AIR.defaultBlockState(), 3);
        VillageManager manager = new VillageManager(level);
        RoomWorkflow workflow = new RoomWorkflow(manager, level);
        BlockPos roomSeed = origin.offset(7, 2, 4);
        BuildingScanResult original = workflow.analyzeBuildingAddition(roomSeed);
        helper.assertTrue(original.result() == Building.validationResult.SUCCESS,
                "saved house with an ordinary air hole did not scan: " + original.result());
        var committed = workflow.commitAddition(original,
                original.isAmbiguous() ? original.matchingTypes().getFirst() : null);
        helper.assertTrue(committed.status() == RoomWorkflow.Status.COMMITTED,
                "saved house with an ordinary air hole did not register");
        Village village = manager.findNearestVillage(roomSeed, Village.MERGE_MARGIN).orElseThrow();
        Building room = village.findInteractionRoomAt(roomSeed).orElseThrow();
        helper.assertTrue(!room.ownsFloorCell(opening),
                "ordinary air hole acquired Room ownership without a ladder");

        var ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
        for (int depth = 2; depth <= 3; depth++) {
            level.setBlock(opening.below(depth).west(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(opening.below(depth), ladder, 3);
        }
        for (BlockPos source : List.of(opening.below(), opening)) {
            RoomScanPlan plan = village.getRoomScanPlan(level, source);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
                    "saved short ladder hole selected " + plan.mode() + " instead of Update Room");
            helper.assertTrue(plan.currentRoom().orElseThrow().getId() == room.getId(),
                    "saved short ladder hole selected another Room");
        }
        var updated = workflow.scanRoom(opening.below(), room.getId(), null);
        helper.assertTrue(updated.status() == RoomWorkflow.Status.COMMITTED,
                "saved short ladder hole update failed: " + updated.result());
        helper.assertTrue(village.getBuilding(room.getId()).orElseThrow().ownsFloorCell(opening),
                "saved short ladder hole was not persisted in its Room");
        Village reloaded = new Village(village.save(), level);
        RoomScanPlan persistedPlan = reloaded.getRoomScanPlan(null, opening.below());
        helper.assertTrue(persistedPlan.mode() == Village.RoomScanMode.UPDATE_ROOM
                        && persistedPlan.currentRoom().orElseThrow().getId() == room.getId(),
                "saved short ladder hole lost its Room identity after save/load");
        helper.succeed();
    }

    private static BlockPos placeSavedHouse(GameTestHelper helper) throws IOException, CommandSyntaxException {
        // Saved world origin (-198, 102, 132); the failing interaction was (-194, 106, 134).
        BlockPos origin = helper.absolutePos(new BlockPos(4, 2, 4));
        var level = helper.getLevel();
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_stair_house.snbt"));
        StructureTemplate template = new StructureTemplate();
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        return origin;
    }
}
