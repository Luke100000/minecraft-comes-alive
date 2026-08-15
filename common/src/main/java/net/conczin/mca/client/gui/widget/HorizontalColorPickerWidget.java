package net.conczin.mca.client.gui.widget;

import net.minecraft.resources.ResourceLocation;

public class HorizontalColorPickerWidget extends ColorPickerWidget {
    public HorizontalColorPickerWidget(int x, int y, int width, int height, double valueX, ResourceLocation texture, DualConsumer<Double, Double> consumer) {
        super(x, y, width, height, valueX, 0.5, texture, consumer);
    }

    @Override
    void update(double mouseX, double mouseY) {
        super.update(mouseX, mouseY);

        this.valueY = 0.5;
    }
}
