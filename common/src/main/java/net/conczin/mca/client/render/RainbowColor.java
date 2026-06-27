package net.conczin.mca.client.render;

import net.minecraft.client.color.ColorLerper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;

public final class RainbowColor {
    public static final int CYCLE_DURATION = 25 * DyeColor.values().length;

    private RainbowColor() {
    }

    public static int sheep(float tick) {
        // Vanilla's lerper expects a non-negative phase; normalize ours so negative or wrapped ticks never crash.
        return ColorLerper.getLerpedColor(ColorLerper.Type.SHEEP, Mth.positiveModulo(tick, CYCLE_DURATION));
    }
}
