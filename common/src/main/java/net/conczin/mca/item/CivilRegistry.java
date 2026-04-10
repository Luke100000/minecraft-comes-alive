package net.conczin.mca.item;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

public class CivilRegistry extends ExtendedWrittenBookItem {
    public CivilRegistry(Properties settings, Book book) {
        super(settings, book);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, tooltip, tooltipFlag);

        FlowingText.wrap(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY), 160).forEach(tooltip);
    }
}
