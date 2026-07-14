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
    private static Integer rememberedFloorOrdinal;
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
    private TooltipButtonWidget structureScanButton;
    private TooltipButtonWidget removeRoomButton;
    private Integer selectedFloorOrdinal = rememberedFloorOrdinal;
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
        structureScanButton = null;
        removeRoomButton = null;

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
                    Network.sendToServer(new GetVillageRequest());
                    onClose();
                }));
                break;
            case "refresh":
                Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FULL_SCAN));
                Network.sendToServer(new GetVillageRequest());
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
                    Network.sendToServer(new GetVillageRequest());
                    village.toggleAutoScan();
                    setPage(page);
                }));
                by += 22;

                //restrict access
                addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.restrictAccess", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.FORCE_TYPE, "blocked"));
                    Network.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //add whole building
                addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.addBuilding", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.ADD));
                    Network.sendToServer(new GetVillageRequest());
                }));
                by += 22 * 4;

                //rename village
                if (isVillage) {
                    addRenderableWidget(new ButtonWidget(bx, by, 96, 20, Component.translatable("gui.blueprint.renameVillage"), b -> {
                        setPage("rename");
                    }));
                }
            case "map":
                // A grouped POI such as the town bell keeps the settlement alive, but
                // rooms still need a complete structural root to attach to.
                bx = width / 2 + 180 - 64 - 16;
                by = height / 2 - 56 + 22 * 3;
                structureScanButton = addRenderableWidget(new TooltipButtonWidget(
                        bx, by, 96, 20, getStructureScanTranslationKey(), b -> {
                    Network.sendToServer(new ReportBuildingMessage(getStructureScanAction()));
                    Network.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //remove only the room the player is currently standing in
                removeRoomButton = addRenderableWidget(new TooltipButtonWidget(bx, by, 96, 20, "gui.blueprint.removeRoom", b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE_ROOM));
                    Network.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //remove building
                addRenderableWidget(new ButtonWidget(bx, by, 96, 20, Component.translatable("gui.blueprint.removeBuilding"), b -> {
                    Network.sendToServer(new ReportBuildingMessage(ReportBuildingMessage.Action.REMOVE));
                    Network.sendToServer(new GetVillageRequest());
                }));
                by += 22;

                //advanced
                if (!page.equals("advanced")) {
                    addRenderableWidget(new ButtonWidget(bx, by, 96, 20, Component.translatable("gui.blueprint.advanced"), b -> {
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
                        floorControlX + 26, floorControlY + 22, 98, 20,
                        getBuildingIconsLabel(), b -> {
                    showBuildingIcons = !showBuildingIcons;
                    updateBuildingIconsControl();
                }));

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
            updateStructureScanControl();
            updateRemoveRoomControl();
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

    private ReportBuildingMessage.Action getStructureScanAction() {
        return village != null && village.hasStructuralBuilding()
                ? ReportBuildingMessage.Action.ADD_ROOM
                : ReportBuildingMessage.Action.ADD;
    }

    private String getStructureScanTranslationKey() {
        return getStructureScanAction() == ReportBuildingMessage.Action.ADD_ROOM
                ? "gui.blueprint.addRoom"
                : "gui.blueprint.addBuilding";
    }

    private void updateStructureScanControl() {
        if (structureScanButton != null) {
            structureScanButton.setMessage(getStructureScanTranslationKey());
        }
    }

    private void updateRemoveRoomControl() {
        if (removeRoomButton == null) {
            return;
        }

        boolean onGroundFloor = isPlayerOnStructuralGroundFloor();
        removeRoomButton.active = !onGroundFloor;
        removeRoomButton.setTooltip(Tooltip.create(Component.translatable(onGroundFloor
                ? "gui.blueprint.removeRoom.disabled.groundFloor"
                : "gui.blueprint.removeRoom.tooltip")));
    }

    private boolean isPlayerOnStructuralGroundFloor() {
        if (village == null || minecraft == null || minecraft.player == null) {
            return false;
        }

        BlockPos playerPos = minecraft.player.blockPosition();
        return village.getBuildings().values().stream()
                .filter(Building::isStructureRoot)
                .filter(building -> !building.getBuildingType().grouped())
                .anyMatch(building -> building.containsFloorPosition(playerPos)
                        && floorLayout.isBlockOnFloor(building, playerPos, 0));
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
        int mapSize = 75;
        int y = height / 2 + 8;
        Integer selectedFloor = selectedFloorOrdinal;
        WidgetUtils.drawRectangle(context, width / 2 - mapSize, y - mapSize, width / 2 + mapSize, y + mapSize, 0xffffff88);

        //hint
        if (!village.isAutoScan() && village.getBuildings().size() <= 1) {
            int hintY = floorLayout.ordinals().size() > 1 ? height / 2 + 134 : height / 2 + 90;
            context.drawCenteredString(font, Component.translatable("gui.blueprint.autoScanDisabled"), width / 2, hintY, 0xaaffffff);
        }

        matrices.pushPose();

        //center and scale the map
        int horizontalSpan = Math.max(village.getBox().getXSpan(), village.getBox().getZSpan());
        float sc = Math.min((float) mapSize / (horizontalSpan + 3) * 2, 2.0f);
        int mouseLocalX = (int) ((mouseX - width / 2.0) / sc + village.getCenter().getX());
        int mouseLocalY = (int) ((mouseY - y) / sc + village.getCenter().getZ());
        matrices.translate(width / 2.0, y, 0);
        matrices.scale(sc, sc, 0.0f);
        matrices.translate(-village.getCenter().getX(), -village.getCenter().getZ(), 0);

        //show the players location
        assert minecraft != null;
        LocalPlayer player = minecraft.player;
        if (player != null && (selectedFloor == null || floorLayout.isPlayerVisible(player, selectedFloor, village))) {
            WidgetUtils.drawRectangle(context, (int) player.getX() - 1, (int) player.getZ() - 1, (int) player.getX() + 1, (int) player.getZ() + 1, 0xffff00ff);
        }

        //buildings
        Map<Integer, Building> hoverBuildings = new LinkedHashMap<>();
        List<Building> iconBuildings = new ArrayList<>();
        for (Building building : village.getBuildings().values()) {
            if (!building.isComplete() || !floorLayout.isBuildingVisible(building, selectedFloor)) {
                continue;
            }

            BuildingType bt = building.getBuildingType();
            if (bt.isIcon()) {
                BlockPos c = building.getCenter();
                iconBuildings.add(building);

                //tooltip
                int margin = 6;
                if (c.distSqr(new Vec3i(mouseLocalX, c.getY(), mouseLocalY)) < margin * margin) {
                    addHoveredBuilding(hoverBuildings, building, selectedFloor);
                }
            } else {
                List<BlueprintFloorLayout.RegionBounds> renderRegions = floorLayout.regionsFor(building, selectedFloor);
                for (BlueprintFloorLayout.RegionBounds region : renderRegions) {
                    WidgetUtils.drawRectangle(
                            context,
                            region.minX(), region.minZ(), region.maxX(), region.maxZ(),
                            bt.getColor()
                    );
                }

                if (bt.visible() && bt.hasIcon()) {
                    iconBuildings.add(building);
                }

                //tooltip
                int margin = 1;
                boolean hovering = renderRegions.stream().anyMatch(region ->
                        mouseLocalX >= region.minX() - margin && mouseLocalX <= region.maxX() + margin
                                && mouseLocalY >= region.minZ() - margin && mouseLocalY <= region.maxZ() + margin);
                if (hovering) {
                    addHoveredBuilding(hoverBuildings, building, selectedFloor);
                }
            }
        }

        // Icons deliberately render last so a floor region can never cover them.
        if (showBuildingIcons) {
            Set<Integer> renderedStructureIcons = new HashSet<>();
            for (Building building : iconBuildings) {
                BuildingType bt = building.getBuildingType();
                if (bt.isIcon()) {
                    BlockPos center = building.getCenter();
                    drawBuildingIcon(context, ICON_TEXTURES, center.getX(), center.getZ(), bt.iconU(), bt.iconV());
                } else if (renderedStructureIcons.add(building.getEffectiveStructureId())) {
                    BlockPos iconPosition = floorLayout.iconPositionFor(building, village);
                    drawBuildingIcon(context, ICON_TEXTURES,
                            iconPosition.getX(), iconPosition.getZ(), bt.iconU(), bt.iconV());
                }
            }
        }

        matrices.popPose();

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

    private void changeSelectedFloor(int direction) {
        List<Integer> ordinals = floorLayout.ordinals();
        if (ordinals.size() <= 1) {
            updateFloorControls();
            return;
        }

        if (selectedFloorOrdinal == null) {
            selectFloor(ordinals.contains(0) ? 0 : ordinals.getFirst());
            return;
        }

        int currentIndex = ordinals.indexOf(selectedFloorOrdinal);
        if (currentIndex < 0) {
            reconcileSelectedFloor();
            updateFloorControls();
            return;
        }

        int nextIndex = currentIndex + direction;
        selectFloor(nextIndex < 0 || nextIndex >= ordinals.size()
                ? null
                : ordinals.get(nextIndex));
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

        boolean canChangeFloors = floorLayout.ordinals().size() > 1;
        Component tooltip = getFloorControlTooltip();
        floorPreviousButton.active = canChangeFloors;
        floorNextButton.active = canChangeFloors;
        floorLabelButton.active = canChangeFloors;
        floorPreviousButton.setTooltip(Tooltip.create(tooltip));
        floorLabelButton.setTooltip(Tooltip.create(tooltip));
        floorNextButton.setTooltip(Tooltip.create(tooltip));

        floorLabelButton.setMessage(getFloorLabel(selectedFloorOrdinal));
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

    private void addHoveredBuilding(Map<Integer, Building> hoveredBuildings,
                                    Building candidate,
                                    Integer selectedFloor) {
        hoveredBuildings.merge(candidate.getEffectiveStructureId(), candidate,
                (existing, replacement) -> isBetterTooltipBuilding(existing, replacement, selectedFloor)
                        ? replacement
                        : existing);
    }

    private static boolean isBetterTooltipBuilding(Building existing,
                                                    Building replacement,
                                                    Integer selectedFloor) {
        if (selectedFloor == null || selectedFloor == 0) {
            if (existing.isStructureRoot() != replacement.isStructureRoot()) {
                return replacement.isStructureRoot();
            }
        } else if (existing.isStrictScan() != replacement.isStrictScan()) {
            return replacement.isStrictScan();
        }
        return replacement.getId() < existing.getId();
    }

    private List<Component> getBuildingTooltip(Building hoverBuilding, Integer selectedFloor) {
        List<Component> lines = new LinkedList<>();

        //name
        BuildingType bt = BuildingTypes.getInstance().getBuildingType(hoverBuilding.getType());
        lines.add(Component.translatable("buildingType." + bt.name()));

        //residents
        for (String name : village.getResidents(hoverBuilding.getId())) {
            lines.add(Component.literal(name));
        }

        if (selectedFloor == null && floorLayout.ordinals().size() > 1) {
            for (int floorOrdinal : floorLayout.ordinals()) {
                List<Component> blockLines = getBlockTooltipLines(hoverBuilding, floorOrdinal);
                if (!blockLines.isEmpty()) {
                    lines.add(getFloorLabel(floorOrdinal).copy().withStyle(ChatFormatting.GRAY));
                    lines.addAll(blockLines);
                }
            }
            return lines;
        }

        lines.addAll(getBlockTooltipLines(hoverBuilding, selectedFloor));
        return lines;
    }

    private List<Component> getBlockTooltipLines(Building building, Integer selectedFloor) {
        List<Component> lines = new ArrayList<>();
        for (Map.Entry<ResourceLocation, List<BlockPos>> block : building.getBlocks().entrySet()) {
            long count = block.getValue().stream()
                    .filter(pos -> selectedFloor == null || floorLayout.isBlockOnFloor(building, pos, selectedFloor))
                    .count();
            if (count > 0) {
                lines.add(Component.literal(count + " x ").append(getBlockName(block.getKey())).withStyle(ChatFormatting.GRAY));
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
        this.village = village;
        this.floorLayout = village == null ? BlueprintFloorLayout.empty() : BlueprintFloorLayout.build(village);
        reconcileSelectedFloor();
        updateFloorControls();
        updateBuildingIconsControl();
        updateStructureScanControl();
        updateRemoveRoomControl();

        if (village == null) {
            setPage("empty");
        } else if (page.equals("waiting")) {
            setPage("map");
        }
    }

    public void setVillageData(Rank rank, int reputation, boolean isVillage, Set<String> completedTasks, Map<Rank, List<Task>> tasks) {
        this.rank = rank;
        this.reputation = reputation;
        this.isVillage = isVillage;
        this.completedTasks = completedTasks;
        this.tasks = tasks;
    }
}
