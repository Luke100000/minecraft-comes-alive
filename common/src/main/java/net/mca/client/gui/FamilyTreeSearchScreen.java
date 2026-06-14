package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.FamilyTreeUUIDLookup;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;

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
        super(Text.translatable("gui.family_tree.title"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void init() {
        TextFieldWidget field = addDrawableChild(new TextFieldWidget(this.textRenderer, width / 2 - DATA_WIDTH / 2, height / 2 - 80, DATA_WIDTH, 18, Text.translatable("structure_block.structure_name")));
        field.setMaxLength(32);
        field.setChangedListener(this::searchVillager);
        field.setFocused(true);
        setFocused(field);

        addDrawableChild(new ButtonWidget(width / 2 - 44, height / 2 + 82, 88, 20, Text.translatable("gui.done"), sender -> {
            close();
        }));

        addDrawableChild(new ButtonWidget(width / 2 - 24 - 20, height / 2 + 60, 20, 20, Text.literal("<"), (b) -> {
            if (pageNumber > 0) {
                pageNumber--;
            }
        }));
        addDrawableChild(new ButtonWidget(width / 2 + 24, height / 2 + 60, 20, 20, Text.literal(">"), (b) -> {
            if (pageNumber < Math.ceil(list.size() / 9.0) - 1) {
                pageNumber++;
            }
        }));
        buttonPage = addDrawableChild(new ButtonWidget(width / 2 - 24, height / 2 + 60, 48, 20, Text.literal("0/0)"), (b) -> {
        }));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        assert client != null;
        this.mouseX = (int)(client.mouse.getX() * width / client.getWindow().getFramebufferWidth());
        this.mouseY = (int)(client.mouse.getY() * height / client.getWindow().getFramebufferHeight());

        context.fill(width / 2 - DATA_WIDTH / 2 - 10, height / 2 - 110, width / 2 + DATA_WIDTH / 2 + 10, height / 2 + 110, 0x66000000);

        renderBackground(context);

        renderVillagers(context);

        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("gui.title.family_tree"), width / 2, height / 2 - 100, 16777215);

        super.render(context, mouseX, mouseY, delta);
    }

    private void renderVillagers(DrawContext context) {
        int maxPages = (int)Math.ceil(list.size() / 9.0);
        buttonPage.setMessage(Text.literal((pageNumber + 1) + "/" + maxPages));

        selectedVillager = null;
        for (int i = 0; i < 9; i++) {
            int index = i + pageNumber * 9;
            if (index < list.size()) {
                int y = height / 2 - 52 + i * 12;
                boolean hover = isMouseWithin(width / 2 - 50, y - 1, 100, 12);
                Entry entry = list.get(index);

                Text text;
                if (MCA.isBlankString(entry.mother) && MCA.isBlankString(entry.father)) {
                    text = Text.translatable("gui.family_tree.child_of_0");
                } else if (MCA.isBlankString(entry.mother)) {
                    text = Text.translatable("gui.family_tree.child_of_1", entry.father);
                } else if (MCA.isBlankString(entry.father)) {
                    text = Text.translatable("gui.family_tree.child_of_1", entry.mother);
                } else {
                    text = Text.translatable("gui.family_tree.child_of_2", entry.father, entry.mother);
                }

                context.drawCenteredTextWithShadow(textRenderer, text, width / 2, y, hover ? 0xFFD7D784 : 0xFFFFFFFF);
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
        assert client != null;
        client.setScreen(new FamilyTreeScreen(villager));
    }

    public record Entry(UUID uuid, String name, String father, String mother) implements Serializable {

    }
}
