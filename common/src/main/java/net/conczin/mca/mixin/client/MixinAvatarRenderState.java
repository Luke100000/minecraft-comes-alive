package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class MixinAvatarRenderState implements VillagerStateHolder {
    @Unique
    private @Nullable VillagerVisualSnapshot mca$visualSnapshot;
    @Unique
    private boolean mca$geneticsRendererActive;
    @Unique
    private boolean mca$villagerRendererActive;

    @Override
    public @Nullable VillagerVisualSnapshot mca$getVisualSnapshot() {
        return mca$visualSnapshot;
    }

    @Override
    public void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot) {
        this.mca$visualSnapshot = snapshot;
    }

    @Override
    public boolean mca$isGeneticsRendererActive() {
        return mca$geneticsRendererActive;
    }

    @Override
    public void mca$setGeneticsRendererActive(boolean active) {
        this.mca$geneticsRendererActive = active;
    }

    @Override
    public boolean mca$isVillagerRendererActive() {
        return mca$villagerRendererActive;
    }

    @Override
    public void mca$setVillagerRendererActive(boolean active) {
        this.mca$villagerRendererActive = active;
    }
}
