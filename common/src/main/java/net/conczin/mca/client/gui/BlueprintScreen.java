package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
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
    private static final int TERRAIN_CACHE_MARGIN_BLOCKS = 64;
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
    private static final int PLAYER_CENTERED_BUTTON_WIDTH = 78;
    private static final int PLAYER_HEAD_BUTTON_SIZE = 16;
    private static final int PLAYER_HEAD_ICON_SIZE = 12;
    private static final int ALL_FLOORS_GEOMETRY_KEY = Integer.MIN_VALUE;
    private static final int ROOM_FILL_ALPHA_ALL_FLOORS = 0x60;
    private static final int ROOM_FILL_ALPHA_SELECTED_FLOOR = 0x70;
    private static final float ROOM_FILL_BRIGHTEN_FACTOR = 1.15f;
    private static final int ROOM_BORDER_ALPHA_ALL_FLOORS = 0xd0;
    private static final int ROOM_BORDER_ALPHA_SELECTED_FLOOR = 0xee;
    private static final int ROOM_BORDER_ALPHA_HOVERED = 0xff;
    private static final float ROOM_BORDER_BRIGHTEN_FACTOR = 1.35f;
    private static final int BUILDING_OUTLINE_WIDTH = 1;
    private static final int BUILDING_SHADE_ALPHA = 0x24;
    private static final int BUILDING_BORDER_ALPHA = 0xc0;
    private static final float BUILDING_BORDER_DARKEN_FACTOR = 0.58f;
    private static final float ROOM_ICON_MIN_SCALE = 0.90f;
    private static final float ROOM_ICON_MAX_SCALE = 1.35f;
    private static final float ROOM_ICON_AREA_REFERENCE = 6.0f;
    private static final int ROOM_FILL_ALPHA_HOVERED = 0x98;
    private static final int TOOLTIP_FLOOR_BASEMENT_COLOR = 0x9b8cff;
    private static final int TOOLTIP_FLOOR_GROUND_COLOR = 0xf2c94c;
    private static final int TOOLTIP_FLOOR_UPPER_COLOR = 0x6fd6a5;
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
    private TooltipButtonWidget groundAnchorButton;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private ButtonWidget advancedButton;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
    private MapScaleMode mapScaleMode = rememberedMapScaleMode;
    private boolean playerCentered = rememberedPlayerCentered;
    private boolean showPlayerHead = rememberedShowPlayerHead;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private boolean showBuildingIcons = true;
    private boolean showTerrain = true;
    private BlueprintFloorLayout floorLayout = BlueprintFloorLayout.empty();
    private TerrainSnapshot terrainSnapshot;
    private ResourceLocation terrainTextureLocation;
    private final Map<Integer, MapGeometry> mapGeometryCache = new HashMap<>();
    private List<MapStructureLayer> structureLayerCache;
    private boolean logNextFloorRoomVillageResponse;
    private BuildingType selectedBuilding;
    private UUID selectedVillager;
    private static final int ROOM_SHADOW_COLOR = 0x50000000;

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
        playerHeadButton = null;
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
        selectPlayerFloorOnNextVillageResponse = action == ReportBuildingMessage.Action.ADD
                || action == ReportBuildingMessage.Action.ADD_ROOM
                || action == ReportBuildingMessage.Action.UPDATE_ROOM;
        // Arm one response snapshot for this explicit user action. Passive village
        // syncs must not periodically dump every building into the log.
        logNextFloorRoomVillageResponse = true;
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

    private void renderMap(GuiGraphics context, float partialTick) {
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
        double playerRenderX = player == null ? 0.0D : Mth.lerp(partialTick, player.xo, player.getX());
        double playerRenderZ = player == null ? 0.0D : Mth.lerp(partialTick, player.zo, player.getZ());
        double villageCenterX = (village.getBox().minX() + village.getBox().maxX() + 1) / 2.0D;
        double villageCenterZ = (village.getBox().minZ() + village.getBox().maxZ() + 1) / 2.0D;
        double requestedMapCenterX = playerCentered && player != null ? playerRenderX : villageCenterX;
        double requestedMapCenterZ = playerCentered && player != null ? playerRenderZ : villageCenterZ;
        float scale = getMapScale();

        // Pixel-lock one shared map origin for terrain, room fills, structure shade,
        // outlines and icons. Player momentum now translates the complete map as one unit
        // instead of independently re-rounding every screen-space edge each frame.
        double mapOriginX = Math.rint(centerX - requestedMapCenterX * scale);
        double mapOriginZ = Math.rint(centerY - requestedMapCenterZ * scale);
        double mapCenterX = (centerX - mapOriginX) / scale;
        double mapCenterZ = (centerY - mapOriginZ) / scale;

        int mouseLocalX = (int) Math.floor((mouseX - centerX) / scale + mapCenterX);
        int mouseLocalZ = (int) Math.floor((mouseY - centerY) / scale + mapCenterZ);
        BlueprintMapFootprint.Cell hoveredMapCell =
                new BlueprintMapFootprint.Cell(mouseLocalX, mouseLocalZ);
        boolean mouseInsideMap = mouseX >= left + 1 && mouseX < right - 1
                && mouseY >= top + 1 && mouseY < bottom - 1;

        List<MapHoverTarget> hoverTargets = new ArrayList<>();
        List<Building> groupedIconBuildings = new ArrayList<>();
        MapGeometry geometry = getMapGeometry(selectedFloor);
        List<MapFootprintLayer> footprintLayers = geometry.footprintLayers();
        List<MapStructureLayer> structureLayers = geometry.structureLayers();
        List<MapIconLayer> footprintIconLayers = geometry.iconLayers();

        context.enableScissor(left + 1, top + 1, right - 1, bottom - 1);

        matrices.pushPose();
        matrices.translate(centerX, centerY, 0.0D);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-mapCenterX, -mapCenterZ, 0.0D);

        if (showTerrain) {
            renderTerrain(context, mapCenterX, mapCenterZ, scale);
        }

        // Grouped POIs keep their legacy point/rectangle rendering. Persistent room
        // structures are rendered from exact floor footprints below.
        for (Building building : geometry.groupedBuildings()) {

            BuildingType buildingType = building.getBuildingType();
            if (buildingType.isIcon()) {
                BlockPos center = building.getCenter();
                groupedIconBuildings.add(building);

                int hoverMargin = 6;
                if (mouseInsideMap
                        && center.distSqr(new Vec3i(mouseLocalX, center.getY(), mouseLocalZ)) < hoverMargin * hoverMargin) {
                    addRoomHover(hoverTargets, building, selectedFloor);
                }
                continue;
            }

            List<BlueprintFloorLayout.RegionBounds> renderRegions = floorLayout.regionsFor(building, selectedFloor);
            int hoverMargin = 1;
            boolean hovering = mouseInsideMap && renderRegions.stream().anyMatch(region ->
                    mouseLocalX >= region.minX() - hoverMargin && mouseLocalX <= region.maxX() + hoverMargin
                            && mouseLocalZ >= region.minZ() - hoverMargin && mouseLocalZ <= region.maxZ() + hoverMargin);

            for (BlueprintFloorLayout.RegionBounds region : renderRegions) {
                renderRoomRegion(context, region, buildingType.getColor(), selectedFloor != null, hovering);
            }

            if (hovering) {
                addRoomHover(hoverTargets, building, selectedFloor);
            }
        }

        // The structure layer is the separate width-expanded ring around the exact room
        // union. Hovering that ring resolves to the structure root, whose tooltip enumerates
        // every registered room/floor in the structure.
        matrices.popPose();
        for (MapStructureLayer layer : structureLayers) {
            renderStructureShade(context, layer.shadeSpans(),
                    layer.root().getBuildingType().getColor(),
                    centerX, centerY, mapCenterX, mapCenterZ, scale);
        }

        Set<MapFootprintLayer> hoveredFootprintLayers = new HashSet<>();
        for (MapFootprintLayer layer : footprintLayers) {
            boolean hovering = mouseInsideMap && isRoomHovered(
                    layer,
                    hoveredMapCell,
                    mouseX, mouseY,
                    centerX, centerY,
                    mapCenterX, mapCenterZ,
                    scale);
            if (hovering) {
                hoveredFootprintLayers.add(layer);
            }
            renderRoomFootprint(context, layer.fillSpans(),
                    layer.building().getBuildingType().getColor(), selectedFloor != null, hovering,
                    centerX, centerY, mapCenterX, mapCenterZ, scale);
            if (hovering) {
                addRoomHover(hoverTargets, layer.building(), layer.floorOrdinal());
            }
        }

        // Room hover wins over the building shade occupying the same map cells.
        for (MapStructureLayer layer : structureLayers) {
            boolean roomHovered = hoverTargets.stream().anyMatch(target ->
                    !target.building().isStructureRoot()
                            && !target.building().getBuildingType().grouped()
                            && target.building().getEffectiveStructureId()
                            == layer.root().getEffectiveStructureId());
            boolean buildingHovered = layer.shadeCells().contains(hoveredMapCell)
                    || isOutlineHovered(layer.borderEdges(), mouseX, mouseY,
                    centerX, centerY, mapCenterX, mapCenterZ, scale);
            if (!roomHovered && mouseInsideMap && buildingHovered) {
                hoverTargets.add(new MapHoverTarget(layer.root(), null));
            }
        }

        // Building and room outlines are UI detail rather than world geometry. Render their
        // exposed perimeter edges after leaving the scaled map pose so they remain one pixel.
        for (MapStructureLayer layer : structureLayers) {
            renderStructureOutlineScreenSpace(
                    context,
                    layer.borderEdges(),
                    layer.root().getBuildingType().getColor(),
                    centerX,
                    centerY,
                    mapCenterX,
                    mapCenterZ,
                    scale);
        }
        List<MapFootprintLayer> outlinedRooms = selectedFloor == null
                ? getAllFloorsOutlineLayers(footprintLayers, hoverTargets)
                : footprintLayers;
        for (MapFootprintLayer layer : outlinedRooms) {
            renderRoomOutlineScreenSpace(
                    context,
                    layer.outlineEdges(),
                    layer.building().getBuildingType().getColor(),
                    selectedFloor != null,
                    hoveredFootprintLayers.contains(layer),
                    centerX,
                    centerY,
                    mapCenterX,
                    mapCenterZ,
                    scale);
        }

        // Icons still render above room fills/outlines. Re-enter map coordinates only for
        // their world positions; drawScaledBuildingIcon compensates their screen-space size.
        matrices.pushPose();
        matrices.translate(centerX, centerY, 0.0D);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-mapCenterX, -mapCenterZ, 0.0D);

        // Grouped POIs keep their legacy icons. Persistent structures render exactly one
        // scaled icon per functional room, and icon hover resolves to that room/floor.
        if (showBuildingIcons) {
            for (Building building : groupedIconBuildings) {
                BuildingType buildingType = building.getBuildingType();
                BlockPos center = building.getCenter();
                drawBuildingIcon(context, ICON_TEXTURES,
                        center.getX(), center.getZ(), buildingType.iconU(), buildingType.iconV());
            }
            for (MapIconLayer iconLayer : footprintIconLayers) {
                BuildingType buildingType = iconLayer.building().getBuildingType();
                float iconScale = iconLayer.iconScale();
                drawScaledBuildingIcon(context, ICON_TEXTURES,
                        iconLayer.iconX(), iconLayer.iconZ(),
                        buildingType.iconU(), buildingType.iconV(), iconScale / scale);

                // Icons are screen-space UI. Compensate for the map zoom so changing
                // 1:1/2:1/3:1/4:1 does not turn a room icon into a giant map-sized box.
                double iconScreenX = mapCoordinateToScreen(
                        iconLayer.iconX(), centerX, mapCenterX, scale);
                double iconScreenY = mapCoordinateToScreen(
                        iconLayer.iconZ(), centerY, mapCenterZ, scale);
                double hoverRadius = 7.0D * iconScale;
                double dx = mouseX + 0.5D - iconScreenX;
                double dz = mouseY + 0.5D - iconScreenY;
                if (mouseInsideMap && dx * dx + dz * dz < hoverRadius * hoverRadius) {
                    addRoomHover(hoverTargets, iconLayer.building(), iconLayer.floorOrdinal());
                }
            }
        }

        matrices.popPose();
        context.disableScissor();

        // The player is global map context, not part of a floor, and stays above every icon.
        // Use the player's actual skin face and clamp it to the map edge when the player
        // is outside the current viewport instead of letting scissoring hide it.
        renderPlayerMarker(context, player, playerRenderX, playerRenderZ,
                centerX, centerY, left, top, right, bottom,
                mapCenterX, mapCenterZ, scale);
        renderPlayerHeadButtonIcon(context, player);

        //sort vertically
        hoverTargets.sort(Comparator.comparingInt(
                (MapHoverTarget target) -> target.building().getCenter().getY()).reversed());

        //get tooltips
        List<List<Component>> tooltips = new ArrayList<>();
        for (MapHoverTarget target : hoverTargets) {
            tooltips.add(getBuildingTooltip(target.building(), target.floorOrdinal()));
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
                                    double playerRenderX,
                                    double playerRenderZ,
                                    int centerX,
                                    int centerY,
                                    int left,
                                    int top,
                                    int right,
                                    int bottom,
                                    double mapCenterX,
                                    double mapCenterZ,
                                    float scale) {
        if (player == null || !showPlayerHead) {
            return;
        }

        double playerScreenX = centerX + (playerRenderX - mapCenterX) * scale;
        double playerScreenY = centerY + (playerRenderZ - mapCenterZ) * scale;
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

        renderCurrentPlayerFace(context, player, markerX, markerY, PLAYER_MARKER_SIZE);
    }

    private void renderCurrentPlayerFace(GuiGraphics context, LocalPlayer player, int x, int y, int size) {
        // Prefer the exact dynamic MCA face used by the JourneyMap compatibility bridge
        // whenever this player is actually using an MCA-rendered player model. Fall back
        // to the player's current vanilla skin while player data is unavailable or vanilla is selected.
        ResourceLocation mcaFace = MCAClient.getPlayerData(player.getUUID())
                .filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA)
                .map(JourneyMapIconBridge::getOrCreateFaceIcon)
                .orElse(null);
        if (mcaFace != null) {
            // JourneyMapIconBridge returns a complete 24x24 cropped face texture.
            context.blit(mcaFace, x, y, size, size,
                    0.0F, 0.0F, 24, 24, 24, 24);
        } else {
            PlayerFaceRenderer.draw(context, player.getSkin(), x, y, size);
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

    private void renderPlayerHeadButtonIcon(GuiGraphics context, LocalPlayer player) {
        if (playerHeadButton == null || !playerHeadButton.visible || player == null) {
            return;
        }
        int iconSize = PLAYER_HEAD_ICON_SIZE;
        int iconX = playerHeadButton.getX() + (PLAYER_HEAD_BUTTON_SIZE - iconSize) / 2;
        int iconY = playerHeadButton.getY() + (playerHeadButton.getHeight() - iconSize) / 2;
        renderCurrentPlayerFace(context, player, iconX, iconY, iconSize);
        if (!showPlayerHead) {
            context.fill(iconX, iconY, iconX + iconSize, iconY + iconSize, 0x88000000);
        }
    }

    private record ScreenPoint(int x, int y) {
    }

    private record TerrainCell(int minX, int minZ, int maxX, int maxZ, int height, int baseColor) {
    }

    private record TerrainSnapshot(int minX, int minZ, int maxX, int maxZ, int sampleStep,
                                   int minTerrainHeight, int maxTerrainHeight, TerrainCell[][] cells) {
        private boolean covers(int visibleMinX, int visibleMinZ, int visibleMaxX, int visibleMaxZ, int requiredSampleStep) {
            return sampleStep == requiredSampleStep
                    && visibleMinX >= minX
                    && visibleMinZ >= minZ
                    && visibleMaxX <= maxX
                    && visibleMaxZ <= maxZ;
        }
    }

    private void renderTerrain(GuiGraphics context, double mapCenterX, double mapCenterZ, float scale) {
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        int centerBlockX = (int) Math.floor(mapCenterX);
        int centerBlockZ = (int) Math.floor(mapCenterZ);
        int radius = Math.max(1, (int) Math.ceil((MAP_HALF_SIZE - 1) / scale) + 1);
        int sampleStep = Math.max(1, (int) Math.ceil((double) TERRAIN_TARGET_CELL_PIXELS / scale));
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;

        if (terrainSnapshot == null
                || !terrainSnapshot.covers(visibleMinX, visibleMinZ, visibleMaxX, visibleMaxZ, sampleStep)) {
            terrainSnapshot = createTerrainSnapshot(centerBlockX, centerBlockZ, radius, sampleStep);
            releaseTerrainTexture();
        }
        if (terrainSnapshot == null) {
            return;
        }

        renderTerrainTexture(context, terrainSnapshot);
    }

    private TerrainSnapshot createTerrainSnapshot(int centerBlockX, int centerBlockZ, int visibleRadius, int sampleStep) {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }

        int cacheRadius = visibleRadius + TERRAIN_CACHE_MARGIN_BLOCKS;
        int minX = centerBlockX - cacheRadius;
        int maxX = centerBlockX + cacheRadius;
        int minZ = centerBlockZ - cacheRadius;
        int maxZ = centerBlockZ + cacheRadius;
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

        // Cache an empty result as well, otherwise an unloaded/empty area would be re-sampled every frame.
        if (minTerrainHeight == Integer.MAX_VALUE) {
            minTerrainHeight = 0;
            maxTerrainHeight = 0;
        }

        return new TerrainSnapshot(minX, minZ, maxX, maxZ, sampleStep,
                minTerrainHeight, maxTerrainHeight, cells);
    }

    private void renderTerrainTexture(GuiGraphics context, TerrainSnapshot snapshot) {
        if (terrainTextureLocation == null) {
            terrainTextureLocation = createTerrainTexture(snapshot);
        }
        if (terrainTextureLocation == null) {
            return;
        }

        int textureWidth = snapshot.maxX() - snapshot.minX() + 1;
        int textureHeight = snapshot.maxZ() - snapshot.minZ() + 1;
        context.blit(
                terrainTextureLocation,
                snapshot.minX(), snapshot.minZ(),
                textureWidth, textureHeight,
                0.0F, 0.0F,
                textureWidth, textureHeight,
                textureWidth, textureHeight
        );
    }

    private ResourceLocation createTerrainTexture(TerrainSnapshot snapshot) {
        if (minecraft == null) {
            return null;
        }

        TerrainCell[][] cells = snapshot.cells();
        if (cells.length == 0 || cells[0].length == 0) {
            return null;
        }

        int textureWidth = snapshot.maxX() - snapshot.minX() + 1;
        int textureHeight = snapshot.maxZ() - snapshot.minZ() + 1;
        NativeImage image = new NativeImage(textureWidth, textureHeight, true);
        int reliefRange = snapshot.maxTerrainHeight() - snapshot.minTerrainHeight();
        int contourInterval = getTerrainContourInterval(reliefRange);

        for (int cellX = 0; cellX < cells.length; cellX++) {
            for (int cellZ = 0; cellZ < cells[cellX].length; cellZ++) {
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
                        : (cell.height() - snapshot.minTerrainHeight()) / (float) reliefRange;

                int color = shadeTerrainColor(cell.baseColor(), slopeDelta, elevation);
                int nativeColor = FastColor.ABGR32.fromArgb32(color);
                int minPixelX = cell.minX() - snapshot.minX();
                int minPixelZ = cell.minZ() - snapshot.minZ();
                int maxPixelX = cell.maxX() - snapshot.minX();
                int maxPixelZ = cell.maxZ() - snapshot.minZ();

                for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        image.setPixelRGBA(pixelX, pixelZ, nativeColor);
                    }
                }

                boolean northContour = cellZ > 0
                        && Math.floorDiv(cell.height(), contourInterval) != Math.floorDiv(northHeight, contourInterval);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height(), contourInterval) != Math.floorDiv(westHeight, contourInterval);
                int contourColor = FastColor.ABGR32.fromArgb32(blendTerrainContour(color));

                if (northContour && minPixelZ < maxPixelZ) {
                    for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                        image.setPixelRGBA(pixelX, minPixelZ, contourColor);
                    }
                }
                if (westContour && minPixelX < maxPixelX) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        image.setPixelRGBA(minPixelX, pixelZ, contourColor);
                    }
                }
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        return minecraft.getTextureManager().register("mca_blueprint_terrain", texture);
    }

    private static int blendTerrainContour(int baseColor) {
        int overlayAlpha = (TERRAIN_CONTOUR_COLOR >>> 24) & 0xff;
        int inverseAlpha = 255 - overlayAlpha;
        int red = ((((TERRAIN_CONTOUR_COLOR >> 16) & 0xff) * overlayAlpha)
                + (((baseColor >> 16) & 0xff) * inverseAlpha)) / 255;
        int green = ((((TERRAIN_CONTOUR_COLOR >> 8) & 0xff) * overlayAlpha)
                + (((baseColor >> 8) & 0xff) * inverseAlpha)) / 255;
        int blue = (((TERRAIN_CONTOUR_COLOR & 0xff) * overlayAlpha)
                + ((baseColor & 0xff) * inverseAlpha)) / 255;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private void releaseTerrainTexture() {
        if (terrainTextureLocation != null && minecraft != null) {
            minecraft.getTextureManager().release(terrainTextureLocation);
        }
        terrainTextureLocation = null;
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

    private MapGeometry getMapGeometry(Integer selectedFloor) {
        int cacheKey = selectedFloor == null ? ALL_FLOORS_GEOMETRY_KEY : selectedFloor;
        return mapGeometryCache.computeIfAbsent(cacheKey, ignored -> {
            List<MapFootprintLayer> footprintLayers = selectedFloor == null
                    ? buildAllFloorsRoomFootprintLayers()
                    : buildSelectedFloorFootprintLayers(selectedFloor);
            List<MapStructureLayer> structureLayers = getStructureLayers();
            List<MapIconLayer> iconLayers = buildRoomIconLayers(footprintLayers);
            List<Building> groupedBuildings = village.getBuildings().values().stream()
                    .filter(Building::isComplete)
                    .filter(building -> building.getBuildingType().grouped())
                    .filter(building -> floorLayout.isBuildingVisible(building, selectedFloor))
                    .sorted(Comparator.comparingInt(Building::getId))
                    .toList();
            return new MapGeometry(
                    footprintLayers, structureLayers, iconLayers, groupedBuildings);
        });
    }

    private List<MapStructureLayer> getStructureLayers() {
        if (structureLayerCache != null) {
            return structureLayerCache;
        }

        // Every canonical structure has a real registered Ground Floor room. Structure
        // outline geometry is therefore built exactly once from semantic floor 0 and is
        // independent of whichever floor tab is currently selected.
        structureLayerCache = List.copyOf(buildStructureLayers(buildSelectedFloorFootprintLayers(0)));
        return structureLayerCache;
    }

    private List<MapFootprintLayer> buildSelectedFloorFootprintLayers(int selectedFloor) {
        List<Building> rooms = village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .filter(building -> floorLayout.isBuildingVisible(building, selectedFloor))
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        List<MapFootprintLayer> layers = new ArrayList<>();
        for (Building room : rooms) {
            Set<BlueprintMapFootprint.Cell> footprintCells = getRoomFootprintCells(room);
            if (footprintCells.isEmpty()) {
                continue;
            }

            layers.add(new MapFootprintLayer(
                    room,
                    footprintCells,
                    BlueprintMapFootprint.rowSpans(footprintCells),
                    BlueprintMapFootprint.outerEdges(footprintCells),
                    selectedFloor));
        }
        return layers;
    }

    private List<MapFootprintLayer> buildAllFloorsRoomFootprintLayers() {
        List<Integer> floorPriority = getAllFloorsPriority();

        List<Building> rooms = village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .sorted(Comparator
                        .comparingInt(Building::getEffectiveStructureId)
                        .thenComparingInt(Building::getId))
                .toList();

        List<MapFootprintLayer> layers = new ArrayList<>();
        for (int floorOrdinal : floorPriority) {
            for (Building room : rooms) {
                if (!floorLayout.isBuildingVisible(room, floorOrdinal)) {
                    continue;
                }

                Set<BlueprintMapFootprint.Cell> footprintCells = getRoomFootprintCells(room);
                if (footprintCells.isEmpty()) {
                    continue;
                }

                // All Floors shows the complete footprint of every semantic floor. Do not
                // clip a basement/upper room merely because another floor overlaps in X/Z.
                layers.add(new MapFootprintLayer(
                        room,
                        footprintCells,
                        BlueprintMapFootprint.rowSpans(footprintCells),
                        BlueprintMapFootprint.outerEdges(footprintCells),
                        floorOrdinal));
            }
        }
        return layers;
    }

    private List<Integer> getAllFloorsPriority() {
        List<Integer> floorPriority = new ArrayList<>();
        if (floorLayout.ordinals().contains(0)) {
            floorPriority.add(0);
        }
        floorLayout.ordinals().stream()
                .filter(ordinal -> ordinal != 0)
                .sorted(Comparator
                        .comparingInt((Integer ordinal) -> Math.abs(ordinal))
                        .thenComparingInt(Integer::intValue))
                .forEach(floorPriority::add);
        return List.copyOf(floorPriority);
    }

    private List<MapStructureLayer> buildStructureLayers(List<MapFootprintLayer> visibleRoomLayers) {
        Map<Integer, LinkedHashSet<BlueprintMapFootprint.Cell>> roomCellsByStructure = new HashMap<>();
        for (MapFootprintLayer roomLayer : visibleRoomLayers) {
            roomCellsByStructure
                    .computeIfAbsent(roomLayer.building().getEffectiveStructureId(),
                            ignored -> new LinkedHashSet<>())
                    .addAll(roomLayer.footprintCells());
        }

        List<Building> roots = village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isStructureRoot)
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();

        List<MapStructureLayer> layers = new ArrayList<>();
        for (Building root : roots) {
            Set<BlueprintMapFootprint.Cell> roomCells =
                    roomCellsByStructure.get(root.getEffectiveStructureId());
            if (roomCells == null || roomCells.isEmpty()) {
                continue;
            }

            // Grow the exact union of registered rooms by the configured building-outline
            // width. This produces a visible structure area without reducing L/U shapes to
            // one min/max rectangle.
            Set<BlueprintMapFootprint.Cell> buildingCells =
                    BlueprintMapFootprint.expand(roomCells, BUILDING_OUTLINE_WIDTH);
            LinkedHashSet<BlueprintMapFootprint.Cell> outlineArea =
                    new LinkedHashSet<>(buildingCells);
            outlineArea.removeAll(roomCells);

            layers.add(new MapStructureLayer(
                    root,
                    outlineArea,
                    BlueprintMapFootprint.rowSpans(outlineArea),
                    BlueprintMapFootprint.outerEdges(buildingCells)));
        }
        return layers;
    }

    private List<MapIconLayer> buildRoomIconLayers(List<MapFootprintLayer> roomLayers) {
        TreeMap<Integer, List<MapFootprintLayer>> layersByRoom = new TreeMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            if (hasRenderableBuildingIcon(layer.building())) {
                layersByRoom.computeIfAbsent(layer.building().getId(), ignored -> new ArrayList<>())
                        .add(layer);
            }
        }

        List<MapIconLayer> icons = new ArrayList<>();
        for (List<MapFootprintLayer> layers : layersByRoom.values()) {
            Building room = layers.getFirst().building();
            LinkedHashSet<BlueprintMapFootprint.Cell> cells = new LinkedHashSet<>();
            for (MapFootprintLayer layer : layers) {
                cells.addAll(layer.footprintCells());
            }
            FootprintCenter center = getFootprintCenter(cells);
            icons.add(new MapIconLayer(
                    room,
                    layers.getFirst().floorOrdinal(),
                    center.x(),
                    center.z(),
                    getRoomIconScale(cells)));
        }
        return icons;
    }

    private static List<MapFootprintLayer> getAllFloorsOutlineLayers(
            List<MapFootprintLayer> roomLayers,
            List<MapHoverTarget> hoverTargets) {
        Map<Integer, MapFootprintLayer> outlinedByStructure = new LinkedHashMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            outlinedByStructure.putIfAbsent(
                    layer.building().getEffectiveStructureId(), layer);
        }

        for (MapHoverTarget hoverTarget : hoverTargets) {
            Building hoveredRoom = hoverTarget.building();
            if (!hoveredRoom.isFunctionalRoom()) {
                continue;
            }
            roomLayers.stream()
                    .filter(layer -> layer.building().getId() == hoveredRoom.getId())
                    .filter(layer -> Objects.equals(layer.floorOrdinal(), hoverTarget.floorOrdinal()))
                    .findFirst()
                    .ifPresent(layer -> outlinedByStructure.put(
                            hoveredRoom.getEffectiveStructureId(), layer));
        }
        return List.copyOf(outlinedByStructure.values());
    }

    private boolean hasRenderableBuildingIcon(Building building) {
        BuildingType buildingType = building.getBuildingType();
        return buildingType.visible() && buildingType.hasIcon();
    }

    private static Set<BlueprintMapFootprint.Cell> getRoomFootprintCells(Building building) {
        Set<BlueprintMapFootprint.Cell> detectedCells =
                BlueprintMapFootprint.fromFloorRegions(building.getFloorRegions());
        if (!detectedCells.isEmpty()) {
            return detectedCells;
        }

        BlockPos rawMin = building.getRawPos0();
        BlockPos rawMax = building.getRawPos1();
        return BlueprintMapFootprint.rectangle(
                rawMin.getX(), rawMin.getZ(), rawMax.getX(), rawMax.getZ());
    }

    private static void renderRoomFootprint(
            GuiGraphics context,
            List<BlueprintMapFootprint.RowSpan> spans,
            int baseColor,
            boolean selectedFloor,
            boolean hovered,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        // A room is the floor area itself, not another thick building outline. Drawing a
        // one-block opaque ring becomes several screen pixels wide at higher map scales and
        // visually duplicates the structure border. Keep the whole floor uniformly shaded.
        int fillAlpha = hovered
                ? ROOM_FILL_ALPHA_HOVERED
                : selectedFloor ? ROOM_FILL_ALPHA_SELECTED_FLOOR : ROOM_FILL_ALPHA_ALL_FLOORS;
        int color = withAlpha(brightenColor(baseColor, ROOM_FILL_BRIGHTEN_FACTOR), fillAlpha);
        renderCellSpansScreenSpace(
                context, spans, color, centerX, centerY, mapCenterX, mapCenterZ, scale);
    }

    private static void renderRoomOutlineScreenSpace(
            GuiGraphics context,
            List<BlueprintMapFootprint.Edge> edges,
            int baseColor,
            boolean selectedFloor,
            boolean hovered,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        int outlineAlpha = hovered
                ? ROOM_BORDER_ALPHA_HOVERED
                : selectedFloor ? ROOM_BORDER_ALPHA_SELECTED_FLOOR : ROOM_BORDER_ALPHA_ALL_FLOORS;
        int outlineColor = withAlpha(brightenColor(baseColor, ROOM_BORDER_BRIGHTEN_FACTOR), outlineAlpha);
        renderOutlineScreenSpace(
                context, edges, outlineColor, centerX, centerY, mapCenterX, mapCenterZ, scale);
    }

    private static void renderStructureOutlineScreenSpace(
            GuiGraphics context,
            List<BlueprintMapFootprint.Edge> edges,
            int baseColor,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        int outlineColor = withAlpha(
                darkenColor(baseColor, BUILDING_BORDER_DARKEN_FACTOR),
                BUILDING_BORDER_ALPHA);
        renderOutlineScreenSpace(
                context, edges, outlineColor, centerX, centerY, mapCenterX, mapCenterZ, scale);
    }

    private static void renderOutlineScreenSpace(
            GuiGraphics context,
            List<BlueprintMapFootprint.Edge> edges,
            int color,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        for (BlueprintMapFootprint.Edge edge : edges) {
            int x0 = (int) Math.round(mapCoordinateToScreen(
                    edge.x0(), centerX, mapCenterX, scale));
            int z0 = (int) Math.round(mapCoordinateToScreen(
                    edge.z0(), centerY, mapCenterZ, scale));
            int x1 = (int) Math.round(mapCoordinateToScreen(
                    edge.x1(), centerX, mapCenterX, scale));
            int z1 = (int) Math.round(mapCoordinateToScreen(
                    edge.z1(), centerY, mapCenterZ, scale));

            if (edge.z0() == edge.z1()) {
                int minX = Math.min(x0, x1);
                int maxX = Math.max(x0, x1);
                context.fill(minX, z0, Math.max(minX + 1, maxX + 1), z0 + 1, color);
            } else {
                int minZ = Math.min(z0, z1);
                int maxZ = Math.max(z0, z1);
                context.fill(x0, minZ, x0 + 1, Math.max(minZ + 1, maxZ + 1), color);
            }
        }
    }

    private static double mapCoordinateToScreen(
            double coordinate,
            int screenCenter,
            double mapCenter,
            float scale) {
        return screenCenter + (coordinate - mapCenter) * scale;
    }

    private static boolean isRoomHovered(
            MapFootprintLayer layer,
            BlueprintMapFootprint.Cell hoveredMapCell,
            int mouseScreenX,
            int mouseScreenY,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        // footprintCells is a HashSet-backed immutable set, so ordinary room-fill hover is
        // one exact world-cell lookup instead of scanning and transforming every room cell.
        if (layer.footprintCells().contains(hoveredMapCell)) {
            return true;
        }

        return isOutlineHovered(layer.outlineEdges(), mouseScreenX, mouseScreenY,
                centerX, centerY, mapCenterX, mapCenterZ, scale);
    }

    private static boolean isOutlineHovered(
            List<BlueprintMapFootprint.Edge> edges,
            int mouseScreenX,
            int mouseScreenY,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        for (BlueprintMapFootprint.Edge edge : edges) {
            int x0 = (int) Math.round(mapCoordinateToScreen(
                    edge.x0(), centerX, mapCenterX, scale));
            int z0 = (int) Math.round(mapCoordinateToScreen(
                    edge.z0(), centerY, mapCenterZ, scale));
            int x1 = (int) Math.round(mapCoordinateToScreen(
                    edge.x1(), centerX, mapCenterX, scale));
            int z1 = (int) Math.round(mapCoordinateToScreen(
                    edge.z1(), centerY, mapCenterZ, scale));
            if (edge.z0() == edge.z1()) {
                int minX = Math.min(x0, x1);
                int maxX = Math.max(minX + 1, Math.max(x0, x1) + 1);
                if (mouseScreenY == z0 && mouseScreenX >= minX && mouseScreenX < maxX) {
                    return true;
                }
            } else {
                int minZ = Math.min(z0, z1);
                int maxZ = Math.max(minZ + 1, Math.max(z0, z1) + 1);
                if (mouseScreenX == x0 && mouseScreenY >= minZ && mouseScreenY < maxZ) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void renderStructureShade(
            GuiGraphics context,
            List<BlueprintMapFootprint.RowSpan> shadeSpans,
            int baseColor,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        int color = withAlpha(baseColor, BUILDING_SHADE_ALPHA);
        renderCellSpansScreenSpace(
                context, shadeSpans, color, centerX, centerY, mapCenterX, mapCenterZ, scale);
    }

    private static void renderCellSpansScreenSpace(
            GuiGraphics context,
            List<BlueprintMapFootprint.RowSpan> spans,
            int color,
            int centerX,
            int centerY,
            double mapCenterX,
            double mapCenterZ,
            float scale) {
        for (BlueprintMapFootprint.RowSpan span : spans) {
            int x0 = (int) Math.round(mapCoordinateToScreen(
                    span.minX(), centerX, mapCenterX, scale));
            int z0 = (int) Math.round(mapCoordinateToScreen(
                    span.z(), centerY, mapCenterZ, scale));
            int x1 = (int) Math.round(mapCoordinateToScreen(
                    span.maxX() + 1, centerX, mapCenterX, scale));
            int z1 = (int) Math.round(mapCoordinateToScreen(
                    span.z() + 1, centerY, mapCenterZ, scale));
            context.fill(Math.min(x0, x1), Math.min(z0, z1),
                    Math.max(Math.min(x0, x1) + 1, Math.max(x0, x1)),
                    Math.max(Math.min(z0, z1) + 1, Math.max(z0, z1)), color);
        }
    }

    private void drawScaledBuildingIcon(
            GuiGraphics context,
            ResourceLocation texture,
            double x,
            double y,
            int u,
            int v,
            float scale) {
        PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.translate(x, y, 0.0D);
        matrices.scale(scale, scale, 1.0F);
        matrices.translate(-6.6D, -6.6D, 0.0D);
        matrices.scale(0.66F, 0.66F, 0.66F);
        context.blit(texture, 0, 0, u, v, 20, 20);
        matrices.popPose();
    }

    private static float getRoomIconScale(Set<BlueprintMapFootprint.Cell> cells) {
        float scale = (float) Math.sqrt(Math.max(1, cells.size())) / ROOM_ICON_AREA_REFERENCE;
        return Math.max(ROOM_ICON_MIN_SCALE, Math.min(ROOM_ICON_MAX_SCALE, scale));
    }

    private static int brightenColor(int color, float factor) {
        int rgb = color & 0x00ffffff;
        int red = Math.min(255, Math.round(((rgb >> 16) & 0xff) * factor));
        int green = Math.min(255, Math.round(((rgb >> 8) & 0xff) * factor));
        int blue = Math.min(255, Math.round((rgb & 0xff) * factor));
        return red << 16 | green << 8 | blue;
    }

    private static int darkenColor(int color, float factor) {
        int rgb = color & 0x00ffffff;
        int red = Math.round(((rgb >> 16) & 0xff) * factor);
        int green = Math.round(((rgb >> 8) & 0xff) * factor);
        int blue = Math.round((rgb & 0xff) * factor);
        return red << 16 | green << 8 | blue;
    }

    private static FootprintCenter getFootprintCenter(Set<BlueprintMapFootprint.Cell> cells) {
        int minX = cells.stream().mapToInt(BlueprintMapFootprint.Cell::x).min().orElseThrow();
        int maxX = cells.stream().mapToInt(BlueprintMapFootprint.Cell::x).max().orElseThrow();
        int minZ = cells.stream().mapToInt(BlueprintMapFootprint.Cell::z).min().orElseThrow();
        int maxZ = cells.stream().mapToInt(BlueprintMapFootprint.Cell::z).max().orElseThrow();
        return new FootprintCenter((minX + maxX + 1) / 2.0D, (minZ + maxZ + 1) / 2.0D);
    }

    private record MapGeometry(List<MapFootprintLayer> footprintLayers,
                               List<MapStructureLayer> structureLayers,
                               List<MapIconLayer> iconLayers,
                               List<Building> groupedBuildings) {
        private MapGeometry {
            footprintLayers = List.copyOf(footprintLayers);
            structureLayers = List.copyOf(structureLayers);
            iconLayers = List.copyOf(iconLayers);
            groupedBuildings = List.copyOf(groupedBuildings);
        }
    }

    private record MapFootprintLayer(Building building,
                                     Set<BlueprintMapFootprint.Cell> footprintCells,
                                     List<BlueprintMapFootprint.RowSpan> fillSpans,
                                     List<BlueprintMapFootprint.Edge> outlineEdges,
                                     Integer floorOrdinal) {
        private MapFootprintLayer {
            footprintCells = Set.copyOf(footprintCells);
            fillSpans = List.copyOf(fillSpans);
            outlineEdges = List.copyOf(outlineEdges);
        }
    }

    private record MapStructureLayer(Building root,
                                     Set<BlueprintMapFootprint.Cell> shadeCells,
                                     List<BlueprintMapFootprint.RowSpan> shadeSpans,
                                     List<BlueprintMapFootprint.Edge> borderEdges) {
        private MapStructureLayer {
            shadeCells = Set.copyOf(shadeCells);
            shadeSpans = List.copyOf(shadeSpans);
            borderEdges = List.copyOf(borderEdges);
        }
    }

    private record MapIconLayer(Building building,
                                Integer floorOrdinal,
                                double iconX,
                                double iconZ,
                                float iconScale) {
    }

    private record MapHoverTarget(Building building, Integer floorOrdinal) {
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

    private static void addRoomHover(List<MapHoverTarget> hoverTargets,
                                     Building building,
                                     Integer floorOrdinal) {
        if (building.getBuildingType().grouped()) {
            MapHoverTarget target = new MapHoverTarget(building, floorOrdinal);
            if (!hoverTargets.contains(target)) {
                hoverTargets.add(target);
            }
            return;
        }

        int structureId = building.getEffectiveStructureId();

        // All Floors can contain vertically overlapping rooms. Keep the first room in the
        // established floor-priority order instead of stacking several tooltips at one pixel.
        boolean alreadyHasRoom = hoverTargets.stream().anyMatch(target ->
                !target.building().getBuildingType().grouped()
                        && target.building().getEffectiveStructureId() == structureId);
        if (!alreadyHasRoom) {
            hoverTargets.add(new MapHoverTarget(building, floorOrdinal));
        }
    }

    private List<Component> getBuildingTooltip(Building hoverBuilding,
                                               Integer floorOrdinal) {
        if (hoverBuilding.isStructureRoot()) {
            return getAllFloorsTooltip(hoverBuilding);
        }

        List<Component> lines = new LinkedList<>();
        if (floorOrdinal != null) {
            lines.add(getTooltipFloorLabel(floorOrdinal));
        }

        BuildingType roomType = BuildingTypes.getInstance().getBuildingType(hoverBuilding.getType());
        lines.add(Component.literal("  ").append(getBuildingTypeTooltipLabel(roomType)));

        village.getResidents(hoverBuilding.getId()).forEach(name ->
                lines.add(Component.literal("    ")
                        .append(Component.literal(name).withStyle(ChatFormatting.GRAY))));
        getBlockTooltipLines(List.of(hoverBuilding), floorOrdinal).forEach(item ->
                lines.add(Component.literal("    ").append(item)));
        return lines;
    }

    private record FootprintCenter(double x, double z) {
    }

    private List<Component> getAllFloorsTooltip(Building structureBuilding) {
        List<Building> structureRooms = getStructureTooltipBuildings(structureBuilding);
        List<Component> lines = new LinkedList<>();

        for (int floorOrdinal : floorLayout.ordinalsFor(structureBuilding)) {
            lines.add(getTooltipFloorLabel(floorOrdinal));

            List<Building> floorRooms = structureRooms.stream()
                    .filter(building -> floorLayout.isBuildingVisible(building, floorOrdinal))
                    .toList();

            for (Building room : floorRooms) {
                BuildingType roomType = BuildingTypes.getInstance().getBuildingType(room.getType());
                lines.add(Component.literal("  ").append(getBuildingTypeTooltipLabel(roomType)));
                village.getResidents(room.getId()).forEach(name ->
                        lines.add(Component.literal("    ")
                                .append(Component.literal(name).withStyle(ChatFormatting.GRAY))));
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

    private Optional<Building> getStructureRoot(Building building) {
        if (building.isStructureRoot()) {
            return Optional.of(building);
        }
        int structureId = building.getEffectiveStructureId();
        return village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isStructureRoot)
                .filter(candidate -> candidate.getEffectiveStructureId() == structureId)
                .findFirst();
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

    @Override
    public void removed() {
        releaseTerrainTexture();
        super.removed();
    }

    public void setVillage(Village village) {
        boolean logFloorRoomDebug = logNextFloorRoomVillageResponse;
        logNextFloorRoomVillageResponse = false;
        Integer selectedBefore = selectedFloorOrdinal;
        boolean pendingFloorSelection = selectPlayerFloorOnNextVillageResponse;
        this.village = village;
        // Terrain is world-derived and independent of village sync packets. Keeping the
        // snapshot/texture alive here prevents ordinary Blueprint data refreshes from
        // forcing an expensive terrain re-sample and GPU upload.
        this.mapGeometryCache.clear();
        this.structureLayerCache = null;
        this.floorLayout = village == null ? BlueprintFloorLayout.empty() : BlueprintFloorLayout.build(village);
        Village.StructuralLookup structuralLookup = getPlayerStructuralLookup();
        Village.StructuralPosition structuralPosition = structuralLookup.position();
        BlockPos playerPos = minecraft != null && minecraft.player != null
                ? minecraft.player.blockPosition()
                : null;
        if (logFloorRoomDebug) {
            MCA.LOGGER.info("[FloorRoomDebug] side=client stage=village-response pos={} villageId={} buildingCount={} lookup={} lookupBuilding={} pendingFloorSelect={} selectedBefore={} availableFloors={}",
                    playerPos, village == null ? -1 : village.getId(), village == null ? 0 : village.getBuildings().size(),
                    structuralPosition, describeBuilding(structuralLookup.building().orElse(null)), pendingFloorSelection,
                    selectedBefore, floorLayout.ordinals());
            logClientBuildings(village);
        }
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

        if (logFloorRoomDebug) {
            MCA.LOGGER.info("[FloorRoomDebug] side=client stage=village-response-applied pos={} lookup={} selectedAfter={} availableFloors={}",
                    playerPos, structuralPosition, selectedFloorOrdinal, floorLayout.ordinals());
        }

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
