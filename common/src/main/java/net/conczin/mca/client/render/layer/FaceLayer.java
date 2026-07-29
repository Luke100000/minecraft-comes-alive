package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
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

import java.io.InputStream;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
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
                renderModel(transform, provider, light, model, OPAQUE_WHITE, blink, overlay, visible, glowing);
            }
            return;
        }

        VillagerLike<?> villagerLike = getVillager(villager);
        if (canUse(skin)) {
            renderModel(transform, provider, light, model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL), overlay, visible, glowing);
            renderModel(transform, provider, light, model, EyeTextureLayers.DETAILS_TINT, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL), overlay, visible, glowing);

            if (villagerLike.getTraits().hasTrait(Traits.HETEROCHROMIA)) {
                renderModel(transform, provider, light, model, getEyeColor(villager, tickDelta, true), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.LEFT), overlay, visible, glowing);
                renderModel(transform, provider, light, model, getEyeColor(villager, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.RIGHT), overlay, visible, glowing);
            } else {
                renderModel(transform, provider, light, model, getEyeColor(villager, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL), overlay, visible, glowing);
            }
        }

        ResourceLocation extraOverlay = getOverlay(villager);
        if (!Objects.equals(skin, extraOverlay) && canUse(extraOverlay)) {
            renderModel(transform, provider, light, model, OPAQUE_WHITE, extraOverlay, overlay, visible, glowing);
        }
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
                ResourceLocation id = key.texture();
                var resource = Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isEmpty()) {
                    return id;
                }

                try (InputStream stream = resource.get().open(); NativeImage originalImage = NativeImage.read(stream)) {
                    int width = originalImage.getWidth();
                    int height = originalImage.getHeight();
                    EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(originalImage);
                    if (key.side() != EyeTextureLayers.Side.FULL && bounds.width() % 2 != 0) {
                        throw new IllegalStateException("Face eye texture width must be divisible by 2 for heterochromia: " + id + " bounds=" + bounds);
                    }
                    int splitX = bounds.minX() + bounds.width() / 2;
                    NativeImage newImage = new NativeImage(width, height, true);

                    try {
                        for (int x = 0; x < width; x++) {
                            for (int y = 0; y < height; y++) {
                                int pixel = originalImage.getPixelRGBA(x, y);
                                int alpha = FastColor.ABGR32.alpha(pixel);
                                if (alpha == 0) {
                                    continue;
                                }
                                if (!EyeTextureLayers.isInSide(x, splitX, key.side())) {
                                    continue;
                                }

                                if (EyeTextureLayers.isPixelForLayer(key.layer(), alpha,
                                        FastColor.ABGR32.red(pixel), FastColor.ABGR32.green(pixel), FastColor.ABGR32.blue(pixel))) {
                                    newImage.setPixelRGBA(x, y, pixel);
                                }
                            }
                        }

                        ResourceLocation newId = ResourceLocation.fromNamespaceAndPath(
                                MCA.MOD_ID,
                                "dynamic/eye/" + key.side().name().toLowerCase(Locale.ROOT) + "/" + key.layer().name().toLowerCase(Locale.ROOT) + "/" + id.getNamespace() + "_" + id.getPath().replace("/", "_")
                        );
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
        return FastColor.ARGB32.lerp(mix, Sheep.getColor(DyeColor.byId(first)), Sheep.getColor(DyeColor.byId(second)));
    }

    private boolean isBlinking(T villager) {
        int time = villager.tickCount / 2 + (int)(getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        return time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
    }

    private VillagerLike<?> getVillager(T villager) {
        return CommonVillagerModel.getVillager(villager);
    }

    private record EyeLayerKey(ResourceLocation texture, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
    }
}
