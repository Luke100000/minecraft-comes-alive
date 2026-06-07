package net.conczin.mca.mixin.client;

import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(LivingEntityRenderState.class)
public class MixinLivingEntityRenderState implements VillagerStateHolder {
    @Unique
    private @Nullable VillagerVisualSnapshot mca$visualSnapshot;
    @Unique
    private VillagerLike.PlayerModel mca$playerModel = VillagerLike.PlayerModel.VANILLA;

    @Override
    public @Nullable VillagerVisualSnapshot mca$getVisualSnapshot() {
        return mca$visualSnapshot;
    }

    @Override
    public void mca$setVisualSnapshot(@Nullable VillagerVisualSnapshot snapshot) {
        this.mca$visualSnapshot = snapshot;
    }

    @Override
    public VillagerLike.PlayerModel mca$getPlayerModel() {
        return mca$playerModel;
    }

    @Override
    public void mca$setPlayerModel(VillagerLike.PlayerModel playerModel) {
        this.mca$playerModel = playerModel;
    }
}
