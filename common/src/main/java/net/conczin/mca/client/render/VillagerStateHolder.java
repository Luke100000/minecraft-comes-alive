package net.conczin.mca.client.render;

import net.conczin.mca.client.gui.PreviewEntityAnimation.State;
import org.jspecify.annotations.Nullable;

public interface VillagerStateHolder {
    static VillagerStateHolder require(Object state) {
        if (!(state instanceof VillagerStateHolder holder)) {
            throw new IllegalStateException("No MCA villager data holder available for render state");
        }
        return holder;
    }

    @Nullable VillagerRenderData mca$getVillagerRenderData();

    void mca$setVillagerRenderData(@Nullable VillagerRenderData renderData);

    @Nullable HumanoidModelPose mca$getHumanoidModelPose();

    void mca$setHumanoidModelPose(@Nullable HumanoidModelPose pose);

    @Nullable State mca$getPreviewEntityAnimationState();

    void mca$setPreviewEntityAnimationState(@Nullable State state);

    default boolean mca$isGeneticsRendererActive() {
        VillagerRenderData renderData = mca$getVillagerRenderData();
        return renderData != null && renderData.usesGeneticsRenderer();
    }

    default boolean mca$isVillagerRendererActive() {
        VillagerRenderData renderData = mca$getVillagerRenderData();
        return renderData != null && renderData.usesVillagerRenderer();
    }

    default @Nullable VillagerVisuals mca$getVisuals() {
        VillagerRenderData renderData = mca$getVillagerRenderData();
        return renderData != null ? renderData.visuals() : null;
    }
}
