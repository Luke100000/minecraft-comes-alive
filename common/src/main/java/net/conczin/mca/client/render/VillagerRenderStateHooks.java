package net.conczin.mca.client.render;

import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.VillagerEditorScreen;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class VillagerRenderStateHooks {
    private VillagerRenderStateHooks() {
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state) {
        if (!(entity instanceof Player) || !(state instanceof VillagerStateHolder holder) || state instanceof VillagerRenderState) {
            return;
        }

        VillagerLike<?> visualsSource = MCAClient.getGeneticsRendererData(entity.getUUID()).orElse(null);
        VillagerLike.PlayerModel playerModel = visualsSource != null
                ? visualsSource.getPlayerModel()
                : VillagerLike.PlayerModel.VANILLA;

        if (Minecraft.getInstance().screen instanceof VillagerEditorScreen editor && editor.hasVillagerData() && editor.isEditingPlayer(entity.getUUID())) {
            visualsSource = editor.getVillager();
            playerModel = editor.getSelectedPlayerModel();
        }

        holder.mca$setVillagerRenderData(VillagerRenderData.create(playerModel, visualsSource));
    }

    public static void extractScaledBounds(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            return;
        }

        var visuals = holder.mca$getVisuals();
        if (visuals == null) {
            return;
        }

        if (!(entity instanceof Player) && !(entity instanceof VillagerLike<?>)) {
            return;
        }

        float horizontalBaseScale = entity instanceof VillagerLike<?> villagerEntity ? villagerEntity.getHorizontalScaleFactor() : 1.0F;
        float verticalBaseScale = entity instanceof VillagerLike<?> villagerEntity ? villagerEntity.getVerticalScaleFactor() : 1.0F;
        float horizontalRatio = visuals.rawHorizontalScaleFactor() / Math.max(horizontalBaseScale, 1.0E-4F);
        float verticalRatio = visuals.rawVerticalScaleFactor() / Math.max(verticalBaseScale, 1.0E-4F);

        state.boundingBoxWidth = entity.getBbWidth() * horizontalRatio;
        state.boundingBoxHeight = entity.getBbHeight() * verticalRatio;
        if (state.hasPose(Pose.SLEEPING) && state.bedOrientation == null && entity.isPassenger()) {
            state.pose = Pose.STANDING;
        } else if (state.hasPose(Pose.SLEEPING)) {
            state.walkAnimationPos = 0.0F;
            state.walkAnimationSpeed = 0.0F;
        }
        // When sleeping, vanilla's extractRenderState() uses getEyeHeight(Pose.STANDING) for the
        // bed head-offset translation in submit(). We must mirror that here instead of using the
        // sleeping pose eye height, otherwise the villager ends up at the wrong position on the bed.
        Pose eyePose = state.bedOrientation != null ? Pose.STANDING : state.pose;
        state.eyeHeight = entity.getEyeHeight(eyePose) * verticalRatio;
        if (state.nameTagAttachment != null) {
            Vec3 nameTagAttachment = entity.getAttachments().get(EntityAttachment.NAME_TAG, 0, entity.getYRot());
            double y = entity.isPassenger() ? 0.55 : state.boundingBoxHeight;
            state.nameTagAttachment = new Vec3(nameTagAttachment.x, y, nameTagAttachment.z);
        }
    }
}
