package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapFootprintLayer;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapGeometry;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapIconLayer;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapConnectorLayer;
import net.conczin.mca.client.gui.BlueprintMapGeometry.MapStructureLayer;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.client.render.JourneyMapIconBridge;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.FloorConnector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

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
    private static final float PLAYER_MARKER_Z = 100.0F;
    private static final float CONNECTOR_GLYPH_CELL_FRACTION = 1.2F;
    private static final int CONNECTOR_MARKER_TEXT = 0xfff4f6f8;
    private static final int ROOM_FILL_ALPHA_ALL_FLOORS = 0x60;
    private static final int ROOM_FILL_ALPHA_SELECTED_FLOOR = 0x70;
    private static final float ROOM_FILL_BRIGHTEN_FACTOR = 1.15f;
    private static final int ROOM_BORDER_ALPHA_ALL_FLOORS = 0xff;
    private static final int ROOM_BORDER_ALPHA_SELECTED_FLOOR = 0xff;
    private static final int ROOM_BORDER_ALPHA_HOVERED = 0xff;
    private static final float ROOM_BORDER_BRIGHTEN_FACTOR = 1.35f;
    private static final int STRUCTURE_BASE_COLOR = 0x00a0a0a0;
    private static final int BUILDING_SHADE_ALPHA = 0x24;
    private static final int BUILDING_SHADE_ALPHA_ACTIVE = 0x38;
    private static final int BUILDING_BORDER_ALPHA = 0xff;
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
        List<MapFootprintLayer> roomHitTestLayers = frontToBack(footprintLayers);
        int structuredHoveredBuildingId = hoveredLogicalBuildingId(
                roomHitTestLayers, structureLayers, hoveredMapCell, mouseX, mouseY,
                viewport, selectedFloor, mouseInsideMap);
        int hoveredLogicalBuildingId = structuredHoveredBuildingId >= 0
                ? structuredHoveredBuildingId
                : hoveredGroupedBuildingId(geometry.groupedBuildings(), hoveredMapCell, mouseInsideMap);
        boolean concreteRoomHovered = hoveredLogicalBuildingId >= 0 && roomHitTestLayers.stream()
                .filter(layer -> layer.logicalBuildingId() == hoveredLogicalBuildingId)
                .anyMatch(layer -> isInsideBuildingOutline(structureLayers, layer.logicalBuildingId(), hoveredMapCell)
                        && isRoomHovered(layer, hoveredMapCell, mouseX, mouseY,
                        viewport, selectedFloor == null));
        List<MapFootprintLayer> roomRenderLayers = new ArrayList<>(footprintLayers);
        roomRenderLayers.sort(Comparator.comparingInt(
                layer -> layer.logicalBuildingId() == hoveredLogicalBuildingId ? 1 : 0));
        List<MapStructureLayer> structureRenderLayers = new ArrayList<>(structureLayers);
        structureRenderLayers.sort(Comparator.comparingInt(
                layer -> layer.logicalBuildingId() == hoveredLogicalBuildingId ? 1 : 0));

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
            boolean hit = mouseInsideMap && isGroupedBuildingHovered(building, hoveredMapCell);
            boolean hovering = hit && building.getId() == hoveredLogicalBuildingId;

            renderRoomRegion(
                    context,
                    min.getX(), min.getZ(), max.getX(), max.getZ(),
                    buildingType.getColor(), selectedFloor != null, hovering
            );
            if (hit) {
                addRoomHover(hoverTargets, building, selectedFloor, building.getId(), building.getCenter().getY());
            }
        }

        List<OutlineLayer> structureOutlineLayers = new ArrayList<>();
        for (MapStructureLayer layer : structureRenderLayers) {
            boolean active = !concreteRoomHovered && layer.logicalBuildingId() == hoveredLogicalBuildingId;
            renderStructureShade(
                    context,
                    layer.shellSpans(),
                    STRUCTURE_BASE_COLOR,
                    false
            );
            int outlineColor = withAlpha(
                    scaleColor(STRUCTURE_BASE_COLOR,
                            active ? BUILDING_BORDER_ACTIVE_FACTOR : BUILDING_BORDER_DARKEN_FACTOR),
                    active ? BUILDING_BORDER_ALPHA_ACTIVE : BUILDING_BORDER_ALPHA
            );
            structureOutlineLayers.add(new OutlineLayer(layer.borderEdges(), outlineColor));
        }
        // The one neutral Building border stays behind the selected Room presentation.
        renderOutlineBatch(context, structureOutlineLayers, viewport.scale());

        Set<MapFootprintLayer> hoveredFootprintLayers = new HashSet<>();
        for (MapFootprintLayer layer : roomHitTestLayers) {
            boolean hovering = mouseInsideMap
                    && isRoomHovered(layer, hoveredMapCell, mouseX, mouseY, viewport, selectedFloor == null);
            if (hovering) {
                // Match HEAD's stable hover semantics: retain every vertically overlapping Room
                // for tooltip stacking, while only the frontmost Room in a Structure gets the
                // visual hover highlight.
                int buildingId = layer.logicalBuildingId();
                if (buildingId == hoveredLogicalBuildingId
                        && !hasRoomHoverForBuilding(hoverTargets, buildingId)
                        && isInsideBuildingOutline(structureLayers, buildingId, hoveredMapCell)) {
                    hoveredFootprintLayers.add(layer);
                }
                addRoomHover(hoverTargets, layer.building(), layer.floorOrdinal(), buildingId, layer.anchorY());
            }
        }

        // Paint back-to-front, moving the hovered logical building last.
        List<OutlineLayer> roomOutlineLayers = new ArrayList<>();
        for (MapFootprintLayer layer : roomRenderLayers) {
            boolean hovering = hoveredFootprintLayers.contains(layer);
            renderRoomFootprint(
                    context,
                    layer.fillSpans(),
                    layer.presentationType().getColor(),
                    selectedFloor != null,
                    hovering
            );
            int outlineAlpha = hovering
                    ? ROOM_BORDER_ALPHA_HOVERED
                    : selectedFloor != null ? ROOM_BORDER_ALPHA_SELECTED_FLOOR : ROOM_BORDER_ALPHA_ALL_FLOORS;
            int outlineColor = withAlpha(
                    scaleColor(layer.presentationType().getColor(), ROOM_BORDER_BRIGHTEN_FACTOR),
                    outlineAlpha
            );
            roomOutlineLayers.add(new OutlineLayer(layer.outlineEdges(), outlineColor));
        }
        renderOutlineBatch(context, roomOutlineLayers, viewport.scale());

        // The shell/outline is an authoritative whole-Building hit region. Collect the hit
        // now, then resolve it after Room hit testing so basement/upper Room geometry cannot steal
        // the aggregate tooltip from the Building shell.
        Set<MapStructureLayer> hoveredStructureLayers = new LinkedHashSet<>();
        for (MapStructureLayer layer : structureLayers) {
            boolean buildingHovered = layer.outlineCells().contains(hoveredMapCell)
                    || isOutlineHovered(layer.borderEdges(), mouseX, mouseY, viewport);
            if (mouseInsideMap && buildingHovered) {
                hoveredStructureLayers.add(layer);
            }
        }

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
        renderConnectorMarkers(context, geometry.connectorLayers());
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

        return new RenderResult(hoverTargets, hoveredLogicalBuildingId);
    }

    private static void renderConnectorMarkers(GuiGraphics context,
                                               List<MapConnectorLayer> connectorLayers) {
        if (connectorLayers.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        for (MapConnectorLayer layer : connectorLayers) {
            BlockPos pos = layer.marker().pos();
            String glyph = connectorGlyph(layer);
            float scale = connectorGlyphScale(font.width(glyph), font.lineHeight);
            PoseStack matrices = context.pose();
            matrices.pushPose();
            matrices.translate(pos.getX() + 0.5D, pos.getZ() + 0.5D, 0.0D);
            matrices.scale(scale, scale, 1.0F);
            context.drawCenteredString(font, Component.literal(glyph),
                    0, -font.lineHeight / 2, CONNECTOR_MARKER_TEXT);
            matrices.popPose();
        }
    }

    static float connectorGlyphScale(int glyphWidth, int lineHeight) {
        return CONNECTOR_GLYPH_CELL_FRACTION / Math.max(1, Math.max(glyphWidth, lineHeight));
    }

    static String connectorGlyph(MapConnectorLayer layer) {
        FloorConnector.Type type = layer.marker().type();
        return switch (type) {
            case LADDER, TRAPDOOR -> "↕";
            case DOOR -> "▯";
            case GATE -> "═";
        };
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
                                            boolean hovered) {
        int fillAlpha = hovered
                ? ROOM_FILL_ALPHA_HOVERED
                : selectedFloor ? ROOM_FILL_ALPHA_SELECTED_FLOOR : ROOM_FILL_ALPHA_ALL_FLOORS;
        int color = withAlpha(scaleColor(baseColor, ROOM_FILL_BRIGHTEN_FACTOR), fillAlpha);
        renderCellSpansMapSpace(context, spans, color);
    }

    private static void renderStructureShade(GuiGraphics context,
                                             List<BlueprintMapFootprint.RowSpan> shadeSpans,
                                             int baseColor,
                                             boolean active) {
        int color = withAlpha(baseColor, active ? BUILDING_SHADE_ALPHA_ACTIVE : BUILDING_SHADE_ALPHA);
        renderCellSpansMapSpace(context, shadeSpans, color);
    }

    static OutlineQuad outlineQuad(BlueprintMapFootprint.Edge edge, float scale) {
        float halfWidth = 0.5F / scale;
        if (edge.z0() == edge.z1()) {
            float minX = Math.min(edge.x0(), edge.x1()) - halfWidth;
            float maxX = Math.max(edge.x0(), edge.x1()) + halfWidth;
            return new OutlineQuad(minX, edge.z0() - halfWidth, maxX, edge.z0() + halfWidth);
        }
        float minZ = Math.min(edge.z0(), edge.z1()) - halfWidth;
        float maxZ = Math.max(edge.z0(), edge.z1()) + halfWidth;
        return new OutlineQuad(edge.x0() - halfWidth, minZ, edge.x0() + halfWidth, maxZ);
    }

    private static void renderOutlineBatch(GuiGraphics context,
                                           List<OutlineLayer> layers,
                                           float scale) {
        if (layers.stream().allMatch(layer -> layer.edges().isEmpty())) return;

        context.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = context.pose().last().pose();

        for (OutlineLayer layer : layers) {
            for (BlueprintMapFootprint.Edge edge : layer.edges()) {
                OutlineQuad quad = outlineQuad(edge, scale);
                builder.addVertex(matrix, quad.minX(), quad.maxZ(), 0.0F).setColor(layer.color());
                builder.addVertex(matrix, quad.maxX(), quad.maxZ(), 0.0F).setColor(layer.color());
                builder.addVertex(matrix, quad.maxX(), quad.minZ(), 0.0F).setColor(layer.color());
                builder.addVertex(matrix, quad.minX(), quad.minZ(), 0.0F).setColor(layer.color());
            }
        }

        BufferUploader.drawWithShader(builder.buildOrThrow());
        RenderSystem.disableBlend();
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
        double worldX = viewport.worldX(mouseScreenX);
        double worldZ = viewport.worldZ(mouseScreenY);
        for (BlueprintMapFootprint.Edge edge : edges) {
            OutlineQuad quad = outlineQuad(edge, viewport.scale());
            if (worldX >= quad.minX() && worldX <= quad.maxX()
                    && worldZ >= quad.minZ() && worldZ <= quad.maxZ()) {
                return true;
            }
        }
        return false;
    }

    private static void renderCellSpansMapSpace(GuiGraphics context,
                                                List<BlueprintMapFootprint.RowSpan> spans,
                                                int color) {
        for (BlueprintMapFootprint.RowSpan span : spans) {
            context.fill(span.minX(), span.z(), span.maxX() + 1, span.z() + 1, color);
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
        double markerX = markerCenter.x() - PLAYER_MARKER_SIZE / 2.0D;
        double markerY = markerCenter.y() - PLAYER_MARKER_SIZE / 2.0D;
        context.pose().pushPose();
        context.pose().translate(markerX, markerY, PLAYER_MARKER_Z);
        context.fill(-1, -1, PLAYER_MARKER_SIZE + 1, PLAYER_MARKER_SIZE + 1, 0xc0000000);
        renderCurrentPlayerFace(context, player, 0, 0, PLAYER_MARKER_SIZE);
        context.pose().popPose();
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
            if (isInsideBuildingOutline(structureLayers, layer.logicalBuildingId(), hoveredMapCell)
                    && isRoomHovered(layer, hoveredMapCell, mouseX, mouseY, viewport, selectedFloor == null)) {
                return layer.logicalBuildingId();
            }
        }

        LinkedHashSet<Integer> hovered = new LinkedHashSet<>();
        for (MapStructureLayer layer : frontToBack(structureLayers)) {
            if (layer.outlineCells().contains(hoveredMapCell)
                    || isOutlineHovered(layer.borderEdges(), mouseX, mouseY, viewport)) {
                hovered.add(layer.logicalBuildingId());
            }
        }
        return hovered.stream().findFirst().orElse(-1);
    }

    private static int hoveredGroupedBuildingId(List<Building> groupedBuildings,
                                                BlueprintMapFootprint.Cell hoveredMapCell,
                                                boolean mouseInsideMap) {
        if (!mouseInsideMap) return -1;
        return groupedBuildings.stream()
                .filter(building -> !building.getBuildingType().isIcon())
                .filter(building -> isGroupedBuildingHovered(building, hoveredMapCell))
                .sorted(Comparator.comparingInt((Building building) -> building.getCenter().getY()).reversed()
                        .thenComparing(Comparator.comparingInt(Building::getId).reversed()))
                .map(Building::getId)
                .findFirst()
                .orElse(-1);
    }

    private static boolean isGroupedBuildingHovered(Building building,
                                                    BlueprintMapFootprint.Cell hoveredMapCell) {
        BlockPos min = building.getRawPos0();
        BlockPos max = building.getRawPos1();
        int hoverMargin = 1;
        return hoveredMapCell.x() >= min.getX() - hoverMargin
                && hoveredMapCell.x() <= max.getX() + hoverMargin
                && hoveredMapCell.z() >= min.getZ() - hoverMargin
                && hoveredMapCell.z() <= max.getZ() + hoverMargin;
    }

    private static boolean isInsideBuildingOutline(List<MapStructureLayer> structureLayers,
                                                   int logicalBuildingId,
                                                   BlueprintMapFootprint.Cell cell) {
        return structureLayers.stream()
                .filter(layer -> layer.logicalBuildingId() == logicalBuildingId)
                .anyMatch(layer -> layer.outlineCells().contains(cell));
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
        // A visible concrete Room is the user's target. The Building shell is only a
        // fallback where no Room for this logical Building is under the pointer.
        if (hoverTargets.stream().anyMatch(target -> !target.structure()
                && target.logicalBuildingId() == buildingId)) {
            return;
        }
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

        PhysicalFloorKey floorKey = new PhysicalFloorKey(building.getStructureId(), building.getFloorId());
        if (hoverTargets.stream().anyMatch(target -> target.samePhysicalFloor(floorKey))) return;

        // A concrete Room/icon hover always wins over the logical Building shade beneath it.
        hoverTargets.removeIf(target -> target.structure()
                && target.logicalBuildingId() == buildingId);
        HoverTarget target = new HoverTarget(building, floorOrdinal, false, buildingId, anchorY);
        if (!hoverTargets.contains(target)) hoverTargets.add(target);
    }

    private static <T> List<T> frontToBack(List<T> renderOrder) {
        ArrayList<T> reversed = new ArrayList<>(renderOrder);
        Collections.reverse(reversed);
        return reversed;
    }

    private static int scaleColor(int color, float factor) {
        int rgb = color & 0x00ffffff;
        int red = Math.min(255, Math.round(((rgb >> 16) & 0xff) * factor));
        int green = Math.min(255, Math.round(((rgb >> 8) & 0xff) * factor));
        int blue = Math.min(255, Math.round((rgb & 0xff) * factor));
        return red << 16 | green << 8 | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    @Override
    public void close() {
        terrainRenderer.close();
    }

    record RenderResult(List<HoverTarget> hoverTargets, int hoveredLogicalBuildingId) {
        RenderResult {
            hoverTargets = List.copyOf(hoverTargets);
        }
    }

    record OutlineQuad(float minX, float minZ, float maxX, float maxZ) {
    }

    private record OutlineLayer(List<BlueprintMapFootprint.Edge> edges, int color) {
    }

    record HoverTarget(Building building, Integer floorOrdinal,
                       boolean structure, int logicalBuildingId, int anchorY) {
        boolean samePhysicalFloor(PhysicalFloorKey key) {
            return !structure && building.isFunctionalRoom()
                    && building.getStructureId() == key.structureId()
                    && building.getFloorId() == key.floorId();
        }
    }

    private record PhysicalFloorKey(int structureId, int floorId) {
    }
}
