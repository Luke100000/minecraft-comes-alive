package net.conczin.mca.client.resources;

import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

public class ColorPalette {
    public static final ColorPalette SKIN = new ColorPalette(MCA.locate("textures/colormap/villager_skin.png"));
    public static final ColorPalette HAIR = new ColorPalette(MCA.locate("textures/colormap/villager_hair.png"));
    static final Data EMPTY = new Data(1, 1, new int[]{0xFFFFFF});
    static final Map<ResourceLocation, ColorPalette> REGISTRY = new HashMap<>();
    private final ResourceLocation id;

    Data data = EMPTY;

    public ColorPalette(ResourceLocation id) {
        this.id = id;
        REGISTRY.put(id, this);
    }

    private static int applyGreenShift(int color, float greenShift) {
        return FastColor.ARGB32.lerp(greenShift / 1.8F, color, 0xFF00FF00);
    }

    private static int clampFloor(float v, int max) {
        return (int) Math.floor(Mth.clamp(v * max, 0, max));
    }

    public ResourceLocation getId() {
        return id;
    }

    public int getColor(float u, float v, float greenShift) {
        int x = clampFloor(v, data.width - 1); // horizontal
        int y = clampFloor(u, data.height - 1); // vertical

        int color = data.colors[y * data.height + x];

        return greenShift > 0 ? applyGreenShift(color, greenShift) : color;
    }

    public record Data(int width, int height, int[] colors) {
    }
}





















