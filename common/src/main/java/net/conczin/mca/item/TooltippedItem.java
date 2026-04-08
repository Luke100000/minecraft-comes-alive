package net.conczin.mca.item;

import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TooltippedItem extends Item {
    public TooltippedItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, consumer, tooltipFlag);

        List<Component> tooltip = new ArrayList<>();
        appendHoverText(stack, context, tooltip, tooltipFlag);
        tooltip.forEach(consumer);
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        tooltip.addAll(FlowingText.wrap(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY), 160));
    }
}
