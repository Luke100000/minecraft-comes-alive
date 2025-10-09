package net.conczin.mca.item;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.pages.TextPage;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.registry.DataComponentsMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;

import java.util.List;

public class ExtendedWrittenBookItem extends WrittenBookItem {
    private final Book book;

    public ExtendedWrittenBookItem(Properties settings, Book book) {
        super(settings);
        this.book = book;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);

        if (book.getBookAuthor() != null) {
            tooltipComponents.add(book.getBookAuthor());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BOOK), serverPlayer);
        }

        return InteractionResultHolder.success(itemStack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public Book getBook(ItemStack item) {
        List<Component> content = item.get(DataComponentsMCA.BOOK_PAGES);
        if (content != null) {
            //seems like a vanilla book, let's convert it into the extended book format
            Book book = this.book.copy();

            //add our text pages
            for (Component page : content) {
                book.addPage(new TextPage(page));
            }

            return book;
        } else {
            return book;
        }
    }
}
