package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.FloorGeometry;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BlueprintScreenLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void closingBlueprintPreservesTerrainUntilTheClientLevelChanges() throws Exception {
        Field tilesField = BlueprintTerrainRenderer.class.getDeclaredField("tiles");
        tilesField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Object, Object> tiles = (Map<Object, Object>) tilesField.get(null);

        Class<?> tileKeyClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TileKey");
        Constructor<?> keyCtor = tileKeyClass.getDeclaredConstructor(int.class, int.class);
        keyCtor.setAccessible(true);

        Class<?> terrainTileClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile");
        Constructor<?> tileCtor = terrainTileClass.getDeclaredConstructor(int.class, int.class);
        tileCtor.setAccessible(true);

        Object world = new Object();
        BlueprintTerrainRenderer.onClientLevelChanged(world);
        try {
            Object key = keyCtor.newInstance(0, 0);
            Object terrain = tileCtor.newInstance(0, 0);
            tiles.put(key, terrain);

            new BlueprintScreen().removed();
            BlueprintTerrainRenderer.onClientLevelChanged(world);
            assertSame(terrain, tiles.get(key), "Reopening in the same world should reuse the sampled tile");

            BlueprintTerrainRenderer.onClientLevelChanged(new Object());
            assertTrue(tiles.isEmpty(), "Changing worlds must release the old terrain");
        } finally {
            BlueprintTerrainRenderer.onClientLevelChanged(null);
        }
    }

    @Test
    void villagersPageInitializesZeroSlashZeroAndCatalogSelectionKeepsClickedButtonInactive() throws Exception {
        JsonObject houseJson = new JsonObject();
        houseJson.addProperty("visible", true);
        JsonObject innJson = new JsonObject();
        innJson.addProperty("visible", true);
        BuildingTypes.getInstance().setBuildingTypes(Map.of(
                "house", new BuildingType("house", houseJson),
                "inn", new BuildingType("inn", innJson)));

        BlueprintScreen screen = new BlueprintScreen();
        screen.width = 640;
        screen.height = 480;
        setField(screen, "village", new Village(1, null));

        Method setPage = BlueprintScreen.class.getDeclaredMethod("setPage", String.class);
        setPage.setAccessible(true);

        setPage.invoke(screen, "villagers");
        Button pageButton = (Button) getField(screen, "buttonPage");
        assertEquals("0/0", pageButton.getMessage().getString());

        setPage.invoke(screen, "catalog");
        @SuppressWarnings("unchecked")
        List<Button> catalogButtons = (List<Button>) getField(screen, "catalogButtons");
        assertEquals(2, catalogButtons.size());

        Button first = catalogButtons.get(0);
        Button second = catalogButtons.get(1);
        first.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        assertFalse(first.active, "Clicked catalog button must stay inactive");
        assertTrue(second.active);

        second.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
        assertTrue(first.active);
        assertFalse(second.active, "Newly selected catalog button must become inactive");
    }

    @Test
    void clientMovementOutsideRegisteredRoomUsesO1PersistedLookupWithoutReadingClientLevelBlocks() throws Exception {
        BlockPos roomPos = new BlockPos(0, 64, 0);
        FloorGeometry geometry = new FloorGeometry(
                List.of(new FloorGeometry.Cell(roomPos, 68)), List.of());
        Structure structure = new Structure(10, roomPos, List.of(new StructureFloor(0, 0, 64, geometry)));
        Building room = new Building(roomPos);
        room.setId(11);
        room.setStructureId(10);
        room.setFloorId(0);
        room.setGeometry(roomPos, roomPos.above(3), List.of(roomPos));
        Village village = new Village(1, null);
        village.registerStructure(structure, room);

        BlueprintScreen screen = new BlueprintScreen();
        screen.width = 640;
        screen.height = 480;
        Minecraft client = allocate(Minecraft.class);
        client.player = allocate(LocalPlayer.class);
        client.level = allocate(ThrowingClientLevel.class);

        Field mcField = Screen.class.getDeclaredField("minecraft");
        mcField.setAccessible(true);
        mcField.set(screen, client);

        Field posField = Entity.class.getDeclaredField("blockPosition");
        posField.setAccessible(true);
        posField.set(client.player, roomPos);

        screen.setVillage(village);
        assertEquals("gui.blueprint.updateRoom",
                ((Button) getField(screen, "structureScanButton")).getMessage().getString());

        // Move 1 block outside the registered room: must NOT touch client.level.getBlockState!
        posField.set(client.player, new BlockPos(1, 64, 0));
        assertDoesNotThrow(screen::tick);
        assertEquals("gui.blueprint.addStructure",
                ((Button) getField(screen, "structureScanButton")).getMessage().getString());
    }

    @Test
    void blueprintPreviewsBasementFromOpeningLadderAndSendsTheAttachmentAction() throws Exception {
        BlockPos ladder = new BlockPos(0, 61, 1);
        BlockPos groundSeed = new BlockPos(1, 65, 1);
        List<FloorGeometry.Cell> cells = new ArrayList<>();
        for (int x = 0; x < 4; x++) {
            for (int z = 0; z < 4; z++) {
                if (x != 0 || z != 1) cells.add(new FloorGeometry.Cell(new BlockPos(x, 65, z), 68));
            }
        }
        Structure structure = new Structure(43, groundSeed,
                List.of(new StructureFloor(0, 0, 65, new FloorGeometry(cells, List.of()))));
        Building room = new Building(groundSeed);
        room.setId(44);
        room.setStructureId(43);
        room.setFloorId(0);
        room.setGeometry(new BlockPos(0, 65, 0), new BlockPos(3, 67, 3),
                cells.stream().map(FloorGeometry.Cell::feet).toList());
        Village village = new Village(1, null);
        village.registerStructure(structure, room);
        assertEquals(Village.ClientPositionTarget.NONE, village.resolveClientTarget(ladder));

        BlueprintScreen screen = new BlueprintScreen();
        screen.width = 640;
        screen.height = 480;
        Minecraft client = allocate(Minecraft.class);
        client.player = allocate(LocalPlayer.class);
        BasementClientLevel level = allocate(BasementClientLevel.class);
        client.level = level;
        Field mcField = Screen.class.getDeclaredField("minecraft");
        mcField.setAccessible(true);
        mcField.set(screen, client);
        Field posField = Entity.class.getDeclaredField("blockPosition");
        posField.setAccessible(true);
        posField.set(client.player, ladder);

        Field senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> requests = new ArrayList<>();
        Network.registerClientSender(requests::add);
        try {
            screen.init();
            screen.setVillage(village);
            assertEquals(1, requests.size(), "The preview must use the existing village request");
            Button scan = (Button) getField(screen, "structureScanButton");
            assertEquals(Component.translatable("gui.blueprint.addBasement"), scan.getMessage());

            level.scanFinished = true;
            screen.init();
            scan = (Button) getField(screen, "structureScanButton");
            assertEquals(Component.translatable("gui.blueprint.addBasement"), scan.getMessage());
            scan.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            assertEquals(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_ATTACHMENT, null, 43),
                    requests.getLast());

            posField.set(client.player, new BlockPos(1, 61, 1));
            screen.tick();
            assertEquals(Component.translatable("gui.blueprint.addStructure"), scan.getMessage());
            scan.onPress(new KeyEvent(InputConstants.KEY_RETURN, 0, 0));
            assertEquals(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_BUILDING), requests.getLast());

            posField.set(client.player, ladder);
            screen.setVillage(village);
            assertEquals(Component.translatable("gui.blueprint.addStructure"), scan.getMessage(),
                    "An edit response invalidates the old preview without rescanning");
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    private static final class BasementClientLevel extends ClientLevel {
        private boolean scanFinished;

        private BasementClientLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 63);
        }

        @Override
        public boolean hasChunk(int x, int z) {
            return true;
        }

        @Override
        public int getMinY() {
            return -64;
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            assertFalse(scanFinished, "Blueprint must only scan once per opening");
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            if (x >= -1 && x <= 4 && z >= -1 && z <= 4 && y >= 60 && y <= 68) {
                if (x == 0 && z == 1 && y >= 61 && y <= 64) {
                    return Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
                }
                if (x == -1 || x == 4 || z == -1 || z == 4 || y == 60 || y == 64 || y == 68) {
                    return Blocks.STONE.defaultBlockState();
                }
            }
            return Blocks.AIR.defaultBlockState();
        }
    }

    private static final class ThrowingClientLevel extends ClientLevel {
        private ThrowingClientLevel() {
            super(null, null, null, null, 0, 0, null, false, 0L, 63);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            throw new AssertionError("BlueprintScreen must not scan ClientLevel blocks on player movement: " + pos);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        return (T) unsafe.allocateInstance(type);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = BlueprintScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = BlueprintScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
