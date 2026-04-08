package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.block.TombstoneBlock.Data;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class TombstoneBlockEntityRenderer implements BlockEntityRenderer<TombstoneBlock.Data, TombstoneBlockEntityRenderState> {
    private final Font text;

    public TombstoneBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.text = context.font();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public TombstoneBlockEntityRenderState createRenderState() {
        return new TombstoneBlockEntityRenderState();
    }

    @Override
    public void extractRenderState(Data entity, TombstoneBlockEntityRenderState state, float partialTicks, Vec3 cameraPosition, @Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(entity, state, partialTicks, cameraPosition, breakProgress);
        state.data = entity;
    }

    @Override
    public void submit(TombstoneBlockEntityRenderState state, PoseStack matrices, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (state.data == null || !state.data.hasEntity()) {
            return;
        }

        BlockState blockState = state.data.getBlockState();

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);

        Direction facing = blockState.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();
        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        matrices.scale(0.010416667F, 0.010416667F, 0.010416667F);
        matrices.mulPose(Axis.ZP.rotationDegrees(180));

        TombstoneBlock block = (TombstoneBlock) blockState.getBlock();
        matrices.mulPose(Axis.XP.rotationDegrees(block.getRotation()));

        Vec3 offset = block.getNameplateOffset();
        matrices.translate(offset.x(), offset.y(), offset.z());

        int maxLineWidth = block.getLineWidth();

        float y = drawText(submitNodeCollector, this.text.split(Component.translatable("block.mca.tombstone.header"), maxLineWidth), 0.0F, matrices, state.lightCoords);
        y += 5.0F;

        FlowingText name = state.data.getOrCreateEntityName(n -> FlowingText.Factory.wrapLines(this.text, n, maxLineWidth, block.getMaxNameHeight()));

        matrices.pushPose();
        matrices.scale(name.scale(), name.scale(), name.scale());
        y = drawText(submitNodeCollector, name.lines(), y / name.scale(), matrices, state.lightCoords) * name.scale();
        matrices.popPose();

        y += 5.0F;
        drawText(
            submitNodeCollector,
            this.text.split(Component.translatable("block.mca.tombstone.footer." + state.data.getGender().binary().getDataName()), maxLineWidth),
            y,
            matrices,
            state.lightCoords
        );

        matrices.popPose();
    }

    private float drawText(SubmitNodeCollector submitNodeCollector, List<FormattedCharSequence> lines, float y, PoseStack matrices, int lightCoords) {
        for (FormattedCharSequence line : lines) {
            float x = -this.text.width(line) / 2F;
            submitNodeCollector.submitText(matrices, x, y, line, false, Font.DisplayMode.POLYGON_OFFSET, lightCoords, 0xFFFFFFFF, 0xFF000000, 0xFF000000);
            y += 10.0F;
        }

        return y;
    }
}
