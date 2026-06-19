package net.conczin.mca.client.render;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

public final class RainbowColor {
    private static final int COLOR_DURATION = 25;
    public static final int CYCLE_DURATION = COLOR_DURATION * DyeColor.values().length;
    private static final float SHEEP_BRIGHTNESS = 0.75F;
    private static final int SHEEP_WHITE = -1644826;

    private RainbowColor() {
    }

    public static int sheep(float tick) {
        int tickCount = Mth.floor(tick);
        int step = tickCount / COLOR_DURATION;
        DyeColor[] colors = DyeColor.values();
        DyeColor from = colors[step % colors.length];
        DyeColor to = colors[(step + 1) % colors.length];
        float delta = (tickCount % COLOR_DURATION + Mth.frac(tick)) / COLOR_DURATION;
        return ARGB.srgbLerp(delta, sheepColor(from), sheepColor(to));
    }

    private static int sheepColor(DyeColor color) {
        if (color == DyeColor.WHITE) {
            return SHEEP_WHITE;
        }

        int source = color.getTextureDiffuseColor();
        return ARGB.color(
                255,
                Mth.floor(ARGB.red(source) * SHEEP_BRIGHTNESS),
                Mth.floor(ARGB.green(source) * SHEEP_BRIGHTNESS),
                Mth.floor(ARGB.blue(source) * SHEEP_BRIGHTNESS)
        );
    }
}
