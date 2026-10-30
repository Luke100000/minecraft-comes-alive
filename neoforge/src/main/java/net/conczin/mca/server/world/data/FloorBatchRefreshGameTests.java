package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Synchronous fixtures restore their own bounded terrain even when an assertion fails. */
@PrefixGameTestTemplate(false)
public final class FloorBatchRefreshGameTests {
    @GameTest(batch = "mca_floor_diagnostic_recovered_owner", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void diagnosticsUseRecoveredRoomOwnerInsteadOfNearerStructure(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.diagnosticsVillage(10);
            Building room = village.getRooms().findFirst().orElseThrow();
            Structure owner = village.getStructureFor(room).orElseThrow();
            BlockPos exit = fixture.min.offset(0, 0, 1);
            fixture.set(exit.below(), Blocks.OAK_TRAPDOOR.defaultBlockState());
            fixture.set(exit.below(2), Blocks.LADDER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LadderBlock.FACING, Direction.EAST));
            StructureFloor floor = owner.getFloor(room.getFloorId()).orElseThrow();
            Set<FloorGeometry.Cell> oldCells = floor.geometry().cells().stream()
                    .filter(cell -> !cell.feet().equals(exit)).collect(java.util.stream.Collectors.toSet());
            helper.assertTrue(owner.replaceFloorGeometry(floor.id(), new FloorGeometry(oldCells, Map.of())),
                    "could not establish missing persisted exit");
            room.setGeometry(room.getPos0(), room.getPos1(), oldCells.stream().map(FloorGeometry.Cell::feet).toList());

            // An unrelated stale saved Structure is nearer, but owns no cell in this fresh Floor.
            BlockPos otherSource = fixture.min.offset(-2, 0, 1);
            Set<BlockPos> otherCells = Set.of(otherSource, otherSource.east(), otherSource.south(), otherSource.east().south());
            FloorGeometry otherGeometry = new FloorGeometry(otherCells.stream()
                    .map(pos -> new FloorGeometry.Cell(pos, pos.getY() + 2)).toList(), Map.of());
            Structure other = new Structure(owner.getId() + 1000, otherSource,
                    List.of(new StructureFloor(0, 0, otherGeometry)));
            Building otherRoom = new Building(otherSource);
            otherRoom.setId(room.getId() + 1000);
            otherRoom.setStructureId(other.getId());
            otherRoom.setFloorId(0);
            otherRoom.setGeometry(otherSource, otherSource.offset(1, 1, 1), otherCells);
            village.registerStructure(other, otherRoom);
            helper.assertTrue(other.getCenter().distSqr(exit) < owner.getCenter().distSqr(exit)
                            && village.getInteractionStructureAt(exit).isEmpty(),
                    "recovered diagnostic fixture still resolves persisted ownership or lacks a nearer unrelated Structure");
            RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), exit);
            helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                            && plan.targetStructureId() == owner.getId() && plan.targetFloorId() == floor.id()
                            && plan.currentRoom().orElseThrow().getId() == room.getId(),
                    "fresh planning did not recover the original Room owner: " + plan.mode());
            helper.assertTrue(fixture.workflow.analyzeRegisteredRoomUpdate(village, room.getId(), exit).result()
                            == Building.validationResult.SUCCESS, "owner-specific control analysis failed");
            CompoundTag before = village.save();
            BuildingDiagnostics.Result result = BuildingDiagnostics.diagnose(helper.getLevel(), exit);
            helper.assertTrue(result.position() == BuildingDiagnostics.StructuralPosition.REGISTERED_ROOM
                            && result.verdict().startsWith("REGISTERED_ROOM:"),
                    "diagnostics used a nearer unrelated Structure for the recovered Room: " + result.verdict());
            helper.assertTrue(before.equals(village.save()), "recovered diagnostics changed saved state");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_diagnostic_provenance", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void diagnosticsReportExactRefreshDiscoveryAndTraversalPositions(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.diagnosticsVillage();
            CompoundTag before = village.save();
            BlockPos query = fixture.min.offset(2, 1, 2);
            String scanLine;
            try (DiagnosticCapture capture = new DiagnosticCapture(message -> { })) {
                BuildingDiagnostics.Result result = BuildingDiagnostics.diagnose(helper.getLevel(), query);
                helper.assertTrue(result.position() == BuildingDiagnostics.StructuralPosition.REGISTERED_ROOM
                                && result.verdict().startsWith("REGISTERED_ROOM:"), "diagnostic provenance fixture failed");
                List<String> lines = capture.messages.stream().filter(message -> message.contains("freshFloorScan ")).toList();
                helper.assertTrue(lines.size() == 1, "expected exactly one fresh Floor diagnostic");
                scanLine = lines.getFirst();
            }
            helper.assertTrue(scanLine.contains("requestedPosition=" + query)
                            && scanLine.contains("discoveryInput=" + fixture.min.offset(2, 0, 2))
                            && scanLine.contains("selectedSeed=" + fixture.min.offset(2, 0, 2))
                            && scanLine.contains("traversalSeed=" + fixture.min)
                            && scanLine.contains("interactionSource=" + fixture.min.offset(2, 0, 2)),
                    "diagnostic refresh provenance conflated requested/discovery/selected/traversal/interaction positions: " + scanLine);
            helper.assertTrue(before.equals(village.save()), "diagnostics changed saved state");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_diagnostic_reuse", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void diagnosticsAnalyzeTheAlreadyObservedRegisteredFloor(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.diagnosticsVillage();
            Building room = village.getRooms().findFirst().orElseThrow();
            Structure structure = village.getStructureFor(room).orElseThrow();
            StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
            CompoundTag before = village.save();
            boolean[] removed = {false};
            BuildingDiagnostics.Result result;
            try (DiagnosticCapture capture = new DiagnosticCapture(message -> {
                if (!removed[0] && message.contains("freshFloorScan ")) {
                    removed[0] = true;
                    floor.geometry().cells().forEach(cell -> fixture.set(cell.feet().below(), Blocks.AIR.defaultBlockState()));
                }
            })) {
                result = BuildingDiagnostics.diagnose(helper.getLevel(), fixture.min.offset(2, 0, 2));
                helper.assertTrue(capture.messages.stream().filter(message -> message.contains("freshFloorScan ")).count() == 1,
                        "reuse fixture did not observe one fresh Floor log");
            }
            helper.assertTrue(removed[0] && StructureScanner.scanExistingFloor(helper.getLevel(), structure, floor,
                            room.getSourceBlock(), village.getStructures().values()).result() != Building.validationResult.SUCCESS,
                    "reuse fixture did not invalidate a second physical scan");
            helper.assertTrue(result.verdict().startsWith("REGISTERED_ROOM:"),
                    "diagnostics rediscovered the Floor after logging the successful observation: " + result.verdict());
            helper.assertTrue(before.equals(village.save()), "diagnostic reuse changed saved state");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_detached", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void floorBatchAnalysisIsDetachedUntilPublication(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            CompoundTag before = village.save();
            fixture.shrinkSecond();
            RoomWorkflow.RegisteredFloorUpdate update = fixture.workflow.analyzeRegisteredFloorUpdate(village, 10, 0);
            helper.assertTrue(update.result() == Building.validationResult.SUCCESS
                            && update.structureId() == 10 && update.floorId() == 0
                            && update.replacementRooms().stream().map(Building::getId).toList().equals(List.of(100, 101)),
                    "detached analysis did not account for every saved Room in stable order");
            helper.assertTrue(before.equals(village.save()), "successful detached analysis changed saved state");
            update.refreshedStructure().setFloorNumber(0, 99);
            update.replacementRooms().forEach(room -> {
                room.setType("changed_detached_type");
                room.setContributesToMain(!room.contributesToMain());
            });
            helper.assertTrue(before.equals(village.save()), "batch payload aliases registered mutable state");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_multiple", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRefreshesMultipleFloorsOfOneStructure(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.stacked();
            Structure before = village.getStructure(10).orElseThrow().copy();
            fixture.shrinkBothFloors();
            helper.assertTrue(fixture.manager.fullScan(village) == Building.validationResult.SUCCESS,
                    "multiple Floors did not refresh successfully");
            Structure after = village.getStructure(10).orElseThrow();
            for (int floorId : List.of(0, 1)) {
                StructureFloor oldFloor = before.getFloor(floorId).orElseThrow();
                StructureFloor newFloor = after.getFloor(floorId).orElseThrow();
                helper.assertTrue(!newFloor.geometry().sameCellPositions(oldFloor.geometry()),
                        "later Structure publication restored old geometry on Floor " + floorId);
                helper.assertTrue(newFloor.floorNumber() == oldFloor.floorNumber()
                                && newFloor.anchorY() == oldFloor.anchorY(), "multiple Floor labels/anchors changed");
                helper.assertTrue(newFloor.geometry().sameExactGeometry(fixture.fresh(village, floorId).scannedFloor()),
                        "published Floor geometry is stale on Floor " + floorId);
            }
            helper.assertTrue(village.getBuildings().keySet().equals(Set.of(100, 101, 200)),
                    "multiple Floor refresh changed Room IDs");
            assertOwnershipAndReload(helper, village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_exception", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRestoresEarlierFloorAfterPublicationException(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village initial = fixture.stacked();
            FailLaterVillage village = new FailLaterVillage(initial.save(), helper.getLevel(), true);
            CompoundTag before = village.save();
            fixture.shrinkBothFloors();
            boolean rethrown = false;
            try {
                fixture.manager.fullScan(village);
            } catch (IllegalStateException exception) {
                rethrown = exception == village.failure;
            }
            helper.assertTrue(rethrown && village.publications == 2 && village.earlierRefreshObserved,
                    "late publication exception did not follow an actual earlier refresh or was not rethrown");
            helper.assertTrue(before.equals(village.save()),
                    "late exception did not restore geometry, Rooms, POIs, types and logical metadata");
            assertOwnershipAndReload(helper, village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_rejection", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRestoresEarlierFloorAfterPublicationRejection(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            FailLaterVillage village = new FailLaterVillage(fixture.stacked().save(), helper.getLevel(), false);
            CompoundTag before = village.save();
            fixture.shrinkBothFloors();
            helper.assertTrue(fixture.manager.fullScan(village) == Building.validationResult.OVERLAP
                            && village.publications == 2 && village.earlierRefreshObserved,
                    "publication rejection did not follow an earlier refresh or did not map to OVERLAP");
            helper.assertTrue(before.equals(village.save()), "later publication rejection did not restore saved state");
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_merge", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRejectsMergedRegisteredRooms(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            fixture.set(fixture.min.offset(4, 0, 1), Blocks.AIR.defaultBlockState());
            fixture.set(fixture.min.offset(4, 1, 1), Blocks.AIR.defaultBlockState());
            List<RoomPartitioner.Component> components = fixture.components(village);
            helper.assertTrue(components.size() == 1, "merge fixture did not discover one fresh component");
            fixture.assertMatches(village, components, 100, 1);
            fixture.assertMatches(village, components, 101, 1);
            fixture.assertRejectedUnchanged(village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_split", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRejectsSplitRegisteredRoom(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            for (int z = 0; z < 4; z++) {
                fixture.set(fixture.min.offset(2, 0, z), Blocks.STONE.defaultBlockState());
                fixture.set(fixture.min.offset(2, 1, z), Blocks.STONE.defaultBlockState());
            }
            fixture.door(fixture.min.offset(2, 0, 1));
            List<RoomPartitioner.Component> components = fixture.components(village);
            helper.assertTrue(components.size() == 3, "split fixture did not discover three fresh components");
            fixture.assertMatches(village, components, 100, 2);
            fixture.assertMatches(village, components, 101, 1);
            fixture.assertRejectedUnchanged(village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_lost", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRejectsLostRoomIdentity(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            Building second = village.getBuilding(101).orElseThrow();
            Set<BlockPos> displaced = second.getFloorCells().stream().map(pos -> pos.offset(20, 0, 0))
                    .collect(java.util.stream.Collectors.toSet());
            second.setGeometry(second.getPos0().offset(20, 0, 0), second.getPos1().offset(20, 0, 0), displaced);
            Structure structure = village.getStructure(10).orElseThrow();
            Set<FloorGeometry.Cell> savedCells = new java.util.HashSet<>(structure.getFloor(0).orElseThrow().geometry().cells());
            displaced.forEach(pos -> savedCells.add(new FloorGeometry.Cell(pos, pos.getY() + 2)));
            helper.assertTrue(structure.replaceFloorGeometry(0, new FloorGeometry(savedCells, Map.of())),
                    "lost identity fixture did not retain stale saved geometry");
            List<RoomPartitioner.Component> components = fixture.components(village);
            helper.assertTrue(components.size() == 2, "lost identity fixture discovery failed");
            fixture.assertMatches(village, components, 100, 1);
            fixture.assertMatches(village, components, 101, 0);
            fixture.assertRejectedUnchanged(village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_unregistered", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanLeavesFreshUnregisteredRoomsUnregistered(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            fixture.closedRoom(fixture.min.offset(11, 0, 0), 2, 4);
            fixture.door(fixture.min.offset(10, 0, 1));
            List<RoomPartitioner.Component> components = fixture.components(village);
            helper.assertTrue(components.size() == 3, "new component was not discovered");
            fixture.assertMatches(village, components, 100, 1);
            fixture.assertMatches(village, components, 101, 1);
            helper.assertTrue(fixture.manager.fullScan(village) == Building.validationResult.SUCCESS,
                    "unregistered component blocked full refresh");
            helper.assertTrue(village.getBuildings().keySet().equals(Set.of(100, 101))
                            && village.findInteractionRoomAt(fixture.min.offset(12, 0, 2)).isEmpty(),
                    "fresh component was automatically registered");
            assertOwnershipAndReload(helper, village);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_order", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanResultDoesNotDependOnRegistrationOrder(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village forward = fixture.siblings();
            Village reverse = new Village(1, helper.getLevel());
            Structure structure = forward.getStructure(10).orElseThrow().copy();
            reverse.registerStructure(structure, forward.getBuilding(101).orElseThrow().copy());
            reverse.registerRoom(forward.getBuilding(100).orElseThrow().copy());
            reverse.setMainRoom(reverse.getBuilding(100).orElseThrow());
            helper.assertTrue(reverse.getStructure(10).orElseThrow().getFloor(0).orElseThrow().floorNumber() == 0,
                    "setMainRoom fixture did not rebase the displayed floor label");
            reverse.getStructure(10).orElseThrow().setFloorNumber(0, 3);
            reverse.setBuildingInheritanceEnabled(reverse.getBuilding(100).orElseThrow(), false);
            helper.assertTrue(forward.getStructure(10).orElseThrow().save()
                            .equals(reverse.getStructure(10).orElseThrow().save()), "order fixture starts with different Structures");
            fixture.shrinkSecond();
            helper.assertTrue(fixture.manager.fullScan(forward) == Building.validationResult.SUCCESS
                            && fixture.manager.fullScan(reverse) == Building.validationResult.SUCCESS,
                    "registration order fixture did not refresh both Villages");
            helper.assertTrue(forward.getStructure(10).orElseThrow().save()
                            .equals(reverse.getStructure(10).orElseThrow().save())
                            && forward.getLogicalBuilding(10).orElseThrow().save()
                            .equals(reverse.getLogicalBuilding(10).orElseThrow().save()),
                    "registration order changed Structure geometry or logical metadata: "
                            + firstDifference(forward.getStructure(10).orElseThrow().save(),
                            reverse.getStructure(10).orElseThrow().save(), "structure") + "; "
                            + firstDifference(forward.getLogicalBuilding(10).orElseThrow().save(),
                            reverse.getLogicalBuilding(10).orElseThrow().save(), "logical"));
            for (int id : List.of(100, 101)) {
                helper.assertTrue(forward.getBuilding(id).orElseThrow().save()
                                .equals(reverse.getBuilding(id).orElseThrow().save()),
                        "registration order changed Room geometry, ownership or metadata for " + id);
            }
            assertOwnershipAndReload(helper, forward);
            assertOwnershipAndReload(helper, reverse);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_unavailable", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRejectsUnavailableColumnWithoutLoading(GameTestHelper helper) {
        BlockPos remote = helper.absolutePos(new BlockPos(1000000, 8, 1000000));
        helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "unavailable-column fixture is already loaded");
        Set<BlockPos> cells = Set.of(remote, remote.east(), remote.south(), remote.east().south());
        FloorGeometry geometry = new FloorGeometry(cells.stream().map(pos -> new FloorGeometry.Cell(pos, pos.getY() + 2))
                .collect(java.util.stream.Collectors.toSet()), Map.of());
        Structure structure = new Structure(10, remote, List.of(new StructureFloor(0, 3, geometry)));
        structure.setLogicalBuildingId(10);
        Building room = new Building(remote);
        room.setId(100);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(remote, remote.offset(1, 1, 1), cells);
        Village village = new Village(1, helper.getLevel());
        village.registerStructure(structure, room);
        CompoundTag before = village.save();
        helper.assertTrue(new VillageManager(helper.getLevel()).fullScan(village) == Building.validationResult.NOT_IN_BUILDING,
                "unavailable column used saved geometry as a fresh scan");
        helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "full scan loaded the unavailable chunk");
        helper.assertTrue(before.equals(village.save()), "unavailable scan changed saved state");
        helper.succeed();
    }

    @GameTest(batch = "mca_floor_batch_shrink", templateNamespace = "mca",
            template = "gametest/isolated_shelter_lookup", timeoutTicks = 180)
    public static void fullScanRefreshesSiblingShrinkAsOneFloor(GameTestHelper helper) {
        try (Fixture fixture = new Fixture(helper)) {
            Village village = fixture.siblings();
            Building first = village.getBuilding(100).orElseThrow();
            Building second = village.getBuilding(101).orElseThrow();
            Set<BlockPos> firstCells = Set.copyOf(first.getFloorCells());
            Set<BlockPos> secondCells = Set.copyOf(second.getFloorCells());
            StructureFloor floor = village.getStructure(10).orElseThrow().getFloor(0).orElseThrow();
            int number = floor.floorNumber(), anchor = floor.anchorY();
            CompoundTag logical = village.getLogicalBuilding(10).orElseThrow().save();

            fixture.shrinkSecond();
            StructureScanner.Result fresh = fixture.fresh(village, 0);
            List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), fresh.scan());
            helper.assertTrue(components.size() == 2, "shrink must retain two fresh components");
            Set<BlockPos> lost = new java.util.HashSet<>(secondCells);
            lost.removeAll(fresh.scannedFloor().cells().stream().map(FloorGeometry.Cell::feet).toList());
            helper.assertTrue(!lost.isEmpty() && lost.contains(fixture.min.offset(9, 0, 1)),
                    "shrink fixture did not remove the known exterior strip");
            CompoundTag before = village.save();
            RegisteredRoomUpdate selected = fixture.workflow.analyzeRegisteredRoomUpdate(
                    village, first.getId(), first.getSourceBlock());
            helper.assertTrue(selected.result() == Building.validationResult.OVERLAP,
                    "selected-room sibling guard returned " + selected.result());
            helper.assertTrue(fixture.workflow.analyzeRegisteredRoomUpdate(village, first.getId(),
                            first.getSourceBlock(), fresh).result() == Building.validationResult.OVERLAP,
                    "reused diagnostic scan bypassed the selected-room sibling guard");
            helper.assertTrue(before.equals(village.save()), "selected analysis mutated saved state");

            Building.validationResult result = fixture.manager.fullScan(village);
            helper.assertTrue(result == Building.validationResult.SUCCESS,
                    "whole-village sibling shrink returned " + result + " instead of SUCCESS");
            Building refreshedFirst = village.getBuilding(100).orElseThrow();
            Building refreshedSecond = village.getBuilding(101).orElseThrow();
            helper.assertTrue(refreshedFirst.getFloorCells().equals(firstCells), "unchanged Room A moved");
            helper.assertTrue(!refreshedSecond.getFloorCells().equals(secondCells)
                            && java.util.Collections.disjoint(refreshedSecond.getFloorCells(), lost),
                    "Room B retained the lost exterior strip");
            helper.assertTrue(village.getBuildings().keySet().equals(Set.of(100, 101)), "Room IDs changed");
            StructureFloor refreshedFloor = village.getStructure(10).orElseThrow().getFloor(0).orElseThrow();
            helper.assertTrue(refreshedFloor.floorNumber() == number && refreshedFloor.anchorY() == anchor,
                    "floor label/anchor changed");
            helper.assertTrue(refreshedFirst.isTypeForced() && refreshedFirst.getType().equals(first.getType())
                            && !refreshedSecond.isTypeForced() && refreshedSecond.getType().equals("building")
                            && !refreshedSecond.contributesToMain()
                            && refreshedFirst.contributesToMain(), "Room type/contribution flags changed");
            helper.assertTrue(logical.equals(village.getLogicalBuilding(10).orElseThrow().save()),
                    "logical identity, main Room or inheritance changed");
            helper.assertTrue(refreshedSecond.getBlocks().values().stream().flatMap(List::stream)
                            .noneMatch(fixture.min.offset(8, -1, 1)::equals), "removed smoker POI survived");
            assertOwnershipAndReload(helper, village);
        }
        helper.succeed();
    }

    private static void assertOwnershipAndReload(GameTestHelper helper, Village village) {
        village.getRooms().forEach(room -> {
            StructureFloor floor = village.getStructureFor(room).orElseThrow().getFloor(room.getFloorId()).orElseThrow();
            helper.assertTrue(room.getFloorCells().stream().allMatch(cell -> floor.geometry().cellAt(cell).isPresent()),
                    "Room cells escaped their refreshed Floor");
        });
        CompoundTag saved = village.save();
        Village loaded = new Village(saved, helper.getLevel());
        village.getRooms().forEach(room -> {
            Building restored = loaded.getBuilding(room.getId()).orElseThrow();
            helper.assertTrue(room.getBlocks().equals(restored.getBlocks()),
                    "reload changed POIs for Room " + room.getId() + ": " + room.getBlocks() + " -> " + restored.getBlocks());
            helper.assertTrue(room.getFloorCells().equals(restored.getFloorCells()),
                    "reload changed exact cells for Room " + room.getId());
        });
        CompoundTag reloaded = loaded.save();
        if (!saved.equals(reloaded)) {
            MCA.LOGGER.info("[MCA-FloorBatch] serializedDifference={} RoomIds={} -> {}",
                    firstDifference(saved, reloaded, "village"), serializedRoomIds(saved), serializedRoomIds(reloaded));
        }
        helper.assertTrue(normalizeUnorderedState(saved).equals(normalizeUnorderedState(reloaded)),
                "save/reload changed geometry, ownership or metadata: " + firstDifference(saved, reloaded, "village"));
    }

    private static List<Integer> serializedRoomIds(CompoundTag village) {
        return village.getList("buildings", net.minecraft.nbt.Tag.TAG_COMPOUND).stream()
                .map(tag -> ((CompoundTag) tag).getInt("id")).toList();
    }

    private static CompoundTag normalizeUnorderedState(CompoundTag saved) {
        CompoundTag normalized = saved.copy();
        // HashMap values and exact Room cell sets have no serialized iteration-order contract.
        for (String key : List.of("buildings", "externalBuildings", "structures", "logicalBuildings")) {
            net.minecraft.nbt.ListTag ordered = new net.minecraft.nbt.ListTag();
            normalized.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND).stream().map(tag -> (CompoundTag) tag)
                    .sorted(java.util.Comparator.comparingInt(tag -> tag.getInt("id"))).forEach(tag -> {
                        if (tag.contains("floorCells")) {
                            net.minecraft.nbt.ListTag cells = new net.minecraft.nbt.ListTag();
                            tag.getList("floorCells", net.minecraft.nbt.Tag.TAG_INT_ARRAY).stream()
                                    .sorted(java.util.Comparator.comparing(Object::toString)).forEach(cells::add);
                            tag.put("floorCells", cells);
                        }
                        ordered.add(tag);
                    });
            normalized.put(key, ordered);
        }
        return normalized;
    }

    private static String firstDifference(net.minecraft.nbt.Tag expected, net.minecraft.nbt.Tag actual, String path) {
        if (java.util.Objects.equals(expected, actual)) return "none";
        if (expected instanceof CompoundTag left && actual instanceof CompoundTag right) {
            Set<String> keys = new java.util.TreeSet<>(left.getAllKeys());
            keys.addAll(right.getAllKeys());
            for (String key : keys) {
                if (!java.util.Objects.equals(left.get(key), right.get(key))) {
                    return firstDifference(left.get(key), right.get(key), path + "." + key);
                }
            }
        }
        if (expected instanceof net.minecraft.nbt.ListTag left && actual instanceof net.minecraft.nbt.ListTag right
                && left.size() == right.size()) {
            for (int i = 0; i < left.size(); i++) {
                if (!left.get(i).equals(right.get(i))) return firstDifference(left.get(i), right.get(i), path + "[" + i + "]");
            }
        }
        return path + ": expected=" + expected + " actual=" + actual;
    }

    private static final class FailLaterVillage extends Village {
        final IllegalStateException failure = new IllegalStateException("intentional later Floor publication failure");
        final Set<BlockPos> originalSecondCells;
        final boolean throwOnFailure;
        int publications;
        boolean earlierRefreshObserved;

        FailLaterVillage(CompoundTag saved, net.minecraft.server.level.ServerLevel level, boolean throwOnFailure) {
            super(saved, level);
            this.throwOnFailure = throwOnFailure;
            originalSecondCells = Set.copyOf(getBuilding(101).orElseThrow().getFloorCells());
        }

        @Override
        boolean publishFloorRefresh(Structure refreshed, int floorId, java.util.Collection<Building> replacements) {
            if (++publications == 2) {
                earlierRefreshObserved = !getBuilding(101).orElseThrow().getFloorCells().equals(originalSecondCells);
                if (throwOnFailure) throw failure;
                return false;
            }
            return super.publishFloorRefresh(refreshed, floorId, replacements);
        }
    }

    private static final class DiagnosticCapture extends AbstractAppender implements AutoCloseable {
        final List<String> messages = new java.util.ArrayList<>();
        final java.util.function.Consumer<String> onMessage;
        final Logger logger = (Logger) MCA.LOGGER;

        DiagnosticCapture(java.util.function.Consumer<String> onMessage) {
            super("mca-floor-diagnostic-capture", null, null, false, Property.EMPTY_ARRAY);
            this.onMessage = onMessage;
            start();
            logger.addAppender(this);
        }

        @Override
        public void append(LogEvent event) {
            String message = event.getMessage().getFormattedMessage();
            if (!message.startsWith("[MCA-Diagnose]")) return;
            messages.add(message);
            onMessage.accept(message);
        }

        @Override
        public void close() {
            logger.removeAppender(this);
            stop();
        }
    }

    private static final class Fixture implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos min;
        final VillageManager manager;
        final RoomWorkflow workflow;
        final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        final List<Integer> diagnosticVillageIds = new java.util.ArrayList<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            min = helper.absolutePos(new BlockPos(32, 8, 32));
            manager = new VillageManager(helper.getLevel());
            workflow = new RoomWorkflow(manager, helper.getLevel());
            BlockPos snapshotMin = min.offset(-2, -3, -2), snapshotMax = min.offset(13, 12, 7);
            helper.assertTrue(helper.getBounds().contains(net.minecraft.world.phys.Vec3.atLowerCornerOf(snapshotMin))
                            && helper.getBounds().contains(net.minecraft.world.phys.Vec3.atCenterOf(snapshotMax)),
                    "Floor batch terrain bounds escape the allocated template");
            for (BlockPos pos : BlockPos.betweenClosed(snapshotMin, snapshotMax)) {
                original.put(pos.immutable(), helper.getLevel().getBlockState(pos));
            }
        }

        Village siblings() {
            closedRoom(min, 4, 4);
            closedRoom(min.offset(5, 0, 0), 5, 4);
            door(min.offset(4, 0, 1));
            set(min.offset(8, -1, 1), Blocks.SMOKER.defaultBlockState());
            BlockPos source = min.offset(1, 0, 1);
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), source, 256, 24);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS, "initial Floor scan: " + scan.result());
            StructureFloor floor = new StructureFloor(0, 3, scan.floor());
            Structure structure = new Structure(10, source, List.of(floor));
            structure.setLogicalBuildingId(10);
            List<RoomPartitioner.Component> components = BuildingRoomScanner.components(helper.getLevel(), scan);
            helper.assertTrue(components.size() == 2, "initial fixture must have two connected door-separated Rooms");
            Building first = room(100, structure, floor, source, components);
            Building second = room(101, structure, floor, min.offset(7, 0, 2), components);
            helper.assertTrue(second.getBlocks().values().stream().flatMap(List::stream)
                            .anyMatch(min.offset(8, -1, 1)::equals), "fixture did not record its owned support POI");
            first.setType("building");
            first.setTypeForced(true);
            second.setContributesToMain(false);
            Village village = new Village(1, helper.getLevel());
            village.registerStructure(structure, first);
            village.registerRoom(second);
            village.setBuildingInheritanceEnabled(first, false);
            helper.assertTrue(first.getFloorId() == second.getFloorId()
                            && first.getFloorCells().contains(source)
                            && second.getFloorCells().contains(min.offset(9, 0, 1)), "invalid sibling ownership setup");
            return village;
        }

        Village diagnosticsVillage() {
            return diagnosticsVillage(4);
        }

        Village diagnosticsVillage(int width) {
            closedRoom(min, width, 4);
            VillageManager global = VillageManager.get(helper.getLevel());
            BlockPos source = min.offset(2, 0, 2);
            helper.assertTrue(global.findNearestVillage(source, Village.MERGE_MARGIN).isEmpty(),
                    "diagnostic fixture overlaps another registered Village");
            RoomWorkflow.Outcome outcome = new RoomWorkflow(global, helper.getLevel()).addBuilding(source, null);
            helper.assertTrue(outcome.status() == RoomWorkflow.Status.COMMITTED, "diagnostic Room registration failed");
            Village village = global.findNearestVillage(source, Village.MERGE_MARGIN).orElseThrow();
            diagnosticVillageIds.add(village.getId());
            helper.assertTrue(village.getRooms().count() == 1, "diagnostic fixture contains unrelated Rooms");
            return village;
        }

        Village stacked() {
            Village lower = siblings();
            BlockPos upper = min.above(5);
            closedRoom(upper, 4, 4);
            BlockPos upperSource = upper.offset(1, 0, 1);
            set(upper.offset(2, -1, 1), Blocks.SMOKER.defaultBlockState());
            SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(helper.getLevel(), upperSource, 256, 24);
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS, "upper Floor did not scan");
            StructureFloor floor = new StructureFloor(1, 8, scan.floor());
            Structure structure = new Structure(10, lower.getStructure(10).orElseThrow().getSource(),
                    List.of(lower.getStructure(10).orElseThrow().getFloor(0).orElseThrow(), floor));
            structure.setLogicalBuildingId(10);
            Village village = new Village(1, helper.getLevel());
            village.registerStructure(structure, lower.getBuilding(100).orElseThrow().copy());
            village.registerRoom(lower.getBuilding(101).orElseThrow().copy());
            village.registerRoom(room(200, structure, floor, upperSource,
                    BuildingRoomScanner.components(helper.getLevel(), scan)));
            village.setBuildingInheritanceEnabled(village.getBuilding(100).orElseThrow(), false);
            return village;
        }

        void shrinkBothFloors() {
            shrinkSecond();
            for (int z = 0; z < 4; z++) {
                set(min.offset(3, 5, z), Blocks.STONE.defaultBlockState());
                set(min.offset(3, 6, z), Blocks.STONE.defaultBlockState());
            }
        }

        Building room(int id, Structure structure, StructureFloor floor, BlockPos source,
                      List<RoomPartitioner.Component> components) {
            RoomPartitioner.Component component = components.stream()
                    .filter(candidate -> candidate.floorCells().contains(source)).findFirst().orElseThrow();
            BuildingRoomScanner.Result geometry = BuildingRoomScanner.materialize(source, 256, components, component);
            Building room = new Building(source);
            helper.assertTrue(room.applyRoomScan(helper.getLevel(), geometry) == Building.validationResult.SUCCESS,
                    "fixture Room did not materialize");
            room.setId(id);
            room.setStructureId(structure.getId());
            room.setFloorId(floor.id());
            return room;
        }

        StructureScanner.Result fresh(Village village, int floorId) {
            Structure structure = village.getStructure(10).orElseThrow();
            StructureScanner.Result scan = StructureScanner.scanExistingFloor(helper.getLevel(), structure,
                    structure.getFloor(floorId).orElseThrow(), min.offset(1, floorId * 5, 1), village.getStructures().values());
            helper.assertTrue(scan.result() == Building.validationResult.SUCCESS, "fresh Floor scan: " + scan.result());
            return scan;
        }

        List<RoomPartitioner.Component> components(Village village) {
            return BuildingRoomScanner.components(helper.getLevel(), fresh(village, 0).scan());
        }

        void assertMatches(Village village, List<RoomPartitioner.Component> components, int roomId, long count) {
            FloorGeometry geometry = fresh(village, 0).scannedFloor();
            Building room = village.getBuilding(roomId).orElseThrow();
            helper.assertTrue(components.stream().filter(component -> geometry.roomIdentityOverlapCount(
                            room.getFloorCells(), component.floorCells()) > 0).count() == count,
                    "unexpected identity graph for Room " + roomId + "; expected " + count + " matches");
        }

        void assertRejectedUnchanged(Village village) {
            CompoundTag before = village.save();
            RoomWorkflow.RegisteredFloorUpdate update = workflow.analyzeRegisteredFloorUpdate(village, 10, 0);
            helper.assertTrue(update.result() == Building.validationResult.OVERLAP
                            && update.refreshedStructure() == null && update.replacementRooms().isEmpty(),
                    "rejected analysis exposed a publishable payload");
            helper.assertTrue(before.equals(village.save()), "rejected analysis changed saved state");
            Building.validationResult result = manager.fullScan(village);
            helper.assertTrue(result == Building.validationResult.OVERLAP, "identity rejection returned " + result);
            helper.assertTrue(before.equals(village.save()), "failed batch changed saved state");
        }

        void shrinkSecond() {
            for (int z = 0; z < 4; z++) {
                set(min.offset(9, 0, z), Blocks.STONE.defaultBlockState());
                set(min.offset(9, 1, z), Blocks.STONE.defaultBlockState());
            }
            set(min.offset(8, -1, 1), Blocks.STONE.defaultBlockState());
        }

        void closedRoom(BlockPos start, int width, int depth) {
            for (int x = -1; x <= width; x++) {
                for (int z = -1; z <= depth; z++) {
                    BlockPos pos = start.offset(x, 0, z);
                    set(pos.below(), Blocks.STONE.defaultBlockState());
                    boolean wall = x == -1 || x == width || z == -1 || z == depth;
                    set(pos, (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                    set(pos.above(), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
                    set(pos.above(2), Blocks.STONE.defaultBlockState());
                }
            }
        }

        void door(BlockPos pos) {
            BlockState state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                    .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            set(pos, state);
            set(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void set(BlockPos pos, BlockState state) {
            if (!original.containsKey(pos)) throw new IllegalArgumentException("Fixture write outside owned bounds: " + pos);
            helper.getLevel().setBlock(pos, state, 2);
        }

        @Override
        public void close() {
            for (int id : diagnosticVillageIds) VillageManager.get(helper.getLevel()).removeVillage(id);
            original.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 2));
        }
    }
}
