package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FamilyTreeSearchScreen extends Screen {
    static final int DATA_WIDTH = 120;
    private static final int RESULT_ROW_HEIGHT = 20;
    private static final int RESULT_ROW_GAP = 1;
    private static final int RESULTS_PER_PAGE = 5;
    private static final long SEARCH_DEBOUNCE_MS = 150L;

    private List<FamilyTreeSearchEntry> list = List.of();
    private final FamilyTreeSearchDebouncer searchDebouncer = new FamilyTreeSearchDebouncer(SEARCH_DEBOUNCE_MS);
    private ButtonWidget buttonPage;
    private int pageNumber;
    private FamilyTreeSearchDebouncer.Request expectedSearchRequest;

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
            if (pageNumber < pageCount() - 1) {
                pageNumber++;
            }
        }));
        buttonPage = addRenderableWidget(new ButtonWidget(width / 2 - 24, height / 2 + 60, 48, 20, Component.literal("1/1"), (b) -> {
        }));

        searchVillager("");
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);

        context.fill(width / 2 - DATA_WIDTH / 2 - 10, height / 2 - 110, width / 2 + DATA_WIDTH / 2 + 10, height / 2 + 110, 0x66000000);

        renderVillagers(context, mouseX, mouseY);

        context.drawCenteredString(font, Component.translatable("gui.title.family_tree"), width / 2, height / 2 - 100, 16777215);

        for (GuiEventListener child : children()) {
            if (child instanceof Renderable renderable) {
                renderable.render(context, mouseX, mouseY, delta);
            }
        }
    }

    private void renderVillagers(GuiGraphics context, int mouseX, int mouseY) {
        int maxPages = pageCount();
        pageNumber = Math.min(pageNumber, maxPages - 1);
        buttonPage.setMessage(Component.literal((pageNumber + 1) + "/" + maxPages));

        for (int i = 0; i < RESULTS_PER_PAGE; i++) {
            int index = i + pageNumber * RESULTS_PER_PAGE;
            if (index < list.size()) {
                int y = height / 2 - 52 + i * (RESULT_ROW_HEIGHT + RESULT_ROW_GAP);
                boolean hover = isMouseWithin(mouseX, mouseY, width / 2 - DATA_WIDTH / 2, y - 1, DATA_WIDTH, RESULT_ROW_HEIGHT);
                FamilyTreeSearchEntry entry = list.get(index);

                List<FormattedCharSequence> lines = font.split(relationshipLabel(entry), DATA_WIDTH);
                int textY = y + Math.max(1, (RESULT_ROW_HEIGHT - Math.min(2, lines.size()) * font.lineHeight) / 2);
                for (int lineIndex = 0; lineIndex < Math.min(2, lines.size()); lineIndex++) {
                    context.drawCenteredString(font, lines.get(lineIndex), width / 2, textY + lineIndex * font.lineHeight, hover ? 0xFFD7D784 : 0xFFFFFFFF);
                }
            } else {
                break;
            }
        }
    }

    private void searchVillager(String value) {
        String playerName = minecraft.player == null ? "" : minecraft.player.getName().getString();
        Optional<String> query = standaloneSearchQuery(value, playerName);
        if (query.isEmpty()) {
            searchDebouncer.clear();
            expectedSearchRequest = null;
            list = List.of();
            pageNumber = 0;
            return;
        }
        list = List.of();
        pageNumber = 0;
        expectedSearchRequest = searchDebouncer.schedule(query.orElseThrow(), Util.getMillis());
    }

    @Override
    public void tick() {
        super.tick();
        searchDebouncer.poll(Util.getMillis())
                .ifPresent(request -> Network.sendToServer(new FamilyTreeUUIDLookup(
                        request.requestId(),
                        request.query()
                )));
    }

    static Optional<String> standaloneSearchQuery(String value, String playerName) {
        String search = value == null ? "" : value.trim();
        if (MCA.isBlankString(search)) {
            search = playerName == null ? "" : playerName.trim();
        }
        return MCA.isBlankString(search) ? Optional.empty() : Optional.of(search);
    }

    public void setList(long requestId, String search, List<FamilyTreeSearchEntry> list) {
        if (expectedSearchRequest == null || !expectedSearchRequest.matches(requestId, search)) {
            return;
        }
        expectedSearchRequest = null;
        this.list = List.copyOf(list);
        pageNumber = Math.min(pageNumber, pageCount() - 1);
    }

    private static boolean isMouseWithin(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            FamilyTreeSearchEntry entry = searchResultAt(mouseX, mouseY);
            if (entry != null) {
                selectVillager(entry.name(), entry.uuid());
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private FamilyTreeSearchEntry searchResultAt(double mouseX, double mouseY) {
        int left = width / 2 - DATA_WIDTH / 2;
        for (int i = 0; i < RESULTS_PER_PAGE; i++) {
            int index = i + pageNumber * RESULTS_PER_PAGE;
            if (index >= list.size()) {
                break;
            }
            int y = height / 2 - 52 + i * (RESULT_ROW_HEIGHT + RESULT_ROW_GAP) - 1;
            if (isMouseWithin(mouseX, mouseY, left, y, DATA_WIDTH, RESULT_ROW_HEIGHT)) {
                return list.get(index);
            }
        }
        return null;
    }

    void selectVillager(String name, UUID villager) {
        minecraft.setScreen(new FamilyTreeScreen(villager));
    }

    private int pageCount() {
        return Math.max(1, (int) Math.ceil(list.size() / (double) RESULTS_PER_PAGE));
    }

    private Component relationshipLabel(FamilyTreeSearchEntry entry) {
        Component name = FamilyTreeSearchPresentation.displayName(entry);
        return FamilyTreeSearchPresentation.parentLine(entry)
                .<Component>map(parent -> Component.empty().append(name).append(" - ").append(parent))
                .orElse(name);
    }
}
