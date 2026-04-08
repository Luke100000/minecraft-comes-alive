package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.jspecify.annotations.Nullable;

public class VillagerRenderState extends UndeadRenderState implements VillagerStateHolder {
    private @Nullable VillagerLike<?> villager;
    private @Nullable VillagerVisualSnapshot visualSnapshot;
    public boolean isConverting;

    @Override
    public @Nullable VillagerLike<?> mca$getVillager() {
        return villager;
    }

    @Override
    public void mca$setVillager(@Nullable VillagerLike<?> villager) {
        this.villager = villager;
    }

    @Override
    public @Nullable VillagerVisualSnapshot mca$getVisualSnapshot() {
        return visualSnapshot;
    }

    @Override
    public void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot) {
        this.visualSnapshot = snapshot;
    }
}
