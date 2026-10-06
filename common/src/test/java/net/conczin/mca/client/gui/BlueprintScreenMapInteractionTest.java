package net.conczin.mca.client.gui;

import com.google.gson.JsonObject;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.TestFloorFootprint;
import net.conczin.mca.server.world.data.RoomScanPlan;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintScreenMapInteractionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    void resetBlueprintSessionState() {
        BlueprintScreen.onClientLevelChanged(new Object());
    }

    @Test
    void freshGameSessionDefaultsBlueprintToGroundFloor() throws Exception {
        Field selectedFloor = BlueprintScreen.class.getDeclaredField("selectedFloorOrdinal");
        selectedFloor.setAccessible(true);
        BlueprintScreen screen = new BlueprintScreen();

        assertEquals(0, selectedFloor.get(screen));
    }

    @Test
    void reopeningBlueprintKeepsMapUiStateForTheClientSession() throws Exception {
        BlueprintScreen first = new BlueprintScreen();
        setField(first, "selectedFloorOrdinal", 2);
        setField(first, "mapScaleFit", false);
        setField(first, "mapScale", 2.36F);
        setField(first, "playerCentered", true);
        setField(first, "showPlayerHead", false);
        setField(first, "showTerrain", false);
        setField(first, "showBuildingIcons", false);
        setField(first, "mapCenterVillageId", 7);
        setField(first, "mapCenterAutomatic", false);
        setField(first, "mapCenterX", 123.25D);
        setField(first, "mapCenterZ", -45.75D);
        setField(first, "lastRoomScanPosition", BlockPos.ZERO);
        setField(first, "cachedRoomScanPlan", RoomScanPlan.addBuilding(BlockPos.ZERO));
        first.removed();

        BlueprintScreen reopened = new BlueprintScreen();

        assertEquals(2, getField(reopened, "selectedFloorOrdinal"));
        assertEquals(false, getField(reopened, "mapScaleFit"));
        assertEquals(2.36F, getField(reopened, "mapScale"));
        assertEquals(true, getField(reopened, "playerCentered"));
        assertEquals(false, getField(reopened, "showPlayerHead"));
        assertEquals(false, getField(reopened, "showTerrain"));
        assertEquals(false, getField(reopened, "showBuildingIcons"));
        assertEquals(7, getField(reopened, "mapCenterVillageId"));
        assertEquals(false, getField(reopened, "mapCenterAutomatic"));
        assertEquals(123.25D, getDoubleField(reopened, "mapCenterX"), 0.0000001D);
        assertEquals(-45.75D, getDoubleField(reopened, "mapCenterZ"), 0.0000001D);
        assertEquals(null, getField(reopened, "lastRoomScanPosition"));
        assertEquals(null, getField(reopened, "cachedRoomScanPlan"));
    }

    @Test
    void changingClientLevelResetsRememberedBlueprintState() throws Exception {
        BlueprintScreen first = new BlueprintScreen();
        setField(first, "selectedFloorOrdinal", -2);
        setField(first, "mapScaleFit", false);
        setField(first, "mapScale", 3.0F);
        setField(first, "playerCentered", true);
        setField(first, "showPlayerHead", false);
        setField(first, "showTerrain", false);
        setField(first, "showBuildingIcons", false);
        setField(first, "mapCenterVillageId", 7);
        setField(first, "mapCenterAutomatic", false);
        setField(first, "mapCenterX", 123.25D);
        setField(first, "mapCenterZ", -45.75D);
        first.removed();

        BlueprintScreen.onClientLevelChanged(new Object());
        BlueprintScreen fresh = new BlueprintScreen();

        assertEquals(0, getField(fresh, "selectedFloorOrdinal"));
        assertEquals(true, getField(fresh, "mapScaleFit"));
        assertEquals(1.0F, getField(fresh, "mapScale"));
        assertEquals(false, getField(fresh, "playerCentered"));
        assertEquals(true, getField(fresh, "showPlayerHead"));
        assertEquals(true, getField(fresh, "showTerrain"));
        assertEquals(true, getField(fresh, "showBuildingIcons"));
        assertEquals(null, getField(fresh, "mapCenterVillageId"));
        assertEquals(true, getField(fresh, "mapCenterAutomatic"));
        assertEquals(0.0D, getDoubleField(fresh, "mapCenterX"), 0.0000001D);
        assertEquals(0.0D, getDoubleField(fresh, "mapCenterZ"), 0.0000001D);
    }

    @Test
    void loadedBlueprintDoesNotNeedAnotherInitialVillageRequestWhenResumed() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        assertTrue(screen.needsVillageRequestOnInit());

        setField(screen, "village", new Village(1, null));

        assertFalse(screen.needsVillageRequestOnInit());
    }

    @Test
    void soleAvailableFloorCannotRemainAllFloors() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        setField(screen, "selectedFloorOrdinal", null);
        setField(screen, "floorOrdinals", List.of(0));

        Method updateFloorControls = BlueprintScreen.class.getDeclaredMethod("updateFloorControls");
        updateFloorControls.setAccessible(true);
        updateFloorControls.invoke(screen);

        assertEquals(0, getField(screen, "selectedFloorOrdinal"));
    }

    @Test
    void registeredRoomScanCarriesExpectedRoomId() {
        Building room = room(42);
        RoomScanPlan plan = RoomScanPlan.updateRoom(room, BlockPos.ZERO);

        ReportBuildingMessage message = BlueprintScreen.structureScanMessage(
                Village.RoomScanMode.UPDATE_ROOM, plan);

        assertEquals(ReportBuildingMessage.Action.SCAN_ROOM, message.action());
        assertEquals(42, message.expectedTargetId());
    }

    @Test
    void roomScanPreviewIsReusedWhileStationaryAndRefreshesAfterMovement() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        Minecraft client = allocate(Minecraft.class);
        client.player = allocate(LocalPlayer.class);
        Field minecraftField = Screen.class.getDeclaredField("minecraft");
        minecraftField.setAccessible(true);
        minecraftField.set(screen, client);
        setField(screen, "village", new Village(1, null));
        BlockPos position = new BlockPos(4, 64, 8);
        Field playerPosition = Entity.class.getDeclaredField("blockPosition");
        playerPosition.setAccessible(true);
        playerPosition.set(client.player, position);
        Method preview = BlueprintScreen.class.getDeclaredMethod("getPlayerRoomScanPlan");
        preview.setAccessible(true);
        RoomScanPlan initial = (RoomScanPlan) preview.invoke(screen);

        client.player.tickCount = 20;
        assertSame(initial, preview.invoke(screen));
        client.player.tickCount = 200;
        assertSame(initial, preview.invoke(screen));

        BlockPos movedPosition = position.offset(1, 0, 0);
        playerPosition.set(client.player, movedPosition);
        RoomScanPlan moved = (RoomScanPlan) preview.invoke(screen);
        assertNotSame(initial, moved);
        assertEquals(Village.RoomScanMode.ADD_BUILDING, moved.mode());
        assertEquals(new BlockPos(5, 64, 8), moved.interactionSource());
    }

    @Test
    void villageResponseInvalidatesRoomScanPreview() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        setField(screen, "page", "map");
        setField(screen, "lastRoomScanPosition", BlockPos.ZERO);
        setField(screen, "cachedRoomScanPlan", RoomScanPlan.addBuilding(BlockPos.ZERO));

        screen.setVillage(new Village(1, null));

        assertEquals(null, getField(screen, "lastRoomScanPosition"));
        assertEquals(null, getField(screen, "cachedRoomScanPlan"));
    }

    @Test
    void attachmentButtonDistinguishesBasementsFromUpperFloors() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        TooltipButtonWidget button = new TooltipButtonWidget(0, 0, 100, 20, "", ignored -> {});
        setField(screen, "structureScanButton", button);
        Method update = BlueprintScreen.class.getDeclaredMethod("updateStructureScanControl", RoomScanPlan.class);
        update.setAccessible(true);

        for (int floorNumber : List.of(-1, -2, 1, 2, Integer.MIN_VALUE)) {
            RoomScanPlan plan = RoomScanPlan.attachment(10, floorNumber, BlockPos.ZERO, BlockPos.ZERO);
            update.invoke(screen, plan);
            assertEquals(Component.translatable(plan.hasProspectiveFloor() && floorNumber < 0
                    ? "gui.blueprint.addBasement" : "gui.blueprint.addFloor"), button.getMessage());
            assertEquals(ReportBuildingMessage.Action.ADD_ATTACHMENT,
                    BlueprintScreen.structureScanMessage(plan.mode(), plan).action());
        }
    }

    @Test
    void inheritanceMessageCarriesExpectedRoomId() {
        Building room = room(43);

        ReportBuildingMessage message = BlueprintScreen.inheritanceMessage(null, room);

        assertEquals(ReportBuildingMessage.Action.SET_ROOM_INHERITANCE, message.action());
        assertEquals(43, message.expectedTargetId());
    }

    @Test
    void destructiveEditMessagesCarryTheDisplayedTargetIdentity() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(
                floor(0, 64, 68, 0),
                floor(1, 72, 76, 1)));
        Building currentRoom = room(43);
        registerStructure(village, structure, currentRoom);
        RoomScanPlan roomPlan = RoomScanPlan.updateRoom(currentRoom, BlockPos.ZERO);

        ReportBuildingMessage forceType = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.FORCE_TYPE, village, roomPlan, 0).orElseThrow();
        ReportBuildingMessage mainRoom = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.SET_MAIN_ROOM, village, roomPlan, 0).orElseThrow();
        ReportBuildingMessage removeRoom = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE_ROOM, village, roomPlan, 0).orElseThrow();
        ReportBuildingMessage removeFloor = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE_FLOOR, village, roomPlan, 1).orElseThrow();
        ReportBuildingMessage removeBuilding = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE, village, roomPlan, 0).orElseThrow();

        assertEquals(43, forceType.expectedTargetId());
        assertEquals("blocked", forceType.data());
        assertEquals(43, mainRoom.expectedTargetId());
        assertEquals(43, removeRoom.expectedTargetId());
        assertEquals(10, removeFloor.expectedTargetId());
        assertEquals("1:10:1", removeFloor.data());
        assertEquals(10, removeBuilding.expectedTargetId());
    }

    @Test
    void buildingRemovalFromAnUnregisteredRoomUsesPersistedLogicalBuildingIdentity() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO, List.of(floor(0, 64, 68, 0)));
        Building main = room(43);
        registerStructure(village, structure, main);
        RoomScanPlan plan = RoomScanPlan.addRoom(10, 0, BlockPos.ZERO);

        ReportBuildingMessage message = BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE, village, plan, 0).orElseThrow();

        assertEquals(10, message.expectedTargetId());
    }

    @Test
    void inheritanceControlCanAppearAfterEnteringRoomWithoutRebuildingPage() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        Class<?> columnType = Arrays.stream(BlueprintScreen.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("SideControlColumn"))
                .findFirst().orElseThrow();
        Constructor<?> constructor = columnType.getDeclaredConstructor(
                BlueprintScreen.class, int.class, int.class);
        constructor.setAccessible(true);
        Object column = constructor.newInstance(screen, 0, 0);
        Method addInheritanceControl = BlueprintScreen.class.getDeclaredMethod(
                "addInheritanceControl", columnType);
        addInheritanceControl.setAccessible(true);

        addInheritanceControl.invoke(screen, column);

        TooltipButtonWidget button = (TooltipButtonWidget) getField(screen, "inheritanceButton");
        assertNotNull(button);
        assertFalse(button.visible);
        assertFalse(button.active);

        Method updateInheritanceControl = BlueprintScreen.class.getDeclaredMethod(
                "updateInheritanceControl", RoomScanPlan.class);
        updateInheritanceControl.setAccessible(true);
        updateInheritanceControl.invoke(screen, RoomScanPlan.updateRoom(room(44), BlockPos.ZERO));

        assertTrue(button.visible);
        assertTrue(button.active);
    }

    @Test
    void rememberedMapCenterIsNotReusedForAnotherVillage() throws Exception {
        BlueprintScreen first = new BlueprintScreen();
        setField(first, "mapCenterVillageId", 7);
        setField(first, "mapCenterAutomatic", false);
        setField(first, "mapCenterX", 123.25D);
        setField(first, "mapCenterZ", -45.75D);
        first.removed();

        BlueprintScreen reopened = new BlueprintScreen();
        setField(reopened, "page", "map");
        reopened.setVillage(villageWithStructures(8, List.of(new BlockPos(40, 64, 10))));

        assertEquals(40.5D, getDoubleField(reopened, "mapCenterX"), 0.0000001D);
        assertEquals(10.5D, getDoubleField(reopened, "mapCenterZ"), 0.0000001D);
        assertEquals(true, getField(reopened, "mapCenterAutomatic"));
    }

    @Test
    void switchingBlueprintSectionsKeepsNavigationInPlaceAtDifferentGuiScales() throws Exception {
        for (int[] viewport : List.of(new int[]{854, 496}, new int[]{427, 248})) {
            BlueprintScreen screen = new BlueprintScreen();
            screen.width = viewport[0];
            screen.height = viewport[1];
            setField(screen, "village", new Village(1, null));
            setField(screen, "page", "map");
            screen.init();

            List<AbstractWidget> initialNavigation = screen.children().stream().skip(1).limit(5)
                    .map(AbstractWidget.class::cast).toList();
            assertEquals(screen.width / 2 - 180, initialNavigation.getFirst().getX(),
                    "Keep navigation at its original horizontal position");
            assertEquals(screen.height / 2 - 56, initialNavigation.getFirst().getY(),
                    "Keep navigation at its original vertical position");
            for (String section : List.of("rank", "catalog", "villagers", "rules", "map")) {
                var sectionButton = screen.children().stream()
                        .filter(child -> child instanceof net.minecraft.client.gui.components.Button button
                                && button.getMessage().equals(net.minecraft.network.chat.Component.translatable(
                                "gui.blueprint." + section)))
                        .map(net.minecraft.client.gui.components.Button.class::cast).findFirst().orElseThrow();
                sectionButton.onPress();
                List<AbstractWidget> navigation = screen.children().stream().skip(1).limit(5)
                        .map(AbstractWidget.class::cast).toList();
                for (int i = 0; i < navigation.size(); i++) {
                    assertEquals(initialNavigation.get(i).getX(), navigation.get(i).getX(), section + " navigation X");
                    assertEquals(initialNavigation.get(i).getY(), navigation.get(i).getY(), section + " navigation Y");
                    assertEquals(80, navigation.get(i).getWidth(), "Preserve vanilla GUI-unit width");
                    assertEquals(20, navigation.get(i).getHeight(), "Preserve vanilla GUI-unit height");
                }
            }
        }
    }

    @Test
    void mapStaysCenteredWithNarrowerSideControls() throws Exception {
        for (String page : List.of("map", "advanced")) {
            for (int width : new int[]{427, 480, 854}) {
                BlueprintScreen screen = new BlueprintScreen();
                screen.width = width;
                screen.height = 250;
                setField(screen, "village", new Village(1, null));
                setField(screen, "page", page);
                screen.init();

                // The back button is fixed in the corner; the remaining widgets form the map columns.
                List<AbstractWidget> columns = screen.children().stream().skip(1)
                        .map(AbstractWidget.class::cast).toList();
                int left = columns.stream().mapToInt(AbstractWidget::getX).min().orElseThrow();
                int right = columns.stream().mapToInt(widget -> widget.getX() + widget.getWidth())
                        .max().orElseThrow();
                assertTrue(left >= 0, page + " left edge at width " + width);
                assertTrue(right <= width, page + " right edge " + right + " at width " + width);
                Method currentViewport = BlueprintScreen.class.getDeclaredMethod("currentViewport");
                currentViewport.setAccessible(true);
                BlueprintMapViewport viewport = (BlueprintMapViewport) currentViewport.invoke(screen);
                assertEquals(width / 2, viewport.centerX(), page + " map center at width " + width);
                AbstractWidget floorPrevious = (AbstractWidget) getField(screen, "floorPreviousButton");
                AbstractWidget floorNext = (AbstractWidget) getField(screen, "floorNextButton");
                AbstractWidget playerCentered = (AbstractWidget) getField(screen, "playerCenteredButton");
                AbstractWidget playerHead = (AbstractWidget) getField(screen, "playerHeadButton");
                assertTrue(playerHead.getX() + playerHead.getWidth() - playerCentered.getX() <= 110);
                assertEquals(viewport.centerX(),
                        (floorPrevious.getX() + floorNext.getX() + floorNext.getWidth()) / 2);
                assertEquals(150, floorNext.getX() + floorNext.getWidth() - floorPrevious.getX());
            }
        }
    }

    @Test
    void mapGrowthStaysModestWithoutScalingButtonsAndRefitsAfterResize() throws Exception {
        BlueprintScreen screen = new BlueprintScreen();
        setField(screen, "village", new Village(1, null));
        setField(screen, "page", "map");
        Method currentViewport = BlueprintScreen.class.getDeclaredMethod("currentViewport");
        currentViewport.setAccessible(true);

        screen.width = 427;
        screen.height = 248;
        screen.init();
        BlueprintMapViewport compact = (BlueprintMapViewport) currentViewport.invoke(screen);
        screen.width = 854;
        screen.height = 496;
        screen.init();
        BlueprintMapViewport expanded = (BlueprintMapViewport) currentViewport.invoke(screen);
        assertTrue(expanded.halfSize() > compact.halfSize(), "Allow modest growth with extra GUI space");
        assertTrue(expanded.halfSize() <= 90, "Limit map diameter to 20 percent above its original size");
        assertEquals(427, expanded.centerX());
        assertTrue(expanded.top() >= 29, "Reserve title space");
        for (var child : screen.children()) {
            AbstractWidget widget = (AbstractWidget) child;
            assertTrue(widget.getX() >= 0 && widget.getX() + widget.getWidth() <= screen.width);
            assertTrue(widget.getY() >= 0 && widget.getY() + widget.getHeight() <= screen.height);
        }
        AbstractWidget floorPrevious = (AbstractWidget) getField(screen, "floorPreviousButton");
        assertEquals(24, floorPrevious.getWidth());
        assertEquals(20, floorPrevious.getHeight());
        assertTrue(floorPrevious.getY() >= expanded.bottom(), "Keep controls below expanded map");
        AbstractWidget playerCentered = (AbstractWidget) getField(screen, "playerCenteredButton");
        assertTrue(playerCentered.getX() >= expanded.right(), "Keep side controls outside expanded map");
        AbstractWidget navigation = (AbstractWidget) screen.children().get(1);
        assertTrue(navigation.getX() + navigation.getWidth() < expanded.left(),
                "Leave a gap between original navigation and expanded map");

        screen.width = 427;
        screen.height = 248;
        screen.init();
        assertEquals(compact, currentViewport.invoke(screen));
    }

    @Test
    void mapControlsFitShortScreenBeforeAndAfterRoomControlUpdates() throws Exception {
        for (String page : List.of("map", "advanced")) {
            BlueprintScreen screen = new BlueprintScreen();
            screen.width = 427;
            screen.height = 248;
            setField(screen, "village", new Village(1, null));
            setField(screen, "isVillage", true);
            setField(screen, "page", page);
            screen.init();

            Method updateControls = BlueprintScreen.class.getDeclaredMethod("updateMapControls", RoomScanPlan.class);
            updateControls.setAccessible(true);
            for (RoomScanPlan scanPlan : List.of(RoomScanPlan.addBuilding(BlockPos.ZERO),
                    RoomScanPlan.updateRoom(room(44), BlockPos.ZERO))) {
                for (var child : screen.children()) {
                    AbstractWidget widget = (AbstractWidget) child;
                    assertTrue(widget.getY() >= 0, page + " control above screen");
                    assertTrue(widget.getY() + widget.getHeight() <= screen.height,
                            page + " control below screen: " + widget.getMessage().getString());
                }
                updateControls.invoke(screen, scanPlan);
                AbstractWidget scanButton = (AbstractWidget) getField(screen, "structureScanButton");
                if (scanButton != null) {
                    AbstractWidget previousFloor = (AbstractWidget) getField(screen, "floorPreviousButton");
                    assertTrue(scanButton.getY() + 3 * 22 <= previousFloor.getY(),
                            "Room controls must stay above the map navigation rows after refresh");
                }
            }

            Method currentViewport = BlueprintScreen.class.getDeclaredMethod("currentViewport");
            currentViewport.setAccessible(true);
            BlueprintMapViewport viewport = (BlueprintMapViewport) currentViewport.invoke(screen);
            assertTrue(viewport.top() >= 30, "Leave room for the title above the map");
            AbstractWidget scaleButton = (AbstractWidget) getField(screen, "mapScaleButton");
            int titleTop = viewport.top() - 29;
            int controlsBottom = scaleButton.getY() + scaleButton.getHeight();
            assertTrue(Math.abs(titleTop - (screen.height - controlsBottom)) <= 1,
                    "Title and bottom controls must have balanced vertical margins");
        }
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(currentRoom, BlockPos.ZERO);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, 1);

        assertTrue(state.visible());
        assertTrue(state.active());
        assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
    }

    @Test
    void unregisteredAttachmentDoesNotOfferFloorRemoval() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(
                        floor(0, 60, 64, -1),
                        floor(1, 64, 68, 0)));
        Building main = room(1);
        main.setFloorId(1);
        registerStructure(village, structure, main);

        RoomScanPlan plan = RoomScanPlan.attachment(
                10, -1, BlockPos.ZERO, BlockPos.ZERO);
        BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(village, plan, -1);

        assertFalse(state.visible());
        assertTrue(BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE_FLOOR, village, plan, -1).isEmpty());
    }

    @Test
    void emptyPersistedFloorOffersRemovalWithoutARegisteredRoom() throws Exception {
        for (int floorNumber : List.of(-1, 1)) {
            Village village = new Village(1, null);
            Structure structure = new Structure(10, BlockPos.ZERO,
                    List.of(floor(0, 64, 68, 0),
                            floor(1, 64 + 8 * floorNumber, 68 + 8 * floorNumber, floorNumber)));
            registerStructure(village, structure, room(1));
            RoomScanPlan plan = RoomScanPlan.addRoom(10, 1, new BlockPos(0, 64 + 8 * floorNumber, 0));

            BlueprintScreen.RemovalControlState state = BlueprintScreen.removalControlState(
                    village, plan, floorNumber);

            assertTrue(state.visible(), "empty floor " + floorNumber + " hid its removal control");
            assertTrue(state.active());
            assertEquals(ReportBuildingMessage.Action.REMOVE_FLOOR, state.action());
            ReportBuildingMessage message = BlueprintScreen.targetedEditMessage(
                    state.action(), village, plan, floorNumber).orElseThrow();
            assertEquals(10, message.expectedTargetId());
            assertEquals(floorNumber + ":10:1", message.data());
        }
    }

    @Test
    void unregisteredComponentCannotRemoveAnOccupiedFloor() throws Exception {
        Village village = new Village(1, null);
        Structure structure = new Structure(10, BlockPos.ZERO,
                List.of(floor(0, 64, 68, 0), floor(1, 72, 76, 1)));
        registerStructure(village, structure, room(1));
        Building upperRoom = room(2);
        upperRoom.setFloorId(1);
        registerRoom(village, upperRoom);
        RoomScanPlan plan = RoomScanPlan.addRoom(10, 1, new BlockPos(1, 72, 0));

        assertFalse(BlueprintScreen.removalControlState(village, plan, 1).visible());
        assertTrue(BlueprintScreen.targetedEditMessage(
                ReportBuildingMessage.Action.REMOVE_FLOOR, village, plan, 1).isEmpty());
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(main, BlockPos.ZERO);
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(main, BlockPos.ZERO);
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(main, BlockPos.ZERO);
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(main, BlockPos.ZERO);
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

        RoomScanPlan plan = RoomScanPlan.updateRoom(main, BlockPos.ZERO);
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
            StructureFloor floor = net.conczin.mca.server.world.data.TestStructureFloors.create(0, cell.getY(), cell.getY() + 4, 0,
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
        return net.conczin.mca.server.world.data.TestStructureFloors.create(id, anchorY, ceilingY, floorNumber,
                region(anchorY));
    }

    private static TestFloorFootprint region(int anchorY) throws Exception {
        return TestFloorFootprint.fromFootprint(
                anchorY, Set.of(new BlockPos(0, anchorY, 0)));
    }

    private static TestFloorFootprint region(BlockPos cell) throws Exception {
        return TestFloorFootprint.fromFootprint(cell.getY(), Set.of(cell));
    }

    private static void setRoomGeometry(Building room, BlockPos cell) throws Exception {
        Method setGeometry = Building.class.getDeclaredMethod(
                "setGeometry", BlockPos.class, BlockPos.class, java.util.Collection.class);
        setGeometry.setAccessible(true);
        setGeometry.invoke(room, cell, cell, Set.of(cell));
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
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
        BlockPos cell = new BlockPos(structure.getPos0().getX(), floor.anchorY(), structure.getPos0().getZ());
        setRoomGeometry(room, cell);
    }
}
