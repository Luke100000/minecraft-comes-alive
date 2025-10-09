package net.mca.client.gui.widget;

import net.mca.util.localization.FlowingText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import java.util.function.Consumer;
import java.util.function.Supplier;

public abstract class ExtendedSliderWidget<T> extends AbstractSliderButton {
    private T oldValue;
    final Consumer<T> onApplyValue;
    protected final Supplier<Component> tooltipSupplier;

    public ExtendedSliderWidget(int x, int y, int width, int height, Component text, double value, Consumer<T> onApplyValue, Supplier<Component> tooltipSupplier) {
        super(x, y, width, height, text, value);
        this.onApplyValue = onApplyValue;
        this.tooltipSupplier = tooltipSupplier;
    }

    protected double getOpticalValue() {
        return value;
    }

    abstract T getValue();

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int i = (this.isHovered() ? 2 : 1) * 20;
        context.blit(WIDGETS_LOCATION, this.getX() + (int) (getOpticalValue() * (double) (this.width - 8)), this.getY(), 0, 46 + i, 4, 20);
        context.blit(WIDGETS_LOCATION, this.getX() + (int) (getOpticalValue() * (double) (this.width - 8)) + 4, this.getY(), 196, 46 + i, 4, 20);

        super.renderWidget(context, mouseX, mouseY, delta);

        if (this.isHovered()) {
            this.renderTooltip(context, mouseX, mouseY);
        }
    }

    @Override
    protected void applyValue() {
        T v = getValue();
        if (v != oldValue) {
            oldValue = v;
            onApplyValue.accept(v);
        }
    }

    public void renderTooltip(GuiGraphics context, int mouseX, int mouseY) {
        assert Minecraft.getInstance() != null;
        context.renderComponentTooltip(Minecraft.getInstance().font, FlowingText.wrap(tooltipSupplier.get(), 160), mouseX, mouseY);
    }
}
