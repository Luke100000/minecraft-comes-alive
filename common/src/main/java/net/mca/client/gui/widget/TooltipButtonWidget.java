package net.mca.client.gui.widget;

import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public class TooltipButtonWidget extends ButtonWidget {
    public TooltipButtonWidget(int x, int y, int width, int height, String message, OnPress onPress) {
        super(x, y, width, height, Component.translatable(message), onPress, Component.translatable(message + ".tooltip"));
    }

    public TooltipButtonWidget(int x, int y, int width, int height, MutableComponent message, MutableComponent tooltip, OnPress onPress) {
        super(x, y, width, height, message, onPress, tooltip);
    }

    public void setMessage(String message) {
        super.setMessage(Component.translatable(message));
        super.setTooltip(Tooltip.create(Component.translatable(message + ".tooltip")));
    }
}
