package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class FamilyTreeSearchScreen extends Screen {
    static final int DATA_WIDTH = 120;

    private List<Entry> list = new LinkedList<>();
    private ButtonWidget buttonPage;
    private int pageNumber;

    private Entry selectedVillager;

    private int mouseX;
    private int mouseY;

    public FamilyTreeSearchScreen() {
        super(Component.translatable("gui.family_tree.title"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void init() {
        EditBox field = addRenderableWidget(new EditBox(this.font, width / 2 - DATA_WIDTH / 2, height / 2 - 80, DATA_WIDTH, 18, Component.translatable("structure_block.structure_name")));
        field.setMaxLength(32);
        field.setResponder(this::searchVillager);
        field.setFocused(true);
        setFocused(field);

        addRenderableWidget(new ButtonWidget(width / 2 - 44, height / 2 + 82, 88, 20, Component.translatable("gui.done"), sender -> {
            onClose();
        }));

        addRenderableWidget(new ButtonWidget(width / 2 - 24 - 20, height / 2 + 60, 20, 20, Component.literal("<"), (b) -> {
            if (pageNumber > 0) {
                pageNumber--;
            }
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 24, height / 2 + 60, 20, 20, Component.literal(">"), (b) -> {
            if (pageNumber < Math.ceil(list.size() / 9.0) - 1) {
                pageNumber++;
            }
        }));
        buttonPage = addRenderableWidget(new ButtonWidget(width / 2 - 24, height / 2 + 60, 48, 20, Component.literal("0/0)"), (b) -> {
        }));
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        assert minecraft != null;
        this.mouseX = (int)(minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getWidth());
        this.mouseY = (int)(minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getHeight());

        context.fill(width / 2 - DATA_WIDTH / 2 - 10, height / 2 - 110, width / 2 + DATA_WIDTH / 2 + 10, height / 2 + 110, 0x66000000);

        renderBackground(context);

        renderVillagers(context);

        context.drawCenteredString(font, Component.translatable("gui.title.family_tree"), width / 2, height / 2 - 100, 16777215);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderVillagers(GuiGraphics context) {
        int maxPages = (int)Math.ceil(list.size() / 9.0);
        buttonPage.setMessage(Component.literal((pageNumber + 1) + "/" + maxPages));

        selectedVillager = null;
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index < list.size()) {
                int y = height / 2 - 52 + i * 12;
                boolean hover = isMouseWithin(width / 2 - 50, y - 1, 100, 12);
                Entry entry = list.get(index);

                Component text;
                if (MCA.isBlankString(entry.mother) && MCA.isBlankString(entry.father)) {
                    text = Component.translatable("gui.family_tree.child_of_0");
                } else if (MCA.isBlankString(entry.mother)) {
                    text = Component.translatable("gui.family_tree.child_of_1", entry.father);
                } else if (MCA.isBlankString(entry.father)) {
                    text = Component.translatable("gui.family_tree.child_of_1", entry.mother);
                } else {
                    text = Component.translatable("gui.family_tree.child_of_2", entry.father, entry.mother);
                }

                context.drawCenteredString(font, text, width / 2, y, hover ? 0xFFD7D784 : 0xFFFFFFFF);
                if (hover) {
                    selectedVillager = entry;
                }
            } else {
                break;
            }
        }
    }

    private void searchVillager(String v) {
        if (!MCA.isBlankString(v)) {
            NetworkHandler.sendToServer(new FamilyTreeUUIDLookup(v));
        }
    }

    public void setList(List<Entry> list) {
        this.list = list;
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedVillager != null) {
            selectVillager(selectedVillager.name, selectedVillager.uuid);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    void selectVillager(String name, UUID villager) {
        assert minecraft != null;
        minecraft.setScreen(new FamilyTreeScreen(villager));
    }

    public record Entry(UUID uuid, String name, String father, String mother) implements Serializable {

    }
}
