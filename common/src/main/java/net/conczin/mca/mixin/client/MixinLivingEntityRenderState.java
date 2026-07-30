package net.conczin.mca.mixin.client;

import net.conczin.mca.client.gui.PreviewEntityAnimation.State;
import net.conczin.mca.client.render.HumanoidModelPose;
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
    @Unique
    private @Nullable HumanoidModelPose mca$humanoidModelPose;
    @Unique
    private @Nullable State mca$previewEntityAnimationState;

    @Override
    public @Nullable VillagerRenderData mca$getVillagerRenderData() {
        return mca$villagerRenderData;
    }

    @Override
    public void mca$setVillagerRenderData(@Nullable VillagerRenderData renderData) {
        this.mca$villagerRenderData = renderData;
    }

    @Override
    public @Nullable HumanoidModelPose mca$getHumanoidModelPose() {
        return mca$humanoidModelPose;
    }

    @Override
    public void mca$setHumanoidModelPose(@Nullable HumanoidModelPose pose) {
        this.mca$humanoidModelPose = pose;
    }

    @Override
    public @Nullable State mca$getPreviewEntityAnimationState() {
        return mca$previewEntityAnimationState;
    }

    @Override
    public void mca$setPreviewEntityAnimationState(@Nullable State state) {
        this.mca$previewEntityAnimationState = state;
    }
}
