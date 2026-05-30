package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import org.jspecify.annotations.Nullable;

public interface VillagerStateHolder {
    @Nullable VillagerLike<?> mca$getVillager();

    void mca$setVillager(@Nullable VillagerLike<?> villager);

    @Nullable VillagerVisualSnapshot mca$getVisualSnapshot();

    void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot);

    boolean mca$isGeneticsRendererActive();

    void mca$setGeneticsRendererActive(boolean active);

    boolean mca$isVillagerRendererActive();

    void mca$setVillagerRendererActive(boolean active);
}
