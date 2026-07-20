package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.material.MapColor;

import java.util.*;

/**
 * Owns Blueprint map drawing and hit testing.
 *
 * <p>The screen supplies one immutable {@link BlueprintMapViewport} per frame and keeps
 * UI state/tooltips. This renderer owns the complete visual pipeline plus the world-derived
 * terrain texture lifecycle.</p>
 */
final class BlueprintMapRenderer implements AutoCloseable {
    private static final ResourceLocation ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static final int TERRAIN_TARGET_CELL_PIXELS = 2;
    private static final int TERRAIN_BACKGROUND_COLOR = 0xd0181c22;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int TERRAIN_CONTOUR_COLOR = 0x66000000;
    private static final float TERRAIN_BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float TERRAIN_ELEVATION_BRIGHTNESS_RANGE = 0.12f;
    private static final float TERRAIN_SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float TERRAIN_MIN_BRIGHTNESS = 0.58f;
    private static final float TERRAIN_MAX_BRIGHTNESS = 1.15f;
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
    private static final int BUILDING_BORDER_ALPHA = 0xc0;
    private static final float BUILDING_BORDER_DARKEN_FACTOR = 0.58f;
    private static final int ROOM_FILL_ALPHA_HOVERED = 0x98;

    private BlueprintTerrainSnapshot terrainSnapshot;
    private ResourceLocation terrainTextureLocation;

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

        context.enableScissor(
                viewport.left() + 1,
                viewport.top() + 1,
                viewport.right() - 1,
                viewport.bottom() - 1
        );

        PoseStack matrices = context.pose();
        pushWorldTransform(matrices, viewport);

        if (showTerrain) {
            renderTerrain(context, viewport);
        }

        // Grouped POIs retain their legacy rectangle/icon presentation. Structural rooms
        // are rendered from exact immutable geometry layers below.
        for (Building building : geometry.groupedBuildings()) {
            BuildingType buildingType = building.getBuildingType();
            if (buildingType.isIcon()) {
                BlockPos center = building.getCenter();
                groupedIconBuildings.add(building);

                int hoverMargin = 6;
                if (mouseInsideMap
                        && center.distSqr(new Vec3i(
                        hoveredMapCell.x(), center.getY(), hoveredMapCell.z())) < hoverMargin * hoverMargin) {
                    addRoomHover(hoverTargets, building, selectedFloor);
                }
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
                addRoomHover(hoverTargets, building, selectedFloor);
            }
        }

        matrices.popPose();

        for (MapStructureLayer layer : structureLayers) {
            renderStructureShade(
                    context,
                    layer.shadeSpans(),
                    STRUCTURE_BASE_COLOR,
                    viewport
            );
        }

        // Match HEAD-style z-order: the neutral building shell is behind Room presentation.
        for (MapStructureLayer layer : structureLayers) {
            renderStructureOutlineScreenSpace(
                    context,
                    layer.borderEdges(),
                    STRUCTURE_BASE_COLOR,
                    viewport
            );
        }

        List<MapFootprintLayer> roomRenderLayers = footprintLayers;
        List<MapFootprintLayer> roomHitTestLayers = selectedFloor == null
                ? BlueprintMapLayering.frontToBack(roomRenderLayers)
                : roomRenderLayers;
        Set<MapFootprintLayer> hoveredFootprintLayers = new HashSet<>();
        for (MapFootprintLayer layer : roomHitTestLayers) {
            boolean hovering = mouseInsideMap
                    && isRoomHovered(layer, hoveredMapCell, mouseX, mouseY, viewport, selectedFloor == null);
            if (hovering) {
                // Match HEAD's stable hover semantics: retain every vertically overlapping Room
                // for tooltip stacking, while only the frontmost Room in a Structure gets the
                // visual hover highlight.
                int structureId = layer.building().getEffectiveStructureId();
                if (!hasRoomHoverForStructure(hoverTargets, structureId)) {
                    hoveredFootprintLayers.add(layer);
                }
                addRoomHover(hoverTargets, layer.building(), layer.floorOrdinal());
            }
        }

        // Paint every Room back-to-front. The matching reverse hit-test order above ensures
        // tooltip ownership follows the Room that is actually visible on top.
        for (MapFootprintLayer layer : roomRenderLayers) {
            boolean hovering = hoveredFootprintLayers.contains(layer);
            renderRoomFootprint(
                    context,
                    layer.fillSpans(),
                    layer.building().getBuildingType().getColor(),
                    selectedFloor != null,
                    hovering,
                    viewport
            );
            renderRoomOutlineScreenSpace(
                    context,
                    layer.outlineEdges(),
                    layer.building().getBuildingType().getColor(),
                    selectedFloor != null,
                    hovering,
                    viewport
            );
        }

        // Structure shade/outline is an authoritative whole-Structure hit region. Collect the hit
        // now, then resolve it after Room/icon hit testing so basement/upper Room geometry cannot
        // steal the aggregate tooltip from the canonical Structure shell.
        Set<MapStructureLayer> hoveredStructureLayers = new LinkedHashSet<>();
        for (MapStructureLayer layer : structureLayers) {
            boolean buildingHovered = layer.shadeCells().contains(hoveredMapCell)
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
                BuildingType buildingType = iconLayer.building().getBuildingType();
                float iconScale = iconLayer.iconScale();
                drawScaledBuildingIcon(
                        context,
                        ICON_TEXTURES,
                        iconLayer.iconX() + iconLayer.screenOffsetX() / viewport.scale(),
                        iconLayer.iconZ() + iconLayer.screenOffsetY() / viewport.scale(),
                        buildingType.iconU(), buildingType.iconV(),
                        iconScale / viewport.scale()
                );

                double iconScreenX = viewport.screenX(iconLayer.iconX()) + iconLayer.screenOffsetX();
                double iconScreenY = viewport.screenY(iconLayer.iconZ()) + iconLayer.screenOffsetY();
                double hoverRadius = 7.0D * iconScale;
                double dx = mouseX + 0.5D - iconScreenX;
                double dz = mouseY + 0.5D - iconScreenY;
                if (mouseInsideMap && dx * dx + dz * dz < hoverRadius * hoverRadius) {
                    addRoomHover(hoverTargets, iconLayer.building(), iconLayer.floorOrdinal());
                }
            }
        }
        matrices.popPose();

        // Resolve canonical Structure hits last. On the shell/shade, the user's intent is the whole
        // building, so replace any same-Structure Room/icon targets with one aggregate Structure target.
        for (MapStructureLayer layer : hoveredStructureLayers) {
            addStructureHover(hoverTargets, layer.root());
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

        return new RenderResult(hoverTargets);
    }

    private static void pushWorldTransform(PoseStack matrices, BlueprintMapViewport viewport) {
        matrices.pushPose();
        matrices.translate(viewport.centerX(), viewport.centerY(), 0.0D);
        matrices.scale(viewport.scale(), viewport.scale(), 1.0F);
        matrices.translate(-viewport.mapCenterX(), -viewport.mapCenterZ(), 0.0D);
    }

    private void renderTerrain(GuiGraphics context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
        int sampleStep = Math.max(
                1,
                (int) Math.ceil((double) TERRAIN_TARGET_CELL_PIXELS / viewport.scale())
        );
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;

        if (terrainSnapshot == null
                || !terrainSnapshot.covers(
                visibleMinX, visibleMinZ, visibleMaxX, visibleMaxZ, sampleStep)) {
            terrainSnapshot = BlueprintTerrainSnapshot.sample(
                    minecraft.level,
                    centerBlockX,
                    centerBlockZ,
                    radius,
                    sampleStep
            );
            releaseTerrainTexture();
        }
        renderTerrainTexture(context, terrainSnapshot);
    }

    private void renderTerrainTexture(GuiGraphics context, BlueprintTerrainSnapshot snapshot) {
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

    private ResourceLocation createTerrainTexture(BlueprintTerrainSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        BlueprintTerrainSnapshot.Cell[][] cells = snapshot.cells();
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
                BlueprintTerrainSnapshot.Cell cell = cells[cellX][cellZ];
                if (cell == null) {
                    continue;
                }

                int northHeight = snapshot.heightAt(cellX, cellZ - 1, cell.height());
                int southHeight = snapshot.heightAt(cellX, cellZ + 1, cell.height());
                int westHeight = snapshot.heightAt(cellX - 1, cellZ, cell.height());
                int eastHeight = snapshot.heightAt(cellX + 1, cellZ, cell.height());
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
                        && Math.floorDiv(cell.height(), contourInterval)
                        != Math.floorDiv(northHeight, contourInterval);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height(), contourInterval)
                        != Math.floorDiv(westHeight, contourInterval);
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
                                                          BlueprintMapViewport viewport) {
        int outlineColor = withAlpha(
                darkenColor(baseColor, BUILDING_BORDER_DARKEN_FACTOR),
                BUILDING_BORDER_ALPHA
        );
        renderOutlineScreenSpace(context, edges, outlineColor, viewport);
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

    private static void renderStructureShade(GuiGraphics context,
                                             List<BlueprintMapFootprint.RowSpan> shadeSpans,
                                             int baseColor,
                                             BlueprintMapViewport viewport) {
        int color = withAlpha(baseColor, BUILDING_SHADE_ALPHA);
        renderCellSpansScreenSpace(context, shadeSpans, color, viewport);
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

    private static boolean hasRoomHoverForStructure(List<HoverTarget> hoverTargets, int structureId) {
        return hoverTargets.stream().anyMatch(target -> !target.structure()
                && target.building().isFunctionalRoom()
                && target.building().getEffectiveStructureId() == structureId);
    }

    private static void addStructureHover(List<HoverTarget> hoverTargets, Building root) {
        int structureId = root.getEffectiveStructureId();
        hoverTargets.removeIf(target -> !target.structure()
                && target.building().isFunctionalRoom()
                && target.building().getEffectiveStructureId() == structureId);
        HoverTarget target = new HoverTarget(root, null, true);
        if (!hoverTargets.contains(target)) {
            hoverTargets.add(target);
        }
    }

    private static void addRoomHover(List<HoverTarget> hoverTargets,
                                     Building building,
                                     Integer floorOrdinal) {
        if (!building.isFunctionalRoom()) {
            HoverTarget target = new HoverTarget(building, floorOrdinal, false);
            if (!hoverTargets.contains(target)) {
                hoverTargets.add(target);
            }
            return;
        }

        int structureId = building.getEffectiveStructureId();
        // A concrete Room/icon hover always wins over the Structure shade beneath it.
        hoverTargets.removeIf(target -> target.structure()
                && target.building().isFunctionalRoom()
                && target.building().getEffectiveStructureId() == structureId);
        HoverTarget target = new HoverTarget(building, floorOrdinal, false);
        if (!hoverTargets.contains(target)) {
            hoverTargets.add(target);
        }
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

    private void releaseTerrainTexture() {
        if (terrainTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(terrainTextureLocation);
        }
        terrainTextureLocation = null;
    }

    @Override
    public void close() {
        releaseTerrainTexture();
    }

    record RenderResult(List<HoverTarget> hoverTargets) {
        RenderResult {
            hoverTargets = List.copyOf(hoverTargets);
        }
    }

    record HoverTarget(Building building, Integer floorOrdinal, boolean structure) {
    }
}
