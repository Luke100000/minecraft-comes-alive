package net.conczin.mca.client.render.layer;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.PlayerArmorExtendedModel;
import net.conczin.mca.client.model.PlayerEntityExtendedModel;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.IdentifierException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class VillagerLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends RenderLayer<S, M> {
    private static final Map<String, Identifier> TEXTURE_CACHE = Maps.newHashMap();
    private static final Map<Identifier, Boolean> TEXTURE_EXIST_CACHE = Maps.newHashMap();

    static {
        // The temp image is used for temporary canvases and definitely exists.
        TEXTURE_EXIST_CACHE.put(MCA.locate("temp"), true);
    }

    public final M model;

    public VillagerLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer);
        this.model = model;
    }

    @Nullable
    public Identifier getSkin(S state) {
        return null;
    }

    @Nullable
    protected Identifier getOverlay(S state) {
        return null;
    }

    public int getColor(S state, float tickDelta) {
        return 0xFFFFFFFF;
    }

    protected boolean isTranslucent() {
        return false;
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float yRot, float xRot) {
        if (!(state instanceof VillagerStateHolder holder) || !holder.mca$isVillagerRendererActive()) {
            return;
        }

        boolean visible = !state.isInvisible;
        boolean glowing = state.appearsGlowing();

        copyParentModelState();
        prepareModel(state);
        renderFinal(poseStack, submitNodeCollector, lightCoords, state, state.ageInTicks, visible, glowing);
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

    public void renderFinal(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        Identifier skin = getSkin(state);
        if (canUse(skin)) {
            int color = getColor(state, tickDelta);
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, color, skin, tint, visible, glowing, state);
        }

        Identifier overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, overlay, tint, visible, glowing, state);
        }
    }

    @Nullable
    protected RenderType getRenderLayer(Identifier texture, boolean showBody, boolean translucent, boolean showOutline) {
        if (translucent) {
            return RenderTypes.entityTranslucent(texture);
        } else if (showBody) {
            return this.model.renderType(texture);
        } else {
            return showOutline ? RenderTypes.outline(texture) : null;
        }
    }

    protected void renderModel(
        PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, M model, int color, Identifier texture, int overlay, boolean visible, boolean glowing, S state
    ) {
        RenderType layer = getRenderLayer(texture, visible, isTranslucent(), glowing);
        if (layer == null) {
            return;
        }

        submitNodeCollector.submitModel(model, state, poseStack, layer, lightCoords, overlay, color, null, state.outlineColor, null);
    }

    public final boolean canUse(Identifier texture) {
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
