package net.conczin.mca.item;

import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class TooltippedItem extends Item {
    public TooltippedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, tooltipFlag);

        FlowingText.wrap(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY), 160).forEach(tooltip);
    }
}
