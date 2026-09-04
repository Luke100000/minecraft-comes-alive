package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructureFloorTest {
    @Test
    void connectorMarkersRoundTripAndRemainOptionalForOldSaves() {
        StructureFloor.ConnectorMarker marker = new StructureFloor.ConnectorMarker(
                new BlockPos(4, 64, 7), StructureFloor.ConnectorType.TRAPDOOR);
        StructureFloor floor = new StructureFloor(3, 64, 70, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(new BlockPos(4, 64, 7))), List.of(marker));

        CompoundTag saved = floor.save();
        assertEquals(List.of(marker), StructureFloor.load(saved).connectors());

        saved.remove("connectors");
        assertEquals(List.of(), StructureFloor.load(saved).connectors());
    }

    @Test
    void connectorMetadataDoesNotManufactureFloorRegionMembership() {
        BlockPos connector = new BlockPos(1, 64, 0);
        StructureFloor.ConnectorMarker marker = new StructureFloor.ConnectorMarker(
                connector, StructureFloor.ConnectorType.DOOR);
        StructureFloor legacy = new StructureFloor(3, 64, 70, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(
                        new BlockPos(0, 64, 0), new BlockPos(2, 64, 0))));
        CompoundTag saved = legacy.save();
        ListTag markers = new ListTag();
        markers.add(marker.save());
        saved.put("connectors", markers);

        StructureFloor loaded = StructureFloor.load(saved);

        assertFalse(loaded.contains(connector.getX(), connector.getZ()),
                "connector metadata must not manufacture persisted Floor geometry");
    }

    @Test
    void loadedFloorNumberIsDerivedRatherThanRestoredFromPersistence() {
        StructureFloor floor = new StructureFloor(3, 64, 70, 0,
                BuildingFloorRegion.fromFootprint(64, Set.of(new BlockPos(0, 64, 0))));
        CompoundTag saved = floor.save();
        saved.putInt("floorNumber", 7);

        assertEquals(0, StructureFloor.load(saved).floorNumber());
    }

    @Test
    void canonicalFloorRequiresPersistedRegion() {
        assertThrows(NullPointerException.class,
                () -> new StructureFloor(3, 64, 70, 0, null, List.of()));
    }

    @Test
    void canonicalFloorRequiresNonEmptyRegion() {
        BuildingFloorRegion empty = BuildingFloorRegion.fromFootprint(64, Set.of());

        assertThrows(IllegalArgumentException.class,
                () -> new StructureFloor(3, 64, 70, 0, empty, List.of()));
    }

    @Test
    void currentFloorLoadRejectsMissingRegion() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", 3);
        tag.putInt("anchorY", 64);
        tag.putInt("ceilingY", 70);

        assertThrows(IllegalArgumentException.class, () -> StructureFloor.load(tag));
    }

    @Test
    void attachmentGapUsesSemanticBandsWhenLegacyCeilingsOverlap() {
        BuildingFloorRegion lowerRegion = BuildingFloorRegion.fromFootprint(
                88, Set.of(new BlockPos(0, 88, 0)));
        BuildingFloorRegion upperRegion = BuildingFloorRegion.fromFootprint(
                91, Set.of(new BlockPos(0, 91, 0)));
        StructureFloor staleLower = new StructureFloor(0, 88, 93, 0, lowerRegion);
        StructureFloor upper = new StructureFloor(0, 91, 94, 1, upperRegion);
        StructureFloor sameBand = new StructureFloor(0, 90, 94, 0,
                BuildingFloorRegion.fromFootprint(90, Set.of(new BlockPos(0, 90, 0))));

        assertEquals(0, upper.attachmentGapTo(staleLower));
        assertEquals(-1, upper.attachmentGapTo(sameBand));
    }

    @Test
    void ceilingBoundaryGeometryRoundTripsAndRemainsOptionalForOldSaves() {
        BuildingFloorRegion footprint = BuildingFloorRegion.fromFootprint(
                88, Set.of(new BlockPos(0, 88, 0), new BlockPos(1, 88, 0)));
        BuildingFloorRegion boundary = BuildingFloorRegion.fromFootprint(
                91, Set.of(new BlockPos(1, 91, 0)));
        StructureFloor floor = new StructureFloor(0, 88, 91, 0, footprint, boundary, List.of());

        CompoundTag saved = floor.save();
        assertEquals(boundary, StructureFloor.load(saved).ceilingBoundaryRegion());

        saved.remove("ceilingBoundaryRegion");
        assertEquals(0, StructureFloor.load(saved).ceilingBoundaryRegion().area());
    }
}
