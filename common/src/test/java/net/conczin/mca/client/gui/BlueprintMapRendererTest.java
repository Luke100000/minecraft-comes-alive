package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.StructureFloor;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintMapRendererTest {
    @Test
    void horizontalOutlineIsOneScreenPixelAtEveryScale() {
        BlueprintMapFootprint.Edge edge = new BlueprintMapFootprint.Edge(10, 20, 14, 20);

        for (float scale : new float[]{0.5F, 1.0F, 1.37F, 2.36F, 4.0F}) {
            BlueprintMapRenderer.OutlineQuad quad = BlueprintMapRenderer.outlineQuad(edge, scale);
            assertEquals(1.0F, (quad.maxZ() - quad.minZ()) * scale, 0.0001F);
            assertTrue(quad.minX() < edge.x0());
            assertTrue(quad.maxX() > edge.x1());
        }
    }

    @Test
    void verticalOutlineIsOneScreenPixelAtEveryScale() {
        BlueprintMapFootprint.Edge edge = new BlueprintMapFootprint.Edge(10, 20, 10, 24);

        for (float scale : new float[]{0.5F, 1.0F, 1.37F, 2.36F, 4.0F}) {
            BlueprintMapRenderer.OutlineQuad quad = BlueprintMapRenderer.outlineQuad(edge, scale);
            assertEquals(1.0F, (quad.maxX() - quad.minX()) * scale, 0.0001F);
            assertTrue(quad.minZ() < edge.z0());
            assertTrue(quad.maxZ() > edge.z1());
        }
    }

    @Test
    void connectorGlyphsStayCompactWithoutInferringPersistedVerticalDestinations() {
        assertEquals("↕", BlueprintMapRenderer.connectorGlyph(connector(StructureFloor.ConnectorType.LADDER)));
        assertEquals("↕", BlueprintMapRenderer.connectorGlyph(connector(StructureFloor.ConnectorType.TRAPDOOR)));
        assertEquals("▯", BlueprintMapRenderer.connectorGlyph(connector(StructureFloor.ConnectorType.DOOR)));
        assertEquals("═", BlueprintMapRenderer.connectorGlyph(connector(StructureFloor.ConnectorType.GATE)));
    }

    @Test
    void connectorGlyphScaleUsesMostOfTheVisibleMapCell() {
        float tallGlyph = BlueprintMapRenderer.connectorGlyphScale(5, 9);
        float wideGlyph = BlueprintMapRenderer.connectorGlyphScale(12, 9);

        assertEquals(1.2F, 9.0F * tallGlyph, 0.0001F);
        assertTrue(5.0F * tallGlyph < 1.0F);
        assertEquals(1.2F, 12.0F * wideGlyph, 0.0001F);
        assertTrue(9.0F * wideGlyph < 1.0F);
    }

    private static BlueprintMapGeometry.MapConnectorLayer connector(StructureFloor.ConnectorType type) {
        return new BlueprintMapGeometry.MapConnectorLayer(
                1,
                new StructureFloor.ConnectorMarker(BlockPos.ZERO, type));
    }
}
