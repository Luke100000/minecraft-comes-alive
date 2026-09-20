package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.FastColor;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import org.jetbrains.annotations.Nullable;

public class VillagerPlayerModel<T extends LivingEntity & VillagerLike<T>> extends PlayerModel<T> {
    @Nullable
    private T currentVillager;

    public VillagerPlayerModel(ModelPart root) {
        super(root, false);
        hidePlayerWears();
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
            UseAnim useAnimation = stack.getUseAnimation();
            if (useAnimation == UseAnim.BOW) {
                return ArmPose.BOW_AND_ARROW;
            }
            if (useAnimation == UseAnim.CROSSBOW) {
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
    public void setupAnim(
            T villager,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
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
        applyPanicAnimation(villager, animationProgress);
        applyHeadScale(villager);
        hidePlayerWears();
        currentVillager = villager;
    }

    private void applyPanicAnimation(T villager, float animationProgress) {
        if (!villager.getVillagerBrain().isPanicking()) {
            return;
        }

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

    private void applyHeadScale(T villager) {
        float headScale = villager.getVillagerDimensions().getHead();
        head.xScale = headScale;
        head.yScale = headScale;
        head.zScale = headScale;
        hat.xScale = headScale;
        hat.yScale = headScale;
        hat.zScale = headScale;
    }

    private void hidePlayerWears() {
        jacket.visible = false;
        leftSleeve.visible = false;
        rightSleeve.visible = false;
        leftPants.visible = false;
        rightPants.visible = false;
    }

    @Override
    public void setAllVisible(boolean visible) {
        super.setAllVisible(visible);
        if (visible) {
            hidePlayerWears();
        }
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        T villager = currentVillager;
        boolean wasYoung = young;
        young = false;
        try {
            if (villager != null) {
                color = FastColor.ARGB32.multiply(color, SkinExporter.getSkinColor(villager));
            }
            super.renderToBuffer(matrices, vertices, light, overlay, color);
        } finally {
            young = wasYoung;
            currentVillager = null;
        }
    }
}
