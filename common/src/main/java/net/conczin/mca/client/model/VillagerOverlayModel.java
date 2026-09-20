package net.conczin.mca.client.model;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

public final class VillagerOverlayModel<T extends LivingEntity> extends PlayerModel<T> {
    private final ModelPart breastTransform;
    private final ModelPart breasts;
    private final ModelPart breastsWear;
    private final List<ModelPart> breastParts;
    private boolean wearsHidden;

    public VillagerOverlayModel(ModelPart root, boolean slim) {
        super(root, slim);
        breastTransform = body.getChild(MCAModelGeometry.BREAST_TRANSFORM);
        breasts = breastTransform.getChild(MCAModelGeometry.BREASTS);
        breastsWear = breastTransform.getChild(MCAModelGeometry.BREASTPLATE);
        breastParts = List.of(breasts, breastsWear);
    }

    public VillagerOverlayModel<T> hideWears() {
        wearsHidden = true;
        jacket.visible = false;
        leftSleeve.visible = false;
        rightSleeve.visible = false;
        leftPants.visible = false;
        rightPants.visible = false;
        breastsWear.visible = false;
        return this;
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

    public void syncWearParts() {
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
        breastsWear.copyFrom(breasts);
    }

    public void applyMorphology(VillagerLike<?> villager) {
        MCAModelMorphology.applyBreastDimensions(villager, breastTransform, breasts, breastParts);
        breastTransform.visible &= body.visible;
        breasts.visible &= body.visible;
        breastsWear.visible = !wearsHidden && breastTransform.visible;
    }

    public void hideBreasts() {
        breastParts.forEach(part -> part.visible = false);
    }

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
        breastTransform.visible = model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = showWears && model.body.visible;
    }
}
