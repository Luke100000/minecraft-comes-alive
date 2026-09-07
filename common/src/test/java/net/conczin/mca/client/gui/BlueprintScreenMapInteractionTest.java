package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingFloorRegion;
import net.conczin.mca.server.world.data.RoomScanPlan;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintScreenMapInteractionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void freshGameSessionDefaultsBlueprintToGroundFloor() throws Exception {
        Field selectedFloor = BlueprintScreen.class.getDeclaredField("selectedFloorOrdinal");
        selectedFloor.setAccessible(true);
        BlueprintScreen screen = new BlueprintScreen();

        assertEquals(0, selectedFloor.get(screen));
    }

    @Test
    void selectedFloorAndPlayerCenteringAreLocalToEachBlueprintScreen() throws Exception {
        BlueprintScreen first = new BlueprintScreen();
        BlueprintScreen second = new BlueprintScreen();

        setField(first, "selectedFloorOrdinal", 2);
        setField(first, "playerCentered", true);

        assertEquals(2, getField(first, "selectedFloorOrdinal"));
        assertEquals(0, getField(second, "selectedFloorOrdinal"));
        assertEquals(true, getField(first, "playerCentered"));
        assertEquals(false, getField(second, "playerCentered"));
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
    void groupedIconHoverUsesLegacySixBlockRadiusAroundIconCenter() throws Exception {
        BuildingTypes buildingTypes = BuildingTypes.getInstance();
        Map<String, BuildingType> previous = buildingTypes.getBuildingTypes();
        JsonObject definition = new JsonObject();
        definition.addProperty("icon", true);
        definition.addProperty("grouped", true);
        buildingTypes.setBuildingTypes(Map.of("marker", new BuildingType("marker", definition)));
        try {
            Building building = new Building(new BlockPos(10, 64, 10));
            building.setId(42);
            building.setType("marker");
            building.setTypeForced(true);

            Method hovered = BlueprintMapRenderer.class.getDeclaredMethod(
                    "isGroupedBuildingHovered", Building.class, BlueprintMapFootprint.Cell.class);
            hovered.setAccessible(true);

            assertTrue((boolean) hovered.invoke(null, building, new BlueprintMapFootprint.Cell(15, 10)));
            assertFalse((boolean) hovered.invoke(null, building, new BlueprintMapFootprint.Cell(16, 10)));
        } finally {
            buildingTypes.setBuildingTypes(previous);
        }
    }

    @Test
    void fitCenterTracksSameVillageBoundsWhileCenterRemainsAutomatic() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        setField(screen, "page", "map");

        Village initial = villageWithStructures(1, List.of(new BlockPos(0, 64, 0)));
        screen.setVillage(initial);
        assertEquals(0.5D, getDoubleField(screen, "mapCenterX"), 0.0000001D);

        Village expanded = villageWithStructures(1, List.of(
                new BlockPos(0, 64, 0),
                new BlockPos(40, 64, 0)));
        screen.setVillage(expanded);

        assertEquals(20.5D, getDoubleField(screen, "mapCenterX"), 0.0000001D);
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
    void terrainBiomeTintMultipliesSurfaceColorWithoutChangingAlpha() {
        assertEquals(0xff207030,
                BlueprintTerrainRenderer.multiplyTint(0xff408060, 0x80e080));
    }

    @Test
    void underwaterBlockChangesFinalWaterPixel() {
        int water = 0x3f76e4;
        int sand = 0xffdbd3a0;
        int stone = 0xff7f7f7f;

        int sandPixel = BlueprintTerrainRenderer.waterColor(sand, water);
        int stonePixel = BlueprintTerrainRenderer.waterColor(stone, water);

        assertNotEquals(sandPixel, stonePixel,
                "identical water must still reveal which block is underneath");
        assertEquals(0xff000000, sandPixel & 0xff000000);
        assertEquals(0xff000000, stonePixel & 0xff000000);
    }

    @Test
    void waterColorUsesFixedSeabedVisibleBlend() {
        assertEquals(0xffcccccc,
                BlueprintTerrainRenderer.waterColor(0xff000000, 0xffffff));
    }

    @Test
    void waterLayerCompositesAfterTerrainStylingToAvoidHardContourStripes() {
        assertEquals(0xff426ec6,
                BlueprintTerrainRenderer.composeTerrainAndWater(
                        0xff808080, 0x3f76e4, 1.0F, true));
    }

    @Test
    void clientWaterFloorSkipsWaterOnlySectionsAndFindsSolidSeabed() {
        int[] reads = {0};
        IntFunction<BlockState> column = y -> {
            reads[0]++;
            return y > 40 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
        };
        IntPredicate sectionMayContainFloor = y -> Math.floorDiv(y, 16) <= 2;

        BlueprintTerrainRenderer.ColumnLayers layers = BlueprintTerrainRenderer.sampleClientOceanFloorColumn(
                column, sectionMayContainFloor, 64, -64);

        assertEquals(8, reads[0],
                "the water-only 48..63 section must be skipped without per-block reads");
        assertEquals(40, layers.terrainY());
        assertEquals(64, layers.waterY());
        assertEquals(Blocks.STONE.defaultBlockState(), layers.terrainState());
    }

    @Test
    void terrainTilePriorityUsesCameraPosition() {
        double cameraX = 220.0D;
        double cameraZ = 64.0D;

        assertTrue(BlueprintTerrainRenderer.tileDistanceSq(128, 0, cameraX, cameraZ)
                < BlueprintTerrainRenderer.tileDistanceSq(0, 0, cameraX, cameraZ));
    }

    @Test
    void wholeTileSchedulingOrdersVisibleBeforePrefetchAndHonorsLifecycleDeadlines() {
        long never = Long.MIN_VALUE;
        long now = 200L;

        assertEquals(0, BlueprintTerrainRenderer.terrainWorkPriority(
                0, false, false, never, never, now));
        assertEquals(1, BlueprintTerrainRenderer.terrainWorkPriority(
                0, true, false, 150L, 200L, now));
        assertEquals(2, BlueprintTerrainRenderer.terrainWorkPriority(
                0, true, true, -400L, never, now));
        assertEquals(3, BlueprintTerrainRenderer.terrainWorkPriority(
                1, false, false, never, never, now));
        assertEquals(4, BlueprintTerrainRenderer.terrainWorkPriority(
                1, true, false, 150L, 200L, now));
        assertEquals(4, BlueprintTerrainRenderer.terrainWorkPriority(
                1, true, true, -400L, never, now));

        assertEquals(Integer.MAX_VALUE, BlueprintTerrainRenderer.terrainWorkPriority(
                        0, true, false, 150L, 201L, now),
                "incomplete tiles must wait for their retry deadline");
        assertEquals(Integer.MAX_VALUE, BlueprintTerrainRenderer.terrainWorkPriority(
                        0, true, true, -399L, never, now),
                "a complete tile younger than 600 ticks must stay fresh");
    }

    @Test
    void wholeTileCompletenessCountsCoreCellsButNotHillshadeHalo() {
        assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(0, 64));
        assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(129, 64));
        assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(64, 0));
        assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(64, 129));
        assertTrue(BlueprintTerrainRenderer.isCoreSampleCell(1, 1));
        assertTrue(BlueprintTerrainRenderer.isCoreSampleCell(128, 128));
    }

    @Test
    void terrainPrefetchCoversExactlyOneTileRingOutsideViewport() {
        assertEquals(0, BlueprintTerrainRenderer.terrainSamplingBand(0, 0, 0, 127, 0, 127));
        assertEquals(1, BlueprintTerrainRenderer.terrainSamplingBand(-128, 0, 0, 127, 0, 127));
        assertEquals(1, BlueprintTerrainRenderer.terrainSamplingBand(128, 128, 0, 127, 0, 127));
        assertEquals(2, BlueprintTerrainRenderer.terrainSamplingBand(-256, 0, 0, 127, 0, 127));
        assertEquals(2, BlueprintTerrainRenderer.terrainSamplingBand(0, 256, 0, 127, 0, 127));
    }

    @Test
    void terrainDoesNotScheduleTileWhenNoneOfItsClientChunksAreLoaded() {
        int[] lookups = {0};

        assertFalse(BlueprintTerrainRenderer.tileTouchesLoadedChunk(0, 0, (chunkX, chunkZ) -> {
            lookups[0]++;
            return false;
        }));
        assertEquals(64, lookups[0]);
    }

    @Test
    void terrainTileBecomesEligibleWhenAnyClientChunkIsLoaded() {
        assertTrue(BlueprintTerrainRenderer.tileTouchesLoadedChunk(
                128, -128, (chunkX, chunkZ) -> chunkX == 10 && chunkZ == -6));
    }

    @Test
    void dryTerrainUsesVanillaNoLeavesHeightWhenAvailable() throws Exception {
        Method method;
        try {
            method = BlueprintTerrainRenderer.class.getDeclaredMethod(
                    "dryTerrainHeight", int.class, int.class, int.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("dry terrain must preserve the vanilla no-leaves heightmap contract", e);
        }
        method.setAccessible(true);

        assertEquals(78, method.invoke(null, 78, 96, -64));
        assertEquals(96, method.invoke(null, -64, 96, -64));
    }

    @Test
    void linearReliefShadeKeepsClassicMapBrightnessAndFullSlopeContrast() {
        assertEquals(0.8627451F,
                BlueprintTerrainRenderer.hillshadeBrightness(64, 64, 64, 64), 0.0001F);

        float litSlope = BlueprintTerrainRenderer.hillshadeBrightness(72, 56, 72, 56);
        float shadowSlope = BlueprintTerrainRenderer.hillshadeBrightness(56, 72, 56, 72);

        assertEquals(1.15F, litSlope, 0.0001F);
        assertEquals(0.58F, shadowSlope, 0.0001F);
    }

    @Test
    void terrainTileCacheIdentityIsOnlyWorldTilePosition() throws Exception {
        Class<?> tileKey = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TileKey");

        assertEquals(2, tileKey.getRecordComponents().length);
        assertEquals("minX", tileKey.getRecordComponents()[0].getName());
        assertEquals("minZ", tileKey.getRecordComponents()[1].getName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void reopeningBlueprintRendererKeepsWarmTerrainForNextScreen() throws Exception {
        Class<?> tileKeyClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TileKey");
        Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, int.class);
        tileKeyConstructor.setAccessible(true);
        Object tileKey = tileKeyConstructor.newInstance(0, 0);

        Class<?> terrainTileClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile");
        Constructor<?> terrainTileConstructor = terrainTileClass.getDeclaredConstructor(int.class, int.class);
        terrainTileConstructor.setAccessible(true);
        Object terrainTile = terrainTileConstructor.newInstance(0, 0);

        Field tilesField = BlueprintTerrainRenderer.class.getDeclaredField("tiles");
        tilesField.setAccessible(true);

        BlueprintTerrainRenderer firstScreenRenderer = new BlueprintTerrainRenderer();
        Map<Object, Object> firstTiles = (Map<Object, Object>) tilesField.get(firstScreenRenderer);
        firstTiles.put(tileKey, terrainTile);

        BlueprintTerrainRenderer reopenedScreenRenderer = new BlueprintTerrainRenderer();
        Map<Object, Object> reopenedTiles = (Map<Object, Object>) tilesField.get(reopenedScreenRenderer);
        assertTrue(reopenedTiles.containsKey(tileKey),
                "reopening Blueprint should reuse sampled terrain from the session cache");
        reopenedTiles.remove(tileKey);
    }

    @Test
    @SuppressWarnings("unchecked")
    void changingClientLevelInvalidatesWarmTerrainCache() throws Exception {
        Object firstLevel = new Object();
        Object secondLevel = new Object();
        BlueprintTerrainRenderer.onClientLevelChanged(firstLevel);

        Class<?> tileKeyClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TileKey");
        Constructor<?> tileKeyConstructor = tileKeyClass.getDeclaredConstructor(int.class, int.class);
        tileKeyConstructor.setAccessible(true);
        Object tileKey = tileKeyConstructor.newInstance(0, 0);

        Class<?> terrainTileClass = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile");
        Constructor<?> terrainTileConstructor = terrainTileClass.getDeclaredConstructor(int.class, int.class);
        terrainTileConstructor.setAccessible(true);
        Object terrainTile = terrainTileConstructor.newInstance(0, 0);

        Field tilesField = BlueprintTerrainRenderer.class.getDeclaredField("tiles");
        tilesField.setAccessible(true);
        Map<Object, Object> tiles = (Map<Object, Object>) tilesField.get(null);
        tiles.put(tileKey, terrainTile);

        BlueprintTerrainRenderer.onClientLevelChanged(firstLevel);
        assertTrue(tiles.containsKey(tileKey), "same level should keep the warm terrain cache");

        BlueprintTerrainRenderer.onClientLevelChanged(secondLevel);
        assertFalse(tiles.containsKey(tileKey), "changing level should invalidate warm terrain");
        BlueprintTerrainRenderer.onClientLevelChanged(null);
    }

    @Test
    void inheritanceControlStateTracksRefreshedVillageWithoutRebuildingPage() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor(0, 64, 68, 0)));
        Building main = room(1);
        registerStructure(village, structure, main);
        Building sideRoom = room(2);
        registerRoom(village, sideRoom);

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
        Structure current = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 64, 68, 0),
                        floor(1, 72, 76, 1)));
        Building currentRoom = room(1);
        registerStructure(village, current, currentRoom);

        Structure other = new Structure(20, BlockPos.ZERO, List.of(floor(0, 64, 68, 0)));
        Building otherRoom = room(2);
        otherRoom.setStructureId(20);
        registerStructure(village, other, otherRoom);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(currentRoom), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, 1);

        assertTrue(state.visible());
        assertTrue(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
    }

    @Test
    void floorRemovalIsUnavailableWhenPlayerIsNotStandingInARoom() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 60, 64, -1),
                        floor(1, 64, 68, 0)));
        Building main = room(1);
        main.setFloorId(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.empty(), Village.RoomScanMode.ADD_BASEMENT,
                10, -1, BlockPos.ZERO, BlockPos.ZERO, -1, -1);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, -1);

        assertFalse(state.visible());
    }

    @Test
    void selectedEmptyTerminalFloorUsesRemoveFloorActionFromCurrentRoom() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 64, 68, 0),
                        floor(1, 72, 76, 1)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, 1);

        assertTrue(state.visible());
        assertTrue(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
        assertEquals("gui.blueprint.removeFloor", state.labelKey());
    }

    @Test
    void occupiedGroundFloorFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor(0, 64, 68, 0)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, 0);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyMiddleFloorFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 64, 68, 0),
                        floor(1, 72, 76, 1),
                        floor(2, 80, 84, 2)));
        Building main = room(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 0);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, 1);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyInnerBasementFallsBackToMainRoomRemovalControl() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 56, 60, -2),
                        floor(1, 60, 64, -1),
                        floor(2, 64, 68, 0)));
        Building main = room(1);
        main.setFloorId(2);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 2);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, -1);

        assertTrue(state.visible());
        assertFalse(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_ROOM, state.action());
    }

    @Test
    void emptyLowestBasementUsesRemoveFloorAction() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 56, 60, -2),
                        floor(1, 60, 64, -1),
                        floor(2, 64, 68, 0)));
        Building main = room(1);
        main.setFloorId(2);
        registerStructure(village, structure, main);

        RoomScanPlan plan = new RoomScanPlan(Optional.of(main), Village.RoomScanMode.UPDATE_ROOM,
                -1, Integer.MIN_VALUE, BlockPos.ZERO, BlockPos.ZERO, 10, 2);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, -2);

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

    private static Village villageWithStructures(int villageId, List<BlockPos> cells) throws Exception {
        Village village = new Village(villageId, null);
        int structureId = 10;
        int roomId = 1;
        for (BlockPos cell : cells) {
            StructureFloor floor = new StructureFloor(0, cell.getY(), cell.getY() + 4, 0,
                    region(cell));
            Structure structure = new Structure(structureId, cell, List.of(floor));
            Building room = new Building(cell);
            room.setId(roomId);
            room.setStructureId(structureId);
            room.setFloorId(0);
            setRoomGeometry(room, cell);
            registerStructure(village, structure, room);
            structureId++;
            roomId++;
        }
        village.calculateDimensions();
        return village;
    }

    private static StructureFloor floor(int id, int anchorY, int ceilingY, int floorNumber) throws Exception {
        return new StructureFloor(id, anchorY, ceilingY, floorNumber,
                region(anchorY));
    }

    private static BuildingFloorRegion region(int anchorY) throws Exception {
        Method fromFootprint = BuildingFloorRegion.class.getDeclaredMethod(
                "fromFootprint", int.class, java.util.Collection.class);
        fromFootprint.setAccessible(true);
        return (BuildingFloorRegion) fromFootprint.invoke(null, anchorY,
                Set.of(new BlockPos(0, anchorY, 0)));
    }

    private static BuildingFloorRegion region(BlockPos cell) throws Exception {
        Method fromFootprint = BuildingFloorRegion.class.getDeclaredMethod(
                "fromFootprint", int.class, java.util.Collection.class);
        fromFootprint.setAccessible(true);
        return (BuildingFloorRegion) fromFootprint.invoke(null, cell.getY(), Set.of(cell));
    }

    private static void setRoomGeometry(Building room, BlockPos cell) throws Exception {
        Method setGeometry = Building.class.getDeclaredMethod(
                "setGeometry", BlockPos.class, BlockPos.class, java.util.Collection.class);
        setGeometry.setAccessible(true);
        setGeometry.invoke(room, cell, cell, Set.of(cell));
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static double getDoubleField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(target);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void registerStructure(Village village, Structure structure, Building room) throws Exception {
        ensureRoomOwnership(structure, room);
        Method register = Village.class.getDeclaredMethod("registerStructure", Structure.class, Building.class);
        register.setAccessible(true);
        register.invoke(village, structure, room);
    }

    private static void registerRoom(Village village, Building room) throws Exception {
        Structure structure = village.getStructure(room.getStructureId()).orElseThrow();
        ensureRoomOwnership(structure, room);
        village.registerRoom(room);
    }

    private static void ensureRoomOwnership(Structure structure, Building room) throws Exception {
        if (!room.getFloorCells().isEmpty()) return;
        StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
        BlockPos cell = floor.region().cells().iterator().next();
        setRoomGeometry(room, cell);
    }
}
