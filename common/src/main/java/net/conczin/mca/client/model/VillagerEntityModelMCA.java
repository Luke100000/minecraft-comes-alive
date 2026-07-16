package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

public class VillagerEntityModelMCA<T extends LivingEntity & VillagerLike<T>> extends VillagerEntityBaseModelMCA<T> {
    protected static final String BREASTPLATE = "breastplate";

    public final ModelPart breastsWear;
    public final ModelPart leftArmwear;
    public final ModelPart rightArmwear;
    public final ModelPart leftLegwear;
    public final ModelPart rightLegwear;
    public final ModelPart bodyWear;

    private boolean wearsHidden;

    public VillagerEntityModelMCA(ModelPart tree) {
        super(tree);
        bodyWear = tree.getChild(PartNames.JACKET);
        leftArmwear = tree.getChild("left_sleeve");
        rightArmwear = tree.getChild("right_sleeve");
        leftLegwear = tree.getChild("left_pants");
        rightLegwear = tree.getChild("right_pants");

        breastsWear = tree.getChild(BREASTPLATE);
    }

    //
    // body - 0 (body.body 0.0)
    // face - 0 (body.head 0.01)
    //  clothing - 1 (clothing.body 0.075)
    //   hair - 2 (hair.body 0.1) + (hair.hat 0.1 + 0.3 = 0.4)
    //    hood - 3 (clothing.hat 0.075 + 0.5 = 0.575)

    public static MeshDefinition hairData(CubeDeformation dilation) {
        MeshDefinition modelData = bodyData(dilation);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(PartNames.HAT, CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)), PartPose.ZERO);
        return modelData;
    }

    public static MeshDefinition bodyData(CubeDeformation dilation) {
        return bodyData(dilation, false);
    }

    public static MeshDefinition bodyData(CubeDeformation dilation, boolean slim) {
        MeshDefinition modelData = PlayerModel.createMesh(dilation, slim);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        root.addOrReplaceChild(BREASTPLATE, newBreasts(dilation.extend(0.1F), 16), PartPose.ZERO);
        return modelData;
    }

    public static MeshDefinition armorData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0f);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        return modelData;
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, bodyWear, leftLegwear, rightLegwear, leftArmwear, rightArmwear);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        syncWearParts();
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);

        leftArmwear.visible = !wearsHidden && visible;
        rightArmwear.visible = !wearsHidden && visible;
        leftLegwear.visible = !wearsHidden && visible;
        rightLegwear.visible = !wearsHidden && visible;
        bodyWear.visible = !wearsHidden && visible;
    }

    public VillagerEntityModelMCA<T> hideWears() {
        wearsHidden = true;
        breastsWear.visible = false;
        leftArmwear.visible = false;
        rightArmwear.visible = false;
        leftLegwear.visible = false;
        rightLegwear.visible = false;
        bodyWear.visible = false;
        return this;
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof VillagerEntityModelMCA) {
            copyAttributes((VillagerEntityModelMCA<T>) target);
        }
    }

    private void copyAttributes(VillagerEntityModelMCA<T> target) {
        // Rebuild wear parts from the canonical bones copied by HumanoidModel.
        target.hat.copyFrom(target.head);
        target.syncWearParts();
    }

    @Override
    public void syncWearParts() {
        leftLegwear.copyFrom(leftLeg);
        rightLegwear.copyFrom(rightLeg);
        leftArmwear.copyFrom(leftArm);
        rightArmwear.copyFrom(rightArm);
        bodyWear.copyFrom(body);
        breastsWear.copyFrom(breasts);
    }

    public void copyVisibility(HumanoidModel<?> model) {
        boolean showWears = !wearsHidden;
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        bodyWear.visible = showWears && model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = showWears && model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftArmwear.visible = showWears && model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightArmwear.visible = showWears && model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftLegwear.visible = showWears && model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightLegwear.visible = showWears && model.rightLeg.visible;
    }
}
