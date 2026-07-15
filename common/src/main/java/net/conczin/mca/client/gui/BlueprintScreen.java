package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.widget.LegacyImageButton;
import net.conczin.mca.client.gui.widget.TooltipButtonWidget;
import net.conczin.mca.client.gui.widget.WidgetUtils;
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
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Consumer;

public class BlueprintScreen extends ExtendedScreen {
    //gui element Y positions
    private static final int POSITION_TAXES = -60;
    private static final int POSITION_BIRTH = -10;
    private static final int POSITION_MARRIAGE = 40;
    private static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static final int MAP_HALF_SIZE = 75;
    private static final int MAP_INNER_MARGIN = 6;
    private static final float MAP_MAX_FIT_SCALE = 2.0f;
    private static final int ROOM_INNER_PADDING = 1;
    private static final int ROOM_SHADOW_COLOR = 0x50000000;
    private static final int ROOM_FILL_ALPHA_ALL_FLOORS = 0x18;
    private static final int ROOM_FILL_ALPHA_SELECTED_FLOOR = 0x38;
    private static final int ROOM_FILL_ALPHA_HOVERED = 0x58;
    private static Integer rememberedFloorOrdinal;
    private static MapScaleMode rememberedMapScaleMode = MapScaleMode.FIT;
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
    private ButtonWidget mapScaleButton;
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget removeRoomButton;
    private ButtonWidget removeBuildingButton;
    private ButtonWidget advancedButton;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
    private MapScaleMode mapScaleMode = rememberedMapScaleMode;
    private boolean selectPlayerFloorOnNextVillageResponse;
    private boolean showBuildingIcons = true;
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
        mapScaleButton = null;
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
                if (page.equals(p)) {
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
            case "advanced":
                //auto-scan
                bx = width / 2 + 180 - 64 - 16;
                by = height / 2 - 56;
                MutableComponent text = Component.translatable("gui.blueprint.autoScan");
                if (village.isAutoScan()) {
                    text.withStyle(ChatFormatting.GREEN);
                } else {
                    text.withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.STRIKETHROUGH);
                }
                addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, text, Component.translatable("gui.blueprint.autoScan.tooltip"), b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.AUTO_SCAN));
                    village.toggleAutoScan();
                    setPage(page);
                }));
                by += 22;

                //restrict access
                addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.restrictAccess", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
                }));
                by += 22;

                //add whole building
                addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.addBuilding", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                }));
                by += 22 * 4;

                //rename village
                if (isVillage) {
                    addRenderableWidget(new ButtonWidget(bx, by, 96, 20, Component.translatable("gui.blueprint.renameVillage"), b -> {
                        setPage("rename");
                    }));
                }
                break;
            case "map":
                // A grouped POI such as the town bell keeps the settlement alive, but
                // rooms still need a complete structural root to attach to.
                bx = width / 2 + 180 - 64 - 16;
                by = height / 2 - 56 + 22 * 3;
                structureScanButton = addRenderableWidget(new TooltipButtonWidget(
                        bx, by, 96, 20, getStructureScanTranslationKey(getPlayerStructuralLookup().position()), b -> {
                    requestStructureScan();
                }));
                by += 22;

                //remove only the room the player is currently standing in
                removeRoomButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.removeRoom", b -> {
                    MCA.LOGGER.debug("[BuildingRemove] stage=client-click action=REMOVE_ROOM");
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE_ROOM));
                }));
                by += 22;

                //remove building
                removeBuildingButton = addRenderableWidget(new ButtonWidget(bx, by, 96, 20,
                        Component.translatable("gui.blueprint.removeBuilding"), b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE));
                }));
                by += 22;

                //advanced
                if (!page.equals("advanced")) {
                    advancedButton = addRenderableWidget(new ButtonWidget(bx, by, 96, 20,
                            Component.translatable("gui.blueprint.advanced"), b -> {
                        setPage("advanced");
                    }));
                }

                int floorControlX = width / 2 - 75;
                int floorControlY = height / 2 + 87;
                floorPreviousButton = addRenderableWidget(new ButtonWidget(floorControlX, floorControlY, 24, 20,
                        Component.literal("<"), b -> changeSelectedFloor(-1)));
                floorLabelButton = addRenderableWidget(new ButtonWidget(floorControlX + 26, floorControlY, 98, 20,
                        Component.empty(), b -> {
                    selectFloor(null);
                }));
                floorNextButton = addRenderableWidget(new ButtonWidget(floorControlX + 126, floorControlY, 24, 20,
                        Component.literal(">"), b -> changeSelectedFloor(1)));
                buildingIconsButton = addRenderableWidget(new ButtonWidget(
                        floorControlX, floorControlY + 22, 96, 20,
                        getBuildingIconsLabel(), b -> {
                    showBuildingIcons = !showBuildingIcons;
                    updateBuildingIconsControl();
                }));
                mapScaleButton = addRenderableWidget(new ButtonWidget(
                        floorControlX + 98, floorControlY + 22, 52, 20,
                        getMapScaleLabel(), b -> cycleMapScale(), getMapScaleTooltip()));

                break;
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

        if (advancedButton != null) {
            advancedButton.setY(y);
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
        WidgetUtils.drawRectangle(context, left, top, right, bottom, 0xffffff88);

        //hint
        if (!village.isAutoScan() && village.getStructureCount() <= 1) {
            int hintY = floorLayout.ordinals().size() > 1 ? height / 2 + 134 : height / 2 + 90;
            context.drawCenteredString(font, Component.translatable("gui.blueprint.autoScanDisabled"), width / 2, hintY, 0xaaffffff);
        }

        double mapCenterX = (village.getBox().minX() + village.getBox().maxX() + 1) / 2.0D;
        double mapCenterZ = (village.getBox().minZ() + village.getBox().maxZ() + 1) / 2.0D;
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

        // The player is global map context, not part of a floor, and stays above every icon.
        // Render it in screen space with a minimum 3x3 footprint so Fit mode cannot
        // shrink a one-block marker below one physical GUI pixel.
        matrices.popPose();
        assert minecraft != null;
        LocalPlayer player = minecraft.player;
        if (player != null) {
            int playerScreenX = (int) Math.floor(centerX + (player.getX() - mapCenterX) * scale);
            int playerScreenY = (int) Math.floor(centerY + (player.getZ() - mapCenterZ) * scale);
            context.fill(
                    playerScreenX - 1, playerScreenY - 1,
                    playerScreenX + 2, playerScreenY + 2,
                    0xffff00ff
            );
        }
        context.disableScissor();

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
        MutableComponent label = Component.translatable("gui.blueprint.buildingIcons");
        return showBuildingIcons
                ? label.withStyle(ChatFormatting.GREEN)
                : label.withStyle(ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH);
    }

    private void updateBuildingIconsControl() {
        if (buildingIconsButton != null) {
            buildingIconsButton.setMessage(getBuildingIconsLabel());
        }
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

        //name
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(headerBuilding.getType());
        lines.add(Component.translatable("buildingType." + bt.name()));

        //residents
        LinkedHashSet<String> residents = new LinkedHashSet<>();
        for (Building building : tooltipBuildings) {
            residents.addAll(village.getResidents(building.getId()));
        }
        for (String name : residents) {
            lines.add(Component.literal(name));
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

            lines.add(getFloorLabel(floorOrdinal).copy().withStyle(ChatFormatting.GRAY));
            for (Building room : floorRooms) {
                BuildingType roomType = BuildingTypes.getInstance().getBuildingType(room.getType());
                lines.add(Component.literal("  ").append(Component.translatable("buildingType." + roomType.name())));
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
                        .withStyle(ChatFormatting.GRAY));
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
        updateMapScaleControl();
        updateStructureScanControl(structuralLookup);
        updateRemoveRoomControl(structuralLookup);

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
