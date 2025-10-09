package net.conczin.mca.item;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

public class CivilRegistry extends ExtendedWrittenBookItem {
    public CivilRegistry(Properties settings, Book book) {
        super(settings, book);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltip, tooltipFlag);

        tooltip.addAll(FlowingText.wrap(Component.translatable(getDescriptionId(stack) + ".tooltip").withStyle(ChatFormatting.GRAY), 160));
    }
}
