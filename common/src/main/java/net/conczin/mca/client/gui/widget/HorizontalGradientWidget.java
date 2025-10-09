package net.conczin.mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class HorizontalGradientWidget extends HorizontalColorPickerWidget {
    private final Supplier<float[]> startColorSupplier;
    private final Supplier<float[]> endColorSupplier;

    public HorizontalGradientWidget(int x, int y, int width, int height, double valueX, Supplier<float[]> startColorSupplier, Supplier<float[]> endColorSupplier, DualConsumer<Double, Double> consumer) {
        super(x, y, width, height, valueX, null, consumer);

        this.startColorSupplier = startColorSupplier;
        this.endColorSupplier = endColorSupplier;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float[] startColor = startColorSupplier.get();
        float[] endColor = endColorSupplier.get();

        float z = 0.0f;
        final PoseStack matrices = context.pose();
        Matrix4f matrix = matrices.last().pose();
        builder.addVertex(matrix, (float) getX() + width, (float) getY(), z).setColor(endColor[0], endColor[1], endColor[2], endColor[3]);
        builder.addVertex(matrix, (float) getX(), (float) getY(), z).setColor(startColor[0], startColor[1], startColor[2], startColor[3]);
        builder.addVertex(matrix, (float) getX(), (float) getY() + height, z).setColor(startColor[0], startColor[1], startColor[2], startColor[3]);
        builder.addVertex(matrix, (float) getX() + width, (float) getY() + height, z).setColor(endColor[0], endColor[1], endColor[2], endColor[3]);

        BufferUploader.drawWithShader(builder.buildOrThrow());

        RenderSystem.disableBlend();

        WidgetUtils.drawRectangle(context, getX(), getY(), getX() + width, getY() + height, 0xaaffffff);

        context.blit(MCA_GUI_ICONS_TEXTURE, (int) (getX() + valueX * width) - 8, (int) (getY() + valueY * height) - 8, 240, 0, 16, 16, 256, 256);
    }
}
