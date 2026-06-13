package net.conczin.mca.client.render;

import net.conczin.mca.MCAClient;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;

public final class VillagerRenderStateHooks {
    private VillagerRenderStateHooks() {
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state) {
        if (!(entity instanceof Player) || !(state instanceof VillagerStateHolder holder) || state instanceof VillagerRenderState) {
            return;
        }

        VillagerLike<?> visualsSource = MCAClient.getPlayerData(entity.getUUID()).orElse(null);
        VillagerLike.PlayerModel playerModel = visualsSource != null
                ? visualsSource.getPlayerModel()
                : VillagerLike.PlayerModel.VANILLA;

        VillagerVisualSnapshot visualSnapshot = playerModel != VillagerLike.PlayerModel.VANILLA && visualsSource != null
                ? VillagerVisualSnapshot.capture(visualsSource)
                : null;

        holder.mca$setPlayerModel(visualSnapshot != null ? playerModel : VillagerLike.PlayerModel.VANILLA);
        holder.mca$setVisualSnapshot(visualSnapshot);
    }

    public static void extractScaledBounds(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            return;
        }

        VillagerVisualSnapshot visuals = holder.mca$getVisualSnapshot();
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
        Pose eyePose = state.bedOrientation != null ? Pose.STANDING : state.pose;
        state.eyeHeight = entity.getEyeHeight(eyePose) * verticalRatio;
    }
}
