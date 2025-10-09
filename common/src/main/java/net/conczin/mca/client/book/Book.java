package net.conczin.mca.client.book;

import net.conczin.mca.client.book.pages.EmptyPage;
import net.conczin.mca.client.book.pages.Page;
import net.conczin.mca.client.book.pages.TextPage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;

public class Book {
    private final String bookName;
    private final Component bookAuthor;
    private final List<Page> pages = new LinkedList<>();
    private ResourceLocation background = ResourceLocation.withDefaultNamespace("textures/gui/book.png");
    private ChatFormatting textFormatting = ChatFormatting.BLACK;
    private boolean pageTurnSound = true;
    private boolean textShadow;

    public Book(String bookName) {
        this(bookName, Component.translatable("mca.books." + bookName + ".author").withStyle(ChatFormatting.GRAY));
    }

    public Book(String bookName, Component bookAuthor) {
        this.bookName = bookName;
        this.bookAuthor = bookAuthor;
    }

    public Book setTextShadow(boolean textShadow) {
        this.textShadow = textShadow;
        return this;
    }

    public Book setPageTurnSound(boolean pageTurnSound) {
        this.pageTurnSound = pageTurnSound;
        return this;
    }

    public Book addPage(Page page) {
        pages.add(page);
        return this;
    }

    private Book addPages(List<Page> pages) {
        for (Page p : pages) {
            addPage(p);
        }
        return this;
    }

    public Book addSimplePages(int n) {
        return addSimplePages(n, 0);
    }

    public Book addSimplePages(int n, int start) {
        for (int i = 0; i < n; i++) {
            addPage(new TextPage(getBookName(), start + i));
        }
        return this;
    }

    public int getPageCount() {
        return pages.size();
    }

    public boolean showPageCount() {
        return getPageCount() > 1;
    }

    public String getBookName() {
        return bookName;
    }

    @Nullable
    public Component getBookAuthor() {
        return bookAuthor;
    }

    public List<Page> getPages() {
        return pages;
    }

    public ResourceLocation getBackground() {
        return background;
    }

    public Book setBackground(ResourceLocation background) {
        this.background = background;
        return this;
    }

    public ChatFormatting getTextFormatting() {
        return textFormatting;
    }

    public Book setTextFormatting(ChatFormatting textFormatting) {
        this.textFormatting = textFormatting;
        return this;
    }

    public boolean hasTextShadow() {
        return textShadow;
    }

    public boolean hasPageTurnSound() {
        return pageTurnSound;
    }

    public Page getPage(int index) {
        return index < pages.size() ? pages.get(index) : new EmptyPage();
    }

    public void open() {

    }

    public void setPage(int i, boolean back) {
        getPage(i).open(back);
    }

    public Book copy() {
        return new Book(getBookName())
                .setBackground(getBackground())
                .setTextFormatting(getTextFormatting())
                .setPageTurnSound(hasPageTurnSound())
                .addPages(pages);
    }
}
