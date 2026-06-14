package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VillagerRenderData;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class MixinLivingEntityRenderState implements VillagerStateHolder {
    @Unique
    private @Nullable VillagerRenderData mca$villagerRenderData;

    @Override
    public @Nullable VillagerRenderData mca$getVillagerRenderData() {
        return mca$villagerRenderData;
    }

    @Override
    public void mca$setVillagerRenderData(@Nullable VillagerRenderData renderData) {
        this.mca$villagerRenderData = renderData;
    }
}
