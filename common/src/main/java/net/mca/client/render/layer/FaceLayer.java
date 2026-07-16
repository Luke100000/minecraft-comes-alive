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
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<T extends LivingEntity, M extends BipedEntityModel<T>> extends VillagerLayer<T, M> {
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
            renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL), 0xFFFFFFFF, overlay, visible, glowing);
            renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL), EyeTextureLayers.DETAILS_TINT, overlay, visible, glowing);

            if (villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA)) {
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.LEFT), getEyeColor(villager, tickDelta, true), overlay, visible, glowing);
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.RIGHT), getEyeColor(villager, tickDelta, false), overlay, visible, glowing);
            } else {
                renderEyeLayer(transform, provider, light, villager, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL), getEyeColor(villager, tickDelta, false), overlay, visible, glowing);
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

    private Identifier getOrGenerateEyeLayer(Identifier original, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, layer, side), key -> {
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

                                boolean includePixel = EyeTextureLayers.isPixelForLayer(
                                        key.layer(), alpha, abgrRed(pixel), abgrGreen(pixel), abgrBlue(pixel));
                                if (includePixel) {
                                    newImage.setColor(x, y, pixel);
                                }
                            }
                        }

                        Identifier newId = MCA.locate("dynamic/eye/" + key.side().name().toLowerCase(Locale.ROOT)
                                + "/" + key.layer().name().toLowerCase(Locale.ROOT) + "/"
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
        int color;
        if (villagerLike.getTraits().hasTrait(Traits.RAINBOW_EYES)) {
            int offset = left && villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA) ? (25 * DyeColor.values().length) / 2 : 0;
            color = getRainbow(villager, tickDelta, offset);
        } else {
            color = EyeTextureLayers.getStaticEyeColor(villagerLike, left);
        }
        return EyeTextureLayers.applyBrightness(color, villagerLike.getGenetics().getGene(Genetics.EYE_BRIGHTNESS));
    }

    private int getRainbow(T villager, float tickDelta, int offset) {
        int ticks = Math.abs(villager.age) + offset;
        int block = ticks / 25 + villager.getId();
        int count = DyeColor.values().length;
        int first = block % count;
        int second = (block + 1) % count;
        float mix = ((float)(ticks % 25) + tickDelta) / 25.0F;
        return ColorHelper.Argb.lerp(mix, rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(first))), rgbToArgb(SheepEntity.getRgbColor(DyeColor.byId(second))));
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

    private record EyeLayerKey(Identifier texture, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
    }
}
