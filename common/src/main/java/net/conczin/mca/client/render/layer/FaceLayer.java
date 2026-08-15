package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    private static final Map<EyeLayerKey, ResourceLocation> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private final String variant;

    public FaceLayer(RenderLayerParent<T, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setAllVisible(false);
        model.head.visible = true;
        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0);
        ResourceLocation skin = getSkin(villager);

        if (isBlinking(villager)) {
            ResourceLocation blink = getBlinkSkin();
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

        ResourceLocation extraOverlay = getOverlay(villager);
        if (!Objects.equals(skin, extraOverlay) && canUse(extraOverlay)) {
            renderModel(transform, provider, light, model, 1.0F, 1.0F, 1.0F, extraOverlay, overlay, visible, glowing);
        }
    }

    private void renderEyeLayer(PoseStack transform, MultiBufferSource provider, int light, T villager, ResourceLocation texture, int color, int overlay, boolean visible, boolean glowing) {
        float[] rgb = argbToRgb(color);
        renderModel(transform, provider, light, model, rgb[0], rgb[1], rgb[2], texture, overlay, visible, glowing);
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        FaceList list = FaceList.getInstance();
        if (list == null) {
            return getBlinkSkin();
        }
        return list.pick(variant, getVillager(villager).getGenetics().getGene(Genetics.FACE));
    }

    private ResourceLocation getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        return null;
    }

    public static void clearGeneratedEyeTextureCache() {
        var textureManager = Minecraft.getInstance().getTextureManager();
        EYE_TEXTURE_CACHE.values().forEach(id -> {
            if (id != null && id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/eye/")) {
                textureManager.release(id);
            }
        });
        EYE_TEXTURE_CACHE.clear();
    }

    private ResourceLocation getOrGenerateEyeLayer(ResourceLocation original, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, layer, side), key -> {
            try {
                var resource = Minecraft.getInstance().getResourceManager().getResource(key.texture());
                if (resource.isEmpty()) {
                    return key.texture();
                }

                try (InputStream stream = resource.get().open(); NativeImage originalImage = NativeImage.read(stream)) {
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
                                int pixel = originalImage.getPixelRGBA(x, y);
                                int alpha = abgrAlpha(pixel);
                                if (alpha == 0 || !EyeTextureLayers.isInSide(x, splitX, key.side())) {
                                    continue;
                                }

                                boolean includePixel = EyeTextureLayers.isPixelForLayer(
                                        key.layer(), alpha, abgrRed(pixel), abgrGreen(pixel), abgrBlue(pixel));
                                if (includePixel) {
                                    newImage.setPixelRGBA(x, y, pixel);
                                }
                            }
                        }

                        ResourceLocation newId = MCA.locate("dynamic/eye/" + key.side().name().toLowerCase(Locale.ROOT)
                                + "/" + key.layer().name().toLowerCase(Locale.ROOT) + "/"
                                + key.texture().getNamespace() + "_" + key.texture().getPath().replace('/', '_'));
                        Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newImage));
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
        int ticks = Math.abs(villager.tickCount) + offset;
        int block = ticks / 25 + villager.getId();
        int count = DyeColor.values().length;
        int first = block % count;
        int second = (block + 1) % count;
        float mix = ((float)(ticks % 25) + tickDelta) / 25.0F;
        return FastColor.ARGB32.lerp(mix, rgbToArgb(Sheep.getColorArray(DyeColor.byId(first))), rgbToArgb(Sheep.getColorArray(DyeColor.byId(second))));
    }

    private boolean isBlinking(T villager) {
        int time = villager.tickCount / 2 + (int)(getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        return time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
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
                | (Mth.clamp(Math.round(rgb[0] * 255.0F), 0, 255) << 16)
                | (Mth.clamp(Math.round(rgb[1] * 255.0F), 0, 255) << 8)
                | Mth.clamp(Math.round(rgb[2] * 255.0F), 0, 255);
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

    private record EyeLayerKey(ResourceLocation texture, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
    }
}
