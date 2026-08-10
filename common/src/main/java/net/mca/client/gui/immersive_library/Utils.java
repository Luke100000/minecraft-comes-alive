package net.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import net.mca.client.resources.SkinLocations;
import net.minecraft.util.Mth;

public class Utils {
    // Sanity check of skins by checking if at least one skin pixel is transparent, thus not being a valid vanilla skin, thus requiring at least minimal effort to convert to a valid skin
    public static boolean verify(NativeImage image) {
        int errors = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                if (SkinLocations.SKIN_LOOKUP[x][y] && image.getLuminanceOrAlpha(x, y) == 0) {
                    errors++;
                    if (errors > 6) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //Check if hair is grayscale and bright
    public static boolean verifyHair(NativeImage image) {
        int errors = 0;
        int pixels = 0;
        double brightness = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int r = image.getRedOrLuminance(x, y) & 0xFF;
                int g = image.getGreenOrLuminance(x, y) & 0xFF;
                int b = image.getBlueOrLuminance(x, y) & 0xFF;
                int a = image.getLuminanceOrAlpha(x, y) & 0xFF;

                if (a > 0) {
                    int l = Mth.clamp((int) (0.2126 * r + 0.7152 * g + 0.0722 * b), 0, 255);

                    brightness += l;

                    pixels++;

                    errors += Math.abs(r - l);
                    errors += Math.abs(g - l);
                    errors += Math.abs(b - l);
                }
            }
        }

        brightness /= pixels;

        return errors < pixels && brightness > 160.0;
    }
}
