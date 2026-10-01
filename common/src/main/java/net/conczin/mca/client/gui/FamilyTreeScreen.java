package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class FamilyTreeScreen extends Screen {
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 30;
    private static final int FIT_PADDING = 24;
    private static final int HEADER_MARGIN = 5;
    private static final int HEADER_GAP = 4;
    private static final int SEARCH_MAX_WIDTH = 180;
    private static final int SEARCH_MIN_WIDTH = 80;
    private static final int BACK_WIDTH = 44;
    private static final int DONE_WIDTH = 72;
    private static final int SEARCH_ROW_HEIGHT = 22;
    private static final int SEARCH_RESULT_LIMIT = 6;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 2.0F;

    private final Screen parent;
    private final FamilyTreeViewModel viewModel;

    private FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
            List.of(),
            List.of(),
            List.of(),
            new FamilyTreeLayout.Bounds(0, 0, 0, 0)
    );
    private FamilyTreeViewModel.ViewportState viewport =
            new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
    @Nullable
    private HitTarget hovered;
    @Nullable
    private ButtonWidget zoomLabel;
    @Nullable
    private EditBox searchField;
    private int searchX;
    private int searchWidth = SEARCH_MAX_WIDTH;
    private List<FamilyTreeSearchEntry> searchResults = List.of();
    private boolean searchOpen;
    private boolean searchPending;
    private long pendingRecenterRequestId = -1L;

    public FamilyTreeScreen(UUID entityId) {
        super(Component.translatable("gui.family_tree.title"));
        this.viewModel = new FamilyTreeViewModel(entityId);
        this.parent = Minecraft.getInstance().screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        HeaderLayout header = headerLayout(width);
        searchX = header.searchX();
        searchWidth = header.searchWidth();
        int topY = 5;
        int controlsY = 29;
        addRenderableWidget(new ButtonWidget(
                header.backX(),
                topY,
                BACK_WIDTH,
                20,
                Component.translatable("gui.family_tree.back"),
                button -> goBack()
        ));
        addRenderableWidget(new ButtonWidget(
                header.doneX(),
                topY,
                DONE_WIDTH,
                20,
                Component.translatable("gui.done"),
                button -> onClose()
        ));

        searchField = addRenderableWidget(new EditBox(
                font,
                header.searchX(),
                controlsY + 1,
                header.searchWidth(),
                18,
                Component.translatable("gui.family_tree.search")
        ));
        searchField.setMaxLength(32);
        searchField.setResponder(this::searchFamily);

        addRenderableWidget(new ButtonWidget(header.zoomOutX(), controlsY, 20, 20, Component.literal("-"), button -> setZoom(viewport.zoom() - 0.1F)));
        zoomLabel = addRenderableWidget(new ButtonWidget(header.zoomLabelX(), controlsY, 48, 20, zoomLabel(), button -> {
        }));
        zoomLabel.active = false;
        addRenderableWidget(new ButtonWidget(header.zoomInX(), controlsY, 20, 20, Component.literal("+"), button -> setZoom(viewport.zoom() + 0.1F)));
        addRenderableWidget(new ButtonWidget(header.fitX(), controlsY, 44, 20, Component.translatable("gui.family_tree.fit"), button -> {
            viewport = fitView(layout, width, canvasHeight(), FIT_PADDING);
            updateZoomLabel();
        }));
        addRenderableWidget(new ButtonWidget(header.centerX(), controlsY, 54, 20, Component.translatable("gui.family_tree.center"), button -> {
            viewport = centerView(layout, viewport);
        }));

        if (viewModel.nodes().isEmpty() && viewModel.pendingFocusId().isEmpty()) {
            requestFocus(viewModel.focusId(), true);
        } else {
            rebuildLayout();
        }
    }

    @Override
    public void onClose() {
        assert minecraft != null;
        minecraft.setScreen(parent);
    }

    public void acceptFamilyData(GetFamilyTreeResponse response) {
        FamilyTreeViewModel.MergeResult result = viewModel.accept(response);
        rebuildLayout();
        if (result == FamilyTreeViewModel.MergeResult.APPLIED
                && response.found()
                && response.requestId() == pendingRecenterRequestId) {
            viewport = focusViewportAfterResponse(layout, viewport, true);
            pendingRecenterRequestId = -1L;
        } else if (result != FamilyTreeViewModel.MergeResult.STALE
                && response.requestId() == pendingRecenterRequestId) {
            pendingRecenterRequestId = -1L;
        }
    }

    public void setSearchResults(List<FamilyTreeSearchEntry> results) {
        searchPending = false;
        searchResults = List.copyOf(results);
        searchOpen = searchField != null && integratedSearchQuery(searchField.getValue()).isPresent();
    }

    private void rebuildLayout() {
        layout = FamilyTreeLayout.layout(viewModel.focusId(), viewModel.snapshot());
    }

    private void requestFocus(UUID id, boolean recenter) {
        long requestId = viewModel.beginFocus(id, viewport);
        pendingRecenterRequestId = recenter ? requestId : -1L;
        sendFocusRequest(id, requestId);
    }

    private void sendFocusRequest(UUID id, long requestId) {
        Network.sendToServer(new GetFamilyTreeRequest(
                id,
                GetFamilyTreeRequest.DEFAULT_ANCESTOR_DEPTH,
                GetFamilyTreeRequest.DEFAULT_DESCENDANT_DEPTH,
                requestId
        ));
    }

    private void requestExpansion(ContinuationTarget target) {
        long requestId = viewModel.beginExpansion(target.anchor(), target.direction());
        int ancestors = target.direction() == FamilyTreeView.Direction.ANCESTORS ? 2 : 0;
        int descendants = target.direction() == FamilyTreeView.Direction.DESCENDANTS ? 2 : 0;
        Network.sendToServer(new GetFamilyTreeRequest(target.anchor(), ancestors, descendants, requestId));
    }

    private void goBack() {
        viewModel.back().ifPresent(entry -> {
            pendingRecenterRequestId = -1L;
            viewport = entry.viewport();
            updateZoomLabel();
            rebuildLayout();
            if (!viewModel.nodes().containsKey(entry.focusId())) {
                requestFocus(entry.focusId(), false);
            }
        });
    }

    private void searchFamily(String value) {
        Optional<String> query = integratedSearchQuery(value);
        if (query.isEmpty()) {
            searchOpen = false;
            searchPending = false;
            searchResults = List.of();
            return;
        }
        searchOpen = true;
        searchPending = true;
        searchResults = List.of();
        Network.sendToServer(new FamilyTreeUUIDLookup(query.orElseThrow()));
    }

    private void selectSearchResult(FamilyTreeSearchEntry entry) {
        long requestId = beginSearchSelection(viewModel, entry, viewport);
        pendingRecenterRequestId = requestId;
        searchOpen = false;
        searchPending = false;
        searchResults = List.of();
        if (searchField != null) {
            searchField.setValue("");
        }
        sendFocusRequest(entry.uuid(), requestId);
    }

    private void setZoom(float targetZoom) {
        viewport = zoomAround(viewport, width / 2.0, height / 2.0, width / 2.0, height / 2.0, targetZoom);
        updateZoomLabel();
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && insideCanvas(mouseX, mouseY)) {
            viewport = new FamilyTreeViewModel.ViewportState(
                    viewport.panX() + deltaX,
                    viewport.panY() + deltaY,
                    viewport.zoom()
            );
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            FamilyTreeSearchEntry searchResult = searchResultAt(mouseX, mouseY);
            if (searchResult != null) {
                selectSearchResult(searchResult);
                return true;
            }
        }
        if (button == 0 && insideCanvas(mouseX, mouseY)) {
            Optional<HitTarget> target = hitTargetAt(layout, worldX(mouseX), worldY(mouseY));
            if (target.isPresent()) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                if (target.get() instanceof PersonTarget person) {
                    if (!person.uuid().equals(viewModel.focusId())) {
                        requestFocus(person.uuid(), true);
                    }
                } else if (target.get() instanceof ContinuationTarget continuation) {
                    requestExpansion(continuation);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Nullable
    private FamilyTreeSearchEntry searchResultAt(double mouseX, double mouseY) {
        if (!searchOpen || searchPending || mouseX < searchX || mouseX >= searchX + searchWidth) {
            return null;
        }
        int top = HEADER_HEIGHT + 2;
        int index = (int) ((mouseY - top) / SEARCH_ROW_HEIGHT);
        if (mouseY < top || index < 0 || index >= Math.min(searchResults.size(), SEARCH_RESULT_LIMIT)) {
            return null;
        }
        return searchResults.get(index);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!insideCanvas(mouseX, mouseY) || scrollY == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        viewport = zoomAround(
                viewport,
                mouseX,
                mouseY,
                width / 2.0,
                canvasCenterY(),
                viewport.zoom() + (float) scrollY * 0.1F
        );
        updateZoomLabel();
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT, 0x66000000);

        hovered = insideCanvas(mouseX, mouseY)
                ? hitTargetAt(layout, worldX(mouseX), worldY(mouseY)).orElse(null)
                : null;

        context.enableScissor(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT);
        PoseStack pose = context.pose();
        pose.pushPose();
        pose.translate(width / 2.0 + viewport.panX(), canvasCenterY() + viewport.panY(), 0);
        pose.scale(viewport.zoom(), viewport.zoom(), 1.0F);

        renderEdges(context);
        renderContinuations(context);
        renderCards(context);

        pose.popPose();
        context.disableScissor();

        renderFixedChrome(context, mouseX, mouseY);
        renderSearchOverlay(context, mouseX, mouseY);
    }

    private void renderEdges(GuiGraphics context) {
        for (FamilyTreeLayout.Edge edge : layout.edges()) {
            FamilyTreeLayout.Card from = card(edge.from());
            FamilyTreeLayout.Card to = card(edge.to());
            if (from == null || to == null) {
                continue;
            }
            int x1 = from.bounds().centerX();
            int y1 = from.bounds().centerY();
            int x2 = to.bounds().centerX();
            int y2 = to.bounds().centerY();
            if (edge.type() == FamilyTreeLayout.EdgeType.PARTNER) {
                context.hLine(Math.min(x1, x2), Math.max(x1, x2), y1, 0xFFE0E0E0);
            } else {
                int midY = (y1 + y2) / 2;
                context.vLine(x1, Math.min(y1, midY), Math.max(y1, midY), 0xFFB8B8B8);
                context.hLine(Math.min(x1, x2), Math.max(x1, x2), midY, 0xFFB8B8B8);
                context.vLine(x2, Math.min(midY, y2), Math.max(midY, y2), 0xFFB8B8B8);
            }
        }
    }

    private void renderContinuations(GuiGraphics context) {
        for (FamilyTreeLayout.ContinuationControl control : layout.continuations()) {
            FamilyTreeLayout.Bounds bounds = control.bounds();
            boolean isHovered = hovered instanceof ContinuationTarget target
                    && target.anchor().equals(control.anchor())
                    && target.direction() == control.direction();
            context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), isHovered ? 0xFF6D8FB3 : 0xFF3F566D);
            String marker = control.direction() == FamilyTreeView.Direction.ANCESTORS ? "↑" : "↓";
            context.drawCenteredString(font, marker, bounds.centerX(), bounds.centerY() - font.lineHeight / 2, 0xFFFFFFFF);
        }
    }

    private void renderCards(GuiGraphics context) {
        for (FamilyTreeLayout.Card card : layout.cards()) {
            FamilyTreeNode node = viewModel.nodes().get(card.uuid());
            if (node == null) {
                continue;
            }
            FamilyTreeLayout.Bounds bounds = card.bounds();
            boolean isHovered = hovered instanceof PersonTarget target && target.uuid().equals(card.uuid());
            int background = card.role() == FamilyTreeLayout.Role.FOCUS
                    ? 0xFF334B63
                    : node.isDeceased() ? 0xFF3D3D3D : 0xFF252D35;
            if (isHovered) {
                background = 0xFF43586C;
            }

            context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), background);
            drawBorder(context, bounds, card.role() == FamilyTreeLayout.Role.FOCUS ? 0xFFFFFFFF : 0xFF9AA7B2);

            String name = nodeDisplayName(node).getString();
            int textWidth = FamilyTreeLayout.CARD_WIDTH - 12;
            if (font.width(name) > textWidth) {
                String ellipsis = "...";
                name = font.plainSubstrByWidth(name, Math.max(0, textWidth - font.width(ellipsis))) + ellipsis;
            }
            context.drawCenteredString(font, name, bounds.centerX(), bounds.top() + 7, 0xFFFFFFFF);

            String profession = node.getProfessionText().getString();
            if (font.width(profession) > textWidth) {
                profession = font.plainSubstrByWidth(profession, textWidth);
            }
            context.drawCenteredString(font, profession, bounds.centerX(), bounds.top() + 22, 0xFFBFC7CE);

            if (node.isDeceased()) {
                context.drawString(font, "†", bounds.left() + 4, bounds.top() + 4, 0xFFD9D9D9);
            }
        }
    }

    private void renderFixedChrome(GuiGraphics context, int mouseX, int mouseY) {
        FamilyTreeNode focused = viewModel.nodes().get(viewModel.focusId());
        Component focusName = focused == null ? title : nodeDisplayName(focused);
        String headerTitle = Component.translatable("gui.family_tree.formatted_title", focusName).getString();
        int titleWidth = Math.max(0, width - (DONE_WIDTH + HEADER_MARGIN + 10) * 2);
        if (font.width(headerTitle) > titleWidth) {
            headerTitle = font.plainSubstrByWidth(headerTitle, titleWidth);
        }
        context.drawCenteredString(font, headerTitle, width / 2, 10, 0xFFFFFFFF);

        Optional<Component> status = statusMessage(viewModel, searchOpen && !searchPending, searchResults);
        if (searchPending) {
            status = Optional.of(Component.translatable("gui.family_tree.loading_family"));
        }
        if (status.isPresent()) {
            context.drawCenteredString(font, status.orElseThrow(), width / 2, height - 20, 0xFFFFFFFF);
        }

        PersonTarget detailTarget = detailPerson(hovered, viewModel.focusId()).map(PersonTarget::new).orElse(null);
        if (detailTarget != null && status.isEmpty()) {
            FamilyTreeNode node = viewModel.nodes().get(detailTarget.uuid());
            if (node != null) {
                FamilyTreeRelationshipResolver.Relation relation =
                        FamilyTreeRelationshipResolver.resolve(viewModel.focusId(), detailTarget.uuid(), viewModel.nodes());
                Component detail = nodeDisplayName(node).copy()
                        .append(" · ")
                        .append(relationLabel(relation));
                context.drawCenteredString(font, detail, width / 2, height - 20, 0xFFFFFFFF);
                if (node.isDeceased() && hovered instanceof PersonTarget) {
                    context.renderTooltip(font, Component.translatable("gui.family_tree.label.deceased"), mouseX, mouseY);
                }
            }
        }

        if (hovered instanceof ContinuationTarget continuation) {
            Component label = continuation.direction() == FamilyTreeView.Direction.ANCESTORS
                    ? Component.translatable("gui.family_tree.more_ancestors")
                    : Component.translatable("gui.family_tree.more_descendants");
            context.renderTooltip(font, label, mouseX, mouseY);
        }
    }

    private void renderSearchOverlay(GuiGraphics context, int mouseX, int mouseY) {
        if (!searchOpen) {
            return;
        }

        int top = HEADER_HEIGHT + 2;
        if (searchPending) {
            context.fill(searchX, top, searchX + searchWidth, top + SEARCH_ROW_HEIGHT, 0xEE20262C);
            context.drawString(
                    font,
                    Component.translatable("gui.family_tree.loading_family"),
                    searchX + 6,
                    top + 7,
                    0xFFFFFFFF
            );
            return;
        }

        if (searchResults.isEmpty()) {
            context.fill(searchX, top, searchX + searchWidth, top + SEARCH_ROW_HEIGHT, 0xEE20262C);
            context.drawString(
                    font,
                    Component.translatable("gui.family_tree.no_records"),
                    searchX + 6,
                    top + 7,
                    0xFFFFFFFF
            );
            return;
        }

        int count = Math.min(searchResults.size(), SEARCH_RESULT_LIMIT);
        context.fill(searchX, top, searchX + searchWidth, top + count * SEARCH_ROW_HEIGHT, 0xEE20262C);
        for (int index = 0; index < count; index++) {
            int rowTop = top + index * SEARCH_ROW_HEIGHT;
            boolean rowHovered = mouseX >= searchX
                    && mouseX < searchX + searchWidth
                    && mouseY >= rowTop
                    && mouseY < rowTop + SEARCH_ROW_HEIGHT;
            if (rowHovered) {
                context.fill(searchX, rowTop, searchX + searchWidth, rowTop + SEARCH_ROW_HEIGHT, 0xFF43586C);
            }
            context.drawString(
                    font,
                    searchResultLabel(searchResults.get(index)),
                    searchX + 6,
                    rowTop + 7,
                    0xFFFFFFFF
            );
        }
    }

    private Component searchResultLabel(FamilyTreeSearchEntry entry) {
        Component name = FamilyTreeSearchPresentation.displayName(entry);
        return FamilyTreeSearchPresentation.parentLine(entry)
                .<Component>map(parent -> Component.empty().append(name).append(" · ").append(parent))
                .orElse(name);
    }

    private void drawBorder(GuiGraphics context, FamilyTreeLayout.Bounds bounds, int color) {
        context.hLine(bounds.left(), bounds.right() - 1, bounds.top(), color);
        context.hLine(bounds.left(), bounds.right() - 1, bounds.bottom() - 1, color);
        context.vLine(bounds.left(), bounds.top(), bounds.bottom() - 1, color);
        context.vLine(bounds.right() - 1, bounds.top(), bounds.bottom() - 1, color);
    }

    @Nullable
    private FamilyTreeLayout.Card card(UUID uuid) {
        return layout.cards().stream().filter(card -> card.uuid().equals(uuid)).findFirst().orElse(null);
    }

    private int worldX(double mouseX) {
        return (int) Math.floor((mouseX - width / 2.0 - viewport.panX()) / viewport.zoom());
    }

    private int worldY(double mouseY) {
        return (int) Math.floor((mouseY - canvasCenterY() - viewport.panY()) / viewport.zoom());
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < width && mouseY >= HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT;
    }

    private int canvasHeight() {
        return Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    private double canvasCenterY() {
        return HEADER_HEIGHT + canvasHeight() / 2.0;
    }

    private Component zoomLabel() {
        return zoomLabel(viewport);
    }

    static Component zoomLabel(FamilyTreeViewModel.ViewportState viewport) {
        return Component.literal(Math.round(viewport.zoom() * 100.0F) + "%");
    }

    static HeaderLayout headerLayout(int screenWidth) {
        int fixedControlsWidth = 20 + 2 + 48 + 2 + 20 + 6 + 44 + HEADER_GAP + 54;
        int searchWidth = Math.max(
                SEARCH_MIN_WIDTH,
                Math.min(SEARCH_MAX_WIDTH, screenWidth - HEADER_MARGIN * 2 - 6 - fixedControlsWidth)
        );
        int controlsWidth = searchWidth + 6 + fixedControlsWidth;
        int controlsLeft = Math.max(HEADER_MARGIN, (screenWidth - controlsWidth) / 2);
        int zoomOutX = controlsLeft + searchWidth + 6;
        int zoomLabelX = zoomOutX + 22;
        int zoomInX = zoomOutX + 72;
        int fitX = zoomOutX + 98;
        int centerX = fitX + 44 + HEADER_GAP;
        return new HeaderLayout(
                HEADER_MARGIN,
                Math.max(HEADER_MARGIN, screenWidth - HEADER_MARGIN - DONE_WIDTH),
                controlsLeft,
                searchWidth,
                zoomOutX,
                zoomLabelX,
                zoomInX,
                fitX,
                centerX,
                controlsLeft,
                centerX + 54
        );
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setMessage(zoomLabel());
        }
    }

    static Component nodeDisplayName(FamilyTreeNode node) {
        return MCA.isBlankString(node.getName())
                ? Component.translatable(node.isPlayer()
                        ? "gui.family_tree.unnamed_player"
                        : "gui.family_tree.unnamed_villager")
                : Component.literal(node.getName());
    }

    static long beginSearchSelection(
            FamilyTreeViewModel model,
            FamilyTreeSearchEntry entry,
            FamilyTreeViewModel.ViewportState viewport
    ) {
        return model.beginFocus(entry.uuid(), viewport);
    }

    static Optional<UUID> detailPerson(@Nullable HitTarget target, UUID focusId) {
        if (target instanceof PersonTarget person && !person.uuid().equals(focusId)) {
            return Optional.of(person.uuid());
        }
        return Optional.empty();
    }

    static Optional<String> integratedSearchQuery(String value) {
        if (MCA.isBlankString(value)) {
            return Optional.empty();
        }
        String query = value.trim();
        return MCA.isBlankString(query) ? Optional.empty() : Optional.of(query);
    }

    static Optional<Component> statusMessage(
            FamilyTreeViewModel model,
            boolean searchOpen,
            List<FamilyTreeSearchEntry> searchResults
    ) {
        if (model.unavailableFocusId().isPresent()) {
            return Optional.of(Component.translatable("gui.family_tree.family_record_unavailable"));
        }
        if (model.loading()) {
            return Optional.of(Component.translatable("gui.family_tree.loading_family"));
        }
        if (searchOpen && searchResults.isEmpty()) {
            return Optional.of(Component.translatable("gui.family_tree.no_records"));
        }
        return Optional.empty();
    }

    private static Component relationLabel(FamilyTreeRelationshipResolver.Relation relation) {
        return Component.translatable(switch (relation) {
            case SELF -> "gui.family_tree.relation.self";
            case FATHER -> "gui.family_tree.relation.father";
            case MOTHER -> "gui.family_tree.relation.mother";
            case CHILD -> "gui.family_tree.relation.child";
            case SIBLING -> "gui.family_tree.relation.sibling";
            case GRANDPARENT -> "gui.family_tree.relation.grandparent";
            case GRANDCHILD -> "gui.family_tree.relation.grandchild";
            case PARTNER -> "gui.family_tree.relation.partner";
            case OTHER -> "gui.family_tree.relation.other";
        });
    }

    static FamilyTreeViewModel.ViewportState zoomAround(
            FamilyTreeViewModel.ViewportState current,
            double cursorX,
            double cursorY,
            double centerX,
            double centerY,
            float targetZoom
    ) {
        float zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, targetZoom));
        double worldX = (cursorX - centerX - current.panX()) / current.zoom();
        double worldY = (cursorY - centerY - current.panY()) / current.zoom();
        return new FamilyTreeViewModel.ViewportState(
                cursorX - centerX - worldX * zoom,
                cursorY - centerY - worldY * zoom,
                zoom
        );
    }

    static FamilyTreeViewModel.ViewportState fitView(
            FamilyTreeLayout.Result result,
            int canvasWidth,
            int canvasHeight,
            int padding
    ) {
        FamilyTreeLayout.Bounds bounds = result.contentBounds();
        int contentWidth = bounds.right() - bounds.left();
        int contentHeight = bounds.bottom() - bounds.top();
        if (contentWidth <= 0 || contentHeight <= 0) {
            return new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
        }
        int availableWidth = Math.max(1, canvasWidth - padding * 2);
        int availableHeight = Math.max(1, canvasHeight - padding * 2);
        float zoom = (float) Math.min(
                availableWidth / (double) contentWidth,
                availableHeight / (double) contentHeight
        );
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
        return new FamilyTreeViewModel.ViewportState(
                -bounds.centerX() * zoom,
                -bounds.centerY() * zoom,
                zoom
        );
    }

    static FamilyTreeViewModel.ViewportState centerView(
            FamilyTreeLayout.Result result,
            FamilyTreeViewModel.ViewportState current
    ) {
        FamilyTreeLayout.Bounds bounds = result.contentBounds();
        return new FamilyTreeViewModel.ViewportState(
                -bounds.centerX() * current.zoom(),
                -bounds.centerY() * current.zoom(),
                current.zoom()
        );
    }

    static FamilyTreeViewModel.ViewportState focusViewportAfterResponse(
            FamilyTreeLayout.Result result,
            FamilyTreeViewModel.ViewportState current,
            boolean recenter
    ) {
        return recenter ? centerView(result, current) : current;
    }

    static Optional<HitTarget> hitTargetAt(FamilyTreeLayout.Result result, int worldX, int worldY) {
        for (FamilyTreeLayout.ContinuationControl continuation : result.continuations()) {
            if (continuation.bounds().contains(worldX, worldY)) {
                return Optional.of(new ContinuationTarget(continuation.anchor(), continuation.direction()));
            }
        }
        for (FamilyTreeLayout.Card card : result.cards()) {
            if (card.bounds().contains(worldX, worldY)) {
                return Optional.of(new PersonTarget(card.uuid()));
            }
        }
        return Optional.empty();
    }

    sealed interface HitTarget permits PersonTarget, ContinuationTarget {
    }

    record PersonTarget(UUID uuid) implements HitTarget {
    }

    record ContinuationTarget(UUID anchor, FamilyTreeView.Direction direction) implements HitTarget {
    }

    record HeaderLayout(
            int backX,
            int doneX,
            int searchX,
            int searchWidth,
            int zoomOutX,
            int zoomLabelX,
            int zoomInX,
            int fitX,
            int centerX,
            int controlsLeft,
            int controlsRight
    ) {
    }
}
