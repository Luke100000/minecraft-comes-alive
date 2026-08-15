package net.conczin.mca.item;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.pages.TextPage;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.WrittenBookItem;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ExtendedWrittenBookItem extends WrittenBookItem {
    private final Book book;

    public ExtendedWrittenBookItem(Properties settings, Book book) {
        super(settings);
        this.book = book;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        if (book.getBookAuthor() != null) {
            tooltip.add(book.getBookAuthor());
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BOOK), (ServerPlayer)player);
        }

        return InteractionResultHolder.success(itemStack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    public Book getBook(ItemStack item) {
        CompoundTag tag = item.getTag();
        if (tag != null && tag.contains("pages")) {
            //seems like a vanilla book, let's make a copy of the book
            Book book = this.book.copy();

            //add our text pages
            ListTag pages = tag.getList("pages", Tag.TAG_STRING);
            for (int i = 0; i < pages.size(); i++) {
                book.addPage(new TextPage(pages.getString(i)));
            }

            return book;
        } else {
            return book;
        }
    }
}
