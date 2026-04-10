package net.conczin.mca.client.resources;

import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

public class ColorPalette {
    static final Data EMPTY = new Data(1, 1, new int[]{0xFFFFFFFF});
    static final Map<Identifier, ColorPalette> REGISTRY = new HashMap<>();

    public static final ColorPalette SKIN = new ColorPalette(MCA.locate("textures/colormap/villager_skin.png"));
    public static final ColorPalette HAIR = new ColorPalette(MCA.locate("textures/colormap/villager_hair.png"));

    private final Identifier id;

    Data data = EMPTY;

    public ColorPalette(Identifier id) {
        this.id = id;
        REGISTRY.put(id, this);
    }

    private static int applyGreenShift(int color, float greenShift) {
        return ARGB.colorFromFloat(
                1.0f,
                Mth.clamp(ARGB.red(color) / 255f * (1.0f - greenShift * 0.3f) - greenShift * 0.1f, 0, 1),
                Mth.clamp(ARGB.green(color) / 255f * (1.0f + greenShift * 0.3f) + greenShift * 0.1f, 0, 1),
                Mth.clamp(ARGB.blue(color) / 255f, 0, 1)
        );
    }

    private static int clampFloor(float v, int max) {
        return (int) Math.floor(Mth.clamp(v * max, 0, max));
    }

    public Identifier getId() {
        return id;
    }

    public int getColor(float u, float v, float greenShift) {
        int x = clampFloor(v, data.width - 1); // horizontal
        int y = clampFloor(u, data.height - 1); // vertical

        int color = data.colors[y * data.width + x];

        return greenShift > 0 ? applyGreenShift(color, greenShift) : color;
    }

    public record Data(int width, int height, int[] colors) {
    }
}





















