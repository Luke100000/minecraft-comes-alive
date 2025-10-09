package net.conczin.mca.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.block.TombstoneBlock.Data;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TombstoneBlockEntityRenderer implements BlockEntityRenderer<TombstoneBlock.Data> {
    private final Font text;

    public TombstoneBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        text = context.getFont();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }

    @Override
    public void render(Data entity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        if (!entity.hasEntity()) {
            return;
        }

        BlockState state = entity.getBlockState();

        matrices.pushPose();
        matrices.translate(0.5, 0.5, 0.5);

        Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite();

        matrices.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        matrices.translate(0, 0, 0);
        matrices.scale(0.010416667F, 0.010416667F, 0.010416667F);
        matrices.mulPose(Axis.ZP.rotationDegrees(180));

        TombstoneBlock block = (TombstoneBlock) state.getBlock();

        matrices.mulPose(Axis.XP.rotationDegrees(block.getRotation()));

        Vec3 offset = block.getNameplateOffset();
        matrices.translate(offset.x(), offset.y(), offset.z());

        int maxLineWidth = block.getLineWidth();

        float y = drawText(text, text.split(Component.translatable("block.mca.tombstone.header"), maxLineWidth), 0, matrices, vertexConsumers, light);

        y += 5;

        FlowingText name = entity.getOrCreateEntityName(n -> FlowingText.Factory.wrapLines(text, n, maxLineWidth, block.getMaxNameHeight()));

        matrices.pushPose();
        matrices.scale(name.scale(), name.scale(), name.scale());

        y = drawText(text, name.lines(), y / name.scale(), matrices, vertexConsumers, light) * name.scale();

        matrices.popPose();

        y += 5;

        drawText(text, text.split(Component.translatable("block.mca.tombstone.footer." + entity.getGender().binary().getDataName()), maxLineWidth), y, matrices, vertexConsumers, light);

        matrices.popPose();
    }

    private float drawText(Font text, List<FormattedCharSequence> lines, float y, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        for (FormattedCharSequence line : lines) {
            float x = -text.width(line) / 2F;

            text.drawInBatch8xOutline(line, x, y, 0xFFFFFFFF, 0xFF000000, matrices.last().pose(), vertexConsumers, light);

            y += 10;
        }

        return y;
    }
}
