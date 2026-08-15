package net.conczin.mca.client.gui;

import net.conczin.mca.client.book.Book;
import net.conczin.mca.client.book.pages.Page;
import net.conczin.mca.client.gui.widget.ExtendedPageTurnWidget;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;

public class ExtendedBookScreen extends Screen {
    private int pageIndex;
    private PageButton nextPageButton;
    private PageButton previousPageButton;
    private final Book book;

    public ExtendedBookScreen(Book book) {
        super(GameNarrator.NO_TITLE);
        this.book = book;
        book.open();
        book.setPage(0, false);
    }

    public boolean setPage(int index) {
        int i = Mth.clamp(index, 0, this.book.getPageCount() - 1);
        if (i != this.pageIndex) {
            book.setPage(i, false);
            this.pageIndex = i;
            this.updatePageButtons();
            return true;
        } else {
            return false;
        }
    }

    protected boolean jumpToPage(int page) {
        return setPage(page);
    }

    @Override
    protected void init() {
        addCloseButton();
        addPageButtons();
    }

    protected void addCloseButton() {
        addRenderableWidget(new ButtonWidget(width / 2 - 100, 196, 200, 20, CommonComponents.GUI_DONE, (buttonWidget) -> this.minecraft.setScreen(null)));
    }

    protected void addPageButtons() {
        int i = (width - 192) / 2;
        nextPageButton = addRenderableWidget(new ExtendedPageTurnWidget(i + 116, 159, true, (buttonWidget) -> goToNextPage(), book.hasPageTurnSound(), book.getBackground()));
        previousPageButton = addRenderableWidget(new ExtendedPageTurnWidget(i + 43, 159, false, (buttonWidget) -> goToPreviousPage(), book.hasPageTurnSound(), book.getBackground()));
        updatePageButtons();
    }

    protected void goToPreviousPage() {
        if (book.getPage(this.pageIndex).previousPage()) {
            if (this.pageIndex > 0) {
                --this.pageIndex;
                book.setPage(this.pageIndex, true);
            }
            this.updatePageButtons();
        }
    }

    protected void goToNextPage() {
        if (book.getPage(this.pageIndex).nextPage()) {
            if (this.pageIndex < book.getPageCount() - 1) {
                ++this.pageIndex;
                book.setPage(this.pageIndex, false);
            }
            this.updatePageButtons();
        }
    }

    private void updatePageButtons() {
        this.nextPageButton.visible = this.pageIndex < book.getPageCount() - 1;
        this.previousPageButton.visible = this.pageIndex > 0;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        switch (keyCode) {
            case 266:
                this.previousPageButton.onPress();
                return true;
            case 267:
                this.nextPageButton.onPress();
                return true;
            default:
                return false;
        }
    }

    public Font getTextRenderer() {
        return font;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // background
        int i = (width - 192) / 2;
        context.blit(book.getBackground(), i, 2, 0, 0, 192, 192);

        // page number
        if (book.showPageCount()) {
            Component pageIndexText = Component.translatable("book.pageIndicator", this.pageIndex + 1, Math.max(book.getPageCount(), 1)).withStyle(book.getTextFormatting());
            int k = font.width(pageIndexText);
            context.drawString(font, pageIndexText, i - k + 192 - 44, 18, 0, getBook().hasTextShadow());
        }

        Page page = book.getPage(pageIndex);
        if (page != null) {
            page.render(this, context, mouseX, mouseY, delta);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean handleComponentClicked(Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return false;
        }

        if (clickEvent.getAction() == ClickEvent.Action.CHANGE_PAGE) {
            try {
                return jumpToPage(Integer.parseInt(clickEvent.getValue()) - 1);
            } catch (Exception var5) {
                return false;
            }
        }

        boolean handled = super.handleComponentClicked(style);
        if (handled && clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
            minecraft.setScreen(null);
        }

        return handled;
    }

    public Book getBook() {
        return book;
    }
}
