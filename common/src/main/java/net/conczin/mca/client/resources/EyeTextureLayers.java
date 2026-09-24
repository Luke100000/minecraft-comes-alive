package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public final class EyeTextureLayers {
    private static final int NATURAL_DYE = 0xFFFFFFFF;

    private static final int ALBINISM_EYE_COLOR = 0xFFE8A0A0;
    private static final int BLUE_EYE_COLOR = 0xFF3A98E8;
    private static final int GREEN_EYE_COLOR = 0xFF4CB346;
    private static final int HAZEL_EYE_COLOR = 0xFFC29B35;
    private static final int BROWN_EYE_COLOR = 0xFF7C4825;
    private EyeTextureLayers() {
    }

    public static int getStaticEyeColor(VillagerLike<?> villager, boolean left) {
        boolean heterochromia = villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villager.getEyeLeftDye() : villager.getEyeDye();
        return dye != NATURAL_DYE ? dye : getGeneticEyeColor(villager, left && heterochromia);
    }

    public static int getBaseEyeColor(VillagerLike<?> villager, boolean left, float tickDelta) {
        if (!villager.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            return getStaticEyeColor(villager, left);
        }

        int colorCount = DyeColor.values().length;
        int offset = left && villager.getTraits().hasTrait(Traits.HETEROCHROMIA)
                ? (25 * colorCount) / 2
                : 0;
        Entity entity = villager.asEntity();
        int ticks = Math.abs(entity.tickCount) + offset;
        int first = (ticks / 25 + entity.getId()) % colorCount;
        float mix = ((float)(ticks % 25) + tickDelta) / 25.0F;
        return FastColor.ARGB32.lerp(
                mix,
                Sheep.getColor(DyeColor.byId(first)),
                Sheep.getColor(DyeColor.byId((first + 1) % colorCount))
        );
    }

    private static int getGeneticEyeColor(VillagerLike<?> villager, boolean shifted) {
        if (villager.getTraits().hasTrait(Traits.ALBINISM)) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = Mth.frac(villager.getGenetics().getGene(Genetics.EYE_COLOR) + (shifted ? 0.43F : 0.0F));
        if (eyeColor < 0.35F) {
            return FastColor.ARGB32.lerp(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return FastColor.ARGB32.lerp((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return FastColor.ARGB32.lerp((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    public static DecodedPixel decodePixel(int pixel) {
        int alpha = FastColor.ABGR32.alpha(pixel);
        if (alpha == 0) {
            return null;
        }
        if (EyeTintPixel.isIrisMarker(alpha)) {
            EyeTintPixel.Mask mask = EyeTintPixel.decodeMarkedMask(pixel);
            return DecodedPixel.tint(mask.tone(), EyeToneRendering.neutralMaskPixel(mask));
        }
        return DecodedPixel.fixed(pixel);
    }

    public static Bounds findBounds(NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                int pixel = image.getPixelRGBA(x, y);
                int alpha = FastColor.ABGR32.alpha(pixel);
                if (alpha == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("Face eye texture has no visible pixels");
        }
        return new Bounds(minX, minY, maxX, maxY);
    }

    public enum Side {
        FULL,
        LEFT,
        RIGHT
    }

    public enum PixelKind {
        FIXED,
        TINT
    }

    public record DecodedPixel(PixelKind kind, EyeTintPixel.Tone tone, int pixel) {
        private static DecodedPixel fixed(int pixel) {
            return new DecodedPixel(PixelKind.FIXED, null, pixel);
        }

        private static DecodedPixel tint(EyeTintPixel.Tone tone, int pixel) {
            return new DecodedPixel(PixelKind.TINT, tone, pixel);
        }
    }

    public record Bounds(int minX, int minY, int maxX, int maxY) {
        public int width() {
            return maxX - minX + 1;
        }
    }
}
