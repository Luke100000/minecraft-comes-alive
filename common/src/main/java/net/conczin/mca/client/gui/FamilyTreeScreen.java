package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.InputConstants;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.client.resources.Icon;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.util.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public class FamilyTreeScreen extends Screen {
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 30;
    private static final int FIT_PADDING = 24;
    private static final int HEADER_MARGIN = 5;
    private static final int HEADER_GAP = 4;
    private static final int DONE_WIDTH = 72;
    private static final int ZOOM_BUTTON_WIDTH = 20;
    private static final int ZOOM_LABEL_WIDTH = 48;
    private static final int FIT_WIDTH = 44;
    private static final int CENTER_WIDTH = 54;
    private static final int CONTROL_GAP = 2;
    private static final int CONTROL_GROUP_GAP = 6;
    private static final int SEARCH_ROW_HEIGHT = 22;
    private static final int SEARCH_RESULT_LIMIT = 6;
    private static final long SEARCH_DEBOUNCE_MS = 150L;
    private static final float MIN_ZOOM = 0.25F;
    private static final float MAX_ZOOM = 2.0F;
    private static final long COPY_FEEDBACK_MS = 1_500L;
    private static final String GRAVE_ICON = "grave";
    static final String DECEASED_MARKER = "☠";
    static final int DECEASED_MARKER_COLOR = 0xFFA94A3A;
    private static final int DECEASED_MARKER_TEXT_INSET = 20;
    private static final int DECEASED_WITH_GRAVE_TEXT_INSET = 38;
    static final int PARTNER_ICON_VISIBLE_EDGE_INSET = 3;

    private final Screen parent;
    private final FamilyTreeViewModel viewModel;
    private final FamilyTreeSearchDebouncer searchDebouncer = new FamilyTreeSearchDebouncer(SEARCH_DEBOUNCE_MS);

    private FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
            List.of(),
            List.of(),
            List.of(),
            new FamilyTreeLayout.Bounds(0, 0, 0, 0)
    );
    private Map<UUID, FamilyTreeLayout.Card> cardsById = Map.of();
    private Map<UUID, CardPresentation> cardPresentations = Map.of();
    private FamilyTreeViewModel.ViewportState viewport =
            new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
    @Nullable
    private HitTarget hovered;
    @Nullable
    private ButtonWidget zoomLabel;
    @Nullable
    private EditBox searchField;
    private int searchX;
    private int searchWidth = 1;
    private List<FamilyTreeSearchEntry> searchResults = List.of();
    @Nullable
    private FamilyTreeSearchDebouncer.Request expectedSearchRequest;
    private boolean searchOpen;
    private boolean searchPending;
    private boolean canvasDragging;
    private long pendingRecenterRequestId = -1L;
    @Nullable
    private Component actionFeedback;
    private long actionFeedbackUntilMs;

    public FamilyTreeScreen(UUID entityId) {
        super(Component.translatable("gui.family_tree.title"));
        this.viewModel = new FamilyTreeViewModel(entityId);
        this.parent = Minecraft.getInstance().gui.screen();
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

        addRenderableWidget(new ButtonWidget(header.zoomOutX(), controlsY, ZOOM_BUTTON_WIDTH, 20, Component.literal("-"), button -> setZoom(viewport.zoom() - 0.1F)));
        zoomLabel = addRenderableWidget(new ButtonWidget(
                header.zoomLabelX(),
                controlsY,
                ZOOM_LABEL_WIDTH,
                20,
                zoomLabel(),
                button -> setZoom(1.0F)
        ));
        addRenderableWidget(new ButtonWidget(header.zoomInX(), controlsY, ZOOM_BUTTON_WIDTH, 20, Component.literal("+"), button -> setZoom(viewport.zoom() + 0.1F)));
        addRenderableWidget(new ButtonWidget(header.fitX(), controlsY, FIT_WIDTH, 20, Component.translatable("gui.family_tree.fit"), button -> {
            viewport = fitView(layout, width, canvasHeight(), FIT_PADDING);
            updateZoomLabel();
        }));
        addRenderableWidget(new ButtonWidget(header.centerX(), controlsY, CENTER_WIDTH, 20, Component.translatable("gui.family_tree.center"), button -> {
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
        minecraft.gui.setScreen(parent);
    }

    public void acceptFamilyData(GetFamilyTreeResponse response) {
        FamilyTreeViewModel.MergeResult result = viewModel.accept(response);
        if (result == FamilyTreeViewModel.MergeResult.STALE) {
            return;
        }
        rebuildLayout();
        if (result == FamilyTreeViewModel.MergeResult.APPLIED
                && response.found()
                && response.requestId() == pendingRecenterRequestId) {
            viewport = focusViewportAfterResponse(layout, viewport, response.uuid());
            pendingRecenterRequestId = -1L;
        } else if (response.requestId() == pendingRecenterRequestId) {
            pendingRecenterRequestId = -1L;
        }
    }

    public void setSearchResults(long requestId, String search, List<FamilyTreeSearchEntry> results) {
        if (expectedSearchRequest == null
                || !expectedSearchRequest.matches(requestId, search)
                || searchField == null
                || integratedSearchQuery(searchField.getValue()).filter(search::equals).isEmpty()) {
            return;
        }
        expectedSearchRequest = null;
        searchPending = false;
        searchResults = List.copyOf(results);
        searchOpen = true;
    }

    private void rebuildLayout() {
        FamilyTreeViewModel.Snapshot snapshot = viewModel.snapshot();
        layout = FamilyTreeLayout.layout(snapshot);
        Map<UUID, FamilyTreeLayout.Card> index = new LinkedHashMap<>();
        for (FamilyTreeLayout.Card card : layout.cards()) {
            index.put(card.uuid(), card);
        }
        cardsById = Map.copyOf(index);
        cardPresentations = cardPresentations(viewModel.focusId(), snapshot.nodes(), viewModel.orphans());
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

    private void searchFamily(String value) {
        Optional<String> query = integratedSearchQuery(value);
        if (query.isEmpty()) {
            searchDebouncer.clear();
            expectedSearchRequest = null;
            searchOpen = false;
            searchPending = false;
            searchResults = List.of();
            return;
        }
        searchOpen = true;
        searchPending = true;
        searchResults = List.of();
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

    private void selectSearchResult(FamilyTreeSearchEntry entry) {
        long requestId = viewModel.beginRootFocus(entry.uuid(), viewport);
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
        viewport = zoomAround(
                viewport,
                width / 2.0,
                canvasCenterY(),
                width / 2.0,
                canvasCenterY(),
                targetZoom
        );
        updateZoomLabel();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (canvasDragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            viewport = new FamilyTreeViewModel.ViewportState(
                    viewport.panX() + deltaX,
                    viewport.panY() + deltaY,
                    viewport.zoom()
            );
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        canvasDragging = false;
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            FamilyTreeSearchEntry searchResult = searchResultAt(mouseX, mouseY);
            if (searchResult != null) {
                selectSearchResult(searchResult);
                return true;
            }
            if (searchOverlayContains(
                    mouseX,
                    mouseY,
                    searchOpen,
                    searchPending,
                    searchX,
                    searchWidth,
                    searchResults.size()
            )) {
                return true;
            }
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && insideCanvas(mouseX, mouseY)) {
            Optional<HitTarget> target = hitTargetAt(
                    layout,
                    viewModel.nodes(),
                    viewModel.graves(),
                    worldX(mouseX),
                    worldY(mouseY)
            );
            if (target.isPresent()) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                if (target.get() instanceof GraveTarget graveTarget) {
                    Minecraft.getInstance().keyboardHandler.setClipboard(graveClipboardText(graveTarget.grave()));
                    actionFeedback = Component.translatable("gui.family_tree.grave.copied");
                    actionFeedbackUntilMs = Util.getMillis() + COPY_FEEDBACK_MS;
                } else if (target.get() instanceof PersonTarget person) {
                    if (!person.uuid().equals(viewModel.focusId())) {
                        requestFocus(person.uuid(), true);
                    }
                } else if (target.get() instanceof ContinuationTarget continuation) {
                    requestExpansion(continuation);
                }
                return true;
            }
            canvasDragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            canvasDragging = false;
        }
        return super.mouseReleased(event);
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

    static boolean searchOverlayContains(
            double mouseX,
            double mouseY,
            boolean searchOpen,
            boolean searchPending,
            int searchX,
            int searchWidth,
            int resultCount
    ) {
        if (!searchOpen || mouseX < searchX || mouseX >= searchX + searchWidth) {
            return false;
        }
        int rows = searchPending || resultCount == 0
                ? 1
                : Math.min(resultCount, SEARCH_RESULT_LIMIT);
        int top = HEADER_HEIGHT + 2;
        return mouseY >= top && mouseY < top + rows * SEARCH_ROW_HEIGHT;
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
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT, 0x66000000);

        hovered = insideCanvas(mouseX, mouseY)
                ? hitTargetAt(
                        layout,
                        viewModel.nodes(),
                        viewModel.graves(),
                        worldX(mouseX),
                        worldY(mouseY)
                ).orElse(null)
                : null;

        context.enableScissor(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT);
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate((float) (width / 2.0 + viewport.panX()), (float) (canvasCenterY() + viewport.panY()));
        pose.scale(viewport.zoom(), viewport.zoom());

        Map<UUID, FamilyTreeNode> nodes = viewModel.nodes();
        renderEdges(context, nodes);
        renderContinuations(context);
        renderCards(context, nodes);

        pose.popMatrix();
        context.disableScissor();

        renderFixedChrome(context, mouseX, mouseY, nodes);
        renderSearchOverlay(context, mouseX, mouseY);
    }

    private void renderEdges(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes) {
        // Draw child stems underneath relationship icons, including their transparent padding.
        renderEdges(context, nodes, FamilyTreeLayout.EdgeType.PARENT_CHILD);
        renderEdges(context, nodes, FamilyTreeLayout.EdgeType.PARTNER);
    }

    private void renderEdges(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes, FamilyTreeLayout.EdgeType type) {
        for (FamilyTreeLayout.Edge edge : layout.edges()) {
            if (edge.type() != type) {
                continue;
            }
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
                FamilyTreeNode fromNode = nodes.get(edge.from());
                FamilyTreeNode toNode = nodes.get(edge.to());
                boolean inferredMarriage = fromNode != null
                        && toNode != null
                        && !fromNode.partner().equals(toNode.id())
                        && !toNode.partner().equals(fromNode.id());
                Optional<RelationshipState> relationshipState = inferredMarriage
                        ? Optional.of(RelationshipState.MARRIED_TO_VILLAGER)
                        : fromNode == null || toNode == null
                        ? Optional.empty()
                        : partnerRelationshipState(
                                fromNode.getRelationshipState(),
                                toNode.getRelationshipState()
                        );
                if (relationshipState.isPresent()) {
                    FamilyTreeLayout.Bounds iconBounds = partnerIconBounds(from.bounds(), to.bounds());
                    int left = Math.min(x1, x2);
                    int right = Math.max(x1, x2);
                    context.horizontalLine(left, iconBounds.left() + PARTNER_ICON_VISIBLE_EDGE_INSET, y1, 0xFFE0E0E0);
                    context.horizontalLine(iconBounds.right() - PARTNER_ICON_VISIBLE_EDGE_INSET, right, y1, 0xFFE0E0E0);
                    Icon icon = MCAScreens.getInstance().getIcon(relationshipState.orElseThrow().getIcon());
                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            InteractScreen.ICON_TEXTURES,
                            iconBounds.left(),
                            iconBounds.top(),
                            0,
                            icon.u(),
                            icon.v(),
                            16,
                            16,
                            256,
                            256
                    );
                } else {
                    context.horizontalLine(Math.min(x1, x2), Math.max(x1, x2), y1, 0xFFE0E0E0);
                }
            } else {
                if (edge.secondParent() != null) {
                    FamilyTreeLayout.Card secondParent = card(edge.secondParent());
                    if (secondParent != null) {
                        x1 = (x1 + secondParent.bounds().centerX()) / 2;
                    }
                } else {
                    y1 = from.bounds().bottom();
                }
                y2 = to.bounds().top();
                int midY = (from.bounds().bottom() + y2) / 2;
                context.verticalLine(x1, Math.min(y1, midY), Math.max(y1, midY), 0xFFB8B8B8);
                context.horizontalLine(Math.min(x1, x2), Math.max(x1, x2), midY, 0xFFB8B8B8);
                context.verticalLine(x2, Math.min(midY, y2), Math.max(midY, y2), 0xFFB8B8B8);
            }
        }
    }

    private void renderContinuations(GuiGraphicsExtractor context) {
        for (FamilyTreeLayout.ContinuationControl control : layout.continuations()) {
            FamilyTreeLayout.Bounds bounds = control.bounds();
            boolean isHovered = hovered instanceof ContinuationTarget target
                    && target.anchor().equals(control.anchor())
                    && target.direction() == control.direction();
            context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), isHovered ? 0xFF6D8FB3 : 0xFF3F566D);
            String marker = control.direction() == FamilyTreeView.Direction.ANCESTORS ? "↑" : "↓";
            context.centeredText(font, marker, bounds.centerX(), bounds.centerY() - font.lineHeight / 2, 0xFFFFFFFF);
        }
    }

    private void renderCards(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes) {
        UUID focusId = viewModel.focusId();
        Map<UUID, GlobalPos> graves = viewModel.graves();
        for (FamilyTreeLayout.Card card : layout.cards()) {
            FamilyTreeNode node = nodes.get(card.uuid());
            if (node == null) {
                continue;
            }
            FamilyTreeLayout.Bounds bounds = card.bounds();
            boolean isHovered = hovered instanceof PersonTarget target && target.uuid().equals(card.uuid());
            boolean focused = card.uuid().equals(focusId);
            int background = focused
                    ? 0xFF334B63
                    : node.isDeceased() ? 0xFF3D3D3D : 0xFF252D35;
            if (isHovered) {
                background = 0xFF43586C;
            }

            context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), background);
            renderCardOutline(context, bounds, viewport.zoom(), focused ? 0xFFFFFFFF : 0xFF9AA7B2);

            CardPresentation presentation = cardPresentations.get(card.uuid());
            if (presentation == null) {
                continue;
            }
            int textWidth = FamilyTreeLayout.CARD_WIDTH - 12;
            int nameTextWidth = node.isDeceased()
                    ? textWidth - (graves.containsKey(card.uuid()) ? DECEASED_WITH_GRAVE_TEXT_INSET : DECEASED_MARKER_TEXT_INSET)
                    : textWidth;
            drawCenteredEllipsized(context, presentation.name(), bounds.centerX(), bounds.top() + 5, nameTextWidth, presentation.nameColor());
            drawCenteredEllipsized(context, presentation.identity(), bounds.centerX(), bounds.top() + 17, textWidth, 0xFFBFC7CE);
            drawCenteredEllipsized(context, presentation.relationship(), bounds.centerX(), bounds.top() + 29, textWidth, 0xFFD9E4EE);

            int statusY = bounds.top() + 41;
            if (presentation.relationshipState() != null) {
                drawRelationshipStatus(
                        context,
                        presentation.relationshipState(),
                        node.getRelationshipState(),
                        bounds.centerX(),
                        statusY,
                        textWidth
                );
                statusY += 10;
            }
            if (presentation.orphan()) {
                drawCenteredEllipsized(
                        context,
                        Component.translatable("gui.family_tree.label.orphan"),
                        bounds.centerX(),
                        statusY,
                        textWidth,
                        0xFFB0B0B0
                );
            }

            if (node.isDeceased()) {
                context.text(
                        font,
                        DECEASED_MARKER,
                        bounds.left() + 4,
                        bounds.top() + 4,
                        DECEASED_MARKER_COLOR
                );
                if (graves.containsKey(card.uuid())) {
                    Icon icon = MCAScreens.getInstance().getIcon(GRAVE_ICON);
                    FamilyTreeLayout.Bounds graveBounds = graveIconBounds(bounds);
                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            InteractScreen.ICON_TEXTURES,
                            graveBounds.left(),
                            graveBounds.top(),
                            0,
                            icon.u(),
                            icon.v(),
                            16,
                            16,
                            256,
                            256
                    );
                }
            }
        }
    }

    private static void renderCardOutline(
            GuiGraphicsExtractor context,
            FamilyTreeLayout.Bounds bounds,
            float zoom,
            int color
    ) {
        float thickness = cardOutlineWorldThickness(zoom);
        float width = bounds.right() - bounds.left();
        float height = bounds.bottom() - bounds.top();
        renderOutlineQuad(context, bounds.left(), bounds.top(), width, thickness, color);
        renderOutlineQuad(context, bounds.left(), bounds.bottom() - thickness, width, thickness, color);
        renderOutlineQuad(context, bounds.left(), bounds.top() + thickness, thickness, height - thickness * 2.0F, color);
        renderOutlineQuad(context, bounds.right() - thickness, bounds.top() + thickness, thickness, height - thickness * 2.0F, color);
    }

    private static void renderOutlineQuad(
            GuiGraphicsExtractor context,
            float x,
            float y,
            float width,
            float height,
            int color
    ) {
        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(width, height);
        context.fill(0, 0, 1, 1, color);
        pose.popMatrix();
    }

    static float cardOutlineWorldThickness(float zoom) {
        return Math.max(1.0F, 1.0F / zoom);
    }

    private void drawRelationshipStatus(
            GuiGraphicsExtractor context,
            Component text,
            RelationshipState state,
            int centerX,
            int y,
            int maxWidth
    ) {
        int iconSize = 8;
        int iconGap = 3;
        String label = ellipsizedText(text, maxWidth - iconSize - iconGap);
        int left = centerX - (iconSize + iconGap + font.width(label)) / 2;
        Icon icon = MCAScreens.getInstance().getIcon(state.getIcon());
        context.pose().pushMatrix();
        context.pose().translate(left, y);
        context.pose().scale(0.5F, 0.5F);
        context.blit(RenderPipelines.GUI_TEXTURED, InteractScreen.ICON_TEXTURES, 0, 0, icon.u(), icon.v(), 16, 16, 256, 256);
        context.pose().popMatrix();
        context.text(font, label, left + iconSize + iconGap, y, 0xFFD8C98C);
    }

    private void drawCenteredEllipsized(
            GuiGraphicsExtractor context,
            Component text,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        context.centeredText(font, ellipsizedText(text, maxWidth), centerX, y, color);
    }

    private String ellipsizedText(Component text, int maxWidth) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            String ellipsis = "...";
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        return value;
    }

    private void renderFixedChrome(
            GuiGraphicsExtractor context,
            int mouseX,
            int mouseY,
            Map<UUID, FamilyTreeNode> nodes
    ) {
        UUID focusId = viewModel.focusId();
        FamilyTreeNode focused = nodes.get(focusId);
        Component focusName = focused == null ? title : nodeDisplayName(focused);
        String headerTitle = Component.translatable("gui.family_tree.formatted_title", focusName).getString();
        int titleWidth = Math.max(0, width - (DONE_WIDTH + HEADER_MARGIN + 10) * 2);
        if (font.width(headerTitle) > titleWidth) {
            headerTitle = font.plainSubstrByWidth(headerTitle, titleWidth);
        }
        context.centeredText(font, headerTitle, width / 2, 10, 0xFFFFFFFF);

        Optional<Component> status = statusMessage(viewModel, searchOpen && !searchPending, searchResults);
        if (searchPending) {
            status = Optional.of(Component.translatable("gui.family_tree.loading_family"));
        }
        if (actionFeedback != null) {
            if (Util.getMillis() < actionFeedbackUntilMs) {
                status = Optional.of(actionFeedback);
            } else {
                actionFeedback = null;
            }
        }
        if (status.isPresent()) {
            context.centeredText(font, status.orElseThrow(), width / 2, height - 20, 0xFFFFFFFF);
        }

        Optional<RelationshipDetail> relationshipDetail = relationshipDetail(
                hovered instanceof PersonTarget person ? person : null,
                viewModel
        );
        if (relationshipDetail.isPresent() && status.isEmpty()) {
            RelationshipDetail detailTarget = relationshipDetail.orElseThrow();
            FamilyTreeNode node = nodes.get(detailTarget.uuid());
            if (node != null) {
                Component detail = nodeDisplayName(node).copy()
                        .append(" · ")
                        .append(relationLabel(detailTarget.relation()));
                context.centeredText(font, detail, width / 2, height - 20, 0xFFFFFFFF);
            }
        }

        if (hovered instanceof GraveTarget graveTarget) {
            GlobalPos grave = graveTarget.grave();
            WidgetUtils.drawTooltip(
                    context,
                    font,
                    Component.translatable(
                            "gui.family_tree.grave.tooltip",
                            graveDimensionLabel(grave.dimension()),
                            grave.pos().getX(),
                            grave.pos().getY(),
                            grave.pos().getZ()
                    ),
                    mouseX,
                    mouseY
            );
        } else if (hovered instanceof ContinuationTarget continuation) {
            Component label = continuation.direction() == FamilyTreeView.Direction.ANCESTORS
                    ? Component.translatable("gui.family_tree.more_ancestors")
                    : Component.translatable("gui.family_tree.more_descendants");
            WidgetUtils.drawTooltip(context, font, label, mouseX, mouseY);
        }
    }

    private void renderSearchOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!searchOpen) {
            return;
        }

        int top = HEADER_HEIGHT + 2;
        if (searchPending) {
            context.fill(searchX, top, searchX + searchWidth, top + SEARCH_ROW_HEIGHT, 0xEE20262C);
            context.text(
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
            context.text(
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
            context.text(
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

    @Nullable
    private FamilyTreeLayout.Card card(UUID uuid) {
        return cardsById.get(uuid);
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
        int fixedControlsWidth = ZOOM_BUTTON_WIDTH
                + CONTROL_GAP
                + ZOOM_LABEL_WIDTH
                + CONTROL_GAP
                + ZOOM_BUTTON_WIDTH
                + CONTROL_GROUP_GAP
                + FIT_WIDTH
                + HEADER_GAP
                + CENTER_WIDTH;
        int controlsLeft = Math.max(HEADER_MARGIN, (screenWidth - fixedControlsWidth) / 2);
        int searchWidth = Math.max(1, controlsLeft - HEADER_MARGIN - CONTROL_GROUP_GAP);
        int zoomOutX = controlsLeft;
        int zoomLabelX = zoomOutX + ZOOM_BUTTON_WIDTH + CONTROL_GAP;
        int zoomInX = zoomLabelX + ZOOM_LABEL_WIDTH + CONTROL_GAP;
        int fitX = zoomInX + ZOOM_BUTTON_WIDTH + CONTROL_GROUP_GAP;
        int centerX = fitX + FIT_WIDTH + HEADER_GAP;
        return new HeaderLayout(
                Math.max(HEADER_MARGIN, screenWidth - HEADER_MARGIN - DONE_WIDTH),
                HEADER_MARGIN,
                searchWidth,
                zoomOutX,
                zoomLabelX,
                zoomInX,
                fitX,
                centerX
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

    static FamilyTreeLayout.Bounds partnerIconBounds(
            FamilyTreeLayout.Bounds from,
            FamilyTreeLayout.Bounds to
    ) {
        int centerX = (from.centerX() + to.centerX()) / 2;
        int centerY = (from.centerY() + to.centerY()) / 2;
        return new FamilyTreeLayout.Bounds(centerX - 8, centerX + 8, centerY - 8, centerY + 8);
    }

    static Optional<RelationshipState> partnerRelationshipState(RelationshipState from, RelationshipState to) {
        if (from == RelationshipState.MARRIED_TO_PLAYER || to == RelationshipState.MARRIED_TO_PLAYER) {
            return Optional.of(RelationshipState.MARRIED_TO_PLAYER);
        }
        if (from != RelationshipState.SINGLE) {
            return Optional.of(from);
        }
        if (to != RelationshipState.SINGLE) {
            return Optional.of(to);
        }
        return Optional.empty();
    }

    static FamilyTreeLayout.Bounds graveIconBounds(FamilyTreeLayout.Bounds cardBounds) {
        int left = cardBounds.left() + 14;
        int top = cardBounds.top() + 1;
        return new FamilyTreeLayout.Bounds(left, left + 16, top, top + 16);
    }

    static String graveClipboardText(GlobalPos grave) {
        return grave.pos().getX() + " " + grave.pos().getY() + " " + grave.pos().getZ();
    }

    static Component graveDimensionLabel(ResourceKey<Level> dimension) {
        Identifier id = dimension.identifier();
        return Component.translatableWithFallback(
                "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.'),
                id.toString()
        );
    }

    static CardPresentation cardPresentation(
            UUID relationshipFocusId,
            FamilyTreeNode node,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> orphans
    ) {
        Component identity = node.isPlayer()
                ? Component.translatable("gui.family_tree.player")
                : node.getProfessionText();
        FamilyTreeRelationshipResolver.Relation relation =
                FamilyTreeRelationshipResolver.resolve(relationshipFocusId, node.id(), nodes);
        Component relationshipState = node.getRelationshipState() == RelationshipState.SINGLE
                ? null
                : Component.translatable("marriage." + node.getRelationshipState().base().getIcon());
        return new CardPresentation(
                nodeDisplayName(node),
                0xFF000000 | node.gender().getColor(),
                identity,
                relationLabel(relation),
                relationshipState,
                orphans.contains(node.id())
        );
    }

    static Map<UUID, CardPresentation> cardPresentations(
            UUID relationshipFocusId,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> orphans
    ) {
        Map<UUID, CardPresentation> presentations = new LinkedHashMap<>(nodes.size());
        nodes.forEach((id, node) -> presentations.put(
                id,
                cardPresentation(relationshipFocusId, node, nodes, orphans)
        ));
        return Map.copyOf(presentations);
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

    static Optional<RelationshipDetail> relationshipDetail(
            @Nullable PersonTarget hoveredPerson,
            FamilyTreeViewModel model
    ) {
        UUID focusId = model.focusId();
        Map<UUID, FamilyTreeNode> nodes = model.nodes();
        if (hoveredPerson != null && !hoveredPerson.uuid().equals(focusId)) {
            return Optional.of(new RelationshipDetail(
                    hoveredPerson.uuid(),
                    FamilyTreeRelationshipResolver.resolve(focusId, hoveredPerson.uuid(), nodes)
            ));
        }
        return model.previousFocusId()
                .filter(previousFocusId -> !previousFocusId.equals(focusId))
                .map(previousFocusId -> new RelationshipDetail(
                        focusId,
                        FamilyTreeRelationshipResolver.resolve(previousFocusId, focusId, nodes)
                ));
    }

    private static Component relationLabel(FamilyTreeRelationshipResolver.Relation relation) {
        return Component.translatable(switch (relation) {
            case SELF -> "gui.family_tree.relation.selected";
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
            UUID focusId
    ) {
        return result.cards().stream()
                .filter(card -> card.uuid().equals(focusId))
                .findFirst()
                .map(card -> new FamilyTreeViewModel.ViewportState(
                        -card.bounds().centerX() * current.zoom(),
                        -card.bounds().centerY() * current.zoom(),
                        current.zoom()
                ))
                .orElse(current);
    }

    static Optional<HitTarget> hitTargetAt(FamilyTreeLayout.Result result, int worldX, int worldY) {
        return hitTargetAt(result, Map.of(), Map.of(), worldX, worldY);
    }

    static Optional<HitTarget> hitTargetAt(
            FamilyTreeLayout.Result result,
            Map<UUID, FamilyTreeNode> nodes,
            Map<UUID, GlobalPos> graves,
            int worldX,
            int worldY
    ) {
        for (FamilyTreeLayout.Card card : result.cards()) {
            FamilyTreeNode node = nodes.get(card.uuid());
            GlobalPos grave = graves.get(card.uuid());
            if (node != null
                    && node.isDeceased()
                    && grave != null
                    && graveIconBounds(card.bounds()).contains(worldX, worldY)) {
                return Optional.of(new GraveTarget(card.uuid(), grave));
            }
        }
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

    sealed interface HitTarget permits PersonTarget, GraveTarget, ContinuationTarget {
    }

    record PersonTarget(UUID uuid) implements HitTarget {
    }

    record GraveTarget(UUID uuid, GlobalPos grave) implements HitTarget {
    }

    record RelationshipDetail(UUID uuid, FamilyTreeRelationshipResolver.Relation relation) {
    }

    record CardPresentation(
            Component name,
            int nameColor,
            Component identity,
            Component relationship,
            @Nullable Component relationshipState,
            boolean orphan
    ) {
    }

    record ContinuationTarget(UUID anchor, FamilyTreeView.Direction direction) implements HitTarget {
    }

    record HeaderLayout(
            int doneX,
            int searchX,
            int searchWidth,
            int zoomOutX,
            int zoomLabelX,
            int zoomInX,
            int fitX,
            int centerX
    ) {
    }
}
