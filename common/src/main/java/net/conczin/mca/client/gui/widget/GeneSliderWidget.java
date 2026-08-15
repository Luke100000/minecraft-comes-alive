package net.conczin.mca.client.gui.widget;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

public class GeneSliderWidget extends AbstractSliderButton {
    private final Consumer<Double> callback;

    public GeneSliderWidget(int x, int y, int width, int height, Component text, double value, Consumer<Double> callback) {
        super(x, y, width, height, text, value);
        this.updateMessage();
        this.callback = callback;
    }

    @Override
    protected void applyValue() {
        callback.accept(value);
    }

    @Override
    protected void updateMessage() {

    }
}
