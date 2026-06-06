package net.conczin.mca.client.render;

import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.jspecify.annotations.Nullable;

public class VillagerRenderState extends UndeadRenderState implements VillagerStateHolder {
    private @Nullable VillagerVisualSnapshot visualSnapshot;
    private boolean geneticsRendererActive;
    private boolean villagerRendererActive;
    public boolean isConverting;
    public boolean cribPassenger;

    @Override
    public @Nullable VillagerVisualSnapshot mca$getVisualSnapshot() {
        return visualSnapshot;
    }

    @Override
    public void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot) {
        this.visualSnapshot = snapshot;
    }

    @Override
    public boolean mca$isGeneticsRendererActive() {
        return geneticsRendererActive;
    }

    @Override
    public void mca$setGeneticsRendererActive(boolean active) {
        this.geneticsRendererActive = active;
    }

    @Override
    public boolean mca$isVillagerRendererActive() {
        return villagerRendererActive;
    }

    @Override
    public void mca$setVillagerRendererActive(boolean active) {
        this.villagerRendererActive = active;
    }
}
