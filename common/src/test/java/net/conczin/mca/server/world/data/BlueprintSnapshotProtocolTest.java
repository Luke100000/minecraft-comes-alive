package net.conczin.mca.server.world.data;

import io.netty.buffer.Unpooled;
import net.conczin.mca.network.s2c.GetVillageResponse;
import net.conczin.mca.resources.Rank;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintSnapshotProtocolTest {
    @Test
    void snapshotAndServerPlannerAgreeForIdenticalEvidence() {
        FloorGeometry ground = flatFloor(64, 0, 3);
        Structure structure = new Structure(10, new BlockPos(0, 64, 0),
                List.of(new StructureFloor(0, 0, ground)));
        Building room = new Building(structure.getSource());
        room.setId(42);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(3, 67, 0),
                ground.cells().stream().map(FloorGeometry.Cell::feet).collect(java.util.stream.Collectors.toSet()));
        Village server = new Village(1, null);
        server.registerStructure(structure, room);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GetVillageResponse.STREAM_CODEC.encode(buffer, new GetVillageResponse(
                    server.save(), Rank.PEASANT, 0, true, Set.of(), Map.of(), Map.of()));
            Village snapshot = new Village(GetVillageResponse.STREAM_CODEC.decode(buffer).getData(), null);
            var modes = new Village.RoomScanMode[]{Village.RoomScanMode.UPDATE_ROOM, Village.RoomScanMode.ADD_ROOM,
                    Village.RoomScanMode.ADD_BUILDING, Village.RoomScanMode.ADD_ATTACHMENT};
            var floors = new FloorGeometry[]{ground, flatFloor(64, 0, 7), flatFloor(64, 20, 23), flatFloor(70, 0, 3)};
            var sources = new BlockPos[]{new BlockPos(1, 64, 0), new BlockPos(6, 64, 0),
                    new BlockPos(21, 64, 0), new BlockPos(1, 70, 0)};
            for (int i = 0; i < modes.length; i++) {
                var authoritative = freshPlan(server, sources[i], floors[i], modes[i] == Village.RoomScanMode.ADD_ATTACHMENT);
                var preview = freshPlan(snapshot, sources[i], floors[i], modes[i] == Village.RoomScanMode.ADD_ATTACHMENT);
                assertEquals(modes[i], authoritative.mode());
                assertEquals(authoritative.mode(), preview.mode());
                assertEquals(authoritative.currentRoom().map(Building::getId), preview.currentRoom().map(Building::getId));
                assertEquals(authoritative.targetStructureId(), preview.targetStructureId());
                assertEquals(authoritative.targetFloorId(), preview.targetFloorId());
                assertEquals(authoritative.targetBuildingId(), preview.targetBuildingId());
                assertEquals(authoritative.prospectiveFloorNumber(), preview.prospectiveFloorNumber());
            }
        } finally {
            buffer.release();
        }
    }

    private static RoomScanPlan freshPlan(Village village, BlockPos source, FloorGeometry floor, boolean attachment) {
        Set<SelectedFloorScanner.Transition> transitions = floor.cells().stream()
                .filter(cell -> floor.cellAt(cell.feet().east()).isPresent())
                .map(cell -> new SelectedFloorScanner.Transition(cell.feet(), cell.feet().east()))
                .collect(java.util.stream.Collectors.toSet());
        var scan = new SelectedFloorScanner.Result(Building.validationResult.SUCCESS, floor, source, source,
                floor.anchorY(), source, source, transitions, Set.of(), Set.of());
        var structure = village.getStructure(10).orElseThrow();
        var contacts = attachment ? Set.of(new StructureConnector.VerticalConnection(
                structure, structure.getFloor(0).orElseThrow())) : Set.<StructureConnector.VerticalConnection>of();
        return RoomScanPlanner.analyzeFresh(village, source,
                new StructureScanner.FloorObservation(scan, contacts, Set.of())).plan();
    }

    private static FloorGeometry flatFloor(int y, int minX, int maxX) {
        return new FloorGeometry(java.util.stream.IntStream.rangeClosed(minX, maxX)
                .mapToObj(x -> new FloorGeometry.Cell(new BlockPos(x, y, 0), y + 4)).toList(), Map.of());
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void villageResponseRoundTripPreservesCheapRoomIdentityForBlueprintUi() {
        BlockPos source = new BlockPos(4, 64, -3);
        FloorGeometry floorGeometry = new FloorGeometry(
                List.of(new FloorGeometry.Cell(source, 68)), Map.of());
        StructureFloor floor = new StructureFloor(0, 0, floorGeometry);
        Structure structure = new Structure(10, source, List.of(floor));
        Building room = new Building(source);
        room.setId(42);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(source, source, Set.of(source));
        Village serverVillage = new Village(1, null);
        serverVillage.registerStructure(structure, room);

        GetVillageResponse response = new GetVillageResponse(
                serverVillage.save(), Rank.PEASANT, 0, true, Set.of(), Map.of(), Map.of());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GetVillageResponse.STREAM_CODEC.encode(buffer, response);
            GetVillageResponse decoded = GetVillageResponse.STREAM_CODEC.decode(buffer);
            Village clientSnapshot = new Village(decoded.getData(), null);

            RoomScanPlan plan = clientSnapshot.getRoomScanPlan(null, source);

            assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode());
            assertEquals(42, plan.currentRoom().orElseThrow().getId());
        } finally {
            buffer.release();
        }
    }
}
