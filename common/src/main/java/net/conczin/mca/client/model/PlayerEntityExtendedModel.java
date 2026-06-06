package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

public class PlayerEntityExtendedModel<T extends LivingEntity> extends PlayerModel implements CommonVillagerModel<T> {
    public final ModelPart breasts;
    public final ModelPart breastsWear;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;
    private boolean wearsHidden;

    public PlayerEntityExtendedModel(ModelPart root) {
        this(root, false);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.breasts = root.getChild(BREASTS);
        this.breastsWear = root.getChild(BREASTPLATE);
    }

    public void copyPropertiesTo(HumanoidModel<?> target) {
        copyBaseHumanoidState(target);
        if (target instanceof PlayerEntityExtendedModel<?> playerTarget) {
            copyAttributes(playerTarget);
        }
        if (target instanceof PlayerArmorExtendedModel<?> armorTarget) {
            copyAttributes(armorTarget);
        }
    }

    private void copyBaseHumanoidState(HumanoidModel<?> target) {
        CommonVillagerModel.copyPartState(target.head, head);
        CommonVillagerModel.copyPartState(target.hat, hat);
        CommonVillagerModel.copyPartState(target.body, body);
        CommonVillagerModel.copyPartState(target.leftArm, leftArm);
        CommonVillagerModel.copyPartState(target.rightArm, rightArm);
        CommonVillagerModel.copyPartState(target.leftLeg, leftLeg);
        CommonVillagerModel.copyPartState(target.rightLeg, rightLeg);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyAttributes(PlayerEntityExtendedModel<?> target) {
        CommonVillagerModel.copyPartState(target.leftPants, leftPants);
        CommonVillagerModel.copyPartState(target.rightPants, rightPants);
        CommonVillagerModel.copyPartState(target.leftSleeve, leftSleeve);
        CommonVillagerModel.copyPartState(target.rightSleeve, rightSleeve);
        CommonVillagerModel.copyPartState(target.jacket, jacket);
        CommonVillagerModel.copyPartState(target.breastsWear, breastsWear);

        copyCommonAttributes((CommonVillagerModel) target);

        target.breasts.visible = breasts.visible;
        CommonVillagerModel.copyPartState(target.breasts, breasts);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyAttributes(PlayerArmorExtendedModel<?> target) {
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
        return ImmutableList.of(breasts, breastsWear);
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


    public PlayerEntityExtendedModel<T> hideWears() {
        this.wearsHidden = true;
        this.jacket.visible = false;
        this.leftPants.visible = false;
        this.rightPants.visible = false;
        this.leftSleeve.visible = false;
        this.rightSleeve.visible = false;
        this.breastsWear.visible = false;
        return this;
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        head.visible = !state.isSpectator;
        super.setupAnim(state);

        breasts.visible = !state.isSpectator;
        breastsWear.visible = state.showJacket;

        if (state instanceof VillagerStateHolder holder) {
            var visuals = holder.mca$getVisualSnapshot();
            if (visuals != null) {
                applyVillagerDimensions(visuals, state.isCrouching);
            }
        }

        if (wearsHidden) {
            jacket.visible = false;
            leftPants.visible = false;
            rightPants.visible = false;
            leftSleeve.visible = false;
            rightSleeve.visible = false;
            breastsWear.visible = false;
        }
    }

    public void copyVisibility(HumanoidModel<?> model) {
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        jacket.visible = model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftSleeve.visible = model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightSleeve.visible = model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftPants.visible = model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightPants.visible = model.rightLeg.visible;
    }
}
