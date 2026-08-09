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
    private static Integer rememberedFloorOrdinal;
    private static MapScaleMode rememberedMapScaleMode = MapScaleMode.FIT;
    private static boolean rememberedPlayerCentered;
    private static boolean rememberedShowPlayerHead = true;
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
    private TooltipButtonWidget mainRoomButton;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget attachmentScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
    private MapScaleMode mapScaleMode = rememberedMapScaleMode;
    private boolean playerCentered = rememberedPlayerCentered;
    private boolean showPlayerHead = rememberedShowPlayerHead;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private boolean showBuildingIcons = true;
    private boolean showTerrain = true;
    private List<Integer> floorOrdinals = List.of();
    private int structureCount;
    private RoomTypeResolver roomTypeResolver = RoomTypeResolver.create(null);
    private BlueprintTooltipFactory tooltipFactory = BlueprintTooltipFactory.empty();
    private BlueprintMapGeometry mapGeometry = BlueprintMapGeometry.empty();
    private final BlueprintMapRenderer mapRenderer = new BlueprintMapRenderer();
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
                        Component.literal(mapScaleMode.label), b -> cycleMapScale(1), getMapScaleTooltip()));

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
                    removeRoomButton = column.addTooltip("gui.blueprint.removeRoom", b ->
                            Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE_ROOM)));
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
        updateRemoveRoomControl(scanContext);
        updateMainRoomControl(scanContext);
    }

    private void updateStructureScanControl(RoomScanPlan scanContext) {
        if (structureScanButton == null) return;

        boolean attachment = scanContext.mode().isAttachment();
        Village.RoomScanMode primaryMode = attachment
                ? Village.RoomScanMode.ADD_BUILDING : scanContext.mode();
        boolean roomRegistered = scanContext.mode() == Village.RoomScanMode.UPDATE_ROOM;
        boolean insideBuilding = roomRegistered || scanContext.mode() == Village.RoomScanMode.ADD_ROOM;
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
            removeRoomButton.visible = roomRegistered;
            removeRoomButton.setY(y);
            if (removeRoomButton.visible) y += 22;
        }

        if (removeBuildingButton != null) {
            removeBuildingButton.visible = insideBuilding;
            removeBuildingButton.active = insideBuilding;
            removeBuildingButton.setY(y);
        }
    }

    private void updateRemoveRoomControl(RoomScanPlan scanContext) {
        if (removeRoomButton == null) {
            return;
        }

        boolean onMainRoom = village != null
                && scanContext.functionalRoom()
                .filter(village::isMainRoom)
                .isPresent();
        removeRoomButton.active = removeRoomButton.visible && !onMainRoom;
        removeRoomButton.setTooltip(Tooltip.create(Component.translatable(onMainRoom
                ? "gui.blueprint.removeRoom.disabled.mainRoom"
                : "gui.blueprint.removeRoom.tooltip")));
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
        double villageCenterX = (village.getBox().minX() + village.getBox().maxX() + 1) / 2.0D;
        double villageCenterZ = (village.getBox().minZ() + village.getBox().maxZ() + 1) / 2.0D;
        double requestedMapCenterX = playerCentered && player != null ? playerRenderX : villageCenterX;
        double requestedMapCenterZ = playerCentered && player != null ? playerRenderZ : villageCenterZ;

        BlueprintMapViewport viewport = BlueprintMapViewport.create(
                centerX,
                centerY,
                MAP_HALF_SIZE,
                requestedMapCenterX,
                requestedMapCenterZ,
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

        List<BlueprintMapRenderer.HoverTarget> hoverTargets = new ArrayList<>(renderResult.hoverTargets());
        hoverTargets.sort(Comparator.comparingInt(BlueprintMapRenderer.HoverTarget::anchorY).reversed()
                .thenComparing(Comparator.comparingInt(
                        BlueprintMapRenderer.HoverTarget::logicalBuildingId).reversed()));

        Map<Integer, BlueprintMapRenderer.HoverTarget> uniqueTargets = new LinkedHashMap<>();
        for (BlueprintMapRenderer.HoverTarget target : hoverTargets) {
            uniqueTargets.putIfAbsent(target.logicalBuildingId(), target);
        }
        if (uniqueTargets.isEmpty()) return;

        BlueprintMapRenderer.HoverTarget preferred = uniqueTargets.get(renderResult.hoveredLogicalBuildingId());
        BlueprintMapRenderer.HoverTarget active = preferred != null
                ? preferred : uniqueTargets.values().iterator().next();

        List<Component> tooltip = new ArrayList<>(tooltipFactory.tooltip(
                active.building(), active.floorOrdinal(), active.structure()));
        List<BlueprintMapRenderer.HoverTarget> alternatives = uniqueTargets.values().stream()
                .filter(target -> target.logicalBuildingId() != active.logicalBuildingId())
                .toList();
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
        rememberedPlayerCentered = playerCentered;
        updatePlayerCenteredControl();
    }

    private void togglePlayerHead() {
        showPlayerHead = !showPlayerHead;
        rememberedShowPlayerHead = showPlayerHead;
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
        return switch (mapScaleMode) {
            case FIT -> {
                int horizontalSpan = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan());
                int usablePixels = (MAP_HALF_SIZE - MAP_INNER_MARGIN) * 2;
                yield Math.min((float) usablePixels / Math.max(1, horizontalSpan), MAP_MAX_FIT_SCALE);
            }
            case HALF_TO_ONE -> 0.5F;
            case ONE_TO_ONE -> 1.0F;
            case TWO_TO_ONE -> 2.0F;
            case THREE_TO_ONE -> 3.0F;
            case FOUR_TO_ONE -> 4.0F;
        };
    }

    private void cycleMapScale(int direction) {
        mapScaleMode = mapScaleMode.step(direction);
        rememberedMapScaleMode = mapScaleMode;
        updateMapScaleControl();
    }

    private Component getMapScaleTooltip() {
        return mapScaleMode.tooltipKey == null
                ? Component.literal("Map scale: " + mapScaleMode.label)
                : Component.translatable(mapScaleMode.tooltipKey);
    }

    private void updateMapScaleControl() {
        if (mapScaleButton != null) {
            mapScaleButton.setMessage(Component.literal(mapScaleMode.label));
            mapScaleButton.setTooltip(Tooltip.create(getMapScaleTooltip()));
        }
    }

    private enum MapScaleMode {
        FIT("Fit", "gui.blueprint.mapScale.fit.tooltip"),
        HALF_TO_ONE("0.5:1", null),
        ONE_TO_ONE("1:1", "gui.blueprint.mapScale.oneToOne.tooltip"),
        TWO_TO_ONE("2:1", "gui.blueprint.mapScale.twoToOne.tooltip"),
        THREE_TO_ONE("3:1", "gui.blueprint.mapScale.threeToOne.tooltip"),
        FOUR_TO_ONE("4:1", "gui.blueprint.mapScale.fourToOne.tooltip");

        private final String label;
        private final String tooltipKey;

        MapScaleMode(String label, String tooltipKey) {
            this.label = label;
            this.tooltipKey = tooltipKey;
        }


        MapScaleMode step(int direction) {
            MapScaleMode[] values = values();
            return values[Math.floorMod(ordinal() + direction, values.length)];
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
        rememberedFloorOrdinal = ordinal;
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
        int selectedIndex = floors.isEmpty() ? -1 : floors.indexOf(selectedFloorOrdinal);
        Component tooltip = getFloorControlTooltip(ordinals);
        floorPreviousButton.active = canChangeFloors && selectedIndex > 0;
        floorNextButton.active = canChangeFloors && selectedIndex >= 0 && selectedIndex < floors.size() - 1;
        floorLabelButton.active = canChangeFloors && selectedFloorOrdinal != null;
        // Keep floor-navigation help on the central label only; the arrow buttons are self-explanatory.
        floorLabelButton.setTooltip(Tooltip.create(tooltip));

        floorLabelButton.setMessage(getFloorLabel(selectedFloorOrdinal));
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
            rememberedFloorOrdinal = null;
        } else if (selectedFloorOrdinal != null && !ordinals.contains(selectedFloorOrdinal)) {
            int previous = selectedFloorOrdinal;
            selectedFloorOrdinal = ordinals.stream()
                    .min(Comparator.comparingInt(ordinal -> Math.abs(ordinal - previous)))
                    .orElse(null);
            rememberedFloorOrdinal = selectedFloorOrdinal;
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
        Building room = getPlayerRoomScanPlan().functionalRoom().orElse(null);
        if (room == null) return;

        boolean mainRoom = roomTypeResolver.resolve(room).isMainRoom();
        boolean enable = !room.isInheritanceEnabled();
        String labelKey;
        String tooltipKey;
        if (enable) {
            labelKey = "gui.blueprint.roomInheritance.enable";
            tooltipKey = mainRoom
                    ? "gui.blueprint.roomInheritance.enableMain.tooltip"
                    : "gui.blueprint.roomInheritance.enable.tooltip";
        } else if (mainRoom) {
            labelKey = "gui.blueprint.roomInheritance.disable";
            tooltipKey = "gui.blueprint.roomInheritance.disable.tooltip";
        } else {
            labelKey = "gui.blueprint.roomInheritance.remove";
            tooltipKey = "gui.blueprint.roomInheritance.remove.tooltip";
        }

        column.addTooltip(Component.translatable(labelKey), Component.translatable(tooltipKey), button -> {
            room.setInheritanceEnabled(enable);
            setVillage(village);
            Network.sendToServer(new ReportBuildingMessage(
                    ReportBuildingMessage.Action.SET_ROOM_INHERITANCE, Boolean.toString(enable)));
            setPage(page);
        });
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
        Optional<Building> room = village == null ? Optional.empty() : scanContext.functionalRoom();
        Structure structure = room.flatMap(village::getStructureFor).orElse(null);
        boolean changeToAutomatic = structure != null && !village.isMainRoomAutomatic(structure);
        mainRoomButton.active = room.isPresent() && structure != null;
        mainRoomButton.setMessage(Component.translatable(changeToAutomatic
                ? "gui.blueprint.useAutomaticMainRoom"
                : "gui.blueprint.setMainRoom"));
        mainRoomButton.setTooltip(Tooltip.create(Component.translatable(changeToAutomatic
                ? "gui.blueprint.useAutomaticMainRoom.tooltip"
                : "gui.blueprint.setMainRoom.tooltip")));
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
                Component count = Component.literal(b.getValue() + "x");
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
        scanContext.functionalRoom()
                .ifPresent(room -> {
                    int ordinal = room.getFloorNumber(village);
                    selectedFloorOrdinal = ordinal;
                    rememberedFloorOrdinal = ordinal;
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
