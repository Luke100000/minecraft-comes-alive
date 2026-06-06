package net.conczin.mca.client.render;

import org.jspecify.annotations.Nullable;

public interface VillagerStateHolder {
    @Nullable VillagerVisualSnapshot mca$getVisualSnapshot();

    void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot);

    boolean mca$isGeneticsRendererActive();

    void mca$setGeneticsRendererActive(boolean active);

    boolean mca$isVillagerRendererActive();

    void mca$setVillagerRendererActive(boolean active);
}
