package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.widget.LegacyImageButton;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.GetVillageRequest;
import net.conczin.mca.network.c2s.RenameVillageMessage;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.conczin.mca.network.c2s.SaveVillageMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.resources.data.tasks.Task;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomScanPlan;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class BlueprintScreen extends ExtendedScreen {
    //gui element Y positions
    private static final int POSITION_TAXES = -60;
    private static final int POSITION_BIRTH = -10;
    private static final int POSITION_MARRIAGE = 40;
    private static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static final int MAP_HALF_SIZE = 75;
    private static final int MAP_INNER_MARGIN = 6;
    private static final float MAP_MAX_FIT_SCALE = 2.0f;
    private static final int MAP_CONTROL_GAP = 2;
    private static final int MAP_ICONS_BUTTON_WIDTH = 47;
    private static final int MAP_TERRAIN_BUTTON_WIDTH = 52;
    private static final int MAP_SCALE_BUTTON_WIDTH = 47;
    private static final int PLAYER_CENTERED_BUTTON_WIDTH = 110;
    private static final int PLAYER_HEAD_BUTTON_SIZE = 20;
    private static final int PLAYER_HEAD_ICON_SIZE = 16;
    private static final int MAP_SIDE_CONTROL_GUTTER = 14;
    private static final int MAP_SIDE_CONTROL_WIDTH = 132;
    private static final float MAP_MIN_SCALE = 0.5F;
    private static final float MAP_MAX_SCALE = 4.0F;
    private static final double MAP_ZOOM_FACTOR = 1.1D;
    private static final double MAP_DRAG_THRESHOLD = 3.0D;
    private static final float[] MAP_SCALE_PRESETS = {0.5F, 1.0F, 2.0F, 3.0F, 4.0F};
    private static Integer selectedFloorOrdinal = 0;
    private static boolean mapScaleFit = true;
    private static float mapScale = 1.0F;
    private static boolean playerCentered;
    private static boolean showPlayerHead = true;
    // 1.19.3: This needs to be the MC type, DO NOT TOUCH !!!
    private final List<net.minecraft.client.gui.components.Button> catalogButtons = new LinkedList<>();
    private Village village;
    private int reputation;
    private boolean isVillage;
    private Rank rank;
    private Set<String> completedTasks;
    private String page;
    private ButtonWidget[] buttonTaxes;
    private ButtonWidget[] buttonBirths;
    private ButtonWidget[] buttonMarriage;
    private ButtonWidget buttonPage;
    private int pageNumber = 0;
    private ButtonWidget floorPreviousButton;
    private ButtonWidget floorLabelButton;
    private ButtonWidget floorNextButton;
    private ButtonWidget buildingIconsButton;
    private ButtonWidget terrainButton;
    private ButtonWidget mapScaleButton;
    private ButtonWidget playerCenteredButton;
    private ButtonWidget playerHeadButton;
    private TooltipButtonWidget inheritanceButton;
    private TooltipButtonWidget mainRoomButton;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget attachmentScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private boolean showBuildingIcons = true;
    private boolean showTerrain = true;
    private List<Integer> floorOrdinals = List.of();
    private int structureCount;
    private RoomTypeResolver roomTypeResolver = RoomTypeResolver.create(null);
    private BlueprintTooltipFactory tooltipFactory = BlueprintTooltipFactory.empty();
    private BlueprintMapGeometry mapGeometry = BlueprintMapGeometry.empty();
    private final BlueprintMapRenderer mapRenderer = new BlueprintMapRenderer();
    private final MapPanState mapPanState = new MapPanState();
    private Integer mapCenterVillageId;
    private double mapCenterX;
    private double mapCenterZ;
    private BuildingType selectedBuilding;
    private UUID selectedVillager;
    private BlockPos lastRoomScanPosition;
    private RoomScanPlan cachedRoomScanPlan;

    private int mouseX;
    private int mouseY;

    private Map<Rank, List<Task>> tasks;

    public BlueprintScreen() {
        super(Component.literal("Blueprint"));
    }

    private void saveVillage() {
        Network.sendToServer(new SaveVillageMessage(village));
    }

    private void changeTaxes(float d) {
        village.setTaxes(Math.max(0.0f, Math.min(1.0f, village.getTaxes() + d)));
        saveVillage();
    }

    private void changePopulationThreshold(float d) {
        village.setPopulationThreshold(Math.max(0.0f, Math.min(1.0f, village.getPopulationThreshold() + d)));
        saveVillage();
    }

    private void changeMarriageThreshold(float d) {
        village.setMarriageThreshold(Math.max(0.0f, Math.min(1.0f, village.getMarriageThreshold() + d)));
        saveVillage();
    }

    private ButtonWidget[] createValueChanger(int x, int y, int w, int h, Consumer<Boolean> onPress, Component tooltip) {
        ButtonWidget[] buttons = new ButtonWidget[3];

        buttons[1] = addRenderableWidget(new ButtonWidget(x - w / 2, y, w / 4, h,
                Component.literal("<<"), b -> onPress.accept(false)));

        buttons[2] = addRenderableWidget(new ButtonWidget(x + w / 4, y, w / 4, h,
                Component.literal(">>"), b -> onPress.accept(true)));

        buttons[0] = addRenderableWidget(new ButtonWidget(x - w / 4, y, w / 2, h,
                Component.literal(""), b -> {
        },
                tooltip
        ));

        return buttons;
    }

    @Override
    public void init() {
        Network.sendToServer(new GetVillageRequest());
        setPage("waiting");
    }

    private void setPage(String page) {
        if (page.equals("close")) {
            assert minecraft != null;
            minecraft.setScreen(null);
            return;
        }

        this.page = page;

        clearWidgets();
        floorPreviousButton = null;
        floorLabelButton = null;
        floorNextButton = null;
        buildingIconsButton = null;
        terrainButton = null;
        mapScaleButton = null;
        playerCenteredButton = null;
        playerHeadButton = null;
        inheritanceButton = null;
        mainRoomButton = null;
        structureScanButton = null;
        attachmentScanButton = null;
        removeRoomButton = null;
        removeBuildingButton = null;

        // back button
        addRenderableWidget(new ButtonWidget(5, 5, 20, 20, Component.translatable("gui.button.backarrow"), b -> setPage("close")));

        //page selection
        int bx = width / 2 - 180;
        int by = height / 2 - 56;
        if (!page.equals("rename") && (!page.equals("empty") && !page.equals("waiting"))) {
            for (String p : new String[]{"map", "rank", "catalog", "villagers", "rules", "refresh"}) {
                ButtonWidget widget = new ButtonWidget(bx, by, 80, 20, Component.translatable("gui.blueprint." + p), b -> setPage(p));
                addRenderableWidget(widget);
                if (page.equals(p) || ("advanced".equals(page) && "map".equals(p))) {
                    widget.active = false;
                }
                by += 22;
            }
        }

        switch (page) {
            case "empty":
                bx = width / 2 - 48;
                by = height / 2;
                addRenderableWidget(new TooltipButtonWidget(bx, by + 5, 96, 20, "gui.blueprint.addBuilding", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD_BUILDING));
                }));
                break;
            case "refresh":
                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
                setPage("map");
                break;
            case "map", "advanced": {
                bx = width / 2 + MAP_HALF_SIZE + MAP_SIDE_CONTROL_GUTTER;
                int floorControlY = height / 2 + 87;

                // Map and Advanced are two control modes over the same map view. Keep all
                // display/navigation controls shared so Advanced cannot lose floor controls,
                // building icons, scale selection, or player centering.
                int floorControlX = width / 2 - 75;
                floorPreviousButton = addRenderableWidget(new ButtonWidget(floorControlX, floorControlY, 24, 20,
                        Component.literal("<"), b -> changeSelectedFloor(-1)));
                floorLabelButton = addRenderableWidget(new ButtonWidget(floorControlX + 26, floorControlY, 98, 20,
                        Component.empty(), b -> selectFloor(null)));
                floorNextButton = addRenderableWidget(new ButtonWidget(floorControlX + 126, floorControlY, 24, 20,
                        Component.literal(">"), b -> changeSelectedFloor(1)));
                int mapControlY = floorControlY + 22;
                buildingIconsButton = addRenderableWidget(new ButtonWidget(
                        floorControlX, mapControlY, MAP_ICONS_BUTTON_WIDTH, 20,
                        toggleLabel("gui.blueprint.buildingIcons.short", showBuildingIcons), b -> {
                    showBuildingIcons = !showBuildingIcons;
                    updateToggleControl(buildingIconsButton,
                            "gui.blueprint.buildingIcons.short", showBuildingIcons);
                }, Component.translatable("gui.blueprint.buildingIcons")));
                int terrainControlX = floorControlX + MAP_ICONS_BUTTON_WIDTH + MAP_CONTROL_GAP;
                terrainButton = addRenderableWidget(new ButtonWidget(
                        terrainControlX, mapControlY, MAP_TERRAIN_BUTTON_WIDTH, 20,
                        toggleLabel("gui.blueprint.terrain", showTerrain), b -> {
                    showTerrain = !showTerrain;
                    updateToggleControl(terrainButton, "gui.blueprint.terrain", showTerrain);
                }, Component.translatable("gui.blueprint.terrain.tooltip")));
                int scaleControlX = terrainControlX + MAP_TERRAIN_BUTTON_WIDTH + MAP_CONTROL_GAP;
                mapScaleButton = addRenderableWidget(new ButtonWidget(
                        scaleControlX, mapControlY, MAP_SCALE_BUTTON_WIDTH, 20,
                        Component.literal(getMapScaleLabel()), b -> cycleMapScale(1), getMapScaleTooltip()));

                playerCenteredButton = addRenderableWidget(new ButtonWidget(
                        bx, floorControlY, PLAYER_CENTERED_BUTTON_WIDTH, 20,
                        getPlayerCenteredLabel(), b -> togglePlayerCentered(),
                        Component.translatable("gui.blueprint.playerCentered.tooltip")) {
                    @Override
                    public boolean isHoveredOrFocused() {
                        // Retained keyboard focus must not force the vanilla highlighted
                        // sprite. Keep actual focus state for navigation/narration.
                        return isHovered();
                    }
                });
                playerHeadButton = addRenderableWidget(new ButtonWidget(
                        bx + PLAYER_CENTERED_BUTTON_WIDTH + MAP_CONTROL_GAP,
                        floorControlY + (20 - PLAYER_HEAD_BUTTON_SIZE) / 2,
                        PLAYER_HEAD_BUTTON_SIZE, PLAYER_HEAD_BUTTON_SIZE,
                        Component.empty(), b -> togglePlayerHead(),
                        Component.translatable("gui.blueprint.playerHead.tooltip")) {
                    @Override
                    public boolean isHoveredOrFocused() {
                        // Keep keyboard focus/narration state intact, but do not let retained
                        // focus force the vanilla highlighted button sprite indefinitely.
                        return isHovered();
                    }
                });

                if ("advanced".equals(page)) {
                    // Advanced is a map sub-view: expose settlement-level settings while
                    // preserving the shared map display controls above.
                    SideControlColumn column = new SideControlColumn(bx, height / 2 - 56);
                    MutableComponent text = Component.translatable("gui.blueprint.autoScan");
                    if (village.isAutoScan()) {
                        text.withStyle(ChatFormatting.GREEN);
                    } else {
                        text.withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.STRIKETHROUGH);
                    }
                    column.addTooltip(text, Component.translatable("gui.blueprint.autoScan.tooltip"), b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.AUTO_SCAN));
                        village.toggleAutoScan();
                        setPage(page);
                    });
                    addInheritanceControl(column);
                    column.addTooltip("gui.blueprint.restrictAccess", b ->
                            Network.sendToServer(new ReportBuildingMessage(
                                    ReportBuildingMessage.Action.FORCE_TYPE, "blocked")));
                    mainRoomButton = column.addTooltip("gui.blueprint.setMainRoom", b -> {
                        selectPlayerFloorOnNextVillageResponse = true;
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.SET_MAIN_ROOM));
                    });
                    updateMainRoomControl(getPlayerRoomScanPlan());
                    if (isVillage) {
                        column.addButton(Component.translatable("gui.blueprint.renameVillage"), b -> setPage("rename"));
                    }

                    addRenderableWidget(new ButtonWidget(
                            bx, floorControlY + 22, MAP_SIDE_CONTROL_WIDTH, 20,
                            Component.translatable("gui.back"), b -> setPage("map")));
                } else {
                    // A grouped POI such as the town bell keeps the settlement alive, but
                    // rooms still need a complete physical Structure to attach to.
                    SideControlColumn column = new SideControlColumn(bx, height / 2 - 56 + 22 * 3);
                    structureScanButton = column.addTooltip(
                            "gui.blueprint.addBuilding", b -> requestPrimaryStructureScan());
                    attachmentScanButton = column.addTooltip(
                            "gui.blueprint.addFloor", b -> requestAttachmentScan());
                    removeRoomButton = column.addTooltip("gui.blueprint.removeRoom", b -> {
                        RemovalControlState state = removalControlState(village, getPlayerRoomScanPlan());
                        if (state.visible() && state.active() && state.action() != null) {
                            ReportBuildingMessage message = state.action() == ReportBuildingMessage.Action.REMOVE_FLOOR
                                    ? new ReportBuildingMessage(state.action(), String.valueOf(selectedFloorOrdinal))
                                    : new ReportBuildingMessage(state.action());
                            Network.sendToServer(message);
                        }
                    });
                    removeBuildingButton = column.addButton(
                            Component.translatable("gui.blueprint.removeBuilding"), b ->
                                    Network.sendToServer(new ReportBuildingMessage(
                                            ReportBuildingMessage.Action.REMOVE)));

                    addRenderableWidget(new ButtonWidget(
                            bx, floorControlY + 22, MAP_SIDE_CONTROL_WIDTH, 20,
                            Component.translatable("gui.blueprint.advanced"), b -> setPage("advanced")));
                }
                break;
            }
            case "rank":
                break;
            case "catalog":
                //list catalog button
                int row = 0;
                int col = 0;
                int size = 21;
                int x = width / 2 - 4 * size - 8;
                int y = (int) (height / 2.0 - 2.0 * size);
                catalogButtons.clear();
                for (BuildingType bt : BuildingTypes.getInstance()) {
                    if (bt.visible()) {
                        Button widget;
                        if (bt.hasIcon()) {
                            widget = new LegacyImageButton(
                                    row * size + x + 10, col * size + y - 10, 20, 20, bt.iconU(), bt.iconV() + 20, 20, ICON_TEXTURES, 256, 256, button -> {
                                selectBuilding(bt);
                                button.active = false;
                                catalogButtons.forEach(b -> b.active = true);
                            }, Component.translatable("buildingType." + bt.name()));
                        } else {
                            widget = new ButtonWidget(row * size + x + 10, col * size + y - 10, 20, 20, Component.empty(), button -> {
                                selectBuilding(bt);
                                button.active = false;
                                catalogButtons.forEach(b -> b.active = true);
                            }, Component.translatable("buildingType." + bt.name()));
                        }
                        catalogButtons.add(addRenderableWidget(widget));

                        row++;
                        if (row > 4) {
                            row = 0;
                            col++;
                        }
                    }
                }
                break;
            case "villagers":
                addRenderableWidget(new ButtonWidget(width / 2 - 24 - 20, height / 2 + 54, 20, 20, Component.literal("<"), b -> {
                    if (pageNumber > 0) {
                        pageNumber--;
                    }
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 24, height / 2 + 54, 20, 20, Component.literal(">"), b -> {
                    if (pageNumber < Math.ceil(village.getPopulation() / 9.0) - 1) {
                        pageNumber++;
                    }
                }));
                buttonPage = addRenderableWidget(new ButtonWidget(width / 2 - 24, height / 2 + 54, 48, 20, Component.literal("0/0)"), b -> {
                }));
                break;
            case "rules":
                //taxes
                buttonTaxes = createValueChanger(width / 2, height / 2 + POSITION_TAXES + 10, 80, 20, b -> changeTaxes(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.taxes"));
                toggleButtons(buttonTaxes, false);

                //birth threshold
                buttonBirths = createValueChanger(width / 2, height / 2 + POSITION_BIRTH + 10, 80, 20, b -> changePopulationThreshold(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.births"));
                toggleButtons(buttonBirths, false);

                //marriage threshold
                buttonMarriage = createValueChanger(width / 2, height / 2 + POSITION_MARRIAGE + 10, 80, 20, b -> changeMarriageThreshold(b ? 0.125f : -0.125f), Component.translatable("gui.blueprint.tooltip.marriage"));
                toggleButtons(buttonMarriage, false);
                break;
            case "rename":
                EditBox field = addRenderableWidget(new EditBox(font, width / 2 - 65, height / 2 - 16, 130, 20, Component.translatable("gui.blueprint.renameVillage")));
                field.setMaxLength(32);
                field.setValue(village.getName());

                addRenderableWidget(new ButtonWidget(width / 2 - 66, height / 2 + 8, 64, 20, Component.translatable("gui.blueprint.cancel"), b -> {
                    setPage("map");
                }));
                addRenderableWidget(new ButtonWidget(width / 2 + 2, height / 2 + 8, 64, 20, Component.translatable("gui.blueprint.rename"), b -> {
                    Network.sendToServer(new RenameVillageMessage(village.getId(), field.getValue()));
                    village.setName(field.getValue());
                    setPage("map");
                }));
                break;
        }
    }

    private void selectBuilding(BuildingType b) {
        selectedBuilding = b;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int sizeX, int sizeY, float offset) {
        if (village != null && ("map".equals(page) || "advanced".equals(page))) {
            updateMapControls(getPlayerRoomScanPlan());
        }

        super.render(context, sizeX, sizeY, offset);

        assert minecraft != null;
        this.mouseX = (int) (minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getWidth());
        this.mouseY = (int) (minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getHeight());

        switch (page) {
            case "waiting" ->
                    context.drawCenteredString(font, Component.translatable("gui.blueprint.waiting"), width / 2, height / 2, 0xffaaaaaa);
            case "empty" ->
                    context.drawCenteredString(font, Component.translatable("gui.blueprint.empty"), width / 2, height / 2 - 20, 0xffaaaaaa);
            case "map" -> {
                renderStats(context);
                renderName(context);
                renderMap(context, offset);
            }
            case "advanced" -> {
                renderName(context);
                renderMap(context, offset);
            }
            case "rank" -> {
                renderTasks(context);
                renderStats(context);
            }
            case "catalog" -> renderCatalog(context);
            case "villagers" -> renderVillagers(context);
            case "rules" -> renderRules(context);
        }

        assert minecraft != null;
        minecraft.gui.renderOverlayMessage(context, minecraft.getTimer());
    }

    private static ReportBuildingMessage.Action getStructureScanAction(Village.RoomScanMode mode) {
        return switch (mode) {
            case ADD_BUILDING -> ReportBuildingMessage.Action.ADD_BUILDING;
            case ADD_ROOM -> ReportBuildingMessage.Action.ADD_ROOM;
            case UPDATE_ROOM -> ReportBuildingMessage.Action.UPDATE_ROOM;
            case ADD_FLOOR -> ReportBuildingMessage.Action.ADD_FLOOR;
            case ADD_BASEMENT -> ReportBuildingMessage.Action.ADD_BASEMENT;
        };
    }

    private void requestPrimaryStructureScan() {
        Village.RoomScanMode mode = getPlayerRoomScanPlan().mode();
        sendStructureScan(mode.isAttachment() ? Village.RoomScanMode.ADD_BUILDING : mode, null);
    }

    private void requestAttachmentScan() {
        RoomScanPlan plan = getPlayerRoomScanPlan();
        if (!plan.mode().isAttachment()) return;
        sendStructureScan(plan.mode(), Integer.toString(plan.targetBuildingId()));
    }

    private void sendStructureScan(Village.RoomScanMode mode, String targetBuildingId) {
        selectPlayerFloorOnNextVillageResponse = true;
        Network.sendToServer(new ReportBuildingMessage(
                getStructureScanAction(mode), targetBuildingId));
    }

    void cancelPendingFloorSelection() {
        selectPlayerFloorOnNextVillageResponse = false;
    }

    private RoomScanPlan getPlayerRoomScanPlan() {
        if (village == null || minecraft == null || minecraft.player == null) {
            return RoomScanPlan.addBuilding(BlockPos.ZERO);
        }

        BlockPos position = minecraft.player.blockPosition();
        if (!position.equals(lastRoomScanPosition) || cachedRoomScanPlan == null) {
            lastRoomScanPosition = position.immutable();
            cachedRoomScanPlan = village.getRoomScanPlan(minecraft.level, position);
        }
        return cachedRoomScanPlan;
    }

    private String getStructureScanTranslationKey(Village.RoomScanMode mode) {
        return switch (mode) {
            case ADD_BUILDING -> "gui.blueprint.addBuilding";
            case ADD_ROOM -> "gui.blueprint.addRoom";
            case UPDATE_ROOM -> "gui.blueprint.updateRoom";
            case ADD_FLOOR -> "gui.blueprint.addFloor";
            case ADD_BASEMENT -> "gui.blueprint.addBasement";
        };
    }

    private void updateMapControls(RoomScanPlan scanContext) {
        updateFloorControls();
        updateToggleControl(buildingIconsButton, "gui.blueprint.buildingIcons.short", showBuildingIcons);
        updateToggleControl(terrainButton, "gui.blueprint.terrain", showTerrain);
        updateMapScaleControl();
        updateStructureScanControl(scanContext);
        updateInheritanceControl(scanContext);
        updateMainRoomControl(scanContext);
    }

    private void updateStructureScanControl(RoomScanPlan scanContext) {
        if (structureScanButton == null) return;

        boolean attachment = scanContext.mode().isAttachment();
        Village.RoomScanMode primaryMode = attachment
                ? Village.RoomScanMode.ADD_BUILDING : scanContext.mode();
        boolean roomRegistered = scanContext.mode() == Village.RoomScanMode.UPDATE_ROOM;
        boolean insideBuilding = roomRegistered || scanContext.mode() == Village.RoomScanMode.ADD_ROOM;
        RemovalControlState removalState = removalControlState(village, scanContext);
        int y = height / 2 - 56 + 22 * 3;

        structureScanButton.setMessage(getStructureScanTranslationKey(primaryMode));
        structureScanButton.setTooltip(Tooltip.create(Component.translatable(
                getStructureScanTranslationKey(primaryMode) + ".tooltip")));
        structureScanButton.active = true;
        structureScanButton.setY(y);
        y += 22;

        if (attachmentScanButton != null) {
            attachmentScanButton.visible = attachment;
            attachmentScanButton.active = attachment;
            attachmentScanButton.setY(y);
            if (attachment) {
                MutableComponent label = Component.translatable(getStructureScanTranslationKey(scanContext.mode()));
                if (scanContext.prospectiveFloorNumber() != Integer.MIN_VALUE) {
                    label.append(Component.literal(" " + scanContext.prospectiveFloorNumber()));
                }
                attachmentScanButton.setMessage(label);
                attachmentScanButton.setTooltip(Tooltip.create(Component.translatable(
                        getStructureScanTranslationKey(scanContext.mode()) + ".tooltip")));
                y += 22;
            }
        }

        if (removeRoomButton != null) {
            removeRoomButton.visible = removalState.visible();
            removeRoomButton.active = removalState.active();
            removeRoomButton.setMessage(Component.translatable(removalState.labelKey()));
            removeRoomButton.setTooltip(Tooltip.create(Component.translatable(removalState.tooltipKey())));
            removeRoomButton.setY(y);
            if (removeRoomButton.visible) y += 22;
        }

        if (removeBuildingButton != null) {
            removeBuildingButton.visible = insideBuilding;
            removeBuildingButton.active = insideBuilding;
            removeBuildingButton.setY(y);
        }
    }

    static RemovalControlState removalControlState(Village village, RoomScanPlan scanContext) {
        if (village == null || scanContext == null) return RemovalControlState.hidden();

        Building room = scanContext.currentRoom().orElse(null);
        if (room == null) return RemovalControlState.hidden();

        Integer selectedFloor = selectedFloorOrdinal;
        if (selectedFloor != null
                && village.canRemoveFloor(village.getLogicalBuildingId(room.getStructureId()), selectedFloor)) {
            return new RemovalControlState(true, true, ReportBuildingMessage.Action.REMOVE_FLOOR,
                    "gui.blueprint.removeFloor", "gui.blueprint.removeFloor.tooltip");
        }

        boolean mainRoom = village.isMainRoom(room);
        return new RemovalControlState(true, !mainRoom, ReportBuildingMessage.Action.REMOVE_ROOM,
                "gui.blueprint.removeRoom",
                mainRoom ? "gui.blueprint.removeRoom.disabled.mainRoom"
                        : "gui.blueprint.removeRoom.tooltip");
    }

    record RemovalControlState(boolean visible, boolean active, ReportBuildingMessage.Action action,
                               String labelKey, String tooltipKey) {
        private static RemovalControlState hidden() {
            return new RemovalControlState(false, false, null,
                    "gui.blueprint.removeRoom", "gui.blueprint.removeRoom.tooltip");
        }
    }

    private void renderName(GuiGraphics context) {
        final PoseStack matrices = context.pose();
        //name
        matrices.pushPose();
        matrices.scale(2.0f, 2.0f, 2.0f);
        if (isVillage) {
            context.drawCenteredString(font, village.getName(), width / 4, height / 4 - 48, 0xffffffff);
        } else {
            context.drawCenteredString(font, Component.translatable("gui.blueprint.settlement"), width / 4, height / 4 - 48, 0xffffffff);
        }
        matrices.popPose();
    }

    private void renderStats(GuiGraphics context) {
        int x = width / 2 + (page.equals("rank") ? -70 : 105);
        int y = height / 2 - 50;

        //rank
        Component rankStr = Component.translatable(rank.getTranslationKey());
        int rankColor = rank.ordinal() == 0 ? 0xffff0000 : 0xffffff00;

        context.drawString(font, Component.translatable("gui.blueprint.currentRank", rankStr), x, y, rankColor);
        context.drawString(font, Component.translatable("gui.blueprint.reputation", String.valueOf(reputation)), x, y + 11, rank.ordinal() == 0 ? 0xffff0000 : 0xffffffff);
        context.drawString(font, Component.translatable("gui.blueprint.buildings", structureCount), x, y + 22, 0xffffffff);
        context.drawString(font, Component.translatable("gui.blueprint.population", village.getPopulation(), village.getMaxPopulation()), x, y + 33, 0xffffffff);
    }

    private void renderMap(GuiGraphics context, float partialTick) {
        int centerX = width / 2;
        int centerY = height / 2 + 8;
        Integer selectedFloor = selectedFloorOrdinal;

        if (!village.isAutoScan() && structureCount <= 1) {
            int hintY = floorOrdinals.size() > 1 ? height / 2 + 134 : height / 2 + 90;
            context.drawCenteredString(font, Component.translatable("gui.blueprint.autoScanDisabled"),
                    width / 2, hintY, 0xaaffffff);
        }

        LocalPlayer player = minecraft == null ? null : minecraft.player;
        double playerRenderX = player == null ? 0.0D : Mth.lerp(partialTick, player.xo, player.getX());
        double playerRenderZ = player == null ? 0.0D : Mth.lerp(partialTick, player.zo, player.getZ());
        if (playerCentered && player != null) {
            mapCenterX = playerRenderX;
            mapCenterZ = playerRenderZ;
        }

        BlueprintMapViewport viewport = BlueprintMapViewport.create(
                centerX,
                centerY,
                MAP_HALF_SIZE,
                mapCenterX,
                mapCenterZ,
                getMapScale()
        );
        BlueprintMapRenderer.RenderResult renderResult = mapRenderer.render(
                context,
                viewport,
                mapGeometry.get(selectedFloor),
                selectedFloor,
                showTerrain,
                showBuildingIcons,
                showPlayerHead,
                player,
                playerRenderX,
                playerRenderZ,
                mouseX,
                mouseY
        );
        renderPlayerHeadButtonIcon(context, player);

        List<BlueprintMapRenderer.HoverTarget> hoverTargets = tooltipTargets(
                renderResult.hoverTargets(), renderResult.hoveredLogicalBuildingId());
        if (hoverTargets.isEmpty()) return;

        BlueprintMapRenderer.HoverTarget active = hoverTargets.getFirst();

        List<Component> tooltip = new ArrayList<>(tooltipFactory.tooltip(
                active.building(), active.floorOrdinal(), active.structure()));
        List<BlueprintMapRenderer.HoverTarget> alternatives = hoverTargets.subList(1, hoverTargets.size());
        if (!alternatives.isEmpty()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("gui.blueprint.roomTooltip.alsoHere")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            for (BlueprintMapRenderer.HoverTarget target : alternatives) {
                tooltip.add(tooltipFactory.compactTooltip(
                        target.building(), target.floorOrdinal(),
                        Integer.compare(target.anchorY(), active.anchorY())));
            }
        }

        int tooltipY = mouseY - getTooltipHeight(tooltip) / 2 + 12;
        context.renderComponentTooltip(font, tooltip, mouseX, tooltipY);
    }

    static List<BlueprintMapRenderer.HoverTarget> tooltipTargets(
            List<BlueprintMapRenderer.HoverTarget> hoverTargets,
            int preferredLogicalBuildingId) {
        List<BlueprintMapRenderer.HoverTarget> ordered = new ArrayList<>(hoverTargets);
        ordered.sort(Comparator.comparingInt(BlueprintMapRenderer.HoverTarget::anchorY).reversed()
                .thenComparing(Comparator.comparingInt(
                        BlueprintMapRenderer.HoverTarget::logicalBuildingId).reversed()));
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).logicalBuildingId() != preferredLogicalBuildingId) continue;
            if (i > 0) ordered.addFirst(ordered.remove(i));
            break;
        }
        return List.copyOf(ordered);
    }

    private void renderPlayerHeadButtonIcon(GuiGraphics context, LocalPlayer player) {
        if (playerHeadButton == null || !playerHeadButton.visible || player == null) {
            return;
        }
        int iconSize = PLAYER_HEAD_ICON_SIZE;
        int iconX = playerHeadButton.getX() + (PLAYER_HEAD_BUTTON_SIZE - iconSize) / 2;
        int iconY = playerHeadButton.getY() + (playerHeadButton.getHeight() - iconSize) / 2;
        BlueprintMapRenderer.renderCurrentPlayerFace(context, player, iconX, iconY, iconSize);
        if (!showPlayerHead) {
            context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x88000000);
        }
    }

    private void togglePlayerCentered() {
        playerCentered = !playerCentered;
        if (!playerCentered) {
            centerMapOnVillage();
        }
        updatePlayerCenteredControl();
    }

    private void togglePlayerHead() {
        showPlayerHead = !showPlayerHead;
    }

    private Component getPlayerCenteredLabel() {
        MutableComponent label = Component.translatable("gui.blueprint.playerCentered");
        return playerCentered
                ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY);
    }

    private void updatePlayerCenteredControl() {
        if (playerCenteredButton != null) {
            playerCenteredButton.setMessage(getPlayerCenteredLabel());
        }
    }

    private float getMapScale() {
        if (!mapScaleFit) return mapScale;
        int horizontalSpan = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan());
        int usablePixels = (MAP_HALF_SIZE - MAP_INNER_MARGIN) * 2;
        return Math.min((float) usablePixels / Math.max(1, horizontalSpan), MAP_MAX_FIT_SCALE);
    }

    private void cycleMapScale(int direction) {
        if (mapScaleFit) {
            float scale = direction > 0 ? MAP_SCALE_PRESETS[0] : MAP_SCALE_PRESETS[MAP_SCALE_PRESETS.length - 1];
            mapScale = scale;
            mapScaleFit = false;
        } else {
            float currentScale = mapScale;
            float snapped = snapMapScale(currentScale, direction);
            boolean wrapsToFit = direction > 0 && currentScale >= MAP_MAX_SCALE
                    || direction < 0 && currentScale <= MAP_MIN_SCALE;
            mapScale = wrapsToFit ? currentScale : snapped;
            mapScaleFit = wrapsToFit;
        }
        updateMapScaleControl();
    }

    private Component getMapScaleTooltip() {
        return mapScaleFit
                ? Component.translatable("gui.blueprint.mapScale.fit.tooltip")
                : Component.literal("Map scale: " + formatMapScale(mapScale));
    }

    private void updateMapScaleControl() {
        if (mapScaleButton != null) {
            mapScaleButton.setMessage(Component.literal(getMapScaleLabel()));
            mapScaleButton.setTooltip(Tooltip.create(getMapScaleTooltip()));
        }
    }

    private String getMapScaleLabel() {
        return mapScaleFit ? "Fit" : formatMapScale(mapScale);
    }

    private void centerMapOnVillage() {
        if (village == null) return;
        mapCenterX = (village.getBox().minX() + village.getBox().maxX() + 1) / 2.0D;
        mapCenterZ = (village.getBox().minZ() + village.getBox().maxZ() + 1) / 2.0D;
    }

    private boolean isMouseOverMap(double mouseX, double mouseY) {
        int centerX = width / 2;
        int centerY = height / 2 + 8;
        return mouseX >= centerX - MAP_HALF_SIZE && mouseX < centerX + MAP_HALF_SIZE
                && mouseY >= centerY - MAP_HALF_SIZE && mouseY < centerY + MAP_HALF_SIZE;
    }

    static float snapMapScale(float currentScale, int direction) {
        if (direction > 0) {
            for (float preset : MAP_SCALE_PRESETS) {
                if (preset > currentScale) return preset;
            }
            return MAP_MAX_SCALE;
        }
        for (int i = MAP_SCALE_PRESETS.length - 1; i >= 0; i--) {
            if (MAP_SCALE_PRESETS[i] < currentScale) return MAP_SCALE_PRESETS[i];
        }
        return MAP_MIN_SCALE;
    }

    static float zoomMapScale(float currentScale, double scrollY) {
        double zoomed = currentScale * Math.pow(MAP_ZOOM_FACTOR, scrollY);
        return (float) Math.max(MAP_MIN_SCALE, Math.min(MAP_MAX_SCALE, zoomed));
    }

    static String formatMapScale(float scale) {
        DecimalFormat formatter = new DecimalFormat(
                "0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        formatter.setGroupingUsed(false);
        return formatter.format(scale) + ":1";
    }

    static final class MapPanState {
        private double startX;
        private double startY;
        private boolean active;
        private boolean panning;

        void begin(double x, double y) {
            startX = x;
            startY = y;
            active = true;
            panning = false;
        }

        boolean update(double x, double y) {
            if (!active) return false;
            if (!panning) {
                panning = Math.hypot(x - startX, y - startY) >= MAP_DRAG_THRESHOLD;
            }
            return panning;
        }

        boolean end() {
            boolean wasPanning = active && panning;
            active = false;
            panning = false;
            return wasPanning;
        }
    }

    private void changeSelectedFloor(int direction) {
        List<Integer> ordinals = floorOrdinals;
        List<Integer> floors = getFloorNavigationOrder(ordinals);
        if (ordinals.size() <= 1) {
            updateFloorControls();
            return;
        }

        int currentIndex = floors.indexOf(selectedFloorOrdinal);
        if (currentIndex < 0) {
            reconcileSelectedFloor(ordinals);
            updateFloorControls();
            return;
        }

        int nextIndex = currentIndex + direction;
        if (nextIndex < 0 || nextIndex >= floors.size()) {
            updateFloorControls();
            return;
        }
        selectFloor(floors.get(nextIndex));
    }

    private void selectFloor(Integer ordinal) {
        selectedFloorOrdinal = ordinal;
        updateFloorControls();
    }

    private void updateFloorControls() {
        List<Integer> ordinals = floorOrdinals;
        reconcileSelectedFloor(ordinals);
        if (floorPreviousButton == null || floorLabelButton == null || floorNextButton == null) {
            return;
        }

        List<Integer> floors = getFloorNavigationOrder(ordinals);
        boolean canChangeFloors = ordinals.size() > 1;
        Integer selectedFloor = selectedFloorOrdinal;
        int selectedIndex = floors.isEmpty() ? -1 : floors.indexOf(selectedFloor);
        Component tooltip = getFloorControlTooltip(ordinals);
        floorPreviousButton.active = canChangeFloors && selectedIndex > 0;
        floorNextButton.active = canChangeFloors && selectedIndex >= 0 && selectedIndex < floors.size() - 1;
        floorLabelButton.active = canChangeFloors && selectedFloor != null;
        // Keep floor-navigation help on the central label only; the arrow buttons are self-explanatory.
        floorLabelButton.setTooltip(Tooltip.create(tooltip));

        floorLabelButton.setMessage(getFloorLabel(selectedFloor));
    }

    private List<Integer> getFloorNavigationOrder(List<Integer> ordinals) {
        if (ordinals.isEmpty()) {
            return List.of();
        }

        List<Integer> floors = new ArrayList<>(ordinals.size() + 1);
        ordinals.stream().filter(ordinal -> ordinal < 0).forEach(floors::add);
        floors.add(null);
        ordinals.stream().filter(ordinal -> ordinal >= 0).forEach(floors::add);
        return Collections.unmodifiableList(floors);
    }

    private Component getFloorControlTooltip(List<Integer> ordinals) {
        if (ordinals.isEmpty()) {
            return Component.translatable("gui.blueprint.floor.disabled.noBuilding");
        }
        return ordinals.size() == 1
                ? Component.translatable("gui.blueprint.floor.disabled.single")
                : Component.translatable("gui.blueprint.floor.tooltip");
    }

    private Component getFloorLabel(Integer floorOrdinal) {
        if (floorOrdinal == null) {
            return Component.translatable("gui.blueprint.floor.all");
        }
        if (floorOrdinal == 0) {
            return Component.translatable("gui.blueprint.floor.ground");
        }
        return floorOrdinal > 0
                ? Component.translatable("gui.blueprint.floor.upper", floorOrdinal)
                : Component.translatable("gui.blueprint.floor.basement", -floorOrdinal);
    }

    private void reconcileSelectedFloor(List<Integer> ordinals) {
        if (ordinals.isEmpty()) {
            selectedFloorOrdinal = null;
        } else if (selectedFloorOrdinal != null
                && !ordinals.contains(selectedFloorOrdinal)) {
            int previous = selectedFloorOrdinal;
            selectedFloorOrdinal = ordinals.stream()
                    .min(Comparator.comparingInt(ordinal -> Math.abs(ordinal - previous)))
                    .orElse(null);
        }
    }

    private static Component toggleLabel(String key, boolean enabled) {
        MutableComponent label = Component.translatable(key);
        return enabled ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private static void updateToggleControl(ButtonWidget button, String key, boolean enabled) {
        if (button != null) button.setMessage(toggleLabel(key, enabled));
    }

    private void addInheritanceControl(SideControlColumn column) {
        Building room = getPlayerRoomScanPlan().currentRoom().orElse(null);
        if (room == null) return;
        InheritanceControlState state = inheritanceControlState(village, room);
        inheritanceButton = column.addTooltip(
                Component.translatable(state.labelKey()),
                Component.translatable(state.tooltipKey()), button -> {
            Building currentRoom = getPlayerRoomScanPlan().currentRoom().orElse(null);
            if (currentRoom == null) return;
            InheritanceControlState current = inheritanceControlState(village, currentRoom);
            Network.sendToServer(new ReportBuildingMessage(
                    ReportBuildingMessage.Action.SET_ROOM_INHERITANCE,
                    Boolean.toString(current.nextEnabled())));
        });
    }

    static InheritanceControlState inheritanceControlState(Village village, Building room) {
        boolean mainRoom = village != null && village.isMainRoom(room);
        boolean enabled = room != null && (mainRoom
                ? village.isBuildingInheritanceEnabled(room)
                : room.contributesToMain());
        boolean nextEnabled = !enabled;
        if (nextEnabled) {
            return new InheritanceControlState(
                    "gui.blueprint.roomInheritance.enable",
                    mainRoom
                            ? "gui.blueprint.roomInheritance.enableMain.tooltip"
                            : "gui.blueprint.roomInheritance.enable.tooltip",
                    true);
        }
        return mainRoom
                ? new InheritanceControlState(
                "gui.blueprint.roomInheritance.disable",
                "gui.blueprint.roomInheritance.disable.tooltip", false)
                : new InheritanceControlState(
                "gui.blueprint.roomInheritance.remove",
                "gui.blueprint.roomInheritance.remove.tooltip", false);
    }

    record InheritanceControlState(String labelKey, String tooltipKey, boolean nextEnabled) {
    }

    private void updateInheritanceControl(RoomScanPlan scanContext) {
        if (inheritanceButton == null) return;
        Building room = scanContext.currentRoom().orElse(null);
        inheritanceButton.active = room != null;
        if (room == null) return;
        InheritanceControlState state = inheritanceControlState(village, room);
        inheritanceButton.setMessage(Component.translatable(state.labelKey()));
        inheritanceButton.setTooltip(Tooltip.create(Component.translatable(state.tooltipKey())));
    }

    private final class SideControlColumn {
        private final int x;
        private int y;

        private SideControlColumn(int x, int y) {
            this.x = x;
            this.y = y;
        }

        private TooltipButtonWidget addTooltip(String key, Button.OnPress action) {
            return add(new TooltipButtonWidget(x, y, MAP_SIDE_CONTROL_WIDTH, 20, key, action));
        }

        private TooltipButtonWidget addTooltip(Component label,
                                               Component tooltip,
                                               Button.OnPress action) {
            return add(new TooltipButtonWidget(
                    x, y, MAP_SIDE_CONTROL_WIDTH, 20, label, tooltip, action));
        }

        private ButtonWidget addButton(Component label, Button.OnPress action) {
            return add(new ButtonWidget(x, y, MAP_SIDE_CONTROL_WIDTH, 20, label, action));
        }

        private <T extends Button> T add(T button) {
            addRenderableWidget(button);
            y += 22;
            return button;
        }
    }

    private void updateMainRoomControl(RoomScanPlan scanContext) {
        if (mainRoomButton == null) {
            return;
        }
        Optional<Building> room = village == null ? Optional.empty() : scanContext.currentRoom();
        Structure structure = room.flatMap(village::getStructureFor).orElse(null);
        mainRoomButton.active = room.isPresent() && structure != null
                && !roomTypeResolver.resolve(room.orElse(null)).isMainRoom();
        mainRoomButton.setMessage(Component.translatable("gui.blueprint.setMainRoom"));
        mainRoomButton.setTooltip(Tooltip.create(Component.translatable("gui.blueprint.setMainRoom.tooltip")));
    }

    private void renderTasks(GuiGraphics context) {
        if (rank == null) {
            return;
        }

        int y = height / 2 + 5;
        int x = width / 2 - 70;

        //tasks
        for (Task task : tasks.get(rank.promote())) {
            boolean completed = completedTasks.contains(task.getId());
            Component t = task.getTranslatable().withStyle(completed ? ChatFormatting.STRIKETHROUGH : ChatFormatting.RESET);
            context.drawString(font, t, x, y, completed ? 0xff88ff88 : 0xffff5555);
            y += 11;
        }
    }

    private void renderCatalog(GuiGraphics context) {
        final PoseStack matrices = context.pose();
        //title
        matrices.pushPose();
        matrices.scale(2.0f, 2.0f, 2.0f);
        context.drawCenteredString(font, Component.translatable("gui.blueprint.catalogFull"), width / 4, height / 4 - 52, 0xffffffff);
        matrices.popPose();

        //explanation
        context.drawCenteredString(font, Component.translatable("gui.blueprint.catalogHint").withStyle(ChatFormatting.GRAY), width / 2, height / 2 - 82, 0xffffffff);

        //building
        int x = width / 2 + 35;
        int y = height / 2 - 50;
        if (selectedBuilding != null) {
            Building currentRoom = selectedBuilding.grouped()
                    ? null : getPlayerRoomScanPlan().currentRoom().orElse(null);
            Map<ResourceLocation, Integer> requirementCounts = currentRoom == null
                    ? Map.of() : catalogRequirementCounts(
                    selectedBuilding, roomTypeResolver.resolve(currentRoom).classificationPoi());

            //name
            context.drawString(font, Component.translatable("buildingType." + selectedBuilding.name()), x, y, selectedBuilding.getColor());
            y += 12;

            //description
            List<Component> wrap = FlowingText.wrap(Component.translatable("buildingType." + selectedBuilding.name() + ".description").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC), 150);
            for (Component t : wrap) {
                context.drawString(font, t, x, y, 0xffffffff);
                y += 10;
            }
            y += 24;

            //required blocks
            for (Map.Entry<ResourceLocation, Integer> b : selectedBuilding.getGroups().entrySet()) {
                int current = requirementCounts.getOrDefault(b.getKey(), 0);
                Component count = currentRoom == null
                        ? Component.literal(b.getValue() + "x")
                        : Component.literal(current + "/" + b.getValue()).withStyle(
                        current >= b.getValue() ? ChatFormatting.GREEN
                                : current > 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
                int textY = y + 4;

                context.drawString(font, count, x, textY, 0xffffffff);

                int iconX = x + font.width(count) + 4;
                ItemStack icon = getBlockIcon(b.getKey());
                if (!icon.isEmpty()) {
                    context.renderItem(icon, iconX, y);
                    iconX += 18;
                }

                context.drawString(font, getBlockName(b.getKey()), iconX, textY, 0xffffffff);
                y += 18;
            }
        } else {
            //help
            List<Component> wrap = FlowingText.wrap(Component.translatable("gui.blueprint.buildingTypes").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC), 150);
            for (Component t : wrap) {
                context.drawString(font, t, x, y, 0xffffffff);
                y += 10;
            }
        }
    }

    static Map<ResourceLocation, Integer> catalogRequirementCounts(
            BuildingType type, Map<ResourceLocation, List<BlockPos>> roomBlocks) {
        if (type == null || roomBlocks == null || roomBlocks.isEmpty()) return Map.of();
        Map<ResourceLocation, Integer> counts = new HashMap<>();
        type.getGroups(roomBlocks).forEach((group, positions) -> counts.put(group, positions.size()));
        return Map.copyOf(counts);
    }

    private void renderVillagers(GuiGraphics context) {
        int maxPages = (int) Math.ceil(village.getPopulation() / 9.0);
        buttonPage.setMessage(Component.literal((pageNumber + 1) + "/" + maxPages));

        List<Map.Entry<UUID, String>> villager = village.getResidentNames().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).toList();

        selectedVillager = null;
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index < villager.size()) {
                int y = height / 2 - 51 + i * 11;
                boolean hover = isMouseWithin(width / 2 - 50, y - 1, 100, 11);
                context.drawCenteredString(font, Component.literal(villager.get(index).getValue()), width / 2, y, hover ? 0xFFD7D784 : 0xFFFFFFFF);
                if (hover) {
                    selectedVillager = villager.get(index).getKey();
                }
            } else {
                break;
            }
        }
    }

    private void renderRules(GuiGraphics context) {
        buttonTaxes[0].setMessage(Component.literal((int) (village.getTaxes() * 100) + "%"));
        buttonMarriage[0].setMessage(Component.literal((int) (village.getMarriageThreshold() * 100) + "%"));
        buttonBirths[0].setMessage(Component.literal((int) (village.getPopulationThreshold() * 100) + "%"));

        //taxes
        context.drawCenteredString(font, Component.translatable("gui.blueprint.taxes"), width / 2, height / 2 + POSITION_TAXES, 0xffffffff);
        if (!rank.isAtLeast(Rank.MERCHANT)) {
            context.drawCenteredString(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_TAXES + 15, 0xffffffff);
            toggleButtons(buttonTaxes, false);
        } else {
            toggleButtons(buttonTaxes, true);
        }

        //births
        context.drawCenteredString(font, Component.translatable("gui.blueprint.birth"), width / 2, height / 2 + POSITION_BIRTH, 0xffffffff);
        if (!rank.isAtLeast(Rank.NOBLE)) {
            context.drawCenteredString(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_BIRTH + 15, 0xffffffff);
            toggleButtons(buttonBirths, false);
        } else {
            toggleButtons(buttonBirths, true);
        }

        //marriages
        context.drawCenteredString(font, Component.translatable("gui.blueprint.marriage"), width / 2, height / 2 + POSITION_MARRIAGE, 0xffffffff);
        if (!rank.isAtLeast(Rank.MAYOR)) {
            context.drawCenteredString(font, Component.translatable("gui.blueprint.rankTooLow"), width / 2, height / 2 + POSITION_MARRIAGE + 15, 0xffffffff);
            toggleButtons(buttonMarriage, false);
        } else {
            toggleButtons(buttonMarriage, true);
        }
    }

    private ItemStack getBlockIcon(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.BLOCK.get(id));
        }

        TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
        return BuiltInRegistries.BLOCK.getTag(tag)
                .flatMap(blocks -> blocks.stream().findFirst())
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    private Component getBlockName(ResourceLocation id) {
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            return Component.translatable(BuiltInRegistries.BLOCK.get(id).getDescriptionId());
        } else {
            return Component.translatable("tag.block." + id.getNamespace() + "." + id.getPath());
        }
    }

    private void toggleButtons(ButtonWidget[] buttons, boolean active) {
        for (ButtonWidget b : buttons) {
            b.active = active;
            b.visible = active;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOverMap(mouseX, mouseY) && ("map".equals(page) || "advanced".equals(page))) {
            mapPanState.begin(mouseX, mouseY);
            return true;
        }

        if (button == 1 && mapScaleButton != null && mapScaleButton.visible && mapScaleButton.active
                && mapScaleButton.isMouseOver(mouseX, mouseY)) {
            cycleMapScale(-1);
            return true;
        }

        if (page.equals("villagers") && selectedVillager != null) {
            assert minecraft != null;
            minecraft.setScreen(new FamilyTreeScreen(selectedVillager));
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && mapPanState.update(mouseX, mouseY)) {
            float scale = getMapScale();
            mapCenterX -= dragX / scale;
            mapCenterZ -= dragY / scale;
            playerCentered = false;
            updatePlayerCenteredControl();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && mapPanState.end()) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0.0D && isMouseOverMap(mouseX, mouseY) && ("map".equals(page) || "advanced".equals(page))) {
            float currentScale = getMapScale();
            float newScale = zoomMapScale(currentScale, scrollY);
            BlueprintMapViewport currentViewport = BlueprintMapViewport.create(
                    width / 2, height / 2 + 8, MAP_HALF_SIZE,
                    mapCenterX, mapCenterZ, currentScale);
            BlueprintMapViewport zoomedViewport = currentViewport.zoomedAround(mouseX, mouseY, newScale);

            mapScale = newScale;
            mapScaleFit = false;
            mapCenterX = zoomedViewport.mapCenterX();
            mapCenterZ = zoomedViewport.mapCenterZ();
            playerCentered = false;
            updatePlayerCenteredControl();
            updateMapScaleControl();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public void removed() {
        mapRenderer.close();
        super.removed();
    }

    public void setVillage(Village village) {
        this.village = village;
        lastRoomScanPosition = null;
        cachedRoomScanPlan = null;
        if (village == null) {
            mapCenterVillageId = null;
        } else if (!Objects.equals(mapCenterVillageId, village.getId())) {
            mapCenterVillageId = village.getId();
            centerMapOnVillage();
        }
        TreeSet<Integer> availableFloors = new TreeSet<>();
        if (village != null) {
            for (Structure s : village.getStructures().values()) {
                for (StructureFloor f : s.getFloors()) {
                    availableFloors.add(f.floorNumber());
                }
            }
        }
        this.floorOrdinals = List.copyOf(availableFloors);
        this.structureCount = village == null ? 0 : village.getStructureCount();
        this.roomTypeResolver = RoomTypeResolver.create(village);
        this.tooltipFactory = BlueprintTooltipFactory.create(village, roomTypeResolver);
        this.mapGeometry = BlueprintMapGeometry.build(village, roomTypeResolver);
        RoomScanPlan scanContext = getPlayerRoomScanPlan();
        if (selectPlayerFloorOnNextVillageResponse
                && scanContext.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            selectPlayerFloor(scanContext);
        }
        selectPlayerFloorOnNextVillageResponse = false;
        updateMapControls(scanContext);

        if (village == null) {
            setPage("empty");
        } else if (page.equals("waiting") || page.equals("empty")) {
            setPage("map");
        }
    }

    private void selectPlayerFloor(RoomScanPlan scanContext) {
        scanContext.currentRoom()
                .ifPresent(room -> {
                    int ordinal = room.getFloorNumber(village);
                    selectedFloorOrdinal = ordinal;
                });
    }

    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
    }
}
