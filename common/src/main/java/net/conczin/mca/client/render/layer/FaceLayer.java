package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.resources.EyeTextureLayers;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import com.mojang.blaze3d.platform.NativeImage;

import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;

    private final String variant;

    public FaceLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, false);
        this.model.head.visible = true;
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public int getColor(S state, float tickDelta) {
        return VillagerVisuals.require(state).eyeDye();
    }

    @Override
    public void renderFinal(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        Identifier skin = getSkin(state);
        var visuals = VillagerVisuals.require(state);
        Identifier renderedSkin = visuals.isBlinking() ? getBlinkSkin() : skin;
        if (canUse(renderedSkin)) {
            if (visuals.isBlinking()) {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, renderedSkin, tint, visible, glowing, state);
            } else if (visuals.heterochromia()) {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, EyeTextureLayers.DETAILS_TINT, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, true), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.LEFT), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.RIGHT), tint, visible, glowing, state);
            } else {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.SCLERA, EyeTextureLayers.Side.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, EyeTextureLayers.DETAILS_TINT, getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.DETAILS, EyeTextureLayers.Side.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, EyeTextureLayers.Layer.IRIS, EyeTextureLayers.Side.FULL), tint, visible, glowing, state);
            }
        }

        Identifier overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, overlay, tint, visible, glowing, state);
        }
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = VillagerVisuals.require(state);
        FaceList list = FaceList.getInstance();
        return list == null ? getBlinkSkin() : list.pick(variant, visuals.faceGene());
    }

    private Identifier getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    private static final Map<EyeLayerKey, Identifier> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();

    public static void clearGeneratedEyeTextureCache() {
        var textureManager = net.minecraft.client.Minecraft.getInstance().getTextureManager();
        EYE_TEXTURE_CACHE.values().forEach(id -> {
            if (id.getNamespace().equals(MCA.MOD_ID) && id.getPath().startsWith("dynamic/eye/")) {
                textureManager.release(id);
            }
        });
        EYE_TEXTURE_CACHE.clear();
    }

    private record EyeLayerKey(Identifier texture, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
    }

    private static int getEyeColor(VillagerVisuals visuals, float tickDelta, boolean left) {
        return visuals.eyeColor(tickDelta, left);
    }

    private Identifier getOrGenerateEyeLayer(Identifier original, EyeTextureLayers.Layer layer, EyeTextureLayers.Side side) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, layer, side), key -> {
            try {
                Identifier id = key.texture();
                var resource = Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isEmpty()) return id;

                try (InputStream stream = resource.get().open(); NativeImage originalImage = NativeImage.read(stream)) {
                    int w = originalImage.getWidth();
                    int h = originalImage.getHeight();
                    EyeTextureLayers.Bounds bounds = EyeTextureLayers.findBounds(originalImage);
                    if (key.side() != EyeTextureLayers.Side.FULL && bounds.width() % 2 != 0) {
                        throw new IllegalStateException("Face eye texture width must be divisible by 2 for heterochromia: " + id + " bounds=" + bounds);
                    }
                    int splitX = bounds.minX() + bounds.width() / 2;
                    NativeImage newImage = new NativeImage(w, h, true);

                    try {
                        for (int x = 0; x < w; x++) {
                            for (int y = 0; y < h; y++) {
                                int pixel = originalImage.getPixel(x, y);
                                int a = ARGB.alpha(pixel);
                                if (a == 0) continue;
                                if (!EyeTextureLayers.isInSide(x, splitX, key.side())) continue;

                                if (EyeTextureLayers.isPixelForLayer(key.layer(), a, ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel))) {
                                    newImage.setPixel(x, y, pixel);
                                }
                            }
                        }

                        Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/eye/" + key.side().name().toLowerCase(Locale.ROOT) + "/" + key.layer().name().toLowerCase(Locale.ROOT) + "/" + id.getNamespace() + "_" + id.getPath().replace("/", "_"));
                        Minecraft.getInstance().getTextureManager().register(newId, new DynamicTexture(newId::toString, newImage));
                        return newId;
                    } catch (Exception exception) {
                        newImage.close();
                        throw exception;
                    }
                }
            } catch (Exception e) {
                MCA.LOGGER.warn("Failed to generate eye texture layer for {}", key.texture(), e);
                return key.texture();
            }
        });
    }
}
