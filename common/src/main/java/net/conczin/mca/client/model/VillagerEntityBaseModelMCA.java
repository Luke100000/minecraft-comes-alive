package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.entity.LivingEntity;

import net.conczin.mca.client.render.MCAHumanoidRenderState;

public class VillagerEntityBaseModelMCA extends HumanoidModel<MCAHumanoidRenderState>
        implements CommonVillagerModel<LivingEntity> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

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

    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(head, hat);
    }

    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    protected static void copyModelTransform(ModelPart source, ModelPart target) {
        target.loadPose(source.storePose());
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
    }

    @Override
    public void setupAnim(MCAHumanoidRenderState renderState) {
        float originalWalkAnimationPos = renderState.walkAnimationPos;
        float originalWalkAnimationSpeed = renderState.walkAnimationSpeed;
        float originalYRot = renderState.yRot;

        VillagerLike<?> villagerLike = renderState.villager instanceof VillagerLike<?> v ? v : null;
        if (villagerLike != null) {
            if (villagerLike.getAgeState() == AgeState.BABY && (!renderState.isPassenger || renderState.cribPassenger) && renderState.villager != null) {
                renderState.walkAnimationSpeed = (float) Math.sin(renderState.villager.tickCount / 12.0F);
                renderState.walkAnimationPos = (float) Math.cos(renderState.villager.tickCount / 9.0F) * 3.0F;
                renderState.yRot = originalYRot + (float) Math.sin(renderState.villager.tickCount / 2.0F);
            }

            if (renderState.isBaby) {
                renderState.walkAnimationPos /= 3.0F;
            }

            renderState.walkAnimationPos /= (0.2F + villagerLike.getRawVerticalScaleFactor());
        }

        super.setupAnim(renderState);

        renderState.walkAnimationPos = originalWalkAnimationPos;
        renderState.walkAnimationSpeed = originalWalkAnimationSpeed;
        renderState.yRot = originalYRot;

        if (villagerLike != null) {
            if (villagerLike.getVillagerBrain().isPanicking()) {
                float toRadians = (float) Math.PI / 180.0F;
                float animationProgress = renderState.ageInTicks;

                float armRaise = (((float) Math.sin(animationProgress / 5.0F) * 30.0F - 180.0F)
                        + ((float) Math.sin(animationProgress / 3.0F) * 3.0F)) * toRadians;
                float waveSideways = ((float) Math.sin(animationProgress / 2.0F) * 12.0F - 17.0F) * toRadians;

                this.leftArm.xRot = armRaise;
                this.leftArm.zRot = -waveSideways;
                this.rightArm.xRot = -armRaise;
                this.rightArm.zRot = waveSideways;
            }

            applyVillagerDimensions(villagerLike, renderState.isCrouching);
            copyModelTransform(head, hat);
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
        return headParts();
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return bodyParts();
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
}
