package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.client.render.VillagerRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.ArmorModelSet;

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
        bodyWear = getChildOrEmpty(body, PartNames.JACKET);
        leftArmwear = getChildOrEmpty(leftArm, "left_sleeve");
        rightArmwear = getChildOrEmpty(rightArm, "right_sleeve");
        leftLegwear = getChildOrEmpty(leftLeg, "left_pants");
        rightLegwear = getChildOrEmpty(rightLeg, "right_pants");

        breastsWear = getChildOrEmpty(tree, BREASTPLATE);
    }

    //
    // body - 0 (body.body 0.0)
    // face - 0 (body.head 0.01)
    //  clothing - 1 (clothing.body 0.075)
    //   hair - 2 (hair.body 0.1) + (hair.hat 0.1 + 0.3 = 0.4)
    //    hood - 3 (clothing.hat 0.075 + 0.5 = 0.575)

    public static MeshDefinition hairData(CubeDeformation dilation) {
        MeshDefinition modelData = bodyData(dilation);
        PartDefinition head = modelData.getRoot().getChild(PartNames.HEAD);
        head.addOrReplaceChild(PartNames.HAT, CubeListBuilder.create().texOffs(32, 0).addBox(-4, -8, -4, 8, 8, 8, dilation.extend(0.3F)), PartPose.ZERO);
        return modelData;
    }

    public static MeshDefinition bodyData(CubeDeformation dilation) {
        return playerData(dilation, false);
    }

    public static MeshDefinition bodyData(CubeDeformation dilation, boolean slim) {
        return playerData(dilation, slim);
    }

    public static MeshDefinition playerData(CubeDeformation dilation) {
        return playerData(dilation, false);
    }

    public static MeshDefinition playerData(CubeDeformation dilation, boolean slim) {
        // Start from Mojang's player mesh so EMF/Fresh Moves sees the normal player part tree.
        // MCA only adds root-level breast extras; proportions, skins, and clothes are applied at render time.
        MeshDefinition modelData = PlayerModel.createMesh(dilation, slim);
        PartDefinition root = modelData.getRoot();
        root.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        root.addOrReplaceChild(BREASTPLATE, newBreasts(dilation.extend(0.1F), 16), PartPose.ZERO);
        return modelData;
    }

    public static ArmorModelSet<MeshDefinition> armorData() {
        ArmorModelSet<MeshDefinition> armor = HumanoidModel.createArmorMeshSet(new CubeDeformation(0.5F), new CubeDeformation(1.0F));
        addEmptyBreasts(armor.head());
        armor.chest().getRoot().addOrReplaceChild(BREASTS, newBreasts(new CubeDeformation(1.0F), 0), PartPose.ZERO);
        addEmptyBreasts(armor.legs());
        addEmptyBreasts(armor.feet());
        return armor;
    }

    private static void addEmptyBreasts(MeshDefinition mesh) {
        mesh.getRoot().addOrReplaceChild(BREASTS, CubeListBuilder.create(), PartPose.ZERO);
    }

    public Iterable<ModelPart> getCommonBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
    }

    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
        CommonVillagerModel.copyPartState(breastsWear, breasts);
    }

    public void setAllVisible(boolean visible) {
        CommonVillagerModel.setBaseVisible(this, visible);
        breasts.visible = visible;
        leftArmwear.visible = !wearsHidden && visible;
        rightArmwear.visible = !wearsHidden && visible;
        leftLegwear.visible = !wearsHidden && visible;
        rightLegwear.visible = !wearsHidden && visible;
        bodyWear.visible = !wearsHidden && visible;
        breastsWear.visible = !wearsHidden && visible;
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

    public void copyPropertiesTo(HumanoidModel<?> target) {
        if (target instanceof VillagerEntityModelMCA m) {
            super.copyPropertiesTo(m);
            copyAttributes(m);
        }
    }

    private void copyAttributes(VillagerEntityModelMCA target) {
        CommonVillagerModel.copyPartState(target.leftLegwear, leftLegwear);
        CommonVillagerModel.copyPartState(target.rightLegwear, rightLegwear);
        CommonVillagerModel.copyPartState(target.leftArmwear, leftArmwear);
        CommonVillagerModel.copyPartState(target.rightArmwear, rightArmwear);
        CommonVillagerModel.copyPartState(target.bodyWear, bodyWear);
        CommonVillagerModel.copyPartState(target.breastsWear, breastsWear);
    }

    public void copyVisibility(HumanoidModel<?> model) {
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        bodyWear.visible = !wearsHidden && model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = !wearsHidden && model.body.visible;
        leftArm.visible = model.leftArm.visible;
        leftArmwear.visible = !wearsHidden && model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightArmwear.visible = !wearsHidden && model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftLegwear.visible = !wearsHidden && model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightLegwear.visible = !wearsHidden && model.rightLeg.visible;
    }
}
