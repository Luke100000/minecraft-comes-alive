package net.conczin.mca.client.resources;

import net.conczin.mca.resources.EyeDefinition;
import net.minecraft.util.Mth;

public final class EyeToneRendering {
    private EyeToneRendering() {
    }

    public static EyeDefinition.Tones resolve(EyeDefinition definition, int selectedArgb, float brightness) {
        EyeDefinition.Tones tones = definition.tones(selectedArgb);
        return new EyeDefinition.Tones(
                applyBrightness(tones.shadow(), brightness),
                applyBrightness(tones.primary(), brightness),
                applyBrightness(tones.highlight(), brightness)
        );
    }

    public static int legacyColor(int selectedArgb, float brightness) {
        return applyBrightness(selectedArgb, brightness);
    }

    public static int modernMaskPixel(EyeTintPixel.Mask mask, int toneArgb) {
        return multiplyPixel(neutralMaskPixel(mask), toneArgb);
    }

    public static int neutralMaskPixel(EyeTintPixel.Mask mask) {
        int alpha = 255;
        int intensity = mask.intensity();
        return (alpha << 24) | (intensity << 16) | (intensity << 8) | intensity;
    }

    public static int multiplyPixel(int packedAbgr, int tintArgb) {
        int tintRed = (tintArgb >>> 16) & 0xFF;
        int tintGreen = (tintArgb >>> 8) & 0xFF;
        int tintBlue = tintArgb & 0xFF;
        int tintAlpha = (tintArgb >>> 24) & 0xFF;

        int alpha = ((packedAbgr >>> 24) & 0xFF) * tintAlpha / 255;
        int red = (packedAbgr & 0xFF) * tintRed / 255;
        int green = ((packedAbgr >>> 8) & 0xFF) * tintGreen / 255;
        int blue = ((packedAbgr >>> 16) & 0xFF) * tintBlue / 255;
        return (alpha << 24) | (blue << 16) | (green << 8) | red;
    }

    public static int applyBrightness(int argb, float brightness) {
        float factor = 0.5F + Mth.clamp(brightness, 0.0F, 1.0F);
        int alpha = (argb >>> 24) & 0xFF;
        int red = scaleChannel((argb >>> 16) & 0xFF, factor);
        int green = scaleChannel((argb >>> 8) & 0xFF, factor);
        int blue = scaleChannel(argb & 0xFF, factor);
        return (alpha << 24) | (red << 16) | (green << 8) | blue;
    }

    private static int scaleChannel(int channel, float factor) {
        return Mth.clamp(Math.round(channel * factor), 0, 255);
    }
}
