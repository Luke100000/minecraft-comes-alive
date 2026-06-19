package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.RainbowColor;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int SCLERA_MIN_CHANNEL = 160;
    private static final int SCLERA_MAX_CHANNEL_SPREAD = 32;

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
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, true, EyeSide.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, true), getOrGenerateEyeLayer(skin, false, EyeSide.LEFT), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, false, EyeSide.RIGHT), tint, visible, glowing, state);
            } else {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, OPAQUE_WHITE, getOrGenerateEyeLayer(skin, true, EyeSide.FULL), tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, getEyeColor(visuals, tickDelta, false), getOrGenerateEyeLayer(skin, false, EyeSide.FULL), tint, visible, glowing, state);
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
        Gender gender = Gender.byName(visuals.genderDataName());

        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(11, Math.max(0, visuals.faceGene() * 12));
            return cached("skins/face/normal/" + (index == 11 ? "blink" : index) + ".png", MCA::locate);
        }
        return list.pick(variant, gender, visuals.faceGene());
    }

    private Identifier getBlinkSkin() {
        return cached("skins/face/normal/blink.png", MCA::locate);
    }

    private static final Map<EyeLayerKey, Identifier> EYE_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private enum EyeSide {
        FULL,
        LEFT,
        RIGHT
    }

    private record EyeLayerKey(Identifier texture, boolean sclera, EyeSide side) {
    }

    private static int getEyeColor(VillagerVisuals visuals, float tickDelta, boolean left) {
        if (visuals.rainbowEyes()) {
            int offset = left && visuals.heterochromia() ? RainbowColor.CYCLE_DURATION / 2 : 0;
            return RainbowColor.sheep(visuals.tickCount() + tickDelta + offset);
        }
        return left && visuals.heterochromia() ? visuals.eyeLeftDye() : visuals.eyeDye();
    }

    private Identifier getOrGenerateEyeLayer(Identifier original, boolean isSclera, EyeSide side) {
        return EYE_TEXTURE_CACHE.computeIfAbsent(new EyeLayerKey(original, isSclera, side), key -> {
            try {
                Identifier id = key.texture();
                var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isEmpty()) return id;
                
                com.mojang.blaze3d.platform.NativeImage originalImage;
                try (java.io.InputStream stream = resource.get().open()) {
                    originalImage = com.mojang.blaze3d.platform.NativeImage.read(stream);
                }
                
                int w = originalImage.getWidth();
                int h = originalImage.getHeight();
                EyeBounds bounds = findEyeBounds(originalImage);
                if (key.side() != EyeSide.FULL && bounds.width() % 2 != 0) {
                    throw new IllegalStateException("Face eye texture width must be divisible by 2 for heterochromia: " + id + " bounds=" + bounds);
                }
                int splitX = bounds.minX() + bounds.width() / 2;
                com.mojang.blaze3d.platform.NativeImage newImage = new com.mojang.blaze3d.platform.NativeImage(w, h, true);
                
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int pixel = originalImage.getPixel(x, y);
                        int a = ARGB.alpha(pixel);
                        if (a == 0) continue;
                        if (!isInEyeSide(x, splitX, key.side())) continue;

                        boolean isPixelSclera = isScleraPixel(a, ARGB.red(pixel), ARGB.green(pixel), ARGB.blue(pixel));
                        
                        if (key.sclera() == isPixelSclera) {
                            newImage.setPixel(x, y, pixel);
                        }
                    }
                }
                
                originalImage.close();
                
                Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/eye/" + key.side().name().toLowerCase(java.util.Locale.ROOT) + "/" + (key.sclera() ? "sclera" : "iris") + "/" + id.getPath().replace("/", "_"));
                net.minecraft.client.Minecraft.getInstance().getTextureManager().register(newId, new net.minecraft.client.renderer.texture.DynamicTexture(newId::toString, newImage));
                return newId;
            } catch (Exception e) {
                throw new IllegalStateException("Failed to generate eye texture layer for " + key.texture(), e);
            }
        });
    }

    private static boolean isInEyeSide(int x, int splitX, EyeSide side) {
        return switch (side) {
            case FULL -> true;
            case LEFT -> x >= splitX;
            case RIGHT -> x < splitX;
        };
    }

    private static EyeBounds findEyeBounds(com.mojang.blaze3d.platform.NativeImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                if (ARGB.alpha(image.getPixel(x, y)) == 0) {
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
        return new EyeBounds(minX, minY, maxX, maxY);
    }

    private record EyeBounds(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX + 1;
        }
    }

    private static boolean isScleraPixel(int alpha, int red, int green, int blue) {
        if (alpha == 1) {
            return true;
        }
        if (alpha != 255) {
            return false;
        }

        int min = Math.min(red, Math.min(green, blue));
        int max = Math.max(red, Math.max(green, blue));
        return min >= SCLERA_MIN_CHANNEL && max - min <= SCLERA_MAX_CHANNEL_SPREAD;
    }
}
