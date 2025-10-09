package net.mca.client.book.pages;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.network.chat.Component;

public class DynamicListPage extends CenteredListPage {
    private final Function<Page, List<Component>> generator;

    public DynamicListPage(String title, Function<Page, List<Component>> generator) {
        super(title, new LinkedList<>());

        this.generator = generator;
    }

    @Override
    public void open(boolean back) {
        text.clear();
        text.addAll(generator.apply(this));

        super.open(back);
    }
}
