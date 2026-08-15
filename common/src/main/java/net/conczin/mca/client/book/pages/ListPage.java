package net.conczin.mca.client.book.pages;

import java.util.LinkedList;
import java.util.List;
import net.minecraft.network.chat.Component;

public abstract class ListPage extends Page {
    final List<Component> text;

    int page;

    public ListPage() {
        this.text = new LinkedList<>();
    }

    public ListPage(List<Component> text) {
        this.text = text;
    }

    @Override
    public void open(boolean back) {
        page = back ? (text.size() - 1) / getEntriesPerPage() : 0;
    }

    @Override
    public boolean previousPage() {
        if (page > 0) {
            page--;
            return false;
        } else {
            return true;
        }
    }

    @Override
    public boolean nextPage() {
        if (page < (text.size() - 1) / getEntriesPerPage()) {
            page++;
            return false;
        } else {
            return true;
        }
    }

    abstract int getEntriesPerPage();
}
