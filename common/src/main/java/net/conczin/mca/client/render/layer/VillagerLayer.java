package net.conczin.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.ResourceLocationException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class VillagerLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final Map<String, ResourceLocation> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer);
        this.model = model;
    }

    @Nullable
    public ResourceLocation getSkin(S state) {
        return null;
    }

    @Nullable
    protected ResourceLocation getOverlay(S state) {
        return null;
    }

    public int getColor(S state, float tickDelta) {
        return 0xFFFFFFFF;
    }

    protected boolean isTranslucent() {
        return false;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, S state, float yRot, float xRot) {
        if (!(state instanceof VillagerStateHolder holder) || holder.mca$getVisualSnapshot() == null) {
            return;
        }

        if (!holder.mca$isVillagerRendererActive()) {
            return;
        }

        // Primarily restores compatibility with Armourers Workshop.
        if (model instanceof VillagerEntityModelMCA layer) {
            layer.copyVisibility(getParentModel());
        }
        if (model instanceof PlayerEntityExtendedModel<?> layer) {
            layer.copyVisibility(getParentModel());
        }

        boolean visible = !state.isInvisible;
        boolean glowing = state.appearsGlowing;

        copyParentModelState();
        prepareModel(state);
        renderFinal(poseStack, bufferSource, packedLight, state, state.ageInTicks, visible, glowing);
    }

    protected void prepareModel(S state) {
    }

    protected void copyParentModelState() {
        M parentModel = this.getParentModel();
        copyVisibility(parentModel, this.model);

        if ((Object) parentModel instanceof VillagerEntityModelMCA parentVillager && (Object) this.model instanceof VillagerEntityModelMCA villagerModel) {
            parentVillager.copyPropertiesTo((HumanoidModel) villagerModel);
            villagerModel.copyVisibility(parentVillager);
        }

        if ((Object) parentModel instanceof PlayerEntityExtendedModel parentPlayer && (Object) this.model instanceof PlayerEntityExtendedModel playerModel) {
            parentPlayer.copyPropertiesTo((HumanoidModel) playerModel);
            playerModel.copyVisibility(parentPlayer);
        }

        if ((Object) parentModel instanceof PlayerEntityExtendedModel parentPlayer && (Object) this.model instanceof PlayerArmorExtendedModel armorModel) {
            parentPlayer.copyPropertiesTo((HumanoidModel) armorModel);
        }
    }

    protected static void copyVisibility(HumanoidModel source, HumanoidModel target) {
        target.head.visible = source.head.visible;
        target.hat.visible = source.hat.visible;
        target.body.visible = source.body.visible;
        target.rightArm.visible = source.rightArm.visible;
        target.leftArm.visible = source.leftArm.visible;
        target.rightLeg.visible = source.rightLeg.visible;
        target.leftLeg.visible = source.leftLeg.visible;
    }

    protected static void setAllVisible(HumanoidModel model, boolean visible) {
        model.head.visible = visible;
        model.hat.visible = visible;
        model.body.visible = visible;
        model.rightArm.visible = visible;
        model.leftArm.visible = visible;
        model.rightLeg.visible = visible;
        model.leftLeg.visible = visible;

        if (model instanceof VillagerEntityModelMCA villagerModel) {
            villagerModel.bodyWear.visible = visible;
            villagerModel.leftArmwear.visible = visible;
            villagerModel.rightArmwear.visible = visible;
            villagerModel.leftLegwear.visible = visible;
            villagerModel.rightLegwear.visible = visible;
            villagerModel.breastsWear.visible = visible;
            villagerModel.breasts.visible = visible;
        }

        if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.breastsWear.visible = visible;
            playerModel.breasts.visible = visible;
        }

        if (model instanceof PlayerArmorExtendedModel<?> armorModel) {
            armorModel.breasts.visible = visible;
        }
    }

    protected static void hideLegs(HumanoidModel model) {
        model.rightLeg.visible = false;
        model.leftLeg.visible = false;

        if (model instanceof VillagerEntityModelMCA villagerModel) {
            villagerModel.leftLegwear.visible = false;
            villagerModel.rightLegwear.visible = false;
        }

        if (model instanceof PlayerEntityExtendedModel<?> playerModel) {
            playerModel.leftPants.visible = false;
            playerModel.rightPants.visible = false;
        }
    }

    public void renderFinal(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, S state, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        ResourceLocation skin = getSkin(state);
        if (canUse(skin)) {
            int color = getColor(state, tickDelta);
            renderModel(poseStack, bufferSource, packedLight, this.model, color, skin, tint, visible, glowing, state);
        }

        ResourceLocation overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, bufferSource, packedLight, this.model, 0xFFFFFF, overlay, tint, visible, glowing, state);
        }
    }

    @Nullable
    protected RenderType getRenderLayer(ResourceLocation texture, boolean showBody, boolean translucent, boolean showOutline) {
        if (translucent) {
            return RenderType.itemEntityTranslucentCull(texture);
        } else if (showBody) {
            return this.model.renderType(texture);
        } else {
            return showOutline ? RenderType.outline(texture) : null;
        }
    }

    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, M model, int color, ResourceLocation texture, int overlay, boolean visible, boolean glowing, S state) {
        RenderType layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) {
            return;
        }
        VertexConsumer buffer = bufferSource.getBuffer(layer);
        model.renderToBuffer(poseStack, buffer, packedLight, overlay, color);
    }

    public final boolean canUse(ResourceLocation texture) {
        if (texture == null || MCA.isBlankString(texture.getPath())) {
            return false;
        }
        return TEXTURE_EXIST_CACHE.computeIfAbsent(texture, s -> {
            if (texture.getNamespace().equals("immersive_library")) {
                return true;
            }
            return Minecraft.getInstance().getResourceManager().getResource(texture).isPresent();
        });
    }

    @Nullable
    protected final ResourceLocation cached(String name, Function<String, ResourceLocation> supplier) {
        return TEXTURE_CACHE.computeIfAbsent(name, s -> {
            try {
                return supplier.apply(s);
            } catch (ResourceLocationException ignored) {
                return null;
            }
        });
    }
}
