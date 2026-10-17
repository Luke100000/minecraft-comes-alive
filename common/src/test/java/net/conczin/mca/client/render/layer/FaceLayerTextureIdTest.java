package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.resources.EyeTextureLayers;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FaceLayerTextureIdTest {
    @Test
    void generatedLayersDoNotCollideWhenEyePathsContainUnderscores() {
        ResourceLocation first = ResourceLocation.fromNamespaceAndPath("addon", "skins/face/a_b/c.png");
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath("addon", "skins/face/a/b_c.png");

        assertNotEquals(
                FaceLayer.eyeTextureId(first, "primary", EyeTextureLayers.Side.FULL),
                FaceLayer.eyeTextureId(second, "primary", EyeTextureLayers.Side.FULL)
        );
    }

    @Test
    void generatedLayersKeepNamespaceAndPathBoundariesDistinct() {
        ResourceLocation first = ResourceLocation.fromNamespaceAndPath("a_b", "c/d.png");
        ResourceLocation second = ResourceLocation.fromNamespaceAndPath("a", "b_c/d.png");

        assertNotEquals(
                FaceLayer.eyeTextureId(first, "shadow", EyeTextureLayers.Side.LEFT),
                FaceLayer.eyeTextureId(second, "shadow", EyeTextureLayers.Side.LEFT)
        );
    }
}
