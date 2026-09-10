package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREAST_TRANSFORM;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

public class PlayerEntityExtendedModel<T extends LivingEntity> extends PlayerModel<T> implements CommonVillagerModel<T> {
    public final ModelPart breastTransform;
    public final ModelPart breasts;
    public final ModelPart breastsWear;
    private boolean wearsHidden;

    public PlayerEntityExtendedModel(ModelPart root) {
        this(root, false);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim) {
        super(root, slim);
        breastTransform = body.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
        breastsWear = breastTransform.getChild(BREASTPLATE);
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof CommonVillagerModel<?> model) {
            copyMorphologyTo(model);
        }
        if (target instanceof PlayerEntityExtendedModel<?> model) {
            model.hat.copyFrom(model.head);
            model.syncWearParts();
        }
    }

    public PlayerEntityExtendedModel<T> hideWears() {
        hideWearsInternal();
        return this;
    }

    private void hideWearsInternal() {
        wearsHidden = true;
        jacket.visible = false;
        leftSleeve.visible = false;
        rightSleeve.visible = false;
        leftPants.visible = false;
        rightPants.visible = false;
        breastsWear.visible = false;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);
        breastTransform.visible = visible;
        breasts.visible = visible;
        boolean showWears = !wearsHidden && visible;
        jacket.visible = showWears;
        leftSleeve.visible = showWears;
        rightSleeve.visible = showWears;
        leftPants.visible = showWears;
        rightPants.visible = showWears;
        breastsWear.visible = showWears;
    }

    @Override
    public void syncWearParts() {
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
        breastsWear.copyFrom(breasts);
    }

    @Override
    public ModelPart getMorphologyHead() {
        return head;
    }

    @Override
    public ModelPart getMorphologyHat() {
        return hat;
    }

    @Override
    public ModelPart getBreastTransform() {
        return breastTransform;
    }

    @Override
    public ModelPart getBreastPart() {
        return breasts;
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
    }

    @Override
    public void setupAnim(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        var villager = CommonVillagerModel.getVillager(entity);
        if (villager.getAgeState() == AgeState.BABY && !entity.isPassenger()) {
            limbDistance = (float) Math.sin(entity.tickCount / 12F);
            limbAngle = (float) Math.cos(entity.tickCount / 9F) * 3;
            headYaw += (float) Math.sin(entity.tickCount / 2F);
        }

        super.setupAnim(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        applyVillagerDimensions(villager);
    }

    @Override
    public void copyVisibility(HumanoidModel<?> model) {
        boolean showWears = !wearsHidden;
        head.visible = model.head.visible;
        hat.visible = model.head.visible && model.hat.visible;
        body.visible = model.body.visible;
        jacket.visible = showWears && model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftSleeve.visible = showWears && model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightSleeve.visible = showWears && model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftPants.visible = showWears && model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightPants.visible = showWears && model.rightLeg.visible;

        if (model instanceof CommonVillagerModel<?> source) {
            breastTransform.visible = model.body.visible && source.getBreastTransform().visible;
            breasts.visible = model.body.visible && source.getBreastPart().visible;
        } else {
            breastTransform.visible &= model.body.visible;
            breasts.visible &= model.body.visible;
        }
        breastsWear.visible = showWears && breastTransform.visible;
    }
}
