package net.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.mca.Config;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.model.*;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.UseAction;

public class VillagerEntityBaseModelMCA<T extends LivingEntity & VillagerLike<T>> extends BipedEntityModel<T> implements CommonVillagerModel<T> {
    protected static final String BREASTS = "breasts";

    public final ModelPart breasts;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    public VillagerEntityBaseModelMCA(ModelPart root) {
        super(root);
        this.breasts = root.getChild(BREASTS);
    }

    public static ModelData getModelData(Dilation dilation) {
        ModelData modelData = BipedEntityModel.getModelData(dilation, 0.0f);
        ModelPartData data = modelData.getRoot();

        data.addChild(BREASTS, newBreasts(dilation, 0), ModelTransform.NONE);

        return modelData;
    }

    protected static ModelPartBuilder newBreasts(Dilation dilation, int oy) {
        ModelPartBuilder builder = ModelPartBuilder.create();
        if (Config.getInstance().enableBoobs) {
            builder.uv(18, 21 + oy).cuboid(-3.25F, -1.25F, -1.5F, 6, 3, 3, dilation);
        }
        return builder;
    }

    @Override
    protected Iterable<ModelPart> getHeadParts() {
        return ImmutableList.of(head, hat);
    }

    @Override
    protected Iterable<ModelPart> getBodyParts() {
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg);
    }

    @Override
    public void animateModel(T entity, float limbAngle, float limbDistance, float tickDelta) {
        updateArmPoses(entity);
        super.animateModel(entity, limbDistance, limbAngle, tickDelta);
        riding |= entity.getAgeState() == AgeState.BABY;
    }

    private void updateArmPoses(T entity) {
        this.leftArmPose = ArmPose.EMPTY;
        this.rightArmPose = ArmPose.EMPTY;

        applyArmPose(entity, Hand.MAIN_HAND, getArmPose(entity, Hand.MAIN_HAND));
        applyArmPose(entity, Hand.OFF_HAND, getArmPose(entity, Hand.OFF_HAND));
    }

    private ArmPose getArmPose(T entity, Hand hand) {
        ItemStack stack = entity.getStackInHand(hand);
        if (stack.isEmpty()) {
            return ArmPose.EMPTY;
        }

        if (entity.isUsingItem() && entity.getActiveHand() == hand && entity.getItemUseTimeLeft() > 0) {
            UseAction useAction = stack.getUseAction();
            if (useAction == UseAction.BOW) {
                return ArmPose.BOW_AND_ARROW;
            }
            if (useAction == UseAction.CROSSBOW) {
                return ArmPose.CROSSBOW_CHARGE;
            }
        } else if (!entity.handSwinging && stack.isOf(Items.CROSSBOW) && CrossbowItem.isCharged(stack)) {
            return ArmPose.CROSSBOW_HOLD;
        }

        return ArmPose.EMPTY;
    }

    private void applyArmPose(T entity, Hand hand, ArmPose pose) {
        if (pose == ArmPose.EMPTY) {
            return;
        }

        if (isRightArm(entity, hand)) {
            this.rightArmPose = pose;
        } else {
            this.leftArmPose = pose;
        }
    }

    private static boolean isRightArm(LivingEntity entity, Hand hand) {
        return (hand == Hand.MAIN_HAND) == (entity.getMainArm() == Arm.RIGHT);
    }

    @Override
    public void setAngles(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        if (villager.getAgeState() == AgeState.BABY && !villager.hasVehicle()) {
            limbDistance = (float)Math.sin(villager.age / 12F);
            limbAngle = (float)Math.cos(villager.age / 9F) * 3;
            headYaw += (float)Math.sin(villager.age / 2F);
        }

        //remove the boost for babies
        if (villager.isBaby()) {
            limbAngle /= 3.0f;
        }

        //and add our own
        limbAngle /= (0.2f + villager.getRawScaleFactor());

        super.setAngles(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);

        if (villager.getVillagerBrain().isPanicking()) {
            float toRadians = (float)Math.PI / 180;

            float armRaise = (((float)Math.sin(animationProgress / 5) * 30 - 180)
                    + ((float)Math.sin(animationProgress / 3) * 3))
                    * toRadians;
            float waveSideways = ((float)Math.sin(animationProgress / 2) * 12 - 17) * toRadians;

            this.leftArm.pitch = armRaise;
            this.leftArm.roll = -waveSideways;
            this.rightArm.pitch = -armRaise;
            this.rightArm.roll = waveSideways;
        }

        applyVillagerDimensions(villager, villager.isInSneakingPose());
    }

    @Override
    public void copyBipedStateTo(BipedEntityModel<T> target) {
        super.copyBipedStateTo(target);

        if (target instanceof VillagerEntityBaseModelMCA<T> m) {
            copyCommonAttributes(m);

            m.breasts.visible = breasts.visible;
            m.breasts.copyTransform(breasts);
        }
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumer vertices, int light, int overlay, float red, float green, float blue, float alpha) {
        renderCommon(matrices, vertices, light, overlay, red, green, blue, alpha);
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
        return getHeadParts();
    }

    @Override
    public Iterable<ModelPart> getCommonBodyParts() {
        return getBodyParts();
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
