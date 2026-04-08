package net.conczin.mca.client.book.pages;

import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public abstract class Page {
    public abstract void render(ExtendedBookScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);

    public void open(boolean back) {
        // N/A
    }

    public boolean previousPage() {
        return true;
    }

    public boolean nextPage() {
        return true;
    }
}
