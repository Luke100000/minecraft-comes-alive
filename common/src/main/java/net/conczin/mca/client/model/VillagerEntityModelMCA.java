package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

public class VillagerEntityModelMCA extends VillagerEntityBaseModelMCA {
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
        bodyWear = body.getChild(PartNames.JACKET);
        leftArmwear = leftArm.getChild("left_sleeve");
        rightArmwear = rightArm.getChild("right_sleeve");
        leftLegwear = leftLeg.getChild("left_pants");
        rightLegwear = rightLeg.getChild("right_pants");

        breastsWear = tree.getChild(BREASTPLATE);
    }

    //
    // body - 0 (body.body 0.0)
    // face - 0 (body.head 0.01)
    // clothing - 1 (clothing.body 0.075)
    // hair - 2 (hair.body 0.1) + (hair.hat 0.1 + 0.3 = 0.4)
    // hood - 3 (clothing.hat 0.075 + 0.5 = 0.575)

    public static MeshDefinition hairData(CubeDeformation dilation) {
        MeshDefinition modelData = bodyData(dilation);
        PartDefinition head = modelData.getRoot().getChild(PartNames.HEAD);
        head.addOrReplaceChild(PartNames.HAT,
                CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)),
                PartPose.ZERO);
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

    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, bodyWear, leftLegwear, rightLegwear,
                leftArmwear, rightArmwear);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
    }

    @Override
    public void setupAnim(MCAHumanoidRenderState renderState) {
        super.setupAnim(renderState);
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

    public VillagerEntityModelMCA hideWears() {
        wearsHidden = true;
        breastsWear.visible = false;
        leftArmwear.visible = false;
        rightArmwear.visible = false;
        leftLegwear.visible = false;
        rightLegwear.visible = false;
        bodyWear.visible = false;
        return this;
    }

    public void copyVisibility(HumanoidModel<? extends HumanoidRenderState> model) {
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        breasts.visible = model.body.visible;
        leftArm.visible = model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;

        if (model instanceof VillagerEntityModelMCA mcaModel) {
            // Only synchronize wear visibility if this model is also supposed to hide them or if the parent didn't hide them.
            // Actually, we should just ensure that if our base limb is hidden, the wear is also hidden.
            bodyWear.visible = !wearsHidden && model.body.visible;
            breastsWear.visible = !wearsHidden && model.body.visible;
            leftArmwear.visible = !wearsHidden && model.leftArm.visible;
            rightArmwear.visible = !wearsHidden && model.rightArm.visible;
            leftLegwear.visible = !wearsHidden && model.leftLeg.visible;
            rightLegwear.visible = !wearsHidden && model.rightLeg.visible;
        } else {
            bodyWear.visible = !wearsHidden && model.body.visible;
            breastsWear.visible = !wearsHidden && model.body.visible;
            leftArmwear.visible = !wearsHidden && model.leftArm.visible;
            rightArmwear.visible = !wearsHidden && model.rightArm.visible;
            leftLegwear.visible = !wearsHidden && model.leftLeg.visible;
            rightLegwear.visible = !wearsHidden && model.rightLeg.visible;
        }
    }
}
