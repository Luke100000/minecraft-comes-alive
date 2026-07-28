package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapFootprintLayer;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapGeometry;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapIconLayer;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapStructureLayer;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.client.render.JourneyMapIconBridge;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Owns Blueprint map drawing and hit testing.
 *
 * <p>The screen supplies one immutable {@link BlueprintMapViewport} per frame and keeps
 * UI state/tooltips. This renderer owns non-terrain map drawing and hover testing.</p>
 */
final class BlueprintMapRenderer implements AutoCloseable {
    private static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static final int TERRAIN_BACKGROUND_COLOR = 0xd0181c22;
    private static final int ROOM_INNER_PADDING = 1;
    private static final int ROOM_SHADOW_COLOR = 0x50000000;
    private static final int PLAYER_MARKER_SIZE = 6;
    private static final int PLAYER_MARKER_EDGE_PADDING = 2;
    private static final int ROOM_FILL_ALPHA_ALL_FLOORS = 0x60;
    private static final int ROOM_FILL_ALPHA_SELECTED_FLOOR = 0x70;
    private static final float ROOM_FILL_BRIGHTEN_FACTOR = 1.15f;
    private static final int ROOM_BORDER_ALPHA_ALL_FLOORS = 0xd0;
    private static final int ROOM_BORDER_ALPHA_SELECTED_FLOOR = 0xee;
    private static final int ROOM_BORDER_ALPHA_HOVERED = 0xff;
    private static final float ROOM_BORDER_BRIGHTEN_FACTOR = 1.35f;
    private static final int STRUCTURE_BASE_COLOR = 0x00a0a0a0;
    private static final int BUILDING_SHADE_ALPHA = 0x24;
    private static final int BUILDING_SHADE_ALPHA_ACTIVE = 0x38;
    private static final int BUILDING_BORDER_ALPHA = 0xc0;
    private static final int BUILDING_BORDER_ALPHA_ACTIVE = 0xff;
    private static final float BUILDING_BORDER_DARKEN_FACTOR = 0.58f;
    private static final float BUILDING_BORDER_ACTIVE_FACTOR = 0.85f;
    private static final int ROOM_FILL_ALPHA_HOVERED = 0x98;

    private final BlueprintTerrainRenderer terrainRenderer = new BlueprintTerrainRenderer();

    RenderResult render(GuiGraphics context,
                        BlueprintMapViewport viewport,
                        MapGeometry geometry,
                        Integer selectedFloor,
                        boolean showTerrain,
                        boolean showBuildingIcons,
                        boolean showPlayerHead,
                        LocalPlayer player,
                        int playerLogicalBuildingId,
                        double playerRenderX,
                        double playerRenderZ,
                        int mouseX,
                        int mouseY) {
        if (showTerrain) {
            context.fill(
                    viewport.left() + 1,
                    viewport.top() + 1,
                    viewport.right() - 1,
                    viewport.bottom() - 1,
                    TERRAIN_BACKGROUND_COLOR
            );
        }
        WidgetUtils.drawRectangle(
                context,
                viewport.left(),
                viewport.top(),
                viewport.right(),
                viewport.bottom(),
                0xffffff88
        );

        BlueprintMapFootprint.Cell hoveredMapCell = viewport.screenToCell(mouseX, mouseY);
        boolean mouseInsideMap = viewport.containsInner(mouseX, mouseY);
        List<HoverTarget> hoverTargets = new ArrayList<>();
        List<Building> groupedIconBuildings = new ArrayList<>();
        List<MapFootprintLayer> footprintLayers = geometry.footprintLayers();
        List<MapStructureLayer> structureLayers = geometry.structureLayers();
        List<MapIconLayer> footprintIconLayers = geometry.iconLayers();
        List<MapFootprintLayer> roomHitTestLayers = BlueprintMapLayering.frontToBack(footprintLayers);
        int hoveredLogicalBuildingId = hoveredLogicalBuildingId(
                roomHitTestLayers, structureLayers, hoveredMapCell, mouseX, mouseY,
                viewport, selectedFloor, mouseInsideMap);
        int activeLogicalBuildingId = hoveredLogicalBuildingId >= 0
                ? hoveredLogicalBuildingId
                : playerLogicalBuildingId >= 0
                ? playerLogicalBuildingId
                : topLogicalBuildingId(footprintLayers, structureLayers);
        List<MapFootprintLayer> roomRenderLayers = new ArrayList<>(footprintLayers);
        roomRenderLayers.sort(Comparator.comparingInt(
                layer -> layer.logicalBuildingId() == activeLogicalBuildingId ? 1 : 0));
        List<MapStructureLayer> structureRenderLayers = new ArrayList<>(structureLayers);
        structureRenderLayers.sort(Comparator.comparingInt(
                layer -> layer.logicalBuildingId() == activeLogicalBuildingId ? 1 : 0));

        context.enableScissor(
                viewport.left() + 1,
                viewport.top() + 1,
                viewport.right() - 1,
                viewport.bottom() - 1
        );

        PoseStack matrices = context.pose();
        pushWorldTransform(matrices, viewport);

        if (showTerrain) {
            terrainRenderer.render(context, viewport);
        }

        // Grouped POIs retain their legacy rectangle/icon presentation. Structural rooms
        // are rendered from exact immutable geometry layers below.
        for (Building building : geometry.groupedBuildings()) {
            BuildingType buildingType = building.getBuildingType();
            if (buildingType.isIcon()) {
                groupedIconBuildings.add(building);
                continue;
            }

            BlockPos min = building.getRawPos0();
            BlockPos max = building.getRawPos1();
            int hoverMargin = 1;
            boolean hovering = mouseInsideMap
                    && hoveredMapCell.x() >= min.getX() - hoverMargin
                    && hoveredMapCell.x() <= max.getX() + hoverMargin
                    && hoveredMapCell.z() >= min.getZ() - hoverMargin
                    && hoveredMapCell.z() <= max.getZ() + hoverMargin;

            renderRoomRegion(
                    context,
                    min.getX(), min.getZ(), max.getX(), max.getZ(),
                    buildingType.getColor(), selectedFloor != null, hovering
            );
            if (hovering) {
                addRoomHover(hoverTargets, building, selectedFloor, building.getId(), building.getCenter().getY());
            }
        }

        matrices.popPose();

        for (MapStructureLayer layer : structureRenderLayers) {
            renderStructureShade(
                    context,
                    layer.shellSpans(),
                    STRUCTURE_BASE_COLOR,
                    layer.logicalBuildingId() == activeLogicalBuildingId,
                    viewport
            );
        }

        // The one neutral Building border stays behind the selected Room presentation.
        for (MapStructureLayer layer : structureRenderLayers) {
            renderStructureOutlineScreenSpace(
                    context,
                    layer.borderEdges(),
                    STRUCTURE_BASE_COLOR,
                    false,
                    viewport
            );
        }

        Set<MapFootprintLayer> hoveredFootprintLayers = new HashSet<>();
        for (MapFootprintLayer layer : roomHitTestLayers) {
            boolean hovering = mouseInsideMap
                    && isRoomHovered(layer, hoveredMapCell, mouseX, mouseY, viewport, selectedFloor == null);
            if (hovering) {
                // Match HEAD's stable hover semantics: retain every vertically overlapping Room
                // for tooltip stacking, while only the frontmost Room in a Structure gets the
                // visual hover highlight.
                int buildingId = layer.logicalBuildingId();
                if (!hasRoomHoverForBuilding(hoverTargets, buildingId)) {
                    hoveredFootprintLayers.add(layer);
                }
                addRoomHover(hoverTargets, layer.building(), layer.floorOrdinal(), buildingId, layer.anchorY());
            }
        }

        // Paint back-to-front, moving the active logical building last. Hover ownership is
        // resolved from physical elevation first, so hovering can override the player's building.
        for (MapFootprintLayer layer : roomRenderLayers) {
            boolean hovering = hoveredFootprintLayers.contains(layer);
            renderRoomFootprint(
                    context,
                    layer.fillSpans(),
                    layer.presentationType().getColor(),
                    selectedFloor != null,
                    hovering,
                    viewport
            );
            renderRoomOutlineScreenSpace(
                    context,
                    layer.outlineEdges(),
                    layer.presentationType().getColor(),
                    selectedFloor != null,
                    hovering,
                    viewport
            );
        }

        structureRenderLayers.stream()
                .filter(layer -> layer.logicalBuildingId() == activeLogicalBuildingId)
                .findFirst()
                .ifPresent(layer -> renderStructureOutlineScreenSpace(
                        context, layer.borderEdges(), STRUCTURE_BASE_COLOR, true, viewport));

        // The shell/outline is an authoritative whole-Building hit region. Collect the hit
        // now, then resolve it after Room hit testing so basement/upper Room geometry cannot steal
        // the aggregate tooltip from the Building shell.
        Set<MapStructureLayer> hoveredStructureLayers = new LinkedHashSet<>();
        for (MapStructureLayer layer : structureLayers) {
            boolean buildingHovered = layer.shellCells().contains(hoveredMapCell)
                    || isOutlineHovered(layer.borderEdges(), mouseX, mouseY, viewport);
            if (mouseInsideMap && buildingHovered) {
                hoveredStructureLayers.add(layer);
            }
        }

        pushWorldTransform(matrices, viewport);
        if (showBuildingIcons) {
            for (Building building : groupedIconBuildings) {
                BuildingType buildingType = building.getBuildingType();
                BlockPos center = building.getCenter();
                WidgetUtils.drawBuildingIcon(
                        context,
                        ICON_TEXTURES,
                        center.getX(), center.getZ(),
                        buildingType.iconU(), buildingType.iconV()
                );
            }
            for (MapIconLayer iconLayer : footprintIconLayers) {
                BuildingType buildingType = iconLayer.presentationType();
                float iconScale = iconLayer.iconScale();
                drawScaledBuildingIcon(
                        context,
                        ICON_TEXTURES,
                        iconLayer.iconX(),
                        iconLayer.iconZ(),
                        buildingType.iconU(), buildingType.iconV(),
                        iconScale / viewport.scale()
                );
            }
        }
        matrices.popPose();

        // Resolve canonical Building hits last. On the shell, the user's intent is the whole
        // building, so replace its Room targets with one aggregate target.
        for (MapStructureLayer layer : hoveredStructureLayers) {
            if (layer.mainRoom() != null) {
                addStructureHover(hoverTargets, layer.mainRoom(), layer.logicalBuildingId(),
                        selectedFloor, layer.anchorY());
            }
        }

        context.disableScissor();
        renderPlayerMarker(
                context,
                player,
                playerRenderX,
                playerRenderZ,
                viewport,
                showPlayerHead
        );

        return new RenderResult(hoverTargets, activeLogicalBuildingId);
    }

    private static void pushWorldTransform(PoseStack matrices, BlueprintMapViewport viewport) {
        matrices.pushPose();
        matrices.translate(viewport.centerX(), viewport.centerY(), 0.0D);
        matrices.scale(viewport.scale(), viewport.scale(), 1.0F);
        matrices.translate(-viewport.mapCenterX(), -viewport.mapCenterZ(), 0.0D);
    }

    private static void renderRoomRegion(GuiGraphics context,
                                         int minX,
                                         int minZ,
                                         int maxInclusiveX,
                                         int maxInclusiveZ,
                                         int baseColor,
                                         boolean selectedFloor,
                                         boolean hovered) {
        int maxX = maxInclusiveX + 1;
        int maxZ = maxInclusiveZ + 1;

        WidgetUtils.drawRectangle(
                context,
                minX + 1, minZ + 1, maxX + 1, maxZ + 1,
                ROOM_SHADOW_COLOR
        );

        int innerMinX = minX + ROOM_INNER_PADDING;
        int innerMinZ = minZ + ROOM_INNER_PADDING;
        int innerMaxX = maxX - ROOM_INNER_PADDING;
        int innerMaxZ = maxZ - ROOM_INNER_PADDING;
        if (innerMinX < innerMaxX && innerMinZ < innerMaxZ) {
            int fillAlpha = hovered
                    ? ROOM_FILL_ALPHA_HOVERED
                    : selectedFloor ? ROOM_FILL_ALPHA_SELECTED_FLOOR : ROOM_FILL_ALPHA_ALL_FLOORS;
            context.fill(
                    innerMinX, innerMinZ, innerMaxX, innerMaxZ,
                    withAlpha(baseColor, fillAlpha)
            );
        }

        int outlineAlpha = hovered ? 0xff : selectedFloor ? 0xdd : 0xaa;
        WidgetUtils.drawRectangle(
                context,
                minX, minZ, maxX, maxZ,
                withAlpha(baseColor, outlineAlpha)
        );

        if (hovered && innerMinX + 1 < innerMaxX && innerMinZ + 1 < innerMaxZ) {
            WidgetUtils.drawRectangle(
                    context,
                    innerMinX, innerMinZ, innerMaxX, innerMaxZ,
                    withAlpha(baseColor, 0x88)
            );
        }
    }

    private static void renderRoomFootprint(GuiGraphics context,
                                            List<BlueprintMapFootprint.RowSpan> spans,
                                            int baseColor,
                                            boolean selectedFloor,
                                            boolean hovered,
                                            BlueprintMapViewport viewport) {
        int fillAlpha = hovered
                ? ROOM_FILL_ALPHA_HOVERED
                : selectedFloor ? ROOM_FILL_ALPHA_SELECTED_FLOOR : ROOM_FILL_ALPHA_ALL_FLOORS;
        int color = withAlpha(brightenColor(baseColor, ROOM_FILL_BRIGHTEN_FACTOR), fillAlpha);
        renderCellSpansScreenSpace(context, spans, color, viewport);
    }

    private static void renderRoomOutlineScreenSpace(GuiGraphics context,
                                                     List<BlueprintMapFootprint.Edge> edges,
                                                     int baseColor,
                                                     boolean selectedFloor,
                                                     boolean hovered,
                                                     BlueprintMapViewport viewport) {
        int outlineAlpha = hovered
                ? ROOM_BORDER_ALPHA_HOVERED
                : selectedFloor ? ROOM_BORDER_ALPHA_SELECTED_FLOOR : ROOM_BORDER_ALPHA_ALL_FLOORS;
        int outlineColor = withAlpha(brightenColor(baseColor, ROOM_BORDER_BRIGHTEN_FACTOR), outlineAlpha);
        renderOutlineScreenSpace(context, edges, outlineColor, viewport);
    }

    private static void renderStructureOutlineScreenSpace(GuiGraphics context,
                                                          List<BlueprintMapFootprint.Edge> edges,
                                                          int baseColor,
                                                          boolean active,
                                                          BlueprintMapViewport viewport) {
        int outlineColor = withAlpha(
                darkenColor(baseColor, active ? BUILDING_BORDER_ACTIVE_FACTOR : BUILDING_BORDER_DARKEN_FACTOR),
                active ? BUILDING_BORDER_ALPHA_ACTIVE : BUILDING_BORDER_ALPHA
        );
        renderOutlineScreenSpace(context, edges, outlineColor, viewport);
    }

    private static void renderStructureShade(GuiGraphics context,
                                             List<BlueprintMapFootprint.RowSpan> shadeSpans,
                                             int baseColor,
                                             boolean active,
                                             BlueprintMapViewport viewport) {
        int color = withAlpha(baseColor, active ? BUILDING_SHADE_ALPHA_ACTIVE : BUILDING_SHADE_ALPHA);
        renderCellSpansScreenSpace(context, shadeSpans, color, viewport);
    }

    private static void renderOutlineScreenSpace(GuiGraphics context,
                                                 List<BlueprintMapFootprint.Edge> edges,
                                                 int color,
                                                 BlueprintMapViewport viewport) {
        for (BlueprintMapFootprint.Edge edge : edges) {
            int x0 = (int) Math.round(viewport.screenX(edge.x0()));
            int z0 = (int) Math.round(viewport.screenY(edge.z0()));
            int x1 = (int) Math.round(viewport.screenX(edge.x1()));
            int z1 = (int) Math.round(viewport.screenY(edge.z1()));

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

    private static boolean isRoomHovered(MapFootprintLayer layer,
                                         BlueprintMapFootprint.Cell hoveredMapCell,
                                         int mouseScreenX,
                                         int mouseScreenY,
                                         BlueprintMapViewport viewport,
                                         boolean allFloors) {
        if (layer.footprintCells().contains(hoveredMapCell)) {
            return true;
        }
        // Canonical outlines from a lower floor can cross cells owned by a higher-priority
        // floor. All Floors hover therefore follows visible cells only.
        return !allFloors && isOutlineHovered(layer.outlineEdges(), mouseScreenX, mouseScreenY, viewport);
    }

    private static boolean isOutlineHovered(List<BlueprintMapFootprint.Edge> edges,
                                            int mouseScreenX,
                                            int mouseScreenY,
                                            BlueprintMapViewport viewport) {
        for (BlueprintMapFootprint.Edge edge : edges) {
            int x0 = (int) Math.round(viewport.screenX(edge.x0()));
            int z0 = (int) Math.round(viewport.screenY(edge.z0()));
            int x1 = (int) Math.round(viewport.screenX(edge.x1()));
            int z1 = (int) Math.round(viewport.screenY(edge.z1()));
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

    private static void renderCellSpansScreenSpace(GuiGraphics context,
                                                   List<BlueprintMapFootprint.RowSpan> spans,
                                                   int color,
                                                   BlueprintMapViewport viewport) {
        for (BlueprintMapFootprint.RowSpan span : spans) {
            int x0 = (int) Math.round(viewport.screenX(span.minX()));
            int z0 = (int) Math.round(viewport.screenY(span.z()));
            int x1 = (int) Math.round(viewport.screenX(span.maxX() + 1));
            int z1 = (int) Math.round(viewport.screenY(span.z() + 1));
            context.fill(
                    Math.min(x0, x1),
                    Math.min(z0, z1),
                    Math.max(Math.min(x0, x1) + 1, Math.max(x0, x1)),
                    Math.max(Math.min(z0, z1) + 1, Math.max(z0, z1)),
                    color
            );
        }
    }

    private static void drawScaledBuildingIcon(GuiGraphics context,
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

    private static void renderPlayerMarker(GuiGraphics context,
                                           LocalPlayer player,
                                           double playerRenderX,
                                           double playerRenderZ,
                                           BlueprintMapViewport viewport,
                                           boolean showPlayerHead) {
        if (player == null || !showPlayerHead) {
            return;
        }

        BlueprintMapViewport.ScreenPoint markerCenter = viewport.clampMarker(
                viewport.screenX(playerRenderX),
                viewport.screenY(playerRenderZ),
                PLAYER_MARKER_SIZE,
                PLAYER_MARKER_EDGE_PADDING
        );
        int markerX = markerCenter.x() - PLAYER_MARKER_SIZE / 2;
        int markerY = markerCenter.y() - PLAYER_MARKER_SIZE / 2;

        context.fill(
                markerX - 1, markerY - 1,
                markerX + PLAYER_MARKER_SIZE + 1, markerY + PLAYER_MARKER_SIZE + 1,
                0xc0000000
        );
        renderCurrentPlayerFace(context, player, markerX, markerY, PLAYER_MARKER_SIZE);
    }

    static void renderCurrentPlayerFace(GuiGraphics context,
                                        LocalPlayer player,
                                        int x,
                                        int y,
                                        int size) {
        ResourceLocation mcaFace = MCAClient.getPlayerData(player.getUUID())
                .filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA)
                .map(JourneyMapIconBridge::getOrCreateFaceIcon)
                .orElse(null);
        if (mcaFace != null) {
            context.blit(mcaFace, x, y, size, size,
                    0.0F, 0.0F, 24, 24, 24, 24);
        } else {
            PlayerFaceRenderer.draw(context, player.getSkin(), x, y, size);
        }
    }

    private static int hoveredLogicalBuildingId(List<MapFootprintLayer> roomHitTestLayers,
                                                List<MapStructureLayer> structureLayers,
                                                BlueprintMapFootprint.Cell hoveredMapCell,
                                                int mouseX,
                                                int mouseY,
                                                BlueprintMapViewport viewport,
                                                Integer selectedFloor,
                                                boolean mouseInsideMap) {
        if (!mouseInsideMap) return -1;
        for (MapFootprintLayer layer : roomHitTestLayers) {
            if (isRoomHovered(layer, hoveredMapCell, mouseX, mouseY, viewport, selectedFloor == null)) {
                return layer.logicalBuildingId();
            }
        }
        for (MapStructureLayer layer : BlueprintMapLayering.frontToBack(structureLayers)) {
            if (layer.shellCells().contains(hoveredMapCell)
                    || isOutlineHovered(layer.borderEdges(), mouseX, mouseY, viewport)) {
                return layer.logicalBuildingId();
            }
        }
        return -1;
    }

    private static int topLogicalBuildingId(List<MapFootprintLayer> roomLayers,
                                            List<MapStructureLayer> structureLayers) {
        MapStructureLayer structure = structureLayers.stream()
                .max(Comparator.comparingInt(MapStructureLayer::anchorY)
                        .thenComparingInt(MapStructureLayer::logicalBuildingId))
                .orElse(null);
        MapFootprintLayer room = roomLayers.stream()
                .max(Comparator.comparingInt(MapFootprintLayer::anchorY)
                        .thenComparingInt(MapFootprintLayer::logicalBuildingId))
                .orElse(null);
        if (structure == null) return room == null ? -1 : room.logicalBuildingId();
        if (room == null) return structure.logicalBuildingId();
        return room.anchorY() > structure.anchorY()
                || room.anchorY() == structure.anchorY()
                && room.logicalBuildingId() > structure.logicalBuildingId()
                ? room.logicalBuildingId() : structure.logicalBuildingId();
    }

    private static boolean hasRoomHoverForBuilding(List<HoverTarget> hoverTargets, int buildingId) {
        return hoverTargets.stream().anyMatch(target -> !target.structure()
                && target.logicalBuildingId() == buildingId);
    }

    private static void addStructureHover(List<HoverTarget> hoverTargets,
                                          Building mainRoom,
                                          int buildingId,
                                          Integer floorOrdinal,
                                          int anchorY) {
        hoverTargets.removeIf(target -> !target.structure()
                && target.logicalBuildingId() == buildingId);
        HoverTarget target = new HoverTarget(mainRoom, floorOrdinal, true, buildingId, anchorY);
        if (!hoverTargets.contains(target)) hoverTargets.add(target);
    }

    private static void addRoomHover(List<HoverTarget> hoverTargets,
                                     Building building,
                                     Integer floorOrdinal,
                                     int buildingId,
                                     int anchorY) {
        if (!building.isFunctionalRoom()) {
            HoverTarget target = new HoverTarget(building, floorOrdinal, false, buildingId, anchorY);
            if (!hoverTargets.contains(target)) hoverTargets.add(target);
            return;
        }

        // A concrete Room/icon hover always wins over the logical Building shade beneath it.
        hoverTargets.removeIf(target -> target.structure()
                && target.logicalBuildingId() == buildingId);
        HoverTarget target = new HoverTarget(building, floorOrdinal, false, buildingId, anchorY);
        if (!hoverTargets.contains(target)) hoverTargets.add(target);
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    @Override
    public void close() {
        terrainRenderer.close();
    }

    record RenderResult(List<HoverTarget> hoverTargets, int activeLogicalBuildingId) {
        RenderResult {
            hoverTargets = List.copyOf(hoverTargets);
        }
    }

    record HoverTarget(Building building, Integer floorOrdinal,
                       boolean structure, int logicalBuildingId, int anchorY) {
    }
}
