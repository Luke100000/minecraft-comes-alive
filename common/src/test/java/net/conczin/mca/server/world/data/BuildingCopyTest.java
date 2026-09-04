package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
        Set<BlockPos> floorCells = Set.of(
                new BlockPos(3, 8, 11),
                new BlockPos(4, 8, 11),
                new BlockPos(3, 8, 12),
                new BlockPos(4, 11, 11));
        original.setGeometry(
                new BlockPos(3, 8, 11),
                new BlockPos(6, 13, 14), floorCells);
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
        assertEquals(floorCells, copy.getFloorCells());
        assertEquals(floorCells, new Building(original.save()).getFloorCells());
        assertEquals(List.of(8, 11), original.getFloorCells().stream()
                .filter(pos -> pos.getX() == 4 && pos.getZ() == 11)
                .map(BlockPos::getY).sorted().toList());
        assertEquals(1234L, copy.getLastScan());

        assertNotSame(original.getBlocks().get(bookshelf), copy.getBlocks().get(bookshelf));
        copy.getBlocks().get(bookshelf).add(new BlockPos(5, 9, 11));
        assertEquals(1, original.getBlocks().get(bookshelf).size());
        assertEquals(2, copy.getBlocks().get(bookshelf).size());
    }
}
