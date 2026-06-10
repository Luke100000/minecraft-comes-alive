package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import org.jspecify.annotations.Nullable;

public interface VillagerStateHolder {
    static VillagerStateHolder require(Object state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            throw new IllegalStateException("No MCA villager data holder available for render state");
        }
        return holder;
    }

    @Nullable VillagerVisualSnapshot mca$getVisualSnapshot();

    void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot);

    VillagerLike.PlayerModel mca$getPlayerModel();

    void mca$setPlayerModel(VillagerLike.PlayerModel playerModel);

    default boolean mca$isGeneticsRendererActive() {
        return mca$getVisualSnapshot() != null && mca$getPlayerModel() != VillagerLike.PlayerModel.VANILLA;
    }

    default boolean mca$isVillagerRendererActive() {
        return mca$getVisualSnapshot() != null && mca$getPlayerModel() == VillagerLike.PlayerModel.VILLAGER;
    }
}
