package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintScreenMapInteractionTest {
    @Test
    void viewportPreservesFractionalRequestedCenter() {
        BlueprintMapViewport viewport = BlueprintMapViewport.create(
                100, 120, 80, -305.25D, -1653.75D, 1.37F);

        assertEquals(-305.25D, viewport.mapCenterX(), 0.0000001D);
        assertEquals(-1653.75D, viewport.mapCenterZ(), 0.0000001D);
        assertEquals(100.3425D, viewport.screenX(-305.0D), 0.0001D);
    }

    @Test
    void zoomAroundPointerKeepsCursorWorldPositionInvariant() {
        BlueprintMapViewport before = BlueprintMapViewport.create(
                100, 120, 80, -305.25D, -1653.75D, 1.37F);
        double mouseX = 137.5D;
        double mouseY = 91.25D;
        double worldX = before.worldX(mouseX);
        double worldZ = before.worldZ(mouseY);

        BlueprintMapViewport after = before.zoomedAround(mouseX, mouseY, 2.36F);

        assertEquals(worldX, after.worldX(mouseX), 0.0000001D);
        assertEquals(worldZ, after.worldZ(mouseY), 0.0000001D);
    }

    @Test
    void customScaleSnapsToTheNextPresetInEitherDirection() {
        assertEquals(2.0F, BlueprintScreen.snapMapScale(1.37F, 1));
        assertEquals(3.0F, BlueprintScreen.snapMapScale(2.0F, 1));
        assertEquals(1.0F, BlueprintScreen.snapMapScale(1.37F, -1));
        assertEquals(0.5F, BlueprintScreen.snapMapScale(1.0F, -1));
    }

    @Test
    void customScaleImmediatelyBesidePresetStillSnapsToThatPreset() {
        assertEquals(2.0F, BlueprintScreen.snapMapScale(1.99995F, 1));
        assertEquals(2.0F, BlueprintScreen.snapMapScale(2.00005F, -1));
    }

    @Test
    void wheelZoomProducesCustomScaleAndClampsToPresetRange() {
        assertEquals(1.1F, BlueprintScreen.zoomMapScale(1.0F, 1.0D), 0.0001F);
        assertEquals(0.5F, BlueprintScreen.zoomMapScale(0.5F, -20.0D), 0.0001F);
        assertEquals(4.0F, BlueprintScreen.zoomMapScale(4.0F, 20.0D), 0.0001F);
    }

    @Test
    void numericScaleLabelAlwaysUsesTwoDecimals() {
        assertEquals("1.37:1", BlueprintScreen.formatMapScale(1.37F));
        assertEquals("2.00:1", BlueprintScreen.formatMapScale(2.0F));
        assertEquals("0.50:1", BlueprintScreen.formatMapScale(0.5F));
    }

    @Test
    void pointerOnlyBecomesAPanAfterCrossingDragThreshold() {
        BlueprintScreen.MapPanState pan = new BlueprintScreen.MapPanState();

        pan.begin(10.0D, 10.0D);
        assertFalse(pan.update(12.0D, 10.0D));
        assertTrue(pan.update(13.0D, 10.0D));
        assertTrue(pan.end());
        assertFalse(pan.end());
    }

    @Test
    void clickWithoutDraggingDoesNotCountAsPan() {
        BlueprintScreen.MapPanState pan = new BlueprintScreen.MapPanState();

        pan.begin(10.0D, 10.0D);
        assertFalse(pan.update(11.0D, 11.0D));
        assertFalse(pan.end());
    }
}
