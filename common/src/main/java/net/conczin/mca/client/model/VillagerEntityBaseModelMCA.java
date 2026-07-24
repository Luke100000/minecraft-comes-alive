package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;

import java.util.List;
import java.util.Map;

public class VillagerEntityBaseModelMCA<T extends LivingEntity & VillagerLike<T>> extends HumanoidModel<T> implements CommonVillagerModel<T> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        this.breasts = getChildOrEmpty(root, BREASTS);
    }

    /** Returns a model child or a detached empty part for vanilla animation roots. */
    static ModelPart getChildOrEmpty(ModelPart root, String name) {
        return root.hasChild(name) ? root.getChild(name) : new ModelPart(List.of(), Map.of());
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
        super.prepareMobModel(entity, limbDistance, limbAngle, tickDelta);
        riding |= entity.getAgeState() == AgeState.BABY;
    }

    private void updateArmPoses(T entity) {
        this.leftArmPose = ArmPose.EMPTY;
        this.rightArmPose = ArmPose.EMPTY;

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

        if (isRightArm(entity, hand)) {
            this.rightArmPose = pose;
        } else {
            this.leftArmPose = pose;
        }
    }

    private static boolean isRightArm(LivingEntity entity, InteractionHand hand) {
        return (hand == InteractionHand.MAIN_HAND) == (entity.getMainArm() == HumanoidArm.RIGHT);
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        if (villager.getAgeState() == AgeState.BABY && !villager.isPassenger()) {
            limbDistance = (float) Math.sin(villager.tickCount / 12F);
            limbAngle = (float) Math.cos(villager.tickCount / 9F) * 3;
            headYaw += (float) Math.sin(villager.tickCount / 2F);
        }

        //remove the boost for babies
        if (villager.isBaby()) {
            limbAngle /= 3.0f;
        }

        //and add our own
        limbAngle /= (0.2f + villager.getRawVerticalScaleFactor());

        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        if (villager.getVillagerBrain().isPanicking()) {
            float toRadians = (float) Math.PI / 180;

            float armRaise = (((float) Math.sin(animationProgress / 5) * 30 - 180)
                              + ((float) Math.sin(animationProgress / 3) * 3))
                             * toRadians;
            float waveSideways = ((float) Math.sin(animationProgress / 2) * 12 - 17) * toRadians;

            this.leftArm.xRot = armRaise;
            this.leftArm.zRot = -waveSideways;
            this.rightArm.xRot = -armRaise;
            this.rightArm.zRot = waveSideways;
        }

        applyVillagerDimensions(villager);
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);

        if (target instanceof VillagerEntityBaseModelMCA<T> m) {
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            m.breasts.copyFrom(breasts);
        }
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        renderCommon(matrices, vertices, light, overlay, color);
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
