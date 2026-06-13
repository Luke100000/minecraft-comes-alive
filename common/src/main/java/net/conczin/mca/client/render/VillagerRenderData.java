package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record VillagerRenderData(
        VillagerLike.PlayerModel playerModel,
        VillagerVisuals visuals
) {
    public VillagerRenderData {
        Objects.requireNonNull(playerModel, "playerModel");
        if (playerModel == VillagerLike.PlayerModel.VANILLA) {
            throw new IllegalArgumentException("Vanilla player model must not carry MCA villager render data");
        }
        Objects.requireNonNull(visuals, "visuals");
    }

    public static @Nullable VillagerRenderData create(VillagerLike.PlayerModel playerModel, @Nullable VillagerLike<?> visualsSource) {
        if (playerModel == VillagerLike.PlayerModel.VANILLA || visualsSource == null) {
            return null;
        }
        return new VillagerRenderData(playerModel, VillagerVisuals.capture(visualsSource));
    }

    public boolean usesGeneticsRenderer() {
        return playerModel != VillagerLike.PlayerModel.VANILLA;
    }

    public boolean usesVillagerRenderer() {
        return playerModel == VillagerLike.PlayerModel.VILLAGER;
    }
}
