package net.conczin.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.minecraft.IdentifierException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;

public abstract class VillagerLayer<M extends HumanoidModel<MCAHumanoidRenderState>>
        extends RenderLayer<MCAHumanoidRenderState, M> {
    private static final Map<String, Identifier> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<Identifier, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        // the temp image is used for temporary canvases and definitely exists
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model) {
        super(renderer);
        this.model = model;
    }

    @Nullable
    public Identifier getSkin(MCAHumanoidRenderState renderState) {
        return null;
    }

    @Nullable
    protected Identifier getOverlay(MCAHumanoidRenderState renderState) {
        return null;
    }

    public void adjustVisibility(MCAHumanoidRenderState renderState) {
    }

    public int getColor(MCAHumanoidRenderState renderState, float tickDelta) {
        return 0xFFFFFFFF;
    }

    protected boolean isTranslucent() {
        return false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int light,
            MCAHumanoidRenderState renderState, float yRot, float xRot) {
        if (!renderState.visible && !renderState.glowing)
            return;
        if (renderState.villager == null || renderState.villager.isInvisible())
            return;
        if (renderState.villager instanceof Player player && !MCAClient.useVillagerRenderer(player.getUUID()))
            return;

        // Keep per-part visibility in sync with the parent model.
        if (model instanceof VillagerEntityModelMCA layerModel) {
            layerModel.copyVisibility(getParentModel());
        }

        model.setupAnim(renderState);

        adjustVisibility(renderState);

        int tint = net.minecraft.client.renderer.entity.LivingEntityRenderer.getOverlayCoords(renderState, 0.0f);
        Identifier skin = getSkin(renderState);
        if (canUse(skin)) {
            int color = getColor(renderState, 0.0f);
            renderModel(poseStack, submitNodeCollector, light, model, renderState, color, skin, tint, renderState.visible,
                    renderState.glowing);
        }

        Identifier overlay = getOverlay(renderState);
        if (overlay != null && !overlay.equals(skin) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, light, model, renderState, 0xFFFFFFFF, overlay, tint, renderState.visible,
                    renderState.glowing);
        }
    }

    @Nullable
    protected RenderType getRenderLayer(Identifier texture, boolean showBody, boolean translucent,
            boolean showOutline) {
        if (translucent) {
            return RenderTypes.itemEntityTranslucentCull(texture);
        }
        if (showBody) {
            return RenderTypes.entityCutoutNoCull(texture);
        }
        return showOutline ? RenderTypes.outline(texture) : null;
    }

    private void renderModel(PoseStack transform, SubmitNodeCollector provider, int light, M model,
            MCAHumanoidRenderState renderState, int color, Identifier texture, int overlay, boolean visible,
            boolean glowing) {
        RenderType layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null)
            return;
        // 1.21.x order: packedLight, packedOverlay, tintColor, sprite, outlineColor, crumblingOverlay.
        provider.submitModel(model, renderState, transform, layer, light, overlay, color, null, 0, null);
    }

    public final boolean canUse(Identifier texture) {
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
            if (texture != null && texture.getNamespace().equals("immersive_library")) {
                return true;
            }
            boolean result = texture != null && Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
            System.out.println("MCA_DEBUG canUse: " + texture + " -> " + result);
            return result;
        });
    }

    @Nullable
    protected final Identifier cached(String name, Function<String, Identifier> supplier) {
        return TEXTURE_CACHE.computeIfAbsent(name, s -> {
            try {
                return supplier.apply(s);
            } catch (IdentifierException ignored) {
                return null;
            }
        });
    }
}
