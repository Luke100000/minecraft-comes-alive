package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomScanPlan;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintScreenMapInteractionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetSelectedFloor() throws Exception {
        setSelectedFloorOrdinal(0);
    }

    @Test
    void freshGameSessionDefaultsBlueprintToGroundFloor() throws Exception {
        Field selectedFloor = BlueprintScreen.class.getDeclaredField("selectedFloorOrdinal");
        selectedFloor.setAccessible(true);

        assertEquals(0, selectedFloor.get(null));
    }

    @Test
    void viewportPreservesFractionalRequestedCenter() {
        BlueprintMapViewport viewport = BlueprintMapViewport.create(
                100, 120, 80, -305.25D, -1653.75D, 1.37F);

        assertEquals(-305.25D, viewport.mapCenterX(), 0.0000001D);
        assertEquals(-1653.75D, viewport.mapCenterZ(), 0.0000001D);
        assertEquals(100.3425D, viewport.screenX(-305.0D), 0.0001D);
    }

    @Test
    void zoomAroundPointerKeepsCursorWorldPositionInvariant() {
        BlueprintMapViewport before = BlueprintMapViewport.create(
                100, 120, 80, -305.25D, -1653.75D, 1.37F);
        double mouseX = 137.5D;
        double mouseY = 91.25D;
        double worldX = before.worldX(mouseX);
        double worldZ = before.worldZ(mouseY);

        BlueprintMapViewport after = before.zoomedAround(mouseX, mouseY, 2.36F);

        assertEquals(worldX, after.worldX(mouseX), 0.0000001D);
        assertEquals(worldZ, after.worldZ(mouseY), 0.0000001D);
    }

    @Test
    void customScaleSnapsToTheNextPresetInEitherDirection() {
        assertEquals(2.0F, BlueprintScreen.snapMapScale(1.37F, 1));
        assertEquals(3.0F, BlueprintScreen.snapMapScale(2.0F, 1));
        assertEquals(1.0F, BlueprintScreen.snapMapScale(1.37F, -1));
        assertEquals(0.5F, BlueprintScreen.snapMapScale(1.0F, -1));
    }

    @Test
    void customScaleImmediatelyBesidePresetStillSnapsToThatPreset() {
        assertEquals(2.0F, BlueprintScreen.snapMapScale(1.99995F, 1));
        assertEquals(2.0F, BlueprintScreen.snapMapScale(2.00005F, -1));
    }

    @Test
    void wheelZoomProducesCustomScaleAndClampsToPresetRange() {
        assertEquals(1.1F, BlueprintScreen.zoomMapScale(1.0F, 1.0D), 0.0001F);
        assertEquals(0.5F, BlueprintScreen.zoomMapScale(0.5F, -20.0D), 0.0001F);
        assertEquals(4.0F, BlueprintScreen.zoomMapScale(4.0F, 20.0D), 0.0001F);
    }

    @Test
    void numericScaleLabelUsesAtMostTwoDecimalsWithoutTrailingZeroes() {
        assertEquals("1.37:1", BlueprintScreen.formatMapScale(1.37F));
        assertEquals("2:1", BlueprintScreen.formatMapScale(2.0F));
        assertEquals("0.5:1", BlueprintScreen.formatMapScale(0.5F));
        assertEquals("2.36:1", BlueprintScreen.formatMapScale(2.356F));
    }

    @Test
    void pointerOnlyBecomesAPanAfterCrossingDragThreshold() {
        BlueprintScreen.MapPanState pan = new BlueprintScreen.MapPanState();

        pan.begin(10.0D, 10.0D);
        assertFalse(pan.update(12.0D, 10.0D));
        assertTrue(pan.update(13.0D, 10.0D));
        assertTrue(pan.end());
        assertFalse(pan.end());
    }

    @Test
    void clickWithoutDraggingDoesNotCountAsPan() {
        BlueprintScreen.MapPanState pan = new BlueprintScreen.MapPanState();

        pan.begin(10.0D, 10.0D);
        assertFalse(pan.update(11.0D, 11.0D));
        assertFalse(pan.end());
    }

    @Test
    void terrainTileOriginsStayAnchoredToWorldCoordinates() throws Exception {
        Method tileMin = BlueprintTerrainRenderer.class.getDeclaredMethod("tileMin", int.class);
        tileMin.setAccessible(true);

        assertEquals(0, tileMin.invoke(null, 0));
        assertEquals(0, tileMin.invoke(null, 127));
        assertEquals(128, tileMin.invoke(null, 128));
        assertEquals(-128, tileMin.invoke(null, -1));
        assertEquals(-128, tileMin.invoke(null, -128));
        assertEquals(-256, tileMin.invoke(null, -129));
    }

    @Test
    void terrainContourSpacingDoesNotChangeWhenCacheReliefChanges() throws Exception {
        Method contourInterval = BlueprintTerrainRenderer.class.getDeclaredMethod("contourInterval", int.class);
        contourInterval.setAccessible(true);

        assertEquals(contourInterval.invoke(null, 2), contourInterval.invoke(null, 30));
    }

    @Test
    void terrainUsesFullResolutionFromOneToFourTimesZoom() {
        assertEquals(4, BlueprintTerrainRenderer.sampleStep(0.5F));
        assertEquals(3, BlueprintTerrainRenderer.sampleStep(0.75F));
        assertEquals(3, BlueprintTerrainRenderer.sampleStep(0.99F));
        assertEquals(1, BlueprintTerrainRenderer.sampleStep(1.0F));
        assertEquals(1, BlueprintTerrainRenderer.sampleStep(2.0F));
        assertEquals(1, BlueprintTerrainRenderer.sampleStep(4.0F));
    }

    @Test
    void terrainTileCacheIdentityDoesNotChangeWithSamplingResolution() throws Exception {
        Class<?> tileKey = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TileKey");

        assertEquals(2, tileKey.getRecordComponents().length);
        assertEquals("minX", tileKey.getRecordComponents()[0].getName());
        assertEquals("minZ", tileKey.getRecordComponents()[1].getName());
    }

    @Test
    void terrainTileCanReuseCoarserCachedSampleForFinerResampling() throws Exception {
        Class<?> tileClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile");
        Class<?> cellClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile$Cell");

        var cellConstructor = cellClass.getDeclaredConstructor(int.class, int.class, int.class, int.class);
        cellConstructor.setAccessible(true);
        Object cachedCell = cellConstructor.newInstance(0, 0, 72, 0x486a3d);

        Object cells = Array.newInstance(cellClass, 3, 3);
        Array.set(Array.get(cells, 1), 1, cachedCell);

        var tileConstructor = tileClass.getDeclaredConstructor(
                int.class, int.class, int.class, long.class, boolean.class, cells.getClass());
        tileConstructor.setAccessible(true);
        Object coarseTile = tileConstructor.newInstance(0, 0, 2, 0L, true, cells);

        Method cellAtBlock = tileClass.getDeclaredMethod("cellAtBlock", int.class, int.class);
        cellAtBlock.setAccessible(true);

        assertEquals(cachedCell, cellAtBlock.invoke(coarseTile, 1, 1));
    }

    @Test
    void inheritanceControlStateTracksRefreshedVillageWithoutRebuildingPage() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(new StructureFloor(0, 64, 68, null)));
        Building main = room(1);
        registerStructure(village, structure, main);
        Building sideRoom = room(2);
        village.registerRoom(sideRoom);

        BlueprintScreen.InheritanceControlState before =
                BlueprintScreen.inheritanceControlState(village, sideRoom);
        assertEquals("gui.blueprint.roomInheritance.remove", before.labelKey());
        assertFalse(before.nextEnabled());

        assertTrue(village.setRoomContributesToMain(sideRoom, false));

        BlueprintScreen.InheritanceControlState after =
                BlueprintScreen.inheritanceControlState(village, sideRoom);
        assertEquals("gui.blueprint.roomInheritance.enable", after.labelKey());
        assertTrue(after.nextEnabled());
    }

    @Test
    void selectedFloorRemovalUsesLogicalBuildingOfRoomPlayerIsStandingIn() throws Exception {
        Village village = new Village(1, null);
        Structure current = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 64, 68, 0, null),
                        new StructureFloor(1, 72, 76, 1, null)));
        Building currentRoom = room(1);
        registerStructure(village, current, currentRoom);

        Structure other = new Structure(20, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(new StructureFloor(0, 64, 68, 0, null)));
        Building otherRoom = room(2);
        otherRoom.setStructureId(20);
        registerStructure(village, other, otherRoom);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(currentRoom), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        setSelectedFloorOrdinal(1);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertTrue(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
    }

    @Test
    void floorRemovalIsUnavailableWhenPlayerIsNotStandingInARoom() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 60, 64, -1, null),
                        new StructureFloor(1, 64, 68, 0, null)));
        Building main = room(1);
        main.setFloorId(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BASEMENT,
                10, -1, BlockPos.ZERO, BlockPos.ZERO, -1, -1);
        setSelectedFloorOrdinal(-1);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertFalse(state.visible());
    }

    @Test
    void selectedEmptyTerminalFloorUsesRemoveFloorActionFromCurrentRoom() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 64, 68, 0, null),
                        new StructureFloor(1, 72, 76, 1, null)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        setSelectedFloorOrdinal(1);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertTrue(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
        assertEquals("gui.blueprint.removeFloor", state.labelKey());
    }

    @Test
    void occupiedGroundFloorFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(new StructureFloor(0, 64, 68, 0, null)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        setSelectedFloorOrdinal(0);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyMiddleFloorFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 64, 68, 0, null),
                        new StructureFloor(1, 72, 76, 1, null),
                        new StructureFloor(2, 80, 84, 2, null)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        setSelectedFloorOrdinal(1);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyInnerBasementFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 56, 60, -2, null),
                        new StructureFloor(1, 60, 64, -1, null),
                        new StructureFloor(2, 64, 68, 0, null)));
        Building main = room(1);
        main.setFloorId(2);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 2);
        setSelectedFloorOrdinal(-1);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyLowestBasementUsesRemoveFloorAction() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, BlockPos.ZERO, BlockPos.ZERO,
                List.of(
                        new StructureFloor(0, 56, 60, -2, null),
                        new StructureFloor(1, 60, 64, -1, null),
                        new StructureFloor(2, 64, 68, 0, null)));
        Building main = room(1);
        main.setFloorId(2);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 2);
        setSelectedFloorOrdinal(-2);

        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan);

        assertTrue(state.visible());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
    }

    @Test
    void catalogRequirementProgressUsesSelectedTypeRequirementsAndCurrentRoomPoi() {
        JsonObject blocks = new JsonObject();
        blocks.addProperty("minecraft:note_block", 3);
        blocks.addProperty("minecraft:jukebox", 1);
        JsonObject definition = new JsonObject();
        definition.add("blocks", blocks);
        BuildingType musicStore = new BuildingType("music_store", definition);
        Building room = new Building(BlockPos.ZERO);
        room.addBlock(Blocks.NOTE_BLOCK, BlockPos.ZERO);

        Map<ResourceLocation, Integer> counts =
                BlueprintScreen.catalogRequirementCounts(musicStore, room.getBlocks());

        assertEquals(1, counts.get(ResourceLocation.parse("minecraft:note_block")));
        assertEquals(0, counts.getOrDefault(ResourceLocation.parse("minecraft:jukebox"), 0));
    }

    private static Building room(int id) {
        Building room = new Building(BlockPos.ZERO);
        room.setId(id);
        room.setStructureId(10);
        room.setFloorId(0);
        return room;
    }

    private static void setSelectedFloorOrdinal(Integer ordinal) throws Exception {
        Field selectedFloor = BlueprintScreen.class.getDeclaredField("selectedFloorOrdinal");
        selectedFloor.setAccessible(true);
        selectedFloor.set(null, ordinal);
    }

    private static void registerStructure(Village village, Structure structure, Building room) throws Exception {
        Method register = Village.class.getDeclaredMethod("registerStructure", Structure.class, Building.class);
        register.setAccessible(true);
        register.invoke(village, structure, room);
    }
}
