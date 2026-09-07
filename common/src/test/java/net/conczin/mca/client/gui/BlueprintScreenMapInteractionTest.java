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
import java.util.function.LongSupplier;

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
    void terrainUsesOneWorldSamplePerBlock() {
        assertEquals(1, BlueprintTerrainRenderer.sampleStep());
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
    void terrainSamplingBudgetCanContinueWithinFrame() throws Exception {
        long[] now = {1_000L};
        BlueprintTerrainRenderer.TileSamplingBudget budget = samplingBudget(() -> now[0], 100L);

        assertTrue(budget.tryAcquire());
        now[0] = 1_050L;
        assertTrue(budget.tryAcquire(),
                "sampling should continue while the small per-frame time budget remains");
    }

    @Test
    void terrainSamplingBudgetAlwaysAllowsFirstSliceThenStopsAtDeadline() throws Exception {
        long[] now = {1_000L};
        BlueprintTerrainRenderer.TileSamplingBudget budget = samplingBudget(() -> now[0], 100L);
        now[0] = 1_200L;

        assertTrue(budget.tryAcquire(), "the nearest slice must never miss the first frame");
        assertFalse(budget.tryAcquire(), "additional slices must stop once the deadline is exhausted");
    }

    @Test
    void partialTerrainBecomesRenderableAfterFirstSampledSlice() {
        assertFalse(BlueprintTerrainRenderer.hasSampledTerrain(0));
        assertTrue(BlueprintTerrainRenderer.hasSampledTerrain(1),
                "the camera-nearest slice should be drawable immediately");
    }

    @Test
    void partialTerrainTextureRefreshIsThrottledBetweenFirstSliceAndCompletion() {
        assertTrue(BlueprintTerrainRenderer.shouldRefreshTexture(1));
        assertFalse(BlueprintTerrainRenderer.shouldRefreshTexture(2));
        assertFalse(BlueprintTerrainRenderer.shouldRefreshTexture(3));
        assertTrue(BlueprintTerrainRenderer.shouldRefreshTexture(4));
        assertTrue(BlueprintTerrainRenderer.shouldRefreshTexture(64));
    }

    @Test
    void terrainSamplingUsesSixteenBlockSlices() {
        BlueprintTerrainRenderer.SliceBounds first = BlueprintTerrainRenderer.sampleSliceBounds(0);
        BlueprintTerrainRenderer.SliceBounds last = BlueprintTerrainRenderer.sampleSliceBounds(63);

        assertEquals(0, first.minLocalX());
        assertEquals(16, first.maxLocalX());
        assertEquals(0, first.minLocalZ());
        assertEquals(16, first.maxLocalZ());
        assertEquals(112, last.minLocalX());
        assertEquals(128, last.maxLocalX());
        assertEquals(112, last.minLocalZ());
        assertEquals(128, last.maxLocalZ());
    }

    @Test
    void terrainSamplingPrioritizesSliceNearestCameraPosition() {
        long[] sampledAt = new long[64];
        long[] retryAfter = new long[64];
        java.util.Arrays.fill(sampledAt, Long.MIN_VALUE);
        java.util.Arrays.fill(retryAfter, Long.MIN_VALUE);
        BlueprintTerrainRenderer.LoadedChunkLookup allLoaded = (chunkX, chunkZ) -> true;

        assertEquals(0, BlueprintTerrainRenderer.nearestReadySlice(
                0, 0, sampledAt, retryAfter, 0L, 4.0D, 4.0D, allLoaded));
        assertEquals(63, BlueprintTerrainRenderer.nearestReadySlice(
                0, 0, sampledAt, retryAfter, 0L, 124.0D, 124.0D, allLoaded));

        sampledAt[63] = 0L;
        assertNotEquals(63,
                BlueprintTerrainRenderer.nearestReadySlice(
                        0, 0, sampledAt, retryAfter, 0L, 124.0D, 124.0D, allLoaded));
    }

    @Test
    void nearestTerrainSliceStopsAfterFirstLoadedCandidate() {
        long[] sampledAt = new long[64];
        long[] retryAfter = new long[64];
        java.util.Arrays.fill(sampledAt, Long.MIN_VALUE);
        java.util.Arrays.fill(retryAfter, Long.MIN_VALUE);
        int[] lookups = {0};

        assertEquals(0, BlueprintTerrainRenderer.nearestReadySlice(
                0, 0, sampledAt, retryAfter, 0L, 4.0D, 4.0D,
                (chunkX, chunkZ) -> {
                    lookups[0]++;
                    return true;
                }));
        assertEquals(1, lookups[0], "nearest loaded slice should not probe the other 63 chunks");
    }

    @Test
    void terminalPartialTerrainSampleFlushesDirtyTexture() {
        assertTrue(BlueprintTerrainRenderer.shouldRefreshTexture(5, false));
        assertFalse(BlueprintTerrainRenderer.shouldRefreshTexture(5, true));
        assertTrue(BlueprintTerrainRenderer.shouldRefreshTexture(4, true));
    }

    @Test
    void terrainTilePriorityUsesCameraPosition() {
        double cameraX = 220.0D;
        double cameraZ = 64.0D;

        assertTrue(BlueprintTerrainRenderer.tileDistanceSq(128, 0, cameraX, cameraZ)
                < BlueprintTerrainRenderer.tileDistanceSq(0, 0, cameraX, cameraZ));
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
    void nearestPendingTerrainSliceSkipsItsOwnUnloadedChunk() {
        long[] sampledAt = new long[64];
        long[] retryAfter = new long[64];
        java.util.Arrays.fill(sampledAt, Long.MIN_VALUE);
        java.util.Arrays.fill(retryAfter, Long.MIN_VALUE);

        assertEquals(1, BlueprintTerrainRenderer.nearestReadySlice(
                0, 0, sampledAt, retryAfter, 0L, 4.0D, 4.0D,
                (chunkX, chunkZ) -> chunkX == 1 && chunkZ == 0));
    }

    @Test
    void failedTerrainSliceStaysPendingWithoutResettingSuccessfulSlices() {
        long[] sampledAt = new long[64];
        long[] retryAfter = new long[64];
        java.util.Arrays.fill(sampledAt, Long.MIN_VALUE);
        java.util.Arrays.fill(retryAfter, Long.MIN_VALUE);
        sampledAt[5] = 10L;

        BlueprintTerrainRenderer.recordSliceSampleResult(sampledAt, retryAfter, 0, 20L, false);

        assertEquals(Long.MIN_VALUE, sampledAt[0], "failed slice must remain pending");
        assertEquals(10L, sampledAt[5], "successful slices must not be reset by another slice failure");
        assertEquals(40L, retryAfter[0], "failed slice should use the existing 20-tick retry interval");
    }

    @Test
    void completedTerrainSliceBecomesStaleAfterFixedRefreshInterval() {
        assertFalse(BlueprintTerrainRenderer.sliceNeedsSampling(10L, Long.MIN_VALUE, 109L));
        assertTrue(BlueprintTerrainRenderer.sliceNeedsSampling(10L, Long.MIN_VALUE, 110L),
                "a completed slice should refresh after 100 game ticks");
    }

    @Test
    void dirtyTextureBoundsExpandOnlyOnePixelAndClipToTile() {
        BlueprintTerrainRenderer.SliceBounds first = BlueprintTerrainRenderer.dirtyTextureBounds(0);
        assertEquals(0, first.minLocalX());
        assertEquals(17, first.maxLocalX());
        assertEquals(0, first.minLocalZ());
        assertEquals(17, first.maxLocalZ());

        BlueprintTerrainRenderer.SliceBounds last = BlueprintTerrainRenderer.dirtyTextureBounds(63);
        assertEquals(111, last.minLocalX());
        assertEquals(128, last.maxLocalX());
        assertEquals(111, last.minLocalZ());
        assertEquals(128, last.maxLocalZ());
    }

    @Test
    void dryTerrainHeightSkipsLeavesBelowMotionBlockingUpperBound() {
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        IntFunction<BlockState> stateAtY = y -> y >= 78 ? leaves : y == 77 ? stone : Blocks.AIR.defaultBlockState();

        assertEquals(78, BlueprintTerrainRenderer.sampleClientDryTerrainHeight(
                stateAtY, y -> true, state -> state.is(Blocks.OAK_LEAVES), 80, 80, -64));
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

    private static BlueprintTerrainRenderer.TileSamplingBudget samplingBudget(
            LongSupplier nanoTime,
            long budgetNanos) throws Exception {
        Constructor<BlueprintTerrainRenderer.TileSamplingBudget> constructor;
        try {
            constructor = BlueprintTerrainRenderer.TileSamplingBudget.class
                    .getDeclaredConstructor(LongSupplier.class, long.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("terrain sampling needs a clock-backed time budget", e);
        }
        constructor.setAccessible(true);
        return constructor.newInstance(nanoTime, budgetNanos);
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
