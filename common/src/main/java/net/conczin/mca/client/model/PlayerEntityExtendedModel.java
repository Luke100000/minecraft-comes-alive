package net.conczin.mca.client.model;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.render.PlayerInteractionAnimationManager;
import net.conczin.mca.client.render.SkinLayers3dCompat;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.VillagerDimensions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.VillagerEntityBaseModelMCA.BREASTS;
import static net.conczin.mca.client.model.VillagerEntityModelMCA.BREASTPLATE;

public class PlayerEntityExtendedModel extends PlayerModel implements CommonVillagerModel<LivingEntity> {
    public final ModelPart breasts;
    public final ModelPart breastsWear;

    final VillagerDimensions.Mutable dimensions = new VillagerDimensions.Mutable(AgeState.ADULT);
    float breastSize;

    public PlayerEntityExtendedModel(ModelPart root) {
        super(root, false);
        this.breasts = root.getChild(BREASTS);
        this.breastsWear = root.getChild(BREASTPLATE);
        SkinLayers3dCompat.setIgnored(this, true);
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
        return ImmutableList.of(body, rightArm, leftArm, rightLeg, leftLeg, jacket, leftSleeve, rightSleeve, leftPants, rightPants);
    }

    @Override
    public Iterable<ModelPart> getBreastParts() {
        return ImmutableList.of(breasts, breastsWear);
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
    public void setupAnim(AvatarRenderState renderState) {
        super.setupAnim(renderState);
        breasts.visible = false;
        breastsWear.visible = false;

        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(renderState.id);
            if (entity instanceof Avatar avatar) {
                MCAClient.getPlayerData(avatar.getUUID()).ifPresent(villager -> applyVillagerDimensions(villager, renderState.isCrouching));
                PlayerInteractionAnimationManager.applyToHumanoidModel(avatar.getUUID(), this, renderState.ageInTicks);
            }
        }

        SkinLayers3dCompat.clearInjectedMeshes(this);
        breastsWear.visible = false;
        breasts.visible = false;
    }

    public void copyVisibility(HumanoidModel<? extends HumanoidRenderState> model) {
        head.visible = model.head.visible;
        hat.visible = model.head.visible;
        body.visible = model.body.visible;
        jacket.visible = model.body.visible;
        breasts.visible = model.body.visible;
        breastsWear.visible = false;
        leftArm.visible = model.leftArm.visible;
        leftSleeve.visible = model.leftArm.visible;
        rightArm.visible = model.rightArm.visible;
        rightSleeve.visible = model.rightArm.visible;
        leftLeg.visible = model.leftLeg.visible;
        leftPants.visible = model.leftLeg.visible;
        rightLeg.visible = model.rightLeg.visible;
        rightPants.visible = model.rightLeg.visible;
    }
}
