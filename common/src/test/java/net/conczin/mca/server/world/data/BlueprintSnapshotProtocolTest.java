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
