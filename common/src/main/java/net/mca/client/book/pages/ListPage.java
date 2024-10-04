package net.mca.client.book.pages;

import net.minecraft.text.Text;

import java.util.LinkedList;
import java.util.List;

public abstract class ListPage extends Page {
    final List<Text> text;

    int page;

    public ListPage() {
        this.text = new LinkedList<>();
    }

    public ListPage(List<Text> text) {
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
