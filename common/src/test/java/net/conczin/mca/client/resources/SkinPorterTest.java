package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SkinPorterTest {
    private static final int[] ARM_X = {40, 40, 32, 48};
    private static final int[] ARM_Y = {20, 36, 52, 52};

    @Test
    void convertsDefaultArmUvsToTheLayoutExpectedBySlimPlayerGeometry() {
        try (NativeImage image = new NativeImage(64, 64, true)) {
            fillWithCoordinates(image);

            int[][] original = new int[64][64];
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    original[x][y] = image.getPixelRGBA(x, y);
                }
            }

            SkinPorter.convertDefaultToSlim(image);

            // Vanilla slim side faces are 4 (west) + 3 (north) + 4 (east) + 3 (south).
            // The west face stays in place, north/south lose one center column, and the
            // 4-wide east face must be copied intact rather than bleeding into south.
            int[] sideSource = {4, 5, 7, 8, 9, 10, 11, 12, 13, 15};
            int[] topSource = {4, 5, 7, 8, 9, 11};
            for (int arm = 0; arm < ARM_X.length; arm++) {
                int offsetX = ARM_X[arm];
                int offsetY = ARM_Y[arm];
                for (int y = 0; y < 12; y++) {
                    for (int p = 0; p < sideSource.length; p++) {
                        assertEquals(original[offsetX + sideSource[p]][offsetY + y],
                                image.getPixelRGBA(offsetX + 4 + p, offsetY + y));
                    }
                    assertEquals(0, image.getPixelRGBA(offsetX + 14, offsetY + y));
                    assertEquals(0, image.getPixelRGBA(offsetX + 15, offsetY + y));
                }

                int topY = offsetY - 4;
                for (int y = 0; y < 4; y++) {
                    for (int p = 0; p < topSource.length; p++) {
                        assertEquals(original[offsetX + topSource[p]][topY + y],
                                image.getPixelRGBA(offsetX + 4 + p, topY + y));
                    }
                    assertEquals(0, image.getPixelRGBA(offsetX + 10, topY + y));
                    assertEquals(0, image.getPixelRGBA(offsetX + 11, topY + y));
                }
            }
        }
    }

    private static void fillWithCoordinates(NativeImage image) {
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                image.setPixelRGBA(x, y, 0xFF000000 | x << 8 | y);
            }
        }
    }
}
