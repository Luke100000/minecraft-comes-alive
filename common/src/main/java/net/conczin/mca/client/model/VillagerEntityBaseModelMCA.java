package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.Config;
import net.conczin.mca.client.render.VillagerRenderState;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

public class VillagerEntityBaseModelMCA extends HumanoidModel<VillagerRenderState> implements CommonVillagerModel<VillagerRenderState> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;
    private RenderMask renderMask = RenderMask.FULL;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        this.breasts = root.getChild(BREASTS);
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0f);
        PartDefinition data = modelData.getRoot();

        data.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);

        return modelData;
    }

    protected static CubeListBuilder newBreasts(CubeDeformation dilation, int oy) {
        CubeListBuilder builder = CubeListBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.texOffs(18, 21 + oy).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);

        var visuals = CommonVillagerModel.getVisuals(state);
        if (visuals.panicking()) {
            float toRadians = (float) Math.PI / 180;

            float armRaise = (((float) Math.sin(state.ageInTicks / 5) * 30 - 180)
                              + ((float) Math.sin(state.ageInTicks / 3) * 3))
                             * toRadians;
            float waveSideways = ((float) Math.sin(state.ageInTicks / 2) * 12 - 17) * toRadians;

            this.leftArm.xRot = armRaise;
            this.leftArm.zRot = -waveSideways;
            this.rightArm.xRot = -armRaise;
            this.rightArm.zRot = waveSideways;
        }

        applyVillagerDimensions(visuals, state.isCrouching);
        CommonVillagerModel.applyRenderMask(this, renderMask);
    }

    public void copyPropertiesTo(HumanoidModel<?> target) {
        if (target instanceof VillagerEntityBaseModelMCA m) {
            CommonVillagerModel.copyPartState(m.head, head);
            CommonVillagerModel.copyPartState(m.hat, hat);
            CommonVillagerModel.copyPartState(m.body, body);
            CommonVillagerModel.copyPartState(m.leftArm, leftArm);
            CommonVillagerModel.copyPartState(m.rightArm, rightArm);
            CommonVillagerModel.copyPartState(m.leftLeg, leftLeg);
            CommonVillagerModel.copyPartState(m.rightLeg, rightLeg);
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            CommonVillagerModel.copyPartState(m.breasts, breasts);
        }
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
}
