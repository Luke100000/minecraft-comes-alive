package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class BuildingCopyTest {
    @Test
    void runtimeCopyPreservesStateAndOwnsMutablePoiCollections() throws Exception {
        Building original = new Building(new BlockPos(4, 8, 12));
        original.setId(7);
        original.setStructureId(3);
        original.setFloorId(2);
        original.setType("library");
        original.setTypeForced(true);
        original.setContributesToMain(false);
        original.setLastScan(1234L);
        original.setGeometry(
                new BlockPos(3, 8, 11),
                new BlockPos(6, 10, 14),
                BuildingFloorRegion.fromFootprint(8, List.of(
                        new BlockPos(3, 8, 11),
                        new BlockPos(4, 8, 11),
                        new BlockPos(3, 8, 12),
                        new BlockPos(4, 8, 12))));
        ResourceLocation bookshelf = ResourceLocation.parse("minecraft:bookshelf");
        original.blocks.put(bookshelf, new ArrayList<>(List.of(new BlockPos(3, 9, 11))));

        Building copy = original.copy();

        assertEquals(original.getId(), copy.getId());
        assertEquals(original.getStructureId(), copy.getStructureId());
        assertEquals(original.getFloorId(), copy.getFloorId());
        assertEquals(original.getType(), copy.getType());
        assertEquals(original.isTypeForced(), copy.isTypeForced());
        assertEquals(original.contributesToMain(), copy.contributesToMain());
        assertEquals(original.getSourceBlock(), copy.getSourceBlock());
        assertEquals(original.getRawPos0(), copy.getRawPos0());
        assertEquals(original.getRawPos1(), copy.getRawPos1());
        assertEquals(original.getFloorRegion(), copy.getFloorRegion());
        assertEquals(1234L, copy.getLastScan());

        assertNotSame(original.getBlocks().get(bookshelf), copy.getBlocks().get(bookshelf));
        copy.getBlocks().get(bookshelf).add(new BlockPos(5, 9, 11));
        assertEquals(1, original.getBlocks().get(bookshelf).size());
        assertEquals(2, copy.getBlocks().get(bookshelf).size());
    }
}
