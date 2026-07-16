package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.widget.LegacyImageButton;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.client.render.JourneyMapIconBridge;
import net.conczin.mca.entity.VillagerLike;
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
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.util.compat.ButtonWidget;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

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
    private static final int TERRAIN_TARGET_CELL_PIXELS = 2;
    private static final int TERRAIN_BACKGROUND_COLOR = 0xd0181c22;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int TERRAIN_FALLBACK_COLOR = 0x6f766f;
    private static final int TERRAIN_CONTOUR_COLOR = 0x66000000;
    private static final float TERRAIN_BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float TERRAIN_ELEVATION_BRIGHTNESS_RANGE = 0.12f;
    private static final float TERRAIN_SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float TERRAIN_MIN_BRIGHTNESS = 0.58f;
    private static final float TERRAIN_MAX_BRIGHTNESS = 1.15f;
    private static final int ROOM_INNER_PADDING = 1;
    private static final int PLAYER_MARKER_SIZE = 6;
    private static final int PLAYER_MARKER_EDGE_PADDING = 2;
    private static final int ROOM_SHADOW_COLOR = 0x50000000;
    private static final int ROOM_FILL_ALPHA_ALL_FLOORS = 0x18;
    private static final int ROOM_FILL_ALPHA_SELECTED_FLOOR = 0x38;
    private static final int ROOM_FILL_ALPHA_HOVERED = 0x58;
    private static final int TOOLTIP_FLOOR_BASEMENT_COLOR = 0x9b8cff;
    private static final int TOOLTIP_FLOOR_GROUND_COLOR = 0xf2c94c;
    private static final int TOOLTIP_FLOOR_UPPER_COLOR = 0x6fd6a5;
    private static Integer rememberedFloorOrdinal;
    private static MapScaleMode rememberedMapScaleMode = MapScaleMode.FIT;
    private static boolean rememberedPlayerCentered;
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
    private TooltipButtonWidget groundAnchorButton;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private ButtonWidget advancedButton;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
    private MapScaleMode mapScaleMode = rememberedMapScaleMode;
    private boolean playerCentered = rememberedPlayerCentered;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private boolean showBuildingIcons = true;
    private boolean showTerrain = true;
    private BlueprintFloorLayout floorLayout = BlueprintFloorLayout.empty();
    private BuildingType selectedBuilding;
    private UUID selectedVillager;

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

    protected void drawBuildingIcon(GuiGraphics context, ResourceLocation texture, int x, int y, int u, int v) {
        WidgetUtils.drawBuildingIcon(context, texture, x, y, u, v);
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
        groundAnchorButton = null;
        structureScanButton = null;
        removeRoomButton = null;
        removeBuildingButton = null;
        advancedButton = null;

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
                // A room cannot exist before its root building.
                bx = width / 2 - 48;
                by = height / 2;
                addRenderableWidget(new TooltipButtonWidget(bx, by + 5, 96, 20, "gui.blueprint.addBuilding", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                }));
                break;
            case "refresh":
                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
                assert minecraft != null;
                assert minecraft.player != null;
                minecraft.player.displayClientMessage(Component.translatable("blueprint.refreshed"), true);
                setPage("map");
                break;
            case "map", "advanced": {
                bx = width / 2 + 180 - 64 - 16;
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
                        getBuildingIconsLabel(), b -> {
                    showBuildingIcons = !showBuildingIcons;
                    updateBuildingIconsControl();
                }, Component.translatable("gui.blueprint.buildingIcons")));
                int terrainControlX = floorControlX + MAP_ICONS_BUTTON_WIDTH + MAP_CONTROL_GAP;
                terrainButton = addRenderableWidget(new ButtonWidget(
                        terrainControlX, mapControlY, MAP_TERRAIN_BUTTON_WIDTH, 20,
                        getTerrainLabel(), b -> {
                    showTerrain = !showTerrain;
                    updateTerrainControl();
                }, Component.translatable("gui.blueprint.terrain.tooltip")));
                int scaleControlX = terrainControlX + MAP_TERRAIN_BUTTON_WIDTH + MAP_CONTROL_GAP;
                mapScaleButton = addRenderableWidget(new ButtonWidget(
                        scaleControlX, mapControlY, MAP_SCALE_BUTTON_WIDTH, 20,
                        getMapScaleLabel(), b -> cycleMapScale(), getMapScaleTooltip()));

                playerCenteredButton = addRenderableWidget(new ButtonWidget(
                        bx, floorControlY, 96, 20,
                        getPlayerCenteredLabel(), b -> togglePlayerCentered(),
                        Component.translatable("gui.blueprint.playerCentered.tooltip")));

                if ("advanced".equals(page)) {
                    // Advanced is a map sub-view: expose settlement-level settings while
                    // preserving the shared map display controls above.
                    by = height / 2 - 56;
                    MutableComponent text = Component.translatable("gui.blueprint.autoScan");
                    if (village.isAutoScan()) {
                        text.withStyle(ChatFormatting.GREEN);
                    } else {
                        text.withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.STRIKETHROUGH);
                    }
                    addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, text,
                            Component.translatable("gui.blueprint.autoScan.tooltip"), b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.AUTO_SCAN));
                        village.toggleAutoScan();
                        setPage(page);
                    }));
                    by += 22;

                    addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20,
                            "gui.blueprint.restrictAccess", b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
                    }));
                    by += 22;

                    groundAnchorButton = addRenderableWidget(new TooltipButtonWidget(
                            bx, by, 96, 20, "gui.blueprint.setGroundAnchor", b -> {
                        selectPlayerFloorOnNextVillageResponse = true;
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.SET_GROUND_ANCHOR));
                    }));
                    updateGroundAnchorControl(getPlayerStructuralLookup());
                    by += 22;

                    if (isVillage) {
                        addRenderableWidget(new ButtonWidget(bx, by, 96, 20,
                                Component.translatable("gui.blueprint.renameVillage"), b -> setPage("rename")));
                    }

                    addRenderableWidget(new ButtonWidget(
                            bx, floorControlY + 22, 96, 20,
                            Component.translatable("gui.back"), b -> setPage("map")));
                } else {
                    // A grouped POI such as the town bell keeps the settlement alive, but
                    // rooms still need a complete structural root to attach to.
                    by = height / 2 - 56 + 22 * 3;
                    structureScanButton = addRenderableWidget(new TooltipButtonWidget(
                            bx, by, 96, 20,
                            getStructureScanTranslationKey(getPlayerStructuralLookup().position()),
                            b -> requestStructureScan()));
                    by += 22;

                    removeRoomButton = addRenderableWidget(new TooltipButtonWidget(
                            bx, by, 96, 20, "gui.blueprint.removeRoom", b -> {
                        MCA.LOGGER.debug("[BuildingRemove] stage=client-click action=REMOVE_ROOM");
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE_ROOM));
                    }));
                    by += 22;

                    removeBuildingButton = addRenderableWidget(new ButtonWidget(
                            bx, by, 96, 20, Component.translatable("gui.blueprint.removeBuilding"), b -> {
                        Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE));
                    }));

                    advancedButton = addRenderableWidget(new ButtonWidget(
                            bx, floorControlY + 22, 96, 20,
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
            updateFloorControls();
            Village.StructuralLookup structuralLookup = getPlayerStructuralLookup();
            updateStructureScanControl(structuralLookup);
            updateRemoveRoomControl(structuralLookup);
            updateGroundAnchorControl(structuralLookup);
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
                renderMap(context);
            }
            case "advanced" -> {
                renderName(context);
                renderMap(context);
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

    private static ReportBuildingMessage.Action getStructureScanAction(Village.StructuralPosition structuralPosition) {
        return switch (structuralPosition) {
            case OUTSIDE -> ReportBuildingMessage.Action.ADD;
            case ATTACHABLE_ROOM -> ReportBuildingMessage.Action.ADD_ROOM;
            case REGISTERED_ROOM -> ReportBuildingMessage.Action.UPDATE_ROOM;
        };
    }

    private void requestStructureScan() {
        Village.StructuralLookup structuralLookup = getPlayerStructuralLookup();
        ReportBuildingMessage.Action action = getStructureScanAction(structuralLookup.position());
        BlockPos playerPos = minecraft != null && minecraft.player != null
                ? minecraft.player.blockPosition()
                : null;
        MCA.LOGGER.info("[FloorRoomDebug] side=client stage=request-structure-scan pos={} lookup={} lookupBuilding={} action={} selectedFloor={} availableFloors={} pendingFloorSelectBefore={}",
                playerPos, structuralLookup.position(), describeBuilding(structuralLookup.building().orElse(null)),
                action, selectedFloorOrdinal, floorLayout.ordinals(), selectPlayerFloorOnNextVillageResponse);
        selectPlayerFloorOnNextVillageResponse = action == ReportBuildingMessage.Action.ADD_ROOM
                || action == ReportBuildingMessage.Action.UPDATE_ROOM;
        Network.sendToServer(new ReportBuildingMessage(action));
    }

    void cancelPendingFloorSelection() {
        selectPlayerFloorOnNextVillageResponse = false;
    }

    private Village.StructuralLookup getPlayerStructuralLookup() {
        if (village == null || minecraft == null || minecraft.player == null) {
            return new Village.StructuralLookup(Village.StructuralPosition.OUTSIDE, Optional.empty());
        }

        return village.getStructuralLookup(minecraft.player.blockPosition());
    }

    private static String getStructureScanTranslationKey(Village.StructuralPosition structuralPosition) {
        return switch (structuralPosition) {
            case OUTSIDE -> "gui.blueprint.addBuilding";
            case ATTACHABLE_ROOM -> "gui.blueprint.addRoom";
            case REGISTERED_ROOM -> "gui.blueprint.updateRoom";
        };
    }

    private void updateStructureScanControl(Village.StructuralLookup structuralLookup) {
        if (structureScanButton == null) {
            return;
        }

        Village.StructuralPosition structuralPosition = structuralLookup.position();
        boolean insideBuilding = structuralPosition != Village.StructuralPosition.OUTSIDE;
        boolean roomRegistered = structuralPosition == Village.StructuralPosition.REGISTERED_ROOM;
        int y = height / 2 - 56 + 22 * 3;

        structureScanButton.setMessage(getStructureScanTranslationKey(structuralPosition));
        structureScanButton.active = true;
        structureScanButton.setY(y);
        y += 22;

        if (removeRoomButton != null) {
            removeRoomButton.visible = roomRegistered;
            removeRoomButton.setY(y);
            if (removeRoomButton.visible) {
                y += 22;
            }
        }

        if (removeBuildingButton != null) {
            removeBuildingButton.visible = insideBuilding;
            removeBuildingButton.active = insideBuilding;
            removeBuildingButton.setY(y);
            if (insideBuilding) {
                y += 22;
            }
        }

    }

    private void updateRemoveRoomControl(Village.StructuralLookup structuralLookup) {
        if (removeRoomButton == null) {
            return;
        }

        boolean onGroundFloor = village != null
                && structuralLookup.functionalRoom()
                .filter(village::isStructuralGroundFloor)
                .isPresent();
        removeRoomButton.active = removeRoomButton.visible && !onGroundFloor;
        removeRoomButton.setTooltip(Tooltip.create(Component.translatable(onGroundFloor
                ? "gui.blueprint.removeRoom.disabled.groundFloor"
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
        context.drawString(font, Component.translatable("gui.blueprint.buildings", village.getStructureCount()), x, y + 22, 0xffffffff);
        context.drawString(font, Component.translatable("gui.blueprint.population", village.getPopulation(), village.getMaxPopulation()), x, y + 33, 0xffffffff);
    }

    private void renderMap(GuiGraphics context) {
        final PoseStack matrices = context.pose();
        int centerX = width / 2;
        int centerY = height / 2 + 8;
        int left = centerX - MAP_HALF_SIZE;
        int top = centerY - MAP_HALF_SIZE;
        int right = centerX + MAP_HALF_SIZE;
        int bottom = centerY + MAP_HALF_SIZE;
        Integer selectedFloor = selectedFloorOrdinal;
        if (showTerrain) {
            // Give the topographic cells a stable backing instead of blending them into the
            // blurred 3D world behind the screen. Keep one pixel free for the map border.
            context.fill(left + 1, top + 1, right - 1, bottom - 1, TERRAIN_BACKGROUND_COLOR);
        }
        WidgetUtils.drawRectangle(context, left, top, right, bottom, 0xffffff88);

        //hint
        if (!village.isAutoScan() && village.getStructureCount() <= 1) {
            int hintY = floorLayout.ordinals().size() > 1 ? height / 2 + 134 : height / 2 + 90;
            context.drawCenteredString(font, Component.translatable("gui.blueprint.autoScanDisabled"), width / 2, hintY, 0xaaffffff);
        }

        LocalPlayer player = minecraft == null ? null : minecraft.player;
        double villageCenterX = (village.getBox().minX() + village.getBox().maxX() + 1) / 2.0D;
        double villageCenterZ = (village.getBox().minZ() + village.getBox().maxZ() + 1) / 2.0D;
        double mapCenterX = playerCentered && player != null ? player.getX() : villageCenterX;
        double mapCenterZ = playerCentered && player != null ? player.getZ() : villageCenterZ;
        float scale = getMapScale();
        int mouseLocalX = (int) Math.floor((mouseX - centerX) / scale + mapCenterX);
        int mouseLocalZ = (int) Math.floor((mouseY - centerY) / scale + mapCenterZ);

        Map<Integer, Building> hoverBuildings = new LinkedHashMap<>();
        List<Building> iconBuildings = new ArrayList<>();

        context.enableScissor(left + 1, top + 1, right - 1, bottom - 1);

        matrices.pushPose();
        matrices.translate(centerX, centerY, 0.0D);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-mapCenterX, -mapCenterZ, 0.0D);

        if (showTerrain) {
            renderTerrain(context, mapCenterX, mapCenterZ, scale);
        }

        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete() || !floorLayout.isBuildingVisible(building, selectedFloor)) {
                continue;
            }

            BuildingType buildingType = building.getBuildingType();
            if (buildingType.isIcon()) {
                BlockPos center = building.getCenter();
                iconBuildings.add(building);

                int hoverMargin = 6;
                if (center.distSqr(new Vec3i(mouseLocalX, center.getY(), mouseLocalZ)) < hoverMargin * hoverMargin) {
                    addHoveredBuilding(hoverBuildings, building, selectedFloor);
                }
                continue;
            }

            List<BlueprintFloorLayout.RegionBounds> renderRegions = floorLayout.regionsFor(building, selectedFloor);
            int hoverMargin = 1;
            boolean hovering = renderRegions.stream().anyMatch(region ->
                    mouseLocalX >= region.minX() - hoverMargin && mouseLocalX <= region.maxX() + hoverMargin
                            && mouseLocalZ >= region.minZ() - hoverMargin && mouseLocalZ <= region.maxZ() + hoverMargin);

            for (BlueprintFloorLayout.RegionBounds region : renderRegions) {
                renderRoomRegion(context, region, buildingType.getColor(), selectedFloor != null, hovering);
            }

            if (buildingType.visible() && buildingType.hasIcon()) {
                iconBuildings.add(building);
            }
            if (hovering) {
                addHoveredBuilding(hoverBuildings, building, selectedFloor);
            }
        }

        // Icons deliberately render last so a floor region can never cover them.
        if (showBuildingIcons) {
            Set<Integer> renderedStructureIcons = new HashSet<>();
            for (Building building : iconBuildings) {
                BuildingType buildingType = building.getBuildingType();
                if (buildingType.isIcon()) {
                    BlockPos center = building.getCenter();
                    drawBuildingIcon(context, ICON_TEXTURES,
                            center.getX(), center.getZ(), buildingType.iconU(), buildingType.iconV());
                } else if (selectedFloor != null
                        || renderedStructureIcons.add(building.getEffectiveStructureId())) {
                    BlockPos iconPosition = getBuildingIconPosition(building, selectedFloor);
                    drawBuildingIcon(context, ICON_TEXTURES,
                            iconPosition.getX(), iconPosition.getZ(), buildingType.iconU(), buildingType.iconV());
                }
            }
        }

        matrices.popPose();
        context.disableScissor();

        // The player is global map context, not part of a floor, and stays above every icon.
        // Use the player's actual skin face and clamp it to the map edge when the player
        // is outside the current viewport instead of letting scissoring hide it.
        renderPlayerMarker(context, player, centerX, centerY, left, top, right, bottom,
                mapCenterX, mapCenterZ, scale);
        renderPlayerCenteredButtonIcon(context);

        //sort vertically
        List<Building> sortedHoverBuildings = new ArrayList<>(hoverBuildings.values());
        sortedHoverBuildings.sort((a, b) -> b.getCenter().getY() - a.getCenter().getY());

        //get tooltips
        List<List<Component>> tooltips = new LinkedList<>();
        for (Building b : sortedHoverBuildings) {
            tooltips.add(getBuildingTooltip(b, selectedFloor));
        }

        //get height
        int h = 0;
        for (List<Component> b : tooltips) {
            h += getTooltipHeight(b) + 9;
        }

        //render
        int py = mouseY - h / 2 + 12;
        for (List<Component> b : tooltips) {
            context.renderComponentTooltip(font, b, mouseX, py);
            py += getTooltipHeight(b) + 9;
        }
    }

    private void renderPlayerMarker(GuiGraphics context,
                                    LocalPlayer player,
                                    int centerX,
                                    int centerY,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom,
                                    double mapCenterX,
                                    double mapCenterZ,
                                    float scale) {
        if (player == null) {
            return;
        }

        double playerScreenX = centerX + (player.getX() - mapCenterX) * scale;
        double playerScreenY = centerY + (player.getZ() - mapCenterZ) * scale;
        ScreenPoint markerCenter = clampPlayerMarkerToMap(
                playerScreenX, playerScreenY,
                centerX, centerY, left, top, right, bottom
        );
        int markerX = markerCenter.x() - PLAYER_MARKER_SIZE / 2;
        int markerY = markerCenter.y() - PLAYER_MARKER_SIZE / 2;

        // Dark backing keeps pale and transparent skins readable over bright room colors.
        context.fill(markerX - 1, markerY - 1,
                markerX + PLAYER_MARKER_SIZE + 1, markerY + PLAYER_MARKER_SIZE + 1,
                0xc0000000);

        // Prefer the exact dynamic MCA face used by the JourneyMap compatibility bridge
        // whenever this player is actually using an MCA-rendered player model. Fall back
        // to the vanilla skin face while player data is unavailable or the vanilla model is selected.
        ResourceLocation mcaFace = MCAClient.getPlayerData(player.getUUID())
                .filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA)
                .map(JourneyMapIconBridge::getOrCreateFaceIcon)
                .orElse(null);
        if (mcaFace != null) {
            // JourneyMapIconBridge returns a complete 24x24 cropped face texture.
            // Sample the full source image and scale it down to the map marker size.
            context.blit(mcaFace, markerX, markerY,
                    PLAYER_MARKER_SIZE, PLAYER_MARKER_SIZE,
                    0.0F, 0.0F, 24, 24, 24, 24);
        } else {
            PlayerFaceRenderer.draw(context, player.getSkin(), markerX, markerY, PLAYER_MARKER_SIZE);
        }
    }

    private static ScreenPoint clampPlayerMarkerToMap(double playerScreenX,
                                                       double playerScreenY,
                                                       int centerX,
                                                       int centerY,
                                                       int left,
                                                       int top,
                                                       int right,
                                                       int bottom) {
        double halfMarker = PLAYER_MARKER_SIZE / 2.0D;
        double minCenterX = left + PLAYER_MARKER_EDGE_PADDING + halfMarker;
        double maxCenterX = right - PLAYER_MARKER_EDGE_PADDING - halfMarker;
        double minCenterY = top + PLAYER_MARKER_EDGE_PADDING + halfMarker;
        double maxCenterY = bottom - PLAYER_MARKER_EDGE_PADDING - halfMarker;

        double dx = playerScreenX - centerX;
        double dy = playerScreenY - centerY;
        boolean outside = playerScreenX < minCenterX || playerScreenX > maxCenterX
                || playerScreenY < minCenterY || playerScreenY > maxCenterY;
        double factor = 1.0D;
        if (outside) {
            double maxDx = Math.min(centerX - minCenterX, maxCenterX - centerX);
            double maxDy = Math.min(centerY - minCenterY, maxCenterY - centerY);
            double xFactor = dx == 0.0D ? Double.POSITIVE_INFINITY : maxDx / Math.abs(dx);
            double yFactor = dy == 0.0D ? Double.POSITIVE_INFINITY : maxDy / Math.abs(dy);
            factor = Math.min(xFactor, yFactor);
        }

        int x = (int) Math.round(centerX + dx * factor);
        int y = (int) Math.round(centerY + dy * factor);
        x = Math.max((int) Math.ceil(minCenterX), Math.min((int) Math.floor(maxCenterX), x));
        y = Math.max((int) Math.ceil(minCenterY), Math.min((int) Math.floor(maxCenterY), y));
        return new ScreenPoint(x, y);
    }

    private void renderPlayerCenteredButtonIcon(GuiGraphics context) {
        if (playerCenteredButton == null || !playerCenteredButton.visible) {
            return;
        }
        int iconSize = 7;
        int iconX = playerCenteredButton.getX() + 4;
        int iconY = playerCenteredButton.getY() + (playerCenteredButton.getHeight() - iconSize) / 2;
        PlayerFaceRenderer.draw(context, DefaultPlayerSkin.getDefaultTexture(), iconX, iconY, iconSize);
    }

    private record ScreenPoint(int x, int y) {
    }

    private record TerrainCell(int minX, int minZ, int maxX, int maxZ, int height, int baseColor) {
    }

    private void renderTerrain(GuiGraphics context, double mapCenterX, double mapCenterZ, float scale) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        int centerBlockX = (int) Math.floor(mapCenterX);
        int centerBlockZ = (int) Math.floor(mapCenterZ);
        int radius = Math.max(1, (int) Math.ceil((MAP_HALF_SIZE - 1) / scale) + 1);
        int sampleStep = Math.max(1, (int) Math.ceil((double) TERRAIN_TARGET_CELL_PIXELS / scale));
        int minX = centerBlockX - radius;
        int maxX = centerBlockX + radius;
        int minZ = centerBlockZ - radius;
        int maxZ = centerBlockZ + radius;
        int minBuildHeight = minecraft.level.getMinBuildHeight();
        int xCellCount = (maxX - minX) / sampleStep + 1;
        int zCellCount = (maxZ - minZ) / sampleStep + 1;
        TerrainCell[][] cells = new TerrainCell[xCellCount][zCellCount];
        int minTerrainHeight = Integer.MAX_VALUE;
        int maxTerrainHeight = Integer.MIN_VALUE;

        BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
        for (int cellX = 0; cellX < xCellCount; cellX++) {
            int x = minX + cellX * sampleStep;
            int cellMaxX = Math.min(x + sampleStep, maxX + 1);
            int sampleX = Math.min(x + sampleStep / 2, maxX);
            for (int cellZ = 0; cellZ < zCellCount; cellZ++) {
                int z = minZ + cellZ * sampleStep;
                int cellMaxZ = Math.min(z + sampleStep, maxZ + 1);
                int sampleZ = Math.min(z + sampleStep / 2, maxZ);
                if (!minecraft.level.hasChunkAt(sampleX, sampleZ)) {
                    continue;
                }

                int surfaceHeight = minecraft.level.getHeight(
                        Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ);
                if (surfaceHeight <= minBuildHeight) {
                    continue;
                }

                // Match vanilla map sampling: start at WORLD_SURFACE and walk through
                // colourless blocks (for example glass) until a visible map colour is found.
                surfacePos.set(sampleX, surfaceHeight - 1, sampleZ);
                BlockState surfaceState = minecraft.level.getBlockState(surfacePos);
                MapColor mapColor = surfaceState.getMapColor(minecraft.level, surfacePos);
                while (mapColor == MapColor.NONE && surfacePos.getY() > minBuildHeight) {
                    surfacePos.move(0, -1, 0);
                    surfaceState = minecraft.level.getBlockState(surfacePos);
                    mapColor = surfaceState.getMapColor(minecraft.level, surfacePos);
                }

                // Keep visible surface colour and terrain relief separate. This prevents tree
                // canopies from turning into fake hills while preserving vanilla map colours.
                int terrainHeight = minecraft.level.getHeight(
                        Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                if (terrainHeight <= minBuildHeight) {
                    terrainHeight = surfacePos.getY() + 1;
                }

                int baseColor = mapColor == MapColor.NONE ? TERRAIN_FALLBACK_COLOR : mapColor.col;
                cells[cellX][cellZ] = new TerrainCell(x, z, cellMaxX, cellMaxZ, terrainHeight, baseColor);
                minTerrainHeight = Math.min(minTerrainHeight, terrainHeight);
                maxTerrainHeight = Math.max(maxTerrainHeight, terrainHeight);
            }
        }

        if (minTerrainHeight == Integer.MAX_VALUE) {
            return;
        }

        int reliefRange = maxTerrainHeight - minTerrainHeight;
        int contourInterval = getTerrainContourInterval(reliefRange);
        for (int cellX = 0; cellX < xCellCount; cellX++) {
            for (int cellZ = 0; cellZ < zCellCount; cellZ++) {
                TerrainCell cell = cells[cellX][cellZ];
                if (cell == null) {
                    continue;
                }

                int northHeight = getTerrainCellHeight(cells, cellX, cellZ - 1, cell.height());
                int southHeight = getTerrainCellHeight(cells, cellX, cellZ + 1, cell.height());
                int westHeight = getTerrainCellHeight(cells, cellX - 1, cellZ, cell.height());
                int eastHeight = getTerrainCellHeight(cells, cellX + 1, cellZ, cell.height());
                float slopeDelta = ((westHeight - eastHeight) + (northHeight - southHeight)) * 0.25f;
                float elevation = reliefRange == 0
                        ? 0.5f
                        : (cell.height() - minTerrainHeight) / (float) reliefRange;

                int color = shadeTerrainColor(cell.baseColor(), slopeDelta, elevation);
                context.fill(cell.minX(), cell.minZ(), cell.maxX(), cell.maxZ(), color);

                boolean northContour = cellZ > 0
                        && Math.floorDiv(cell.height(), contourInterval) != Math.floorDiv(northHeight, contourInterval);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height(), contourInterval) != Math.floorDiv(westHeight, contourInterval);
                drawTerrainContourEdges(context, cell, northContour, westContour);
            }
        }
    }

    private static int getTerrainCellHeight(TerrainCell[][] cells, int x, int z, int fallbackHeight) {
        if (x < 0 || z < 0 || x >= cells.length || z >= cells[x].length || cells[x][z] == null) {
            return fallbackHeight;
        }
        return cells[x][z].height();
    }

    private static int getTerrainContourInterval(int reliefRange) {
        if (reliefRange <= 2) {
            return 1;
        }
        if (reliefRange <= 6) {
            return 2;
        }
        return 4;
    }

    private static int shadeTerrainColor(int baseColor, float slopeDelta, float elevation) {
        float elevationBrightness = (elevation - 0.5f) * 2.0f * TERRAIN_ELEVATION_BRIGHTNESS_RANGE;
        float brightness = TERRAIN_BASE_BRIGHTNESS
                + slopeDelta * TERRAIN_SLOPE_BRIGHTNESS_PER_BLOCK
                + elevationBrightness;
        brightness = Math.max(TERRAIN_MIN_BRIGHTNESS, Math.min(TERRAIN_MAX_BRIGHTNESS, brightness));

        int red = Math.min(255, Math.round(((baseColor >> 16) & 0xff) * brightness));
        int green = Math.min(255, Math.round(((baseColor >> 8) & 0xff) * brightness));
        int blue = Math.min(255, Math.round((baseColor & 0xff) * brightness));
        return (TERRAIN_ALPHA << 24) | (red << 16) | (green << 8) | blue;
    }

    private static void drawTerrainContourEdges(GuiGraphics context, TerrainCell cell,
                                                boolean northContour, boolean westContour) {
        if (northContour && cell.minZ() < cell.maxZ()) {
            context.fill(cell.minX(), cell.minZ(), cell.maxX(), Math.min(cell.minZ() + 1, cell.maxZ()),
                    TERRAIN_CONTOUR_COLOR);
        }
        if (westContour && cell.minX() < cell.maxX()) {
            context.fill(cell.minX(), cell.minZ(), Math.min(cell.minX() + 1, cell.maxX()), cell.maxZ(),
                    TERRAIN_CONTOUR_COLOR);
        }
    }

    private static void renderRoomRegion(GuiGraphics context,
                                         BlueprintFloorLayout.RegionBounds region,
                                         int baseColor,
                                         boolean selectedFloor,
                                         boolean hovered) {
        int minX = region.minX();
        int minZ = region.minZ();
        // Region bounds are inclusive Minecraft block coordinates. Rendering uses
        // half-open rectangles, so max + 1 makes one block occupy exactly one map unit.
        int maxX = region.maxX() + 1;
        int maxZ = region.maxZ() + 1;

        WidgetUtils.drawRectangle(context,
                minX + 1, minZ + 1, maxX + 1, maxZ + 1,
                ROOM_SHADOW_COLOR);

        int innerMinX = minX + ROOM_INNER_PADDING;
        int innerMinZ = minZ + ROOM_INNER_PADDING;
        int innerMaxX = maxX - ROOM_INNER_PADDING;
        int innerMaxZ = maxZ - ROOM_INNER_PADDING;
        if (innerMinX < innerMaxX && innerMinZ < innerMaxZ) {
            int fillAlpha = hovered
                    ? ROOM_FILL_ALPHA_HOVERED
                    : selectedFloor ? ROOM_FILL_ALPHA_SELECTED_FLOOR : ROOM_FILL_ALPHA_ALL_FLOORS;
            context.fill(innerMinX, innerMinZ, innerMaxX, innerMaxZ, withAlpha(baseColor, fillAlpha));
        }

        int outlineAlpha = hovered ? 0xff : selectedFloor ? 0xdd : 0xaa;
        WidgetUtils.drawRectangle(context,
                minX, minZ, maxX, maxZ,
                withAlpha(baseColor, outlineAlpha));

        if (hovered && innerMinX + 1 < innerMaxX && innerMinZ + 1 < innerMaxZ) {
            WidgetUtils.drawRectangle(context,
                    innerMinX, innerMinZ, innerMaxX, innerMaxZ,
                    withAlpha(baseColor, 0x88));
        }
    }

    private BlockPos getBuildingIconPosition(Building building, Integer selectedFloor) {
        if (selectedFloor == null) {
            return floorLayout.iconPositionFor(building);
        }

        return floorLayout.regionsFor(building, selectedFloor).stream()
                .max(Comparator.comparingLong(BlueprintScreen::getRegionArea))
                .map(region -> new BlockPos(
                        region.minX() + (region.maxX() - region.minX()) / 2,
                        building.getCenter().getY(),
                        region.minZ() + (region.maxZ() - region.minZ()) / 2
                ))
                .orElseGet(() -> floorLayout.iconPositionFor(building));
    }

    private static long getRegionArea(BlueprintFloorLayout.RegionBounds region) {
        return (long) (region.maxX() - region.minX() + 1)
                * (region.maxZ() - region.minZ() + 1);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private void togglePlayerCentered() {
        playerCentered = !playerCentered;
        rememberedPlayerCentered = playerCentered;
        updatePlayerCenteredControl();
        MCA.LOGGER.info("[FloorRoomDebug] side=client stage=player-centered-change enabled={} mode={} effectiveScale={}",
                playerCentered, mapScaleMode, village == null ? 0.0F : getMapScale());
    }

    private Component getPlayerCenteredLabel() {
        // The button text is centered by vanilla. Reserve space on the left for the
        // overlaid Steve icon so it never draws on top of the first letters.
        MutableComponent label = Component.literal("   ")
                .append(Component.translatable("gui.blueprint.playerCentered"));
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
            case ONE_TO_ONE -> 1.0F;
            case TWO_TO_ONE -> 2.0F;
            case THREE_TO_ONE -> 3.0F;
            case FOUR_TO_ONE -> 4.0F;
        };
    }

    private void cycleMapScale() {
        mapScaleMode = mapScaleMode.next();
        rememberedMapScaleMode = mapScaleMode;
        updateMapScaleControl();
        MCA.LOGGER.info("[FloorRoomDebug] side=client stage=map-scale-change mode={} effectiveScale={}",
                mapScaleMode, village == null ? 0.0F : getMapScale());
    }

    private Component getMapScaleLabel() {
        return Component.literal(mapScaleMode.label());
    }

    private Component getMapScaleTooltip() {
        return Component.translatable(mapScaleMode.tooltipKey());
    }

    private void updateMapScaleControl() {
        if (mapScaleButton != null) {
            mapScaleButton.setMessage(getMapScaleLabel());
            mapScaleButton.setTooltip(Tooltip.create(getMapScaleTooltip()));
        }
    }

    private enum MapScaleMode {
        FIT("Fit", "gui.blueprint.mapScale.fit.tooltip"),
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

        String label() {
            return label;
        }

        String tooltipKey() {
            return tooltipKey;
        }

        MapScaleMode next() {
            MapScaleMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private void changeSelectedFloor(int direction) {
        List<Integer> floors = getFloorNavigationOrder();
        if (floorLayout.ordinals().size() <= 1) {
            updateFloorControls();
            return;
        }

        int currentIndex = floors.indexOf(selectedFloorOrdinal);
        if (currentIndex < 0) {
            reconcileSelectedFloor();
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
        reconcileSelectedFloor();
        if (floorPreviousButton == null || floorLabelButton == null || floorNextButton == null) {
            return;
        }

        List<Integer> floors = getFloorNavigationOrder();
        boolean canChangeFloors = floorLayout.ordinals().size() > 1;
        int selectedIndex = floors.isEmpty() ? -1 : floors.indexOf(selectedFloorOrdinal);
        Component tooltip = getFloorControlTooltip();
        floorPreviousButton.active = canChangeFloors && selectedIndex > 0;
        floorNextButton.active = canChangeFloors && selectedIndex >= 0 && selectedIndex < floors.size() - 1;
        floorLabelButton.active = canChangeFloors && selectedFloorOrdinal != null;
        // Keep floor-navigation help on the central label only; the arrow buttons are self-explanatory.
        floorLabelButton.setTooltip(Tooltip.create(tooltip));

        floorLabelButton.setMessage(getFloorLabel(selectedFloorOrdinal));
    }

    private List<Integer> getFloorNavigationOrder() {
        List<Integer> ordinals = floorLayout.ordinals();
        if (ordinals.isEmpty()) {
            return List.of();
        }

        List<Integer> floors = new ArrayList<>(ordinals.size() + 1);
        ordinals.stream().filter(ordinal -> ordinal < 0).forEach(floors::add);
        floors.add(null);
        ordinals.stream().filter(ordinal -> ordinal >= 0).forEach(floors::add);
        return Collections.unmodifiableList(floors);
    }

    private Component getFloorControlTooltip() {
        if (floorLayout.ordinals().isEmpty()) {
            return Component.translatable("gui.blueprint.floor.disabled.noBuilding");
        }
        return floorLayout.ordinals().size() == 1
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

    private void reconcileSelectedFloor() {
        List<Integer> ordinals = floorLayout.ordinals();
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

    private Component getBuildingIconsLabel() {
        MutableComponent label = Component.translatable("gui.blueprint.buildingIcons.short");
        return showBuildingIcons
                ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private void updateBuildingIconsControl() {
        if (buildingIconsButton != null) {
            buildingIconsButton.setMessage(getBuildingIconsLabel());
        }
    }

    private Component getTerrainLabel() {
        MutableComponent label = Component.translatable("gui.blueprint.terrain");
        return showTerrain
                ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private void updateTerrainControl() {
        if (terrainButton != null) {
            terrainButton.setMessage(getTerrainLabel());
        }
    }

    private void updateGroundAnchorControl(Village.StructuralLookup structuralLookup) {
        if (groundAnchorButton == null) {
            return;
        }
        groundAnchorButton.active = village != null
                && structuralLookup.functionalRoom()
                .filter(room -> !village.isStructuralGroundFloor(room))
                .isPresent();
    }

    private static void addHoveredBuilding(Map<Integer, Building> hoveredBuildings,
                                           Building building,
                                           Integer selectedFloor) {
        int hoverKey = selectedFloor == null && !building.getBuildingType().grouped()
                ? building.getEffectiveStructureId()
                : building.getId();
        hoveredBuildings.putIfAbsent(hoverKey, building);
    }

    private List<Component> getBuildingTooltip(Building hoverBuilding, Integer selectedFloor) {
        List<Building> tooltipBuildings = selectedFloor == null
                ? getStructureTooltipBuildings(hoverBuilding)
                : List.of(hoverBuilding);
        if (selectedFloor == null
                && !hoverBuilding.getBuildingType().grouped()
                && (floorLayout.ordinals().size() > 1 || tooltipBuildings.size() > 1)) {
            return getAllFloorsTooltip(tooltipBuildings);
        }

        List<Component> lines = new LinkedList<>();
        Building headerBuilding = selectedFloor == null
                ? tooltipBuildings.stream().findFirst().orElse(hoverBuilding)
                : hoverBuilding;

        // Match the tooltip header to the same configured color used by the building on the map.
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(headerBuilding.getType());
        lines.add(getBuildingTypeTooltipLabel(bt));

        //residents
        LinkedHashSet<String> residents = new LinkedHashSet<>();
        for (Building building : tooltipBuildings) {
            residents.addAll(village.getResidents(building.getId()));
        }
        for (String name : residents) {
            lines.add(Component.literal(name).withStyle(ChatFormatting.GRAY));
        }

        lines.addAll(getBlockTooltipLines(tooltipBuildings, selectedFloor));
        return lines;
    }

    private List<Component> getAllFloorsTooltip(List<Building> structureBuildings) {
        List<Component> lines = new LinkedList<>();
        for (int floorOrdinal : floorLayout.ordinals()) {
            List<Building> floorRooms = structureBuildings.stream()
                    .filter(building -> floorLayout.isBuildingVisible(building, floorOrdinal))
                    .toList();
            if (floorRooms.isEmpty()) {
                continue;
            }

            lines.add(getTooltipFloorLabel(floorOrdinal));
            for (Building room : floorRooms) {
                BuildingType roomType = BuildingTypes.getInstance().getBuildingType(room.getType());
                lines.add(Component.literal("  ").append(getBuildingTypeTooltipLabel(roomType)));
                village.getResidents(room.getId()).forEach(name ->
                        lines.add(Component.literal("    ").append(Component.literal(name).withStyle(ChatFormatting.GRAY))));
                getBlockTooltipLines(List.of(room), floorOrdinal).forEach(item ->
                        lines.add(Component.literal("    ").append(item)));
            }
        }
        return lines;
    }

    private List<Building> getStructureTooltipBuildings(Building building) {
        if (building.getBuildingType().grouped()) {
            return List.of(building);
        }
        int structureId = building.getEffectiveStructureId();
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(candidate -> candidate.getEffectiveStructureId() == structureId)
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
    }

    private Component getBuildingTypeTooltipLabel(BuildingType buildingType) {
        return Component.translatable("buildingType." + buildingType.name())
                .withStyle(style -> style.withColor(buildingType.getColor() & 0x00ffffff));
    }

    private Component getTooltipFloorLabel(int floorOrdinal) {
        int color = floorOrdinal < 0
                ? TOOLTIP_FLOOR_BASEMENT_COLOR
                : floorOrdinal == 0 ? TOOLTIP_FLOOR_GROUND_COLOR : TOOLTIP_FLOOR_UPPER_COLOR;
        return getFloorLabel(floorOrdinal).copy()
                .withStyle(style -> style.withColor(color).withBold(true));
    }

    private List<Component> getBlockTooltipLines(Collection<Building> buildings, Integer selectedFloor) {
        List<Component> lines = new ArrayList<>();
        Map<ResourceLocation, Set<BlockPos>> positionsByBlock = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        for (Building building : buildings) {
            for (Map.Entry<ResourceLocation, List<BlockPos>> block : building.getBlocks().entrySet()) {
                Set<BlockPos> positions = positionsByBlock.computeIfAbsent(block.getKey(), ignored -> new HashSet<>());
                block.getValue().stream()
                        .filter(pos -> selectedFloor == null || floorLayout.isBlockOnFloor(building, pos, selectedFloor))
                        .forEach(positions::add);
            }
        }
        for (Map.Entry<ResourceLocation, Set<BlockPos>> block : positionsByBlock.entrySet()) {
            if (!block.getValue().isEmpty()) {
                lines.add(Component.literal(block.getValue().size() + " x ")
                        .append(getBlockName(block.getKey()))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
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
                context.drawString(font, Component.literal(b.getValue() + " x ").append(getBlockName(b.getKey())), x, y, 0xffffffff);
                y += 10;
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
        if (page.equals("villagers") && selectedVillager != null) {
            assert minecraft != null;
            minecraft.setScreen(new FamilyTreeScreen(selectedVillager));
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public void setVillage(Village village) {
        Integer selectedBefore = selectedFloorOrdinal;
        boolean pendingFloorSelection = selectPlayerFloorOnNextVillageResponse;
        this.village = village;
        this.floorLayout = village == null ? BlueprintFloorLayout.empty() : BlueprintFloorLayout.build(village);
        Village.StructuralLookup structuralLookup = getPlayerStructuralLookup();
        Village.StructuralPosition structuralPosition = structuralLookup.position();
        BlockPos playerPos = minecraft != null && minecraft.player != null
                ? minecraft.player.blockPosition()
                : null;
        MCA.LOGGER.info("[FloorRoomDebug] side=client stage=village-response pos={} villageId={} buildingCount={} lookup={} lookupBuilding={} pendingFloorSelect={} selectedBefore={} availableFloors={}",
                playerPos, village == null ? -1 : village.getId(), village == null ? 0 : village.getBuildings().size(),
                structuralPosition, describeBuilding(structuralLookup.building().orElse(null)), pendingFloorSelection,
                selectedBefore, floorLayout.ordinals());
        logClientBuildings(village);
        if (selectPlayerFloorOnNextVillageResponse
                && structuralPosition == Village.StructuralPosition.REGISTERED_ROOM) {
            selectPlayerFloor(structuralLookup);
        }
        selectPlayerFloorOnNextVillageResponse = false;
        reconcileSelectedFloor();
        updateFloorControls();
        updateBuildingIconsControl();
        updateTerrainControl();
        updateMapScaleControl();
        updateStructureScanControl(structuralLookup);
        updateRemoveRoomControl(structuralLookup);
        updateGroundAnchorControl(structuralLookup);

        MCA.LOGGER.info("[FloorRoomDebug] side=client stage=village-response-applied pos={} lookup={} selectedAfter={} availableFloors={}",
                playerPos, structuralPosition, selectedFloorOrdinal, floorLayout.ordinals());

        if (village == null) {
            setPage("empty");
        } else if (page.equals("waiting") || page.equals("empty")) {
            setPage("map");
        }
    }

    private void selectPlayerFloor(Village.StructuralLookup structuralLookup) {
        structuralLookup.functionalRoom()
                .ifPresent(room -> floorLayout.floorOrdinalFor(room).ifPresent(ordinal -> {
                        selectedFloorOrdinal = ordinal;
                        rememberedFloorOrdinal = ordinal;
                    }));
    }

    private static void logClientBuildings(Village village) {
        if (village == null) {
            return;
        }
        village.getBuildings().values().stream()
                .sorted(Comparator.comparingInt(Building::getId))
                .forEach(building -> MCA.LOGGER.info(
                        "[FloorRoomDebug] side=client stage=village-building villageId={} {}",
                        village.getId(), describeBuilding(building)));
    }

    private static String describeBuilding(Building building) {
        if (building == null) {
            return "none";
        }
        return "id=" + building.getId()
                + ",structure=" + building.getEffectiveStructureId()
                + ",root=" + building.isStructureRoot()
                + ",strict=" + building.isStrictScan()
                + ",functional=" + building.isFunctionalRoom()
                + ",floorY=" + building.getFloorY()
                + ",groundFloorY=" + building.getGroundFloorY()
                + ",floorRegions=" + building.getFloorRegions().stream().map(region -> region.anchorY()).toList()
                + ",source=" + building.getSourceBlock()
                + ",bounds=" + building.getPos0() + ".." + building.getPos1();
    }

    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
    }
}
