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
import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.getChildOrEmpty;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

public class PlayerEntityExtendedModel<T extends LivingEntity> extends PlayerModel implements CommonVillagerModel<T> {
    public final ModelPart breasts;
    public final ModelPart breastsWear;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;
    private boolean wearsHidden;
    private boolean receivesDeferredAnimationPose;

    public PlayerEntityExtendedModel(ModelPart root) {
        this(root, false);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim) {
        super(root, slim);
        this.breasts = getChildOrEmpty(root, BREASTS);
        this.breastsWear = getChildOrEmpty(root, BREASTPLATE);
    }

    /** Uses an externally baked player root for animation and detached MCA-only parts for rendering. */
    public PlayerEntityExtendedModel(ModelPart root, boolean slim, ModelPart mcaPartsRoot) {
        super(root, slim);
        this.breasts = mcaPartsRoot.getChild(BREASTS);
        this.breastsWear = mcaPartsRoot.getChild(BREASTPLATE);
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
        CommonVillagerModel.copyPartState(target.body, body);
        CommonVillagerModel.copyPartState(target.leftArm, leftArm);
        CommonVillagerModel.copyPartState(target.rightArm, rightArm);
        CommonVillagerModel.copyPartState(target.leftLeg, leftLeg);
        CommonVillagerModel.copyPartState(target.rightLeg, rightLeg);
        CommonVillagerModel.copyPartState(target.hat, hat);
        if (target instanceof CommonVillagerModel<?> villagerTarget) {
            villagerTarget.syncWearParts();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void copyAttributes(PlayerEntityExtendedModel<?> target) {
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

    public PlayerEntityExtendedModel<T> receiveDeferredAnimationPose() {
        receivesDeferredAnimationPose = true;
        return this;
    }

    public void setAllVisible(boolean visible) {
        head.visible = visible;
        hat.visible = visible;
        body.visible = visible;
        leftArm.visible = visible;
        rightArm.visible = visible;
        leftLeg.visible = visible;
        rightLeg.visible = visible;
        breasts.visible = visible;
        jacket.visible = !wearsHidden && visible;
        leftPants.visible = !wearsHidden && visible;
        rightPants.visible = !wearsHidden && visible;
        leftSleeve.visible = !wearsHidden && visible;
        rightSleeve.visible = !wearsHidden && visible;
        breastsWear.visible = !wearsHidden && visible;
    }

    @Override
    public void syncWearParts() {
        // Player wear parts are children of their canonical bones in 26.1.2+.
        // Only the detached MCA breastplate needs an explicit root-level pose sync.
        CommonVillagerModel.copyPartState(breastsWear, breasts);
    }

    @Override
    public void setupAnim(AvatarRenderState state) {
        head.visible = !state.isSpectator;
        super.setupAnim(state);

        breasts.visible = !state.isSpectator;
        breastsWear.visible = state.showJacket;

        if (state instanceof VillagerStateHolder holder) {
            var visuals = holder.mca$getVisuals();
            if (visuals != null) {
                applyVillagerDimensions(visuals);
            }
            if (receivesDeferredAnimationPose && holder.mca$getHumanoidModelPose() != null) {
                holder.mca$getHumanoidModelPose().applyTo(this);
            }
        }
        syncWearParts();

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
        jacket.visible = !wearsHidden && model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = !wearsHidden && model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftSleeve.visible = !wearsHidden && model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightSleeve.visible = !wearsHidden && model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftPants.visible = !wearsHidden && model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightPants.visible = !wearsHidden && model.rightLeg.visible;
    }
}
