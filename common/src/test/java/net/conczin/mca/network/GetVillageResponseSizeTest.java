package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.network.s2c.GetVillageResponse;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.server.world.data.*;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentInitializers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class GetVillageResponseSizeTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Item prototypes are bound during resource loading, not registry bootstrap.
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(
                        VanillaRegistries.createReloadableLookup(VanillaRegistries.createWorldLookup()))
                .forEach(DataComponentInitializers.PendingComponents::apply);
    }

    @Test
    void villageWith30000FloorCellsEncodesAndDecodesUnder2MbNbtAccounter() {
        Village village = new Village(1, null);
        for (int roomIndex = 0; roomIndex < 30; roomIndex++) {
            int baseX = roomIndex * 40;
            List<FloorGeometry.Cell> cells = new ArrayList<>(1000);
            List<BlockPos> feet = new ArrayList<>(1000);
            for (int dx = 0; dx < 25; dx++) {
                for (int dz = 0; dz < 40; dz++) {
                    BlockPos pos = new BlockPos(baseX + dx, 64, dz);
                    cells.add(new FloorGeometry.Cell(pos, 68));
                    feet.add(pos);
                }
            }
            FloorGeometry geometry = new FloorGeometry(cells, List.of());
            StructureFloor floor = new StructureFloor(0, 0, 64, geometry);
            int structureId = 100 + roomIndex * 2;
            Structure structure = new Structure(structureId, feet.getFirst(), List.of(floor));

            Building room = new Building(feet.getFirst());
            room.setId(structureId + 1);
            room.setStructureId(structureId);
            room.setFloorId(0);
            room.setType("house");
            room.setGeometry(new BlockPos(baseX, 64, 0), new BlockPos(baseX + 24, 67, 39), feet);
            village.registerStructure(structure, room);
        }

        GetVillageResponse response = new GetVillageResponse(
                village.saveForClient(), Rank.PEASANT, 10, true, Set.of(), Map.of(), Map.of());
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            GetVillageResponse.STREAM_CODEC.encode(buf, response);
            int readableBytes = buf.readableBytes();
            assertTrue(readableBytes < 2_097_152,
                    "Expected packet well under 2 MB NbtAccounter limit, got " + readableBytes + " bytes");

            // ByteBufCodecs.COMPOUND_TAG uses NbtAccounter.create(2_097_152L) on decode.
            GetVillageResponse decoded = GetVillageResponse.STREAM_CODEC.decode(buf);
            assertFalse(decoded.getData().contains("reputation"),
                    "Client village payload must not leak per-player reputation maps");

            Village clientVillage = new Village(decoded.getData(), null);
            assertEquals(30, clientVillage.getBuildings().size());
            int totalCells = clientVillage.getBuildings().values().stream()
                    .mapToInt(r -> r.getFloorCells().size())
                    .sum();
            assertEquals(30_000, totalCells);
            assertTrue(clientVillage.findPhysicalRoomAt(new BlockPos(29 * 40 + 24, 64, 39)).isPresent());
        } finally {
            buf.release();
        }
    }

    @Test
    void legacyTagListFloorAndRoomSavesLoadIdentically() {
        List<BlockPos> positions = List.of(
                new BlockPos(-12, 64, 5),
                new BlockPos(-11, 64, 5),
                new BlockPos(-12, 65, 6),
                new BlockPos(-11, 65, 6));
        List<FloorGeometry.Cell> cells = positions.stream()
                .map(pos -> new FloorGeometry.Cell(pos, pos.getY() + 4))
                .toList();
        FloorGeometry geometry = new FloorGeometry(cells, List.of());
        StructureFloor modernFloor = new StructureFloor(0, 0, 64, geometry);
        Structure modernStructure = new Structure(10, positions.getFirst(), List.of(modernFloor));
        Building modernRoom = new Building(positions.getFirst());
        modernRoom.setId(11);
        modernRoom.setStructureId(10);
        modernRoom.setFloorId(0);
        modernRoom.setGeometry(new BlockPos(-12, 64, 5), new BlockPos(-11, 68, 6), positions);

        Village modernVillage = new Village(1, null);
        modernVillage.registerStructure(modernStructure, modernRoom);
        CompoundTag legacyTag = modernVillage.save();

        // Replace packed arrays with legacy TAG_LIST payloads.
        CompoundTag floorTag = legacyTag.getList("structures").orElseThrow()
                .getCompound(0).orElseThrow()
                .getList("floors").orElseThrow()
                .getCompound(0).orElseThrow();
        floorTag.remove("cellPositions");
        floorTag.remove("ceilingYs");
        floorTag.put("cells", NbtHelper.fromList(cells, cell -> {
            CompoundTag cellTag = new CompoundTag();
            cellTag.put("pos", NbtHelper.encodeBlockPos(cell.feet()));
            cellTag.putInt("ceilingY", cell.ceilingY());
            return cellTag;
        }));

        CompoundTag roomTag = legacyTag.getList("buildings").orElseThrow().getCompound(0).orElseThrow();
        roomTag.remove("floorCellPositions");
        roomTag.put("floorCells", NbtHelper.fromList(positions, NbtHelper::encodeBlockPos));

        Village loadedFromLegacy = new Village(legacyTag, null);
        Village loadedFromModern = new Village(modernVillage.save(), null);

        assertEquals(loadedFromModern.getBuilding(11).orElseThrow().getFloorCells(),
                loadedFromLegacy.getBuilding(11).orElseThrow().getFloorCells());
        assertEquals(loadedFromModern.getStructure(10).orElseThrow().getFloor(0).orElseThrow().geometry().cells(),
                loadedFromLegacy.getStructure(10).orElseThrow().getFloor(0).orElseThrow().geometry().cells());
    }

    @Test
    void addingAttachedUpperFloorAppendsFloorToExistingStructure() {
        List<BlockPos> groundPos = List.of(
                new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
                new BlockPos(0, 64, 1), new BlockPos(1, 64, 1));
        FloorGeometry groundGeo = new FloorGeometry(groundPos.stream()
                .map(p -> new FloorGeometry.Cell(p, 68)).toList(), List.of());
        Structure structure = new Structure(10, groundPos.getFirst(),
                List.of(new StructureFloor(0, 0, 64, groundGeo)));
        Building groundRoom = new Building(groundPos.getFirst());
        groundRoom.setId(11);
        groundRoom.setStructureId(10);
        groundRoom.setFloorId(0);
        groundRoom.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 67, 1), groundPos);

        Village village = new Village(1, null);
        village.registerStructure(structure, groundRoom);

        List<BlockPos> upperPos = List.of(
                new BlockPos(0, 69, 0), new BlockPos(1, 69, 0),
                new BlockPos(0, 69, 1), new BlockPos(1, 69, 1));
        FloorGeometry upperGeo = new FloorGeometry(upperPos.stream()
                .map(p -> new FloorGeometry.Cell(p, 73)).toList(), List.of());
        Building upperRoom = new Building(upperPos.getFirst());
        upperRoom.setId(12);
        upperRoom.setGeometry(new BlockPos(0, 69, 0), new BlockPos(1, 72, 1), upperPos);

        assertTrue(village.attachFloorAndRegisterRoom(10, 1, 69, upperGeo, Map.of(), upperRoom));
        assertEquals(1, village.getStructures().size(),
                "Attached upper floor must append onto the existing Structure");
        Structure consolidated = village.getStructure(10).orElseThrow();
        assertEquals(2, consolidated.getFloors().size());
        assertEquals(10, upperRoom.getStructureId());
        assertEquals(1, upperRoom.getFloorId());
        assertEquals(1, consolidated.getFloor(1).orElseThrow().floorNumber());
    }

    @Test
    void villageMergePreservesResidentsReputationAndTaxesAndCorruptRoomIsIsolated() {
        UUID playerUuid = UUID.randomUUID();
        UUID residentA = UUID.randomUUID();
        UUID residentB = UUID.randomUUID();
        BlockPos homeA = new BlockPos(2, 64, 2);
        BlockPos homeB = new BlockPos(20, 64, 20);

        CompoundTag tagA = singleHouseVillageTag(1, 10, 11, new BlockPos(0, 64, 0), residentA, "Alice", homeA, playerUuid, 25);
        CompoundTag tagB = singleHouseVillageTag(2, 20, 21, new BlockPos(20, 64, 20), residentB, "Bob", homeB, playerUuid, 40);

        Village first = new Village(tagA, null);
        Village second = new Village(tagB, null);
        second.storageBuffer.add(new ItemStack(Items.EMERALD, 7));

        first.merge(second);

        assertEquals("Alice", first.getResidentNames().get(residentA));
        assertEquals("Bob", first.getResidentNames().get(residentB));
        assertTrue(first.ownsResidentHome(residentA, homeA));
        assertTrue(first.ownsResidentHome(residentB, homeB));
        assertEquals(1, first.storageBuffer.size());
        assertEquals(7, first.storageBuffer.getFirst().getCount());
        assertTrue(second.storageBuffer.isEmpty());

        CompoundTag mergedSaved = first.save();
        assertEquals(2, mergedSaved.getCompound("reputation").orElseThrow().getCompound(playerUuid.toString()).orElseThrow().size());

        // Inject one corrupt empty-cell room alongside valid rooms; healthy rooms must survive!
        ListTag buildingsList = mergedSaved.getList("buildings").orElseThrow();
        CompoundTag corruptRoom = buildingsList.getCompound(0).orElseThrow().copy();
        corruptRoom.putInt("id", 999);
        corruptRoom.putLongArray("floorCellPositions", new long[0]);
        buildingsList.add(corruptRoom);

        Village reloaded = assertDoesNotThrow(() -> new Village(mergedSaved, null));
        assertFalse(reloaded.getBuildings().containsKey(999));
        assertTrue(reloaded.getBuildings().containsKey(11));
        assertTrue(reloaded.getBuildings().containsKey(21));
    }

    private static CompoundTag singleHouseVillageTag(int villageId,
                                                     int structureId,
                                                     int roomId,
                                                     BlockPos origin,
                                                     UUID residentId,
                                                     String residentName,
                                                     BlockPos homePos,
                                                     UUID playerId,
                                                     int rep) {
        List<BlockPos> cells = List.of(
                origin, origin.east(), origin.south(), origin.east().south());
        FloorGeometry geometry = new FloorGeometry(cells.stream()
                .map(p -> new FloorGeometry.Cell(p, p.getY() + 4)).toList(), List.of());
        Structure structure = new Structure(structureId, origin, List.of(new StructureFloor(0, 0, origin.getY(), geometry)));
        Building room = new Building(origin);
        room.setId(roomId);
        room.setStructureId(structureId);
        room.setFloorId(0);
        room.setGeometry(origin, origin.offset(1, 3, 1), cells);

        Village village = new Village(villageId, null);
        village.registerStructure(structure, room);
        CompoundTag tag = village.save();

        CompoundTag names = new CompoundTag();
        names.put(residentId.toString(), StringTag.valueOf(residentName));
        tag.put("residentNames", names);

        CompoundTag homes = new CompoundTag();
        homes.put(residentId.toString(), LongTag.valueOf(homePos.asLong()));
        tag.put("residentHomes", homes);

        CompoundTag playerRep = new CompoundTag();
        playerRep.put(residentId.toString(), IntTag.valueOf(rep));
        CompoundTag reputation = new CompoundTag();
        reputation.put(playerId.toString(), playerRep);
        tag.put("reputation", reputation);
        return tag;
    }
}
