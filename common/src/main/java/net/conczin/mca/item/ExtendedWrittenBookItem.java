package net.conczin.mca.item;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.pages.TextPage;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.registry.DataComponentsMCA;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Consumer;

public class ExtendedWrittenBookItem extends WrittenBookItem {
    private final Book book;

    public ExtendedWrittenBookItem(Properties settings, Book book) {
        super(settings);
        this.book = book;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag tooltipFlag) {
        super.appendHoverText(stack, context, tooltipDisplay, consumer, tooltipFlag);

        if (book.getBookAuthor() != null) {
            consumer.accept(book.getBookAuthor());
        }
    }

    @Override
    public InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BOOK), serverPlayer);
        }

        return world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
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

