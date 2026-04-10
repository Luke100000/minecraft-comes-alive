package net.conczin.mca.client.render;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.block.TombstoneBlock.Data;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

public class TombstoneBlockEntityRenderer implements BlockEntityRenderer<TombstoneBlock.Data, BlockEntityRenderState> {
    public TombstoneBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public BlockEntityRenderState createRenderState() {
        return new BlockEntityRenderState();
    }

    @Override
    public void submit(BlockEntityRenderState renderState, com.mojang.blaze3d.vertex.PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
    }
}
