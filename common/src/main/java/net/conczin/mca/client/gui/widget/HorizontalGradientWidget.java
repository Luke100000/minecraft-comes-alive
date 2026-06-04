package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.ARGB;

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
        float[] startColor = startColorSupplier.get();
        float[] endColor = endColorSupplier.get();

        context.fillGradient(
                getX(),
                getY(),
                getX() + width,
                getY() + height,
                ARGB.colorFromFloat(startColor[3], startColor[0], startColor[1], startColor[2]),
                ARGB.colorFromFloat(endColor[3], endColor[0], endColor[1], endColor[2])
        );

        WidgetUtils.drawRectangle(context, getX(), getY(), getX() + width, getY() + height, 0xaaffffff);

        context.blit(RenderPipelines.GUI_TEXTURED, MCA_GUI_ICONS_TEXTURE, (int) (getX() + valueX * width) - 8, (int) (getY() + valueY * height) - 8, 240, 0, 16, 16, 256, 256);
    }
}
