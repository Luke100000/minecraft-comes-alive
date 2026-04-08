package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.UndeadRenderState;
import org.jspecify.annotations.Nullable;

public class VillagerRenderState extends UndeadRenderState implements VillagerStateHolder {
    private @Nullable VillagerLike<?> villager;
    public boolean isConverting;

    @Override
    public @Nullable VillagerLike<?> mca$getVillager() {
        return villager;
    }

    @Override
    public void mca$setVillager(@Nullable VillagerLike<?> villager) {
        this.villager = villager;
    }
}
