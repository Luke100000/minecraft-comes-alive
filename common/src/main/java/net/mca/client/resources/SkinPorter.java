package net.mca.client.resources;

import net.minecraft.client.texture.NativeImage;

import java.util.List;

public class SkinPorter {
    private record UVMapping(int x0, int y0, int x1, int y1, int offsetX, int offsetY, boolean flip) {
    }

    private static final List<UVMapping> mappings = List.of(
            // leg
            new UVMapping(4, 16, 8, 20, 16, 32, true), // top
            new UVMapping(8, 16, 12, 20, 16, 32, true), // bottom
            new UVMapping(0, 20, 4, 32, 24, 32, true), // left
            new UVMapping(4, 20, 8, 32, 16, 32, true), // front
            new UVMapping(8, 20, 12, 32, 8, 32, true), // right
            new UVMapping(12, 20, 16, 32, 16, 32, true), // back

            // arm
            new UVMapping(44, 16, 48, 20, -8, 32, true), // top
            new UVMapping(48, 16, 52, 20, -8, 32, true), // bottom
            new UVMapping(40, 20, 44, 32, 0, 32, true), // left
            new UVMapping(44, 20, 48, 32, -8, 32, true), // front
            new UVMapping(48, 20, 52, 32, -16, 32, true), // right
            new UVMapping(52, 20, 56, 32, -8, 32, true) // back
    );

    /**
     * Ports a legacy 32px skin to a 64px skin, mirroring and filling the right arm and left, leaving the rest blank
     */
    public static NativeImage portLegacySkin(NativeImage image) {
        NativeImage ported = new NativeImage(64, 64, false);

        // copy upper part
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 32; y++) {
                ported.setColor(x, y, image.getColor(x, y));
            }
        }

        // clear lower part
        for (int x = 0; x < 64; x++) {
            for (int y = 32; y < 64; y++) {
                ported.setColor(x, y, 0);
            }
        }

        // copy new parts
        for (UVMapping mapping : mappings) {
            for (int x = mapping.x0; x < mapping.x1; x++) {
                for (int y = mapping.y0; y < mapping.y1; y++) {
                    ported.setColor(x + mapping.offsetX, y + mapping.offsetY, image.getColor(mapping.flip ? (mapping.x1 - (x - mapping.x0) - 1) : x, y));
                }
            }
        }

        image.close();
        return ported;
    }

    /**
     * Checks if that skin could be a slim format
     */
    public static boolean isSlimFormat(NativeImage image) {
        return image.getOpacity(54, 25) == 0;
    }

    /**
     * Converts a slim skin to a default one by stretching the center pixel. Not fancy but at least valid.
     */
    public static void convertSlimToDefault(NativeImage image) {
        stretch(image, 40, 20);
        stretch(image, 40, 36);
        stretch(image, 32, 52);
        stretch(image, 48, 52);
    }

    private static void stretch(NativeImage image, int offsetX, int offsetY) {
        int target = offsetX + 16 - 1;
        int original = offsetX + 14 - 1;
        for (int p = 0; p < 12; p++) {
            for (int y = 0; y < 12; y++) {
                image.setColor(target, offsetY + y, image.getColor(original, offsetY + y));
            }
            target--;
            if (p != 6 && p != 9) {
                original--;
            }
        }

        offsetY -= 4;
        target = offsetX + 16 - 1 - 4;
        original = offsetX + 14 - 1 - 4;
        for (int p = 0; p < 8; p++) {
            for (int y = 0; y < 4; y++) {
                image.setColor(target, offsetY + y, image.getColor(original, offsetY + y));
            }
            target--;
            if (p != 1 && p != 5) {
                original--;
            }
        }
    }
}
