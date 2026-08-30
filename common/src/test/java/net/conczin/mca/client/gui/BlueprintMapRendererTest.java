package net.conczin.mca.client.gui;

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
}
