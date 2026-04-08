package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AvatarRenderState.class)
public class MixinAvatarRenderState implements VillagerStateHolder {
    @Unique
    private @Nullable VillagerLike<?> mca$villager;
    @Unique
    private @Nullable VillagerVisualSnapshot mca$visualSnapshot;

    @Override
    public @Nullable VillagerLike<?> mca$getVillager() {
        return mca$villager;
    }

    @Override
    public void mca$setVillager(@Nullable VillagerLike<?> villager) {
        this.mca$villager = villager;
    }

    @Override
    public @Nullable VillagerVisualSnapshot mca$getVisualSnapshot() {
        return mca$visualSnapshot;
    }

    @Override
    public void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot) {
        this.mca$visualSnapshot = snapshot;
    }
}
