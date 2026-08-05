package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.conczin.mca.client.resources.SkinExporter;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartNames;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREAST_TRANSFORM;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

public class PlayerEntityExtendedModel<T extends LivingEntity> extends PlayerModel<T> implements CommonVillagerModel<T> {
    public final ModelPart breastTransform;
    public final ModelPart breasts;
    public final ModelPart breastsWear;
    private final boolean detachedMorphology;
    @Nullable
    private final PlayerAnimationBridge<T> animationBridge;
    private boolean wearsHidden;
    @Nullable
    private T currentEntity;
    private int skinColor = 0xFFFFFFFF;

    public PlayerEntityExtendedModel(ModelPart root) {
        this(root, false, null, null);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim) {
        this(root, slim, null, null);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim, ModelPart attachments) {
        this(root, slim, attachments, null);
    }

    public PlayerEntityExtendedModel(ModelPart root, boolean slim, PlayerAnimationBridge<T> animationBridge) {
        this(root, slim, null, animationBridge);
        hideWearsInternal();
    }

    private PlayerEntityExtendedModel(
            ModelPart root,
            boolean slim,
            @Nullable ModelPart attachments,
            @Nullable PlayerAnimationBridge<T> animationBridge
    ) {
        super(root, slim);
        ModelPart morphologyBody = attachments == null ? body : attachments.getChild(PartNames.BODY);
        breastTransform = morphologyBody.getChild(BREAST_TRANSFORM);
        breasts = breastTransform.getChild(BREASTS);
        breastsWear = breastTransform.getChild(BREASTPLATE);
        detachedMorphology = attachments != null;
        this.animationBridge = animationBridge;
    }

    @Override
    public void copyPropertiesTo(HumanoidModel<T> target) {
        super.copyPropertiesTo(target);
        if (target instanceof CommonVillagerModel<?> model) {
            copyMorphologyTo(model);
        }
        if (target instanceof PlayerEntityExtendedModel<?> rawTarget) {
            @SuppressWarnings("unchecked")
            PlayerEntityExtendedModel<T> model = (PlayerEntityExtendedModel<T>) rawTarget;
            model.hat.copyFrom(model.head);
            model.syncWearParts();
        }
    }

    public PlayerEntityExtendedModel<T> hideWears() {
        hideWearsInternal();
        return this;
    }

    private void hideWearsInternal() {
        wearsHidden = true;
        jacket.visible = false;
        leftSleeve.visible = false;
        rightSleeve.visible = false;
        leftPants.visible = false;
        rightPants.visible = false;
        breastsWear.visible = false;
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

    @Override
    public void syncWearParts() {
        leftPants.copyFrom(leftLeg);
        rightPants.copyFrom(rightLeg);
        leftSleeve.copyFrom(leftArm);
        rightSleeve.copyFrom(rightArm);
        jacket.copyFrom(body);
        breastsWear.copyFrom(breasts);
    }

    public void applyAnimationBridgeForArm(PoseStack matrices, int light, int overlay, boolean right) {
        if (animationBridge == null || currentEntity == null) {
            return;
        }
        animationBridge.applyArm(this, matrices, light, overlay, right);
        applyVillagerDimensions(CommonVillagerModel.getVillager(currentEntity));
        syncWearParts();
        hideWearsInternal();
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (animationBridge != null && currentEntity != null) {
            animationBridge.apply(this, matrices, light, overlay);
            applyVillagerDimensions(CommonVillagerModel.getVillager(currentEntity));
            syncWearParts();
            hideWearsInternal();
            color = CommonVillagerModel.multiplyColor(color, skinColor);
        }

        breastsWear.visible = !wearsHidden && jacket.visible && breastTransform.visible;
        super.renderToBuffer(matrices, vertices, light, overlay, color);

        if (detachedMorphology && body.visible && breastTransform.visible) {
            matrices.pushPose();
            body.translateAndRotate(matrices);
            breastTransform.render(matrices, vertices, light, overlay, color);
            matrices.popPose();
        }
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
        return ImmutableList.of(breasts, breastsWear);
    }

    @Override
    public void setupAnim(T entity, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        var villager = CommonVillagerModel.getVillager(entity);
        if (villager.getAgeState() == AgeState.BABY && !entity.isPassenger()) {
            limbDistance = (float) Math.sin(entity.tickCount / 12F);
            limbAngle = (float) Math.cos(entity.tickCount / 9F) * 3;
            headYaw += (float) Math.sin(entity.tickCount / 2F);
        }

        super.setupAnim(entity, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        applyVillagerDimensions(villager);
        if (animationBridge != null) {
            currentEntity = entity;
            skinColor = SkinExporter.getSkinColor(villager);
        }
    }

    @Override
    public void copyVisibility(HumanoidModel<?> model) {
        boolean showWears = !wearsHidden;
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
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

        if (model instanceof CommonVillagerModel<?> source) {
            breastTransform.visible = model.body.visible && source.getBreastTransform().visible;
            breasts.visible = model.body.visible && source.getBreastPart().visible;
        } else {
            breastTransform.visible &= model.body.visible;
            breasts.visible &= model.body.visible;
        }
        breastsWear.visible = showWears && breastTransform.visible;
    }
}
