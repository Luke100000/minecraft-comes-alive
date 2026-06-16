package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
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
        if (canUse(skin)) {
            var visuals = VillagerVisuals.require(state);
            Identifier leftSkin = cached(skin.toString().replace(".png", "_left.png"), Identifier::parse);
            Identifier rightSkin = cached(skin.toString().replace(".png", "_right.png"), Identifier::parse);

            if (canUse(leftSkin) && canUse(rightSkin)) {
                int leftColor = visuals.heterochromia() ? visuals.eyeLeftDye() : visuals.eyeDye();
                
                Identifier leftIris = getOrGenerateEyeLayer(leftSkin, false);
                Identifier leftSclera = getOrGenerateEyeLayer(leftSkin, true);
                Identifier rightIris = getOrGenerateEyeLayer(rightSkin, false);
                Identifier rightSclera = getOrGenerateEyeLayer(rightSkin, true);
                
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, leftSclera, tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, rightSclera, tint, visible, glowing, state);
                
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, leftColor, leftIris, tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, visuals.eyeDye(), rightIris, tint, visible, glowing, state);
            } else {
                Identifier faceIris = getOrGenerateEyeLayer(skin, false);
                Identifier faceSclera = getOrGenerateEyeLayer(skin, true);
                
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, faceSclera, tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, visuals.eyeDye(), faceIris, tint, visible, glowing, state);
            }
        }

        Identifier overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, overlay, tint, visible, glowing, state);
        }
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = VillagerVisuals.require(state);
        boolean blink = visuals.isBlinking();
        Gender gender = Gender.byName(visuals.genderDataName());

        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = blink ? 2 : (int) Math.min(6, Math.max(0, visuals.faceGene() * 7));
            return cached("skins/face/" + variant + "/" + index + ".png", MCA::locate);
        }
        return list.pick(variant, gender, visuals.faceGene(), blink);
    }

    private static final Map<Identifier, Identifier> IRIS_TEXTURE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Identifier, Identifier> SCLERA_TEXTURE_CACHE = new ConcurrentHashMap<>();

    private Identifier getOrGenerateEyeLayer(Identifier original, boolean isSclera) {
        Map<Identifier, Identifier> cache = isSclera ? SCLERA_TEXTURE_CACHE : IRIS_TEXTURE_CACHE;
        return cache.computeIfAbsent(original, id -> {
            try {
                var resource = net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id);
                if (resource.isEmpty()) return id;
                
                com.mojang.blaze3d.platform.NativeImage originalImage;
                try (java.io.InputStream stream = resource.get().open()) {
                    originalImage = com.mojang.blaze3d.platform.NativeImage.read(stream);
                }
                
                int w = originalImage.getWidth();
                int h = originalImage.getHeight();
                com.mojang.blaze3d.platform.NativeImage newImage = new com.mojang.blaze3d.platform.NativeImage(w, h, true);
                
                for (int x = 0; x < w; x++) {
                    for (int y = 0; y < h; y++) {
                        int pixel = originalImage.getPixel(x, y);
                        int a = net.minecraft.util.ARGB.alpha(pixel);
                        if (a == 0) continue;
                        
                        int r = net.minecraft.util.ARGB.red(pixel);
                        int g = net.minecraft.util.ARGB.green(pixel);
                        int b = net.minecraft.util.ARGB.blue(pixel);
                        
                        // Check if pixel is white/sclera (alpha == 1 OR opaque and very bright white)
                        boolean isPixelSclera = (a == 1) || (a == 255 && r >= 220 && g >= 220 && b >= 220);
                        
                        if (isSclera == isPixelSclera) {
                            newImage.setPixel(x, y, pixel);
                        }
                    }
                }
                
                originalImage.close();
                
                // Register new dynamic texture
                Identifier newId = Identifier.fromNamespaceAndPath("mca", "dynamic/eye/" + (isSclera ? "sclera" : "iris") + "/" + id.getPath().replace("/", "_"));
                net.minecraft.client.Minecraft.getInstance().getTextureManager().register(newId, new net.minecraft.client.renderer.texture.DynamicTexture(newId::toString, newImage));
                return newId;
            } catch (Exception e) {
                return id;
            }
        });
    }
}
