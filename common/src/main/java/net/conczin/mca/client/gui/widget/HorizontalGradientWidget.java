package net.conczin.mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
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
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tessellator = Tesselator.getInstance();
        BufferBuilder builder = tessellator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float[] startColor = startColorSupplier.get();
        float[] endColor = endColorSupplier.get();

        float z = 0.0f;
        final PoseStack matrices = context.pose();
        Matrix4f matrix = matrices.last().pose();
        builder.vertex(matrix, (float)getX() + width, (float)getY(), z).color(endColor[0], endColor[1], endColor[2], endColor[3]).endVertex();
        builder.vertex(matrix, (float)getX(), (float)getY(), z).color(startColor[0], startColor[1], startColor[2], startColor[3]).endVertex();
        builder.vertex(matrix, (float)getX(), (float)getY() + height, z).color(startColor[0], startColor[1], startColor[2], startColor[3]).endVertex();
        builder.vertex(matrix, (float)getX() + width, (float)getY() + height, z).color(endColor[0], endColor[1], endColor[2], endColor[3]).endVertex();

        tessellator.end();

        RenderSystem.disableBlend();

        WidgetUtils.drawRectangle(context, getX(), getY(), getX() + width, getY() + height, 0xaaffffff);

        context.blit(MCA_GUI_ICONS_TEXTURE, (int)(getX() + valueX * width) - 8, (int)(getY() + valueY * height) - 8, 240, 0, 16, 16, 256, 256);
    }
}
