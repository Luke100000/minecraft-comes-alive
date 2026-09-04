package net.conczin.mca.server.world.data;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageFloorSystemTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void floorNumbersAreRelativeToExplicitGroundFloor() {
        Village village = new Village(1, null);
        Structure low = structure(10, 77, floor(0, 40), floor(1, 44));
        Structure high = structure(11, 77, floor(0, 48));
        village.registerStructure(low, room(100, 10, 1, true));
        village.registerStructure(high, room(101, 11, 0, true));

        village.refreshLogicalBuildings();

        assertEquals(-1, low.getFloor(0).orElseThrow().floorNumber());
        assertEquals(0, low.getFloor(1).orElseThrow().floorNumber());
        assertEquals(1, high.getFloor(0).orElseThrow().floorNumber());
    }

    @Test
    void floorNumbersAreRebuiltFromLogicalGroundAfterSaveLoad() {
        Village village = new Village(1, null);
        Structure low = structure(10, 77, floor(0, 40), floor(1, 44));
        Structure high = structure(11, 77, floor(0, 48));
        village.registerStructure(low, room(100, 10, 1, true));
        village.registerStructure(high, room(101, 11, 0, true));
        village.refreshLogicalBuildings();

        Village reloaded = new Village(village.save(), null);

        assertEquals(-1, reloaded.getStructure(10).orElseThrow()
                .getFloor(0).orElseThrow().floorNumber());
        assertEquals(0, reloaded.getStructure(10).orElseThrow()
                .getFloor(1).orElseThrow().floorNumber());
        assertEquals(1, reloaded.getStructure(11).orElseThrow()
                .getFloor(0).orElseThrow().floorNumber());
    }

    @Test
    void explicitRemovalRejectsCurrentMainRoom() {
        Village village = populatedVillage();

        assertFalse(village.removeRoom(1));

        assertTrue(village.getBuildings().containsKey(1));
        assertEquals(1, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    }

    @Test
    void explicitRoomRemovalReturnsFalseForUnknownRoom() {
        Village village = populatedVillage();

        assertFalse(village.removeRoom(999));
        assertEquals(3, village.getBuildings().size());
    }

    @Test
    void explicitExternalRemovalDoesNotTouchFunctionalRooms() {
        Village village = populatedVillage();
        ExternalBuilding external = new ExternalBuilding(new BlockPos(20, 64, 20));
        external.setId(50);
        external.setType("town_center");
        village.registerExternalBuilding(external);

        assertTrue(village.removeExternalBuilding(50));

        assertTrue(village.getBuilding(50).isEmpty());
        assertTrue(village.getBuilding(1).isPresent());
        assertTrue(village.getBuilding(2).isPresent());
        assertFalse(village.removeExternalBuilding(50));
    }

    @Test
    void inheritanceTogglePreservesRoomContributionPreferences() {
        Village village = populatedVillage();
        Building main = village.getBuildings().get(1);

        assertTrue(village.setBuildingInheritanceEnabled(main, false));
        assertFalse(village.isBuildingInheritanceEnabled(main));
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());

        assertTrue(village.setBuildingInheritanceEnabled(main, true));
        assertTrue(village.isBuildingInheritanceEnabled(main));
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());
    }

    @Test
    void disablingSharingWithOneEligibleTypeAutoResolvesWithoutPolymorph() {
        Map<String, BuildingType> previous = installSingleWorkshopType();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);

            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertEquals(List.of("workshop"), update.matchingTypes());
            assertFalse(update.requiresTypeSelection());
            assertEquals(Building.validationResult.SUCCESS,
                    village.commitRoomInheritanceUpdate(update, null));
            assertFalse(room.contributesToMain());
            assertEquals("workshop", room.getType());
            assertFalse(room.isTypeForced());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
    }

    @Test
    void invalidPolymorphChoiceDoesNotDisableRoomSharing() {
        Map<String, BuildingType> previous = installSingleWorkshopType();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);
            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertEquals(Building.validationResult.INVALID_TYPE,
                    village.commitRoomInheritanceUpdate(update, "not_eligible"));
            assertTrue(room.contributesToMain());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
    }

    @Test
    void ambiguousInheritancePolymorphAppliesSharingAndSelectedTypeAtomically() {
        Map<String, BuildingType> previous = installAmbiguousWorkshopTypes();
        try {
            Village village = populatedVillage();
            Building room = village.getBuildings().get(2);
            room.addBlock(Blocks.CRAFTING_TABLE, BlockPos.ZERO);
            RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, false);

            assertTrue(update.requiresTypeSelection());
            assertEquals(Building.validationResult.SUCCESS,
                    village.commitRoomInheritanceUpdate(update, "workshop"));
            assertFalse(room.contributesToMain());
            assertEquals("workshop", room.getType());
            assertTrue(room.isTypeForced());
        } finally {
            BuildingTypes.getInstance().setBuildingTypes(previous);
        }
    }

    @Test
    void changingMainRoomDoesNotRewriteRoomPreferences() {
        Village village = populatedVillage();

        assertTrue(village.setMainRoom(village.getBuildings().get(2)));

        assertEquals(2, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
        assertTrue(village.getBuildings().get(1).contributesToMain());
        assertTrue(village.getBuildings().get(2).contributesToMain());
        assertFalse(village.getBuildings().get(3).contributesToMain());
    }

    @Test
    void changingMainRoomAlsoMakesItsFloorTheGroundFloor() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, -1, region(64)),
                new StructureFloor(1, 72, 76, 0, region(72)),
                new StructureFloor(2, 80, 84, 1, region(80)));
        Building originalMain = room(100, 10, 1, true);
        Building upperRoom = room(101, 10, 2, true);
        village.registerStructure(structure, originalMain);
        village.registerRoom(upperRoom);
        village.refreshLogicalBuildings();

        assertTrue(village.setMainRoom(upperRoom));

        LogicalBuilding logical = village.getLogicalBuilding(10).orElseThrow();
        assertEquals(101, logical.mainRoomId());
        assertEquals(-2, structure.getFloor(0).orElseThrow().floorNumber());
        assertEquals(-1, structure.getFloor(1).orElseThrow().floorNumber());
        assertEquals(0, structure.getFloor(2).orElseThrow().floorNumber());
    }

    @Test
    void logicalBuildingPersistsMainRoomAsTheOnlyGroundFloorAnchor() {
        Village village = populatedVillage();

        CompoundTag saved = village.save();
        CompoundTag logical = saved.getList("logicalBuildings", Tag.TAG_COMPOUND).getCompound(0);

        assertTrue(logical.contains("mainRoomId"));
        assertFalse(logical.contains("groundStructureId"));
        assertFalse(logical.contains("groundFloorId"));
    }

    @Test
    void missingStructureDoesNotPretendToBeItsOwnLogicalBuilding() {
        Village village = populatedVillage();

        assertEquals(-1, village.getLogicalBuildingId(999));
    }

    @Test
    void logicalBuildingWithoutRoomsIsDeletedWithItsPersistedStructures() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)));
        Building main = room(100, 10, 0, true);
        village.registerStructure(structure, main);

        village.removeRooms(List.of(main.getId()));
        village.refreshLogicalBuildings();

        assertTrue(village.getLogicalBuilding(10).isEmpty());
        assertTrue(village.getStructure(10).isEmpty());
    }

    @Test
    void floorAttachmentRequiresProvenVerticalConnectionInsteadOfArbitraryHeightGap() {
        Village village = new Village(1, null);
        StructureFloor existingFloor = new StructureFloor(0, 64, 68, 0, region(64), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 64, 0), StructureFloor.ConnectorType.LADDER)));
        Structure structure = structure(10, 10, existingFloor);
        village.registerStructure(structure, room(100, 10, 0, true));

        StructureFloor connectedHighFloor = new StructureFloor(1, 76, 80, 0, region(76), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 76, 0), StructureFloor.ConnectorType.LADDER)));
        StructureFloor nearbyButDisconnected = new StructureFloor(2, 70, 74, 0, region(70));
        StructureConnector.VerticalConnection connection = new StructureConnector.VerticalConnection(
                structure, existingFloor);

        assertEquals(10, village.selectAttachmentTarget(connectedHighFloor, List.of(connection))
                .orElseThrow().buildingId());
        assertTrue(village.selectAttachmentTarget(nearbyButDisconnected, List.of()).isEmpty());
    }

    @Test
    void floorAttachmentRejectsAnOverlappingCopyOfTheRegisteredFloor() {
        Village village = new Village(1, null);
        StructureFloor existingFloor = new StructureFloor(0, 64, 68, 0, region(64), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 64, 0), StructureFloor.ConnectorType.LADDER)));
        Structure structure = structure(10, 10, existingFloor);
        village.registerStructure(structure, room(100, 10, 0, true));

        StructureFloor rescannedSameFloor = new StructureFloor(1, 64, 68, 0, region(64), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 64, 0), StructureFloor.ConnectorType.LADDER)));

        StructureConnector.VerticalConnection falseConnection = new StructureConnector.VerticalConnection(
                structure, existingFloor);

        assertTrue(village.selectAttachmentTarget(rescannedSameFloor, List.of(falseConnection)).isEmpty(),
                "a rescan of an existing floor cannot become Add Floor 0 just because it sees the same ladder");
    }

    @Test
    void floorAttachmentAcceptsDistinctBandWhenLegacyCeilingOverlaps() {
        Village village = new Village(1, null);
        StructureFloor staleLower = new StructureFloor(0, 88, 93, 0, region(88), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 88, 0), StructureFloor.ConnectorType.LADDER)));
        Structure structure = structure(10, 10, staleLower);
        village.registerStructure(structure, room(100, 10, 0, true));

        StructureFloor upper = new StructureFloor(1, 91, 94, 0, region(91), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 91, 0), StructureFloor.ConnectorType.LADDER)));
        StructureConnector.VerticalConnection connection = new StructureConnector.VerticalConnection(
                structure, staleLower);

        assertEquals(10, village.selectAttachmentTarget(upper, List.of(connection))
                .orElseThrow().buildingId());
    }

    @Test
    void walkableStoreyEvidenceCanProveStairFloorAttachment() {
        Village village = new Village(1, null);
        StructureFloor lower = new StructureFloor(0, 88, 91, 0, region(88));
        village.registerStructure(structure(10, 10, lower), room(100, 10, 0, true));

        StructureFloor upper = new StructureFloor(1, 91, 94, 0, region(91));
        List<FloorGeometry> connectedFloors = List.of(
                scannedFloor(region(88)),
                scannedFloor(region(91)));

        assertEquals(10, village.selectAttachmentTarget(upper, List.of(), connectedFloors)
                .orElseThrow().buildingId());
    }

    @Test
    void logicalRefreshDoesNotRewritePhysicalCeilingsAcrossSeparateStructures() {
        Village village = new Village(1, null);
        Structure lower = structure(10, 10,
                new StructureFloor(0, 88, 93, 0, region(88)));
        Structure upper = structure(11, 10,
                new StructureFloor(0, 91, 94, 0, region(91)));
        village.registerStructure(lower, room(100, 10, 0, true));
        village.registerStructure(upper, room(101, 11, 0, true));

        village.refreshLogicalBuildings();

        StructureFloor refreshedLower = lower.getFloor(0).orElseThrow();
        StructureFloor refreshedUpper = upper.getFloor(0).orElseThrow();
        assertEquals(93, lower.semanticCeilingY(refreshedLower));
        assertEquals(94, upper.semanticCeilingY(refreshedUpper));
        assertEquals(0, refreshedLower.floorNumber());
        assertEquals(1, refreshedUpper.floorNumber());
    }

    @Test
    void duplicateCheckUsesSemanticBandInsteadOfLegacyCeilingVolume() {
        Village village = new Village(1, null);
        Structure staleLower = structure(10, 10,
                new StructureFloor(0, 88, 93, 0, region(88)));
        village.registerStructure(staleLower, room(100, 10, 0, true));

        Structure upper = structure(-1, 10,
                new StructureFloor(0, 91, 94, 0, region(91)));
        Structure sameBand = structure(-1, 10,
                new StructureFloor(0, 90, 94, 0, region(90)));

        assertFalse(village.hasRegisteredFloorOverlap(upper));
        assertTrue(village.hasRegisteredFloorOverlap(sameBand));
    }

    @Test
    void floorAttachmentRejectsExistingGroundFloorEvenWhenBasementWouldAcceptIt() {
        Village village = new Village(1, null);
        StructureFloor groundFloor = new StructureFloor(0, 64, 68, 0, region(64), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 64, 0), StructureFloor.ConnectorType.LADDER)));
        StructureFloor basementFloor = new StructureFloor(0, 60, 64, -1, region(60), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 60, 0), StructureFloor.ConnectorType.LADDER)));
        village.registerStructure(structure(10, 10, groundFloor), room(100, 10, 0, true));
        village.registerStructure(structure(11, 10, basementFloor), room(101, 11, 0, true));
        village.refreshLogicalBuildings();

        StructureFloor rescannedGroundFloor = new StructureFloor(1, 64, 68, 0, region(64), List.of(
                new StructureFloor.ConnectorMarker(new BlockPos(0, 64, 0), StructureFloor.ConnectorType.LADDER)));

        StructureConnector.VerticalConnection falseConnection = new StructureConnector.VerticalConnection(
                village.getStructure(11).orElseThrow(), basementFloor);

        assertTrue(village.selectAttachmentTarget(rescannedGroundFloor, List.of(falseConnection)).isEmpty(),
                "an already registered ground floor cannot reattach through its basement as Add Floor 0");
    }

    @Test
    void interactionRoomLookupFallsBackToPhysicalRoomGeometryWithoutRecursing() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)));
        Building room = room(100, 10, 0, true);
        BuildingFloorRegion legacyRegion = BuildingFloorRegion.fromFootprint(64, Set.of(
                new BlockPos(10, 64, 10)));
        room.setGeometry(new BlockPos(10, 64, 10), new BlockPos(10, 67, 10), legacyRegion);
        village.registerStructure(structure, room);

        assertEquals(room, village.findInteractionRoomAt(new BlockPos(10, 64, 10)).orElseThrow());
    }

    @Test
    void physicalRoomLookupDoesNotUseInteractionSupportBand() {
        Village village = new Village(1, null);
        StructureFloor floor = new StructureFloor(0, 64, 68, 0, region(64));
        Structure structure = structure(10, 10, floor);
        Building room = room(100, 10, 0, true);
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 67, 1), region(64));
        village.registerStructure(structure, room);

        BlockPos supportBlock = new BlockPos(0, 63, 0);
        assertTrue(village.findPhysicalRoomAt(supportBlock).isEmpty());
        assertEquals(room, village.findInteractionRoomAt(supportBlock).orElseThrow());
    }

    @Test
    void invalidMainRoomRepairsToLowestSurvivingRoomId() {
        Village village = populatedVillage();
        village.getLogicalBuilding(10).orElseThrow().setMainRoomId(99);

        village.refreshLogicalBuildings();

        assertEquals(1, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    }

    @Test
    void roomContributionMutationRequiresRegisteredRoom() {
        Village village = populatedVillage();

        assertTrue(village.setRoomContributesToMain(village.getBuildings().get(3), true));
        assertTrue(village.getBuildings().get(3).contributesToMain());
        assertFalse(village.setRoomContributesToMain(room(99, 10, 0, false), true));
    }

    @Test
    void replacingOneFloorGeometryDoesNotRenumberNeighboringFloors() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 77,
                new StructureFloor(3, 64, 70, -1, region(64)),
                new StructureFloor(7, 74, 80, 0, region(74)),
                new StructureFloor(9, 84, 90, 1, region(84)));
        village.registerStructure(structure, room(100, 10, 7, true));

        assertTrue(structure.replaceFloorGeometry(7,
                new StructureFloor(0, 75, 82, region(75))));

        assertEquals(-1, structure.getFloor(3).orElseThrow().floorNumber());
        assertEquals(0, structure.getFloor(7).orElseThrow().floorNumber());
        assertEquals(1, structure.getFloor(9).orElseThrow().floorNumber());
        assertEquals(75, structure.getFloor(7).orElseThrow().anchorY());
    }

    @Test
    void onlyOutermostEmptyUpperAndBasementFloorsAreRemovable() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(1, 64, 68, -2, region(64)),
                new StructureFloor(2, 68, 72, -1, region(68)),
                new StructureFloor(3, 72, 76, 0, region(72)),
                new StructureFloor(4, 76, 80, 1, region(76)),
                new StructureFloor(5, 80, 84, 2, region(80)));
        Building main = room(100, 10, 3, true);
        village.registerStructure(structure, main);
        village.refreshLogicalBuildings();

        assertTrue(village.canRemoveFloor(10, -2));
        assertFalse(village.canRemoveFloor(10, -1));
        assertFalse(village.canRemoveFloor(10, 0));
        assertFalse(village.canRemoveFloor(10, 1));
        assertTrue(village.canRemoveFloor(10, 2));

        assertTrue(village.removeFloor(10, 2));
        assertTrue(structure.getFloor(5).isEmpty());
        assertTrue(village.canRemoveFloor(10, 1));

        assertTrue(village.removeFloor(10, -2));
        assertTrue(structure.getFloor(1).isEmpty());
        assertTrue(village.canRemoveFloor(10, -1));
        assertEquals(main, village.getBuilding(100).orElseThrow());
        assertEquals(0, structure.getFloor(3).orElseThrow().floorNumber());
    }

    @Test
    void removingFloorRemovesEveryStructureSliceInTheLogicalFloor() {
        Village village = new Village(1, null);
        Structure first = structure(10, 10,
                new StructureFloor(0, 60, 64, -1, region(60)),
                new StructureFloor(1, 64, 68, 0, region(64)));
        Structure second = structure(11, 10,
                new StructureFloor(0, 60, 64, -1, region(60)),
                new StructureFloor(1, 64, 68, 0, region(64)));
        village.registerStructure(first, room(100, 10, 1, true));
        village.registerStructure(second, room(101, 11, 1, true));
        village.refreshLogicalBuildings();

        assertTrue(village.canRemoveFloor(10, -1));
        assertTrue(village.removeFloor(10, -1));

        assertTrue(first.getFloor(0).isEmpty());
        assertTrue(second.getFloor(0).isEmpty());
        assertTrue(first.getFloor(1).isPresent());
        assertTrue(second.getFloor(1).isPresent());
    }

    @Test
    void removingSelectedOrdinalOnlyAffectsPlayersLogicalBuilding() {
        Village village = new Village(1, null);
        Structure first = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)));
        Structure second = structure(20, 20,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)));
        village.registerStructure(first, room(100, 10, 0, true));
        village.registerStructure(second, room(200, 20, 0, true));
        village.refreshLogicalBuildings();

        assertTrue(village.removeFloor(10, 1));

        assertTrue(first.getFloor(1).isEmpty());
        assertTrue(second.getFloor(1).isPresent());
    }

    @Test
    void removingLastRoomFromTerminalFloorKeepsFloorAvailableForExplicitRemoval() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)));
        Building main = room(100, 10, 0, true);
        Building upperRoom = room(101, 10, 1, true);
        village.registerStructure(structure, main);
        village.registerRoom(upperRoom);
        village.refreshLogicalBuildings();

        assertTrue(village.removeRoom(101));

        assertTrue(village.getBuilding(101).isEmpty());
        assertTrue(structure.getFloor(1).isPresent());
        assertTrue(village.canRemoveFloor(10, 1));
        assertTrue(structure.getFloor(0).isPresent());
        assertEquals(main, village.getBuilding(100).orElseThrow());
    }

    @Test
    void removingLastRoomFromMiddleFloorKeepsTheFloor() {
        Village village = new Village(1, null);
        Structure structure = structure(10, 10,
                new StructureFloor(0, 64, 68, 0, region(64)),
                new StructureFloor(1, 72, 76, 1, region(72)),
                new StructureFloor(2, 80, 84, 2, region(80)));
        Building main = room(100, 10, 0, true);
        Building middleRoom = room(101, 10, 1, true);
        village.registerStructure(structure, main);
        village.registerRoom(middleRoom);
        village.refreshLogicalBuildings();

        assertTrue(village.removeRoom(101));

        assertTrue(village.getBuilding(101).isEmpty());
        assertTrue(structure.getFloor(1).isPresent());
        assertTrue(structure.getFloor(2).isPresent());
        assertEquals(main, village.getBuilding(100).orElseThrow());
    }

    @Test
    void fullMaintenanceTargetsRegisteredRoomsInStableIdOrder() {
        Village village = populatedVillage();

        assertEquals(List.of(1, 2, 3), VillageManager.fullScanRoomIds(village));
    }

    @Test
    void expandedStructureAndNewRoomCommitAgainstOneRefreshedFloorGeometry() {
        Village village = new Village(1, null);
        BuildingFloorRegion oldRegion = BuildingFloorRegion.fromFootprint(64, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));
        StructureFloor oldFloor = new StructureFloor(0, 64, 68, 0, oldRegion);
        Structure current = structure(10, 10, oldFloor);
        Building main = room(100, 10, 0, true);
        main.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 67, 0), oldRegion);
        village.registerStructure(current, main);

        BuildingFloorRegion freshRegion = BuildingFloorRegion.fromFootprint(64, Set.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        Structure refreshed = current.copy();
        assertTrue(refreshed.replaceFloorGeometry(0,
                new StructureFloor(0, 64, 68, 0, freshRegion)));

        Building added = room(101, 10, 0, true);
        BuildingFloorRegion addedRegion = BuildingFloorRegion.fromFootprint(64, Set.of(
                new BlockPos(2, 64, 0), new BlockPos(3, 64, 0)));
        added.setGeometry(new BlockPos(2, 64, 0), new BlockPos(3, 67, 0), addedRegion);

        assertTrue(village.replaceStructureAndRegisterRoom(refreshed, added));
        assertEquals(4, village.getStructure(10).orElseThrow().getFloor(0).orElseThrow().area());
        assertEquals(added, village.getBuilding(101).orElseThrow());
        assertTrue(village.getStructure(10).orElseThrow().containsPos(new BlockPos(3, 64, 0)));
    }

    private static Village populatedVillage() {
        Village village = new Village(1, null);
        village.registerStructure(structure(10, 10), room(1, 10, 0, true));
        village.registerRoom(room(2, 10, 0, true));
        village.registerRoom(room(3, 10, 0, false));
        village.refreshLogicalBuildings();
        return village;
    }

    private static Map<String, BuildingType> installSingleWorkshopType() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previous = types.getBuildingTypes();
        types.setBuildingTypes(Map.of("workshop", craftingTableType("workshop")));
        return previous;
    }

    private static Map<String, BuildingType> installAmbiguousWorkshopTypes() {
        BuildingTypes types = BuildingTypes.getInstance();
        Map<String, BuildingType> previous = types.getBuildingTypes();
        types.setBuildingTypes(Map.of(
                "music_store", craftingTableType("music_store"),
                "workshop", craftingTableType("workshop")));
        return previous;
    }

    private static BuildingType craftingTableType(String name) {
        JsonObject blocks = new JsonObject();
        blocks.addProperty("minecraft:crafting_table", 1);
        JsonObject type = new JsonObject();
        type.add("blocks", blocks);
        return new BuildingType(name, type);
    }

    private static Structure structure(int id, int logicalId, StructureFloor... floors) {
        List<StructureFloor> structureFloors = floors.length == 0
                ? List.of(floor(0, 64))
                : List.of(floors);
        Structure structure = new Structure(id, BlockPos.ZERO, structureFloors);
        structure.setLogicalBuildingId(logicalId);
        return structure;
    }

    private static StructureFloor floor(int id, int y) {
        return new StructureFloor(id, y, y + 4, region(y));
    }

    private static BuildingFloorRegion region(int y) {
        return BuildingFloorRegion.fromFootprint(y, Set.of(
                new BlockPos(0, y, 0), new BlockPos(1, y, 0),
                new BlockPos(0, y, 1), new BlockPos(1, y, 1)));
    }

    private static FloorGeometry scannedFloor(BuildingFloorRegion region) {
        Set<FloorGeometry.Cell> cells = region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(pos, pos.getY(), pos.getY() + 4))
                .collect(java.util.stream.Collectors.toSet());
        return new FloorGeometry(cells, Map.of());
    }

    private static Building room(int id, int structureId, int floorId, boolean contributes) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(structureId);
        room.setFloorId(floorId);
        room.setContributesToMain(contributes);
        return room;
    }
}
