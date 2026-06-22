package net.mca.client.resources;

import net.mca.MCA;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.util.HashMap;
import java.util.Map;

public class ColorPalette {
    static final Map<Identifier, ColorPalette> REGISTRY = new HashMap<>();

    public static final ColorPalette SKIN = new ColorPalette(new Identifier(MCA.MOD_ID, "textures/colormap/villager_skin.png"));
    public static final ColorPalette HAIR = new ColorPalette(new Identifier(MCA.MOD_ID, "textures/colormap/villager_hair.png"));

    static final Data EMPTY = new Data(1, 1, new int[] {0xFFFFFF});

    private final Identifier id;

    Data data = EMPTY;

    public ColorPalette(Identifier id) {
        this.id = id;
        REGISTRY.put(id, this);
    }

    public Identifier getId() {
        return id;
    }

    public float[] getColor(float u, float v, float greenShift) {
        int x = clampFloor(v, data.width - 1); // horizontal
        int y = clampFloor(u, data.height - 1); // vertical

        int color = data.colors[y * data.height + x];

        float[] result = new float[] {
                ColorHelper.Abgr.getBlue(color) / 255F,
                ColorHelper.Abgr.getGreen(color) / 255F,
                ColorHelper.Abgr.getRed(color) / 255F
        };

        if (greenShift > 0) {
            applyGreenShift(result, greenShift);
        }

        return result;
    }

    private static void applyGreenShift(float[] color, float greenShift) {
        float percentDown = 1 - greenShift / 1.8F;

        color[0] = MathHelper.clamp(color[0] * percentDown, 0, 1);
        color[1] = MathHelper.clamp(color[1] * percentDown, 0, 1);
        color[2] = MathHelper.clamp(color[2] * percentDown, 0, 1);
    }

    private static int clampFloor(float v, int max) {
        return (int)Math.floor(MathHelper.clamp(v * max, 0, max));
    }

    static class Data {
        final int width;
        final int height;

        final int[] colors;

        public Data(int width, int height, int[] colors) {
            this.width = width;
            this.height = height;
            this.colors = colors;
        }
    }
}





















