package net.conczin.mca.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.gui.PreviewEntityAnimation;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.gui.render.pip.GuiEntityRenderer;
import net.minecraft.client.renderer.state.gui.pip.GuiEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GuiEntityRenderer.class)
public class MixinGuiEntityRenderer {
    @WrapMethod(method = "renderToTexture")
    private void mca$renderWithPreviewEntityState(
            GuiEntityRenderState guiState,
            PoseStack poseStack,
            Operation<Void> original
    ) {
        PreviewEntityAnimation.State previewState = guiState.renderState() instanceof VillagerStateHolder holder
                ? holder.mca$getPreviewEntityAnimationState()
                : null;
        if (previewState == null) {
            original.call(guiState, poseStack);
            return;
        }

        previewState.run(() -> {
            original.call(guiState, poseStack);
            return null;
        });
    }
}
