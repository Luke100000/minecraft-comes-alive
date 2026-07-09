package net.mca.client.render.layer;

import net.mca.MCA;
import net.mca.client.model.CommonVillagerModel;
import net.mca.client.resources.EyeTextureLayers;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.mca.resources.FaceList;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<T extends LivingEntity, M extends BipedEntityModel<T>> extends VillagerLayer<T, M> {
    private static final int NATURAL_DYE = 0xFFFFFFFF;
    private static final int ALBINISM_EYE_COLOR = 0xFFE8A0A0;
    private static final int BLUE_EYE_COLOR = 0xFF557FA6;
    private static final int GREEN_EYE_COLOR = 0xFF5B8756;
    private static final int HAZEL_EYE_COLOR = 0xFF8A6A35;
    private static final int BROWN_EYE_COLOR = 0xFF4A2B18;
    private static final Map<EyeLayerKey, Identifier> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private final String variant;

    public FaceLayer(FeatureRendererContext<T, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void render(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setVisible(false);
        model.head.visible = true;
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public void renderFinal(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlay(villager, 0);
        Identifier skin = getSkin(villager);

        if (isBlinking(villager)) {
            Identifier blink = getBlinkSkin();
            if (canUse(blink)) {
                renderModel(transform, provider, light, model, 1.0F, 1.0F, 1.0F, blink, overlay, visible, glowing);
            }
            return;
        }

        VillagerLike<?> villagerLike = getVillager(villager);
        if (canUse(skin)) {
            if (villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA)) {
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, true, EyeTextureLayers.Side.FULL), 0xFFFFFFFF, overlay, visible, glowing);
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, false, EyeTextureLayers.Side.LEFT), getEyeColor(villager, tickDelta, true), overlay, visible, glowing);
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, false, EyeTextureLayers.Side.RIGHT), getEyeColor(villager, tickDelta, false), overlay, visible, glowing);
            } else {
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, true, EyeTextureLayers.Side.FULL), 0xFFFFFFFF, overlay, visible, glowing);
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, false, EyeTextureLayers.Side.FULL), getEyeColor(villager, tickDelta, false), overlay, visible, glowing);
            }
        }

        Identifier extraOverlay = getOverlay(villager);
        if (!Objects.equals(skin, extraOverlay) && canUse(extraOverlay)) {
            renderModel(transform, provider, light, model, 1.0F, 1.0F, 1.0F, extraOverlay, overlay, visible, glowing);
        }
    }

    private void renderEyeLayer(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, Identifier texture, int color, int overlay, boolean visible, boolean glowing) {
        float[] rgb = argbToRgb(color);
        renderModel(transform, provider, light, model, rgb[0], rgb[1], rgb[2], texture, overlay, visible, glowing);
    }

    @Override
    public Identifier getSkin(T villager) {
        FaceList list = FaceList.getInstance();
        if (list == null) {
            return getBlinkSkin();
        }
        return list.pick(variant, getVillager(villager).getGenetics().getGene(Genetics.FACE));
    }

    private Identifier getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    @Override
    protected Identifier getOverlay(T villager) {
        return null;
    }

    public static void clearGeneratedEyeTextureCache() {
        var textureManager = MinecraftClient.getInstance().getTextureManager();
        EYE_TEXTURE_CACHE.values().forEach(id -> {
            if (id != null && id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/eye/")) {
                textureManager.destroyTexture(id);
            }
        });
        EYE_TEXTURE_CACHE.clear();
    }

    private Identifier getOrGenerateEyeLayer(Identifier original, boolean isSclera, EyeTextureLayers.Side side) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, isSclera, side), key -> {
            try {
                var resource = MinecraftClient.getInstance().getResourceManager().getResource(key.texture());
                if (resource.isEmpty()) {
                    return key.texture();
                }

                try (InputStream stream = resource.get().getInputStream(); NativeImage originalImage = NativeImage.read(stream)) {
                    int width = originalImage.getWidth();
                    int height = originalImage.getHeight();
                    EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(originalImage);
                    if (key.side() != EyeTextureLayers.Side.FULL && bounds.width() % 2 != 0) {
                        throw new IllegalStateException("Face eye texture width must be divisible by 2 for heterochromia: " + key.texture() + " bounds=" + bounds);
                    }
                    int splitX = bounds.minX() + bounds.width() / 2;
                    NativeImage newImage = new NativeImage(width, height, true);

                    try {
                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                int pixel = originalImage.getColor(x, y);
                                int alpha = abgrAlpha(pixel);
                                if (alpha == 0 || !EyeTextureLayers.isInSide(x, splitX, key.side())) {
                                    continue;
                                }

                                boolean scleraPixel = EyeTextureLayers.isScleraPixel(
                                        alpha,
                                        abgrRed(pixel),
                                        abgrGreen(pixel),
                                        abgrBlue(pixel)
                                );
                                if (key.sclera() == scleraPixel) {
                                    newImage.setColor(x, y, pixel);
                                }
                            }
                        }

                        Identifier newId = MCA.locate("dynamic/eye/" + key.side().name().toLowerCase(Locale.ROOT)
                                + "/" + (key.sclera() ? "sclera" : "iris") + "/"
                                + key.texture().getNamespace() + "_" + key.texture().getPath().replace('/', '_'));
                        MinecraftClient.getInstance().getTextureManager().registerTexture(newId, new NativeImageBackedTexture(newImage));
                        return newId;
                    } catch (Exception exception) {
                        newImage.close();
                        throw exception;
                    }
                }
            } catch (Exception exception) {
                MCA.LOGGER.warn("Failed to generate eye texture layer for {}", key.texture(), exception);
                return key.texture();
            }
        });
    }

    private int getEyeColor(T villager, float tickDelta, boolean left) {
        VillagerLike<?> villagerLike = getVillager(villager);
        if (villagerLike.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            int offset = left && villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA) ? (25 * DyeColor.values().length) / 2 : 0;
            return getRainbow(villager, tickDelta, offset);
        }
        return getStaticEyeColor(villager, left);
    }

    private int getStaticEyeColor(T villager, boolean left) {
        VillagerLike<?> villagerLike = getVillager(villager);
        boolean heterochromia = villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA);
        int dye = left && heterochromia ? villagerLike.getEyeLeftDye() : villagerLike.getEyeDye();
        return dye != NATURAL_DYE ? dye : getGeneticEyeColor(villager, left && heterochromia);
    }

    private int getGeneticEyeColor(T villager, boolean shifted) {
        VillagerLike<?> villagerLike = getVillager(villager);
        if (villagerLike.getTraits().hasTrait(Traits.ALBINISM)) {
            return ALBINISM_EYE_COLOR;
        }

        float eyeColor = MathHelper.fractionalPart(villagerLike.getGenetics().getGene(Genetics.FACE) + (shifted ? 0.43F : 0.0F));
        if (eyeColor < 0.35F) {
            return lerpColor(eyeColor / 0.35F, BLUE_EYE_COLOR, GREEN_EYE_COLOR);
        }
        if (eyeColor < 0.70F) {
            return lerpColor((eyeColor - 0.35F) / 0.35F, GREEN_EYE_COLOR, HAZEL_EYE_COLOR);
        }
        return lerpColor((eyeColor - 0.70F) / 0.30F, HAZEL_EYE_COLOR, BROWN_EYE_COLOR);
    }

    private int getRainbow(T villager, float tickDelta, int offset) {
        int ticks = Math.abs(villager.age) + offset;
        int block = ticks / 25 + villager.getId();
        int count = DyeColor.values().length;
        int first = block % count;
        int second = (block + 1) % count;
        float mix = ((float)(ticks % 25) + tickDelta) / 25.0F;
        return lerpColor(mix, rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(first))), rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(second))));
    }

    private boolean isBlinking(T villager) {
        int time = villager.age / 2 + (int)(getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        return time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDead();
    }

    private VillagerLike<?> getVillager(T villager) {
        return CommonVillagerModel.getVillager(villager);
    }

    private static float[] argbToRgb(int color) {
        return new float[] {
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }

    private static int rgbToArgb(float[] rgb) {
        return 0xFF000000
                | (MathHelper.clamp(Math.round(rgb[0] * 255.0F), 0, 255) << 16)
                | (MathHelper.clamp(Math.round(rgb[1] * 255.0F), 0, 255) << 8)
                | MathHelper.clamp(Math.round(rgb[2] * 255.0F), 0, 255);
    }

    private static int lerpColor(float delta, int from, int to) {
        delta = MathHelper.clamp(delta, 0.0F, 1.0F);
        int a = Math.round(MathHelper.lerp(delta, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF));
        int r = Math.round(MathHelper.lerp(delta, (from >>> 16) & 0xFF, (to >>> 16) & 0xFF));
        int g = Math.round(MathHelper.lerp(delta, (from >>> 8) & 0xFF, (to >>> 8) & 0xFF));
        int b = Math.round(MathHelper.lerp(delta, from & 0xFF, to & 0xFF));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int abgrAlpha(int color) {
        return (color >>> 24) & 0xFF;
    }

    private static int abgrRed(int color) {
        return color & 0xFF;
    }

    private static int abgrGreen(int color) {
        return (color >>> 8) & 0xFF;
    }

    private static int abgrBlue(int color) {
        return (color >>> 16) & 0xFF;
    }

    private record EyeLayerKey(Identifier texture, boolean sclera, EyeTextureLayers.Side side) {
    }
}
