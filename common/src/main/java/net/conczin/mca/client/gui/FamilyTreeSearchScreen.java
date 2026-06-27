package net.conczin.mca.client.gui;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.MCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.NonNull;

import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

public class FamilyTreeSearchScreen extends Screen {
    static final int DATA_WIDTH = 120;
    private static final int RESULT_ROW_HEIGHT = 20;
    private static final int RESULT_ROW_GAP = 1;
    private static final int RESULTS_PER_PAGE = 5;

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
        addRenderableWidget(new ButtonWidget(width / 2 - 44, height / 2 + 82, 88, 20, Component.translatable("gui.done"), sender -> onClose()));
        addRenderableWidget(new ButtonWidget(width / 2 - 44, height / 2 + 60, 20, 20, Component.literal("<"), b -> {
            if (pageNumber > 0) pageNumber--;
        }));
        addRenderableWidget(new ButtonWidget(width / 2 + 24, height / 2 + 60, 20, 20, Component.literal(">"), b -> {
            if (pageNumber < pageCount() - 1) pageNumber++;
        }));
        buttonPage = addRenderableWidget(new ButtonWidget(width / 2 - 24, height / 2 + 60, 48, 20, Component.literal("1/1"), b -> {}));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(context, mouseX, mouseY, partialTick);
        context.fill(width / 2 - DATA_WIDTH / 2 - 10, height / 2 - 110, width / 2 + DATA_WIDTH / 2 + 10, height / 2 + 110, 0x66000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.mouseX = mouseX;
        this.mouseY = mouseY;
        renderVillagers(context);
        context.centeredText(font, Component.translatable("gui.title.family_tree"), width / 2, height / 2 - 100, 16777215);
    }

    private void renderVillagers(GuiGraphicsExtractor context) {
        int maxPages = pageCount();
        pageNumber = Math.min(pageNumber, maxPages - 1);
        buttonPage.setMessage(Component.literal((pageNumber + 1) + "/" + maxPages));
        selectedVillager = null;
        for (int i = 0; i < RESULTS_PER_PAGE; i++) {
            int index = i + pageNumber * RESULTS_PER_PAGE;
            if (index >= list.size()) break;
            int y = height / 2 - 52 + i * (RESULT_ROW_HEIGHT + RESULT_ROW_GAP);
            boolean hover = isMouseWithin(width / 2 - DATA_WIDTH / 2, y - 1, DATA_WIDTH, RESULT_ROW_HEIGHT);
            Entry entry = list.get(index);
            if (hover) selectedVillager = entry;
            List<FormattedCharSequence> lines = font.split(entry.relationshipLabel(), DATA_WIDTH);
            int textY = y + Math.max(1, (RESULT_ROW_HEIGHT - Math.min(2, lines.size()) * font.lineHeight) / 2);
            for (int lineIndex = 0; lineIndex < Math.min(2, lines.size()); lineIndex++) {
                context.centeredText(font, lines.get(lineIndex), width / 2, textY + lineIndex * font.lineHeight, hover ? 0xFFD7D784 : 0xFFFFFFFF);
            }
        }
    }

    private void searchVillager(String v) {
        if (!MCA.isBlankString(v)) Network.sendToServer(new FamilyTreeUUIDLookup(v));
    }

    public void setList(List<Entry> list) {
        this.list = list;
        pageNumber = Math.min(pageNumber, pageCount() - 1);
    }

    protected boolean isMouseWithin(int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(@NonNull MouseButtonEvent event, boolean doubleClick) {
        if (selectedVillager != null) selectVillager(selectedVillager.name, selectedVillager.uuid);
        return super.mouseClicked(event, doubleClick);
    }

    void selectVillager(String name, UUID villager) {
        minecraft.gui.setScreen(new FamilyTreeScreen(villager));
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(list.size() / (double) RESULTS_PER_PAGE));
    }

    public record Entry(UUID uuid, String name, String father, String mother) {
        public static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                UUIDUtil.STREAM_CODEC, Entry::uuid,
                ByteBufCodecs.STRING_UTF8, Entry::name,
                ByteBufCodecs.STRING_UTF8, Entry::father,
                ByteBufCodecs.STRING_UTF8, Entry::mother,
                Entry::new
        );

        private Component relationshipLabel() {
            return Component.literal(name).append(" - ").append(childOfLabel());
        }

        private Component childOfLabel() {
            if (MCA.isBlankString(mother) && MCA.isBlankString(father)) {
                return Component.translatable("gui.family_tree.child_of_0");
            } else if (MCA.isBlankString(mother)) {
                return Component.translatable("gui.family_tree.child_of_1", father);
            } else if (MCA.isBlankString(father)) {
                return Component.translatable("gui.family_tree.child_of_1", mother);
            } else {
                return Component.translatable("gui.family_tree.child_of_2", father, mother);
            }
        }
    }
}
