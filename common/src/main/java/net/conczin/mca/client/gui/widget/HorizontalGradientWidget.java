package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float[] startColor = startColorSupplier.get();
        float[] endColor = endColorSupplier.get();
        int segments = Math.max(width, 1);
        for (int x = 0; x < segments; x++) {
            float t = segments == 1 ? 0.0f : x / (float) (segments - 1);
            int color = ARGB.colorFromFloat(
                    startColor[3] + (endColor[3] - startColor[3]) * t,
                    startColor[0] + (endColor[0] - startColor[0]) * t,
                    startColor[1] + (endColor[1] - startColor[1]) * t,
                    startColor[2] + (endColor[2] - startColor[2]) * t
            );
            context.fill(getX() + x, getY(), getX() + x + 1, getY() + height, color);
        }

        WidgetUtils.drawRectangle(context, getX(), getY(), getX() + width, getY() + height, 0xaaffffff);

        context.blit(RenderPipelines.GUI_TEXTURED, MCA_GUI_ICONS_TEXTURE, (int) (getX() + valueX * width) - 8, (int) (getY() + valueY * height) - 8, 240, 0, 16, 16, 256, 256);
    }
}

