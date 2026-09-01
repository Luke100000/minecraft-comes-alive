package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StructureFloorTest {
    @Test
    void connectorMarkersRoundTripAndRemainOptionalForOldSaves() {
        StructureFloor.ConnectorMarker marker = new StructureFloor.ConnectorMarker(
                new BlockPos(4, 64, 7), StructureFloor.ConnectorType.TRAPDOOR);
        StructureFloor floor = new StructureFloor(3, 64, 70, 0, null, List.of(marker));

        CompoundTag saved = floor.save();
        assertEquals(List.of(marker), StructureFloor.load(saved).connectors());

        saved.remove("connectors");
        assertEquals(List.of(), StructureFloor.load(saved).connectors());
    }
}
