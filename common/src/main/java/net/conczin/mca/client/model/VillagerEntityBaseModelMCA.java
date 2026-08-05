package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

public class VillagerEntityBaseModelMCA<T extends LivingEntity & VillagerLike<T>> extends HumanoidModel<T> implements CommonVillagerModel<T> {
    protected static final String BREAST_TRANSFORM = "breast_transform";
    protected static final String BREASTS = "breasts";

    public final ModelPart breastTransform;
    public final ModelPart breasts;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        breastTransform = body.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
    }

    public static MeshDefinition getModelData(CubeDeformation dilation) {
        MeshDefinition modelData = HumanoidModel.createMesh(dilation, 0.0F);
        addBreastParts(modelData.getRoot().getChild("body"), dilation, false);
        return modelData;
    }

    protected static void addBreastParts(PartDefinition body, CubeDeformation dilation, boolean withBreastplate) {
        PartDefinition transform = body.addOrReplaceChild(BREAST_TRANSFORM, CubeListBuilder.create(), PartPose.ZERO);
        transform.addOrReplaceChild(BREASTS, newBreasts(dilation, 0), PartPose.ZERO);
        if (withBreastplate) {
            transform.addOrReplaceChild(VillagerEntityModelMCA.BREASTPLATE, newBreasts(dilation.extend(0.1F), 16), PartPose.ZERO);
        }
    }

    protected static CubeListBuilder newBreasts(CubeDeformation dilation, int oy) {
        CubeListBuilder builder = CubeListBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.texOffs(18, 21 + oy).addBox(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    protected Iterable<ModelPart> headParts() {
        return ImmutableList.of(head, hat);
    }

    @Override
    protected Iterable<ModelPart> bodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public void prepareMobModel(T entity, float limbAngle, float limbDistance, float tickDelta) {
        updateArmPoses(entity);
        super.prepareMobModel(entity, limbAngle, limbDistance, tickDelta);
        riding |= entity.getAgeState() == AgeState.BABY;
    }

    private void updateArmPoses(T entity) {
        leftArmPose = ArmPose.EMPTY;
        rightArmPose = ArmPose.EMPTY;

        applyArmPose(entity, InteractionHand.MAIN_HAND, getArmPose(entity, InteractionHand.MAIN_HAND));
        applyArmPose(entity, InteractionHand.OFF_HAND, getArmPose(entity, InteractionHand.OFF_HAND));
    }

    private HumanoidModel.ArmPose getArmPose(T entity, InteractionHand hand) {
        ItemStack stack = entity.getItemInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }

        if (entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0) {
            UseAnim useAnim = stack.getUseAnimation();
            if (useAnim == UseAnim.BOW) {
                return ArmPose.BOW_AND_ARROW;
            }
            if (useAnim == UseAnim.CROSSBOW) {
                return ArmPose.CROSSBOW_CHARGE;
            }
        } else if (!entity.swinging && stack.is(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }

        return ArmPose.EMPTY;
    }

    private void applyArmPose(T entity, InteractionHand hand, HumanoidModel.ArmPose pose) {
        if (pose == ArmPose.EMPTY) {
            return;
        }

        if ((hand == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.RIGHT)) {
            rightArmPose = pose;
        } else {
            leftArmPose = pose;
        }
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            limbDistance = (float) Math.sin(villager.tickCount / 12F);
            limbAngle = (float) Math.cos(villager.tickCount / 9F) * 3;
            headYaw += (float) Math.sin(villager.tickCount / 2F);
        }

        if (villager.isBaby()) {
            limbAngle /= 3.0F;
        }
        limbAngle /= 0.2F + villager.getRawVerticalScaleFactor();

        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        if (villager.getVillagerBrain().isPanicking()) {
            float toRadians = (float) Math.PI / 180;
            float armRaise = (((float) Math.sin(animationProgress / 5) * 30 - 180)
                              + ((float) Math.sin(animationProgress / 3) * 3))
                             * toRadians;
            float waveSideways = ((float) Math.sin(animationProgress / 2) * 12 - 17) * toRadians;

            leftArm.xRot = armRaise;
            leftArm.zRot = -waveSideways;
            rightArm.xRot = -armRaise;
            rightArm.zRot = waveSideways;
        }

        applyVillagerDimensions(villager);
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof CommonVillagerModel<?> model) {
            copyMorphologyTo(model);
        }
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        // MCA already scales/positions child entities in the renderer; avoid applying
        // AgeableListModel's second baby transform while retaining vanilla traversal.
        boolean wasYoung = young;
        young = false;
        super.renderToBuffer(matrices, vertices, light, overlay, color);
        young = wasYoung;
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
        return ImmutableList.of(breasts);
    }
}
