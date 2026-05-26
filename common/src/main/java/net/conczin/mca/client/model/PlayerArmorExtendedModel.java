package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;

public class PlayerArmorExtendedModel<T extends LivingEntity> extends HumanoidModel<AvatarRenderState> implements CommonVillagerModel<T> {
    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;
    private RenderMask renderMask = RenderMask.FULL;

    public PlayerArmorExtendedModel(ModelPart root) {
        super(root);
        this.breasts = root.getChild(BREASTS);
    }

    public void copyPropertiesTo(HumanoidModel<?> target) {
        if (target instanceof PlayerEntityExtendedModel<?> playerTarget) {
            copyAttributes(playerTarget);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyAttributes(PlayerEntityExtendedModel<?> target) {
        CommonVillagerModel.copyPartState(target.head, head);
        CommonVillagerModel.copyPartState(target.hat, hat);
        CommonVillagerModel.copyPartState(target.body, body);
        CommonVillagerModel.copyPartState(target.leftArm, leftArm);
        CommonVillagerModel.copyPartState(target.rightArm, rightArm);
        CommonVillagerModel.copyPartState(target.leftLeg, leftLeg);
        CommonVillagerModel.copyPartState(target.rightLeg, rightLeg);
        copyCommonAttributes((CommonVillagerModel) target);

        target.breasts.visible = breasts.visible;
        CommonVillagerModel.copyPartState(target.breasts, breasts);
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public ModelPart getBodyPart() {
        return body;
    }

    @Override
    public Iterable<ModelPart> getCommonHeadParts() {
        return ImmutableList.of(head);
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts);
    }

    @Override
    public VillagerDimensions.Mutable getDimensions() {
        return dimensions;
    }

    @Override
    public float getBreastSize() {
        return breastSize;
    }

    @Override
    public void setBreastSize(float breastSize) {
        this.breastSize = breastSize;
    }

    @Override
    public RenderMask getRenderMask() {
        return renderMask;
    }

    @Override
    public void setRenderMask(RenderMask renderMask) {
        this.renderMask = renderMask;
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        head.visible = !state.isSpectator;
        hat.visible = state.showHat;
        body.visible = !state.isSpectator;
        breasts.visible = !state.isSpectator;
        leftArm.visible = !state.isSpectator;
        rightArm.visible = !state.isSpectator;
        leftLeg.visible = !state.isSpectator;
        rightLeg.visible = !state.isSpectator;
        super.setupAnim(state);

        if (state instanceof VillagerStateHolder holder) {
            var visuals = CommonVillagerModel.peekVisuals(holder);
            if (visuals != null) {
                applyVillagerDimensions(visuals, state.isCrouching);
            }
        }

        CommonVillagerModel.applyRenderMask(this, renderMask);
    }
}
