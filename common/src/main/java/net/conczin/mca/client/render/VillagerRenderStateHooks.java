package net.conczin.mca.client.render;

import net.conczin.mca.MCAClient;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.Optional;

public final class VillagerRenderStateHooks {
    private VillagerRenderStateHooks() {
    }

    public static void extract(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            return;
        }

        Optional<VillagerLike<?>> playerData = entity instanceof Player ? MCAClient.getPlayerData(entity.getUUID()) : Optional.empty();
        VillagerLike.PlayerModel playerModel = playerData.map(VillagerLike::getPlayerModel).orElse(VillagerLike.PlayerModel.VANILLA);
        boolean geneticsRenderer = playerModel != VillagerLike.PlayerModel.VANILLA;
        boolean villagerRenderer = playerModel == VillagerLike.PlayerModel.VILLAGER;
        VillagerLike<?> villager = null;
        if (entity instanceof VillagerLike<?> villagerLike) {
            villager = villagerLike;
        } else if (geneticsRenderer) {
            villager = playerData.orElse(null);
        }

        holder.mca$setGeneticsRendererActive(geneticsRenderer);
        holder.mca$setVillagerRendererActive(villagerRenderer);
        holder.mca$setVillager(villager);
        holder.mca$setVisualSnapshot(villager != null ? VillagerVisualSnapshot.capture(villager) : null);
    }

    public static void extractScaledBounds(LivingEntity entity, LivingEntityRenderState state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            return;
        }

        var visuals = CommonVillagerModel.peekVisuals(holder);
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
        state.eyeHeight = entity.getEyeHeight(state.pose) * verticalRatio;
    }
}
