package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

public class ItemButtonWidget extends TooltipButtonWidget {
    final ItemStack item;

    public ItemButtonWidget(int x, int y, int size, MutableComponent message, ItemStack item, OnPress onPress) {
        super(x, y, size, size, Component.literal(""), message, onPress);

        this.item = item;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);

        int size = 16;

        context.renderItem(item, getX() + (width - size) / 2, getY() + (height - size) / 2);
    }
}
