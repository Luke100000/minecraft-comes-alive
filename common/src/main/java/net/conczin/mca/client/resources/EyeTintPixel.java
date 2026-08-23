package net.conczin.mca.client.resources;

import net.minecraft.util.FastColor;

public final class EyeTintPixel {
    public static final int IRIS_MARKER_ALPHA = 254;

    private EyeTintPixel() {
    }

    public static boolean isIrisMarker(int alpha) {
        return alpha == IRIS_MARKER_ALPHA;
    }

    public static Mask decodeMarkedMask(int packedColor) {
        int alpha = FastColor.ABGR32.alpha(packedColor);
        if (!isIrisMarker(alpha)) {
            throw new IllegalArgumentException("Expected alpha 254 eye tint marker, got " + alpha);
        }
        int red = FastColor.ABGR32.red(packedColor);
        int green = FastColor.ABGR32.green(packedColor);
        int blue = FastColor.ABGR32.blue(packedColor);
        int active = (red > 0 ? 1 : 0) + (green > 0 ? 1 : 0) + (blue > 0 ? 1 : 0);
        if (active != 1) {
            throw new IllegalArgumentException(
                    "Eye tint mask pixel must have exactly one active RGB channel: "
                            + red + "," + green + "," + blue
            );
        }

        if (red > 0) {
            return new Mask(Tone.SHADOW, red);
        }
        if (green > 0) {
            return new Mask(Tone.PRIMARY, green);
        }
        return new Mask(Tone.HIGHLIGHT, blue);
    }

    public enum Tone {
        SHADOW,
        PRIMARY,
        HIGHLIGHT
    }

    public record Mask(Tone tone, int intensity) {
    }
}
