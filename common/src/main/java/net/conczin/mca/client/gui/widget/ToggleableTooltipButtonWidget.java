package net.conczin.mca.client.gui.widget;

import net.minecraft.network.chat.MutableComponent;

public class ToggleableTooltipButtonWidget extends TooltipButtonWidget {
    public boolean toggle;

    public ToggleableTooltipButtonWidget(int x, int y, int width, int height, boolean toggle, MutableComponent message, MutableComponent tooltip, OnPress onPress) {
        super(x, y, width, height, message, tooltip, onPress);

        this.toggle = toggle;
    }

    protected int getYImage(boolean hovered) {
        int i = 1;
        if (this.toggle) {
            i = 0;
        } else if (hovered) {
            i = 2;
        }
        return i;
    }
}
