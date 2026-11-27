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
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
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

import java.util.*;
import java.util.function.LongSupplier;

public class FamilyTreeScreen extends Screen {
    private static final int HEADER_HEIGHT = 54;
    private static final int FOOTER_HEIGHT = 30;
    private static final int FIT_PADDING = 24;
    private static final int HEADER_MARGIN = 5;
    private static final int HEADER_GAP = 4;
    private static final int DONE_WIDTH = 72;
    private static final int BACK_WIDTH = 54;
    private static final int ZOOM_BUTTON_WIDTH = 20;
    private static final int ZOOM_LABEL_WIDTH = 48;
    private static final int FIT_WIDTH = 44;
    private static final int CENTER_WIDTH = 54;
    private static final int MIN_STANDARD_SEARCH_WIDTH = 48;
    private static final int MIN_COMPACT_SEARCH_WIDTH = 24;
    private static final int CONTROL_GAP = 2;
    private static final int CONTROL_GROUP_GAP = 6;
    private static final int SEARCH_ROW_HORIZONTAL_PADDING = 12;
    private static final int SEARCH_ROW_HEIGHT = 22;
    private static final int SEARCH_RESULT_LIMIT = 6;
    private static final long SEARCH_DEBOUNCE_MS = 150L;
    static final long CLIENT_REQUEST_PACE_MS = 150L;
    static final long REQUEST_TIMEOUT_MS = 5_000L;
    private static final float MIN_ZOOM = 0.01F;
    private static final float MAX_ZOOM = 2.0F;
    private static final long GENERATION_TRANSITION_MS = 420L;
    private static final long COPY_FEEDBACK_MS = 1_500L;
    private static final int GENERATION_BUTTON_SIZE = 24;
    private static final int MAX_BRANCH_TAB_HEIGHT = 6;
    private static final int MIN_BRANCH_TAB_HEIGHT = 3;
    private static final int CARD_TEXT_WIDTH = FamilyTreeLayout.CARD_WIDTH - 12;
    private static final int RELATIONSHIP_STATUS_ICON_SIZE = 8;
    private static final int RELATIONSHIP_STATUS_ICON_GAP = 3;
    private static final int CARD_BACKGROUND = 0xFF252D35;
    private static final int CARD_HOVER_BACKGROUND = 0xFF43586C;
    private static final int CARD_BORDER = 0xFF9AA7B2;
    private static final int[] FAMILY_CONNECTOR_COLORS = {
            0xFFB8B8B8, 0xFF94B8D0, 0xFFD0BE99, 0xFFBBA9CE
    };
    private static final String GRAVE_ICON = "grave";
    static final String DECEASED_MARKER = "☠";
    static final int DECEASED_MARKER_COLOR = 0xFFA94A3A;
    private static final int DECEASED_MARKER_TEXT_INSET = 20;
    private static final int DECEASED_WITH_GRAVE_TEXT_INSET = 38;
    static final int PARTNER_ICON_VISIBLE_EDGE_INSET = 3;

    private final Screen parent;
    private final FamilyTreeViewModel viewModel;
    private final FamilyTreeSearchDebouncer searchDebouncer = new FamilyTreeSearchDebouncer(SEARCH_DEBOUNCE_MS);

    private FamilyTreeTransition transition;
    private long transitionStartedMs;
    private FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
            List.of(),
            List.of(),
            new FamilyTreeLayout.Bounds(0, 0, 0, 0)
    );
    private Map<UUID, FamilyTreeLayout.Card> cardsById = Map.of();
    @Nullable
    private GenerationAvailability cachedGenerationAvailability;
    private Map<UUID, CardPresentation> cardPresentations = Map.of();
    private FamilyTreeViewModel.ViewportState viewport =
            new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
    @Nullable
    private HitTarget hovered;
    @Nullable
    private ButtonWidget zoomLabel;
    @Nullable
    private ButtonWidget backButton;
    @Nullable
    private EditBox searchField;
    private int searchX;
    private int searchWidth = 1;
    private List<FamilyTreeSearchEntry> searchResults = List.of();
    @Nullable
    private FamilyTreeSearchDebouncer.Request expectedSearchRequest;
    private long activeSearchDeadlineMs;
    private boolean searchOpen;
    private boolean searchPending;
    private boolean canvasDragging;
    private long pendingRecenterRequestId = -1L;
    @Nullable
    private LongSupplier clock;
    private boolean hasSentTreeRequest;
    private long lastTreeRequestSentAtMs;
    @Nullable
    private GetFamilyTreeRequest queuedTreeRequest;
    private long activeTreeRequestDeadlineMs;
    @Nullable
    private Component actionFeedback;
    private long actionFeedbackUntilMs;

    public FamilyTreeScreen(UUID entityId) {
        super(Component.translatable("gui.family_tree.title"));
        this.viewModel = new FamilyTreeViewModel(entityId);
        Minecraft client = Minecraft.getInstance();
        this.parent = client == null ? null : client.gui.screen();
    }

    void setClockForTest(LongSupplier clock) {
        this.clock = clock;
    }

    private long nowMs() {
        return clock != null ? clock.getAsLong() : Util.getMillis();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void repositionElements() {
        finishTransition();
        boolean searchWasFocused = searchField != null && searchField.isFocused();
        super.repositionElements();
        if (searchWasFocused && searchField != null) {
            setFocused(searchField);
            searchField.setFocused(true);
        }
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
                header.doneWidth(),
                20,
                Component.translatable("gui.done"),
                button -> onClose()
        ));
        backButton = addRenderableWidget(new ButtonWidget(
                HEADER_MARGIN, topY, BACK_WIDTH, 20, Component.translatable("gui.back"),
                button -> goBack()
        ));
        updateBackButton();

        if (searchField == null) {
            searchField = new EditBox(
                    font,
                    header.searchX(),
                    controlsY + 1,
                    header.searchWidth(),
                    18,
                    Component.translatable("gui.family_tree.search")
            );
            searchField.setMaxLength(32);
            searchField.setResponder(this::searchFamily);
        } else {
            searchField.setX(header.searchX());
            searchField.setY(controlsY + 1);
            if (searchField.getWidth() != header.searchWidth()) {
                searchField.setWidth(header.searchWidth());
                if (searchField.getHighlighted().isEmpty()) {
                    searchField.setCursorPosition(searchField.getCursorPosition());
                }
            }
        }
        addRenderableWidget(searchField);

        addRenderableWidget(new ButtonWidget(
                header.zoomOutX(),
                controlsY,
                header.zoomButtonWidth(),
                20,
                Component.literal("-"),
                button -> setZoom(viewport.zoom() / 1.2F)
        ));
        zoomLabel = addRenderableWidget(new ButtonWidget(
                header.zoomLabelX(),
                controlsY,
                header.zoomLabelWidth(),
                20,
                zoomLabel(),
                button -> setZoom(1.0F)
        ));
        addRenderableWidget(new ButtonWidget(
                header.zoomInX(),
                controlsY,
                header.zoomButtonWidth(),
                20,
                Component.literal("+"),
                button -> setZoom(viewport.zoom() * 1.2F)
        ));
        addRenderableWidget(new ButtonWidget(
                header.fitX(),
                controlsY,
                header.fitWidth(),
                20,
                Component.translatable("gui.family_tree.fit"),
                button -> {
                    finishTransition();
                    viewport = fitView(layout, width, canvasHeight(), FIT_PADDING);
                    updateZoomLabel();
                }
        ));
        addRenderableWidget(new ButtonWidget(
                header.centerX(),
                controlsY,
                header.centerWidth(),
                20,
                Component.translatable("gui.family_tree.center"),
                button -> {
                    finishTransition();
                    viewport = centerView(layout, viewport);
                }
        ));

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
        FamilyTreeViewModel.GenerationResult generation = viewModel.acceptGeneration(response);
        if (generation.handled()) {
            clearCompletedTreeRequestTracking();
            invalidateGenerationAvailability();
            if (generation.layoutChanged()) {
                FamilyTreeLayout.Result previous = displayedLayout();
                transition = null;
                rebuildLayout();
                if (generation.anchor() != null && !previous.cards().isEmpty()) {
                    animateLayout(previous, generation.anchor());
                }
            } else if (response.found()) {
                refreshCardPresentations();
            }
            return;
        }
        FamilyTreeViewModel.MergeResult result = viewModel.accept(response);
        if (result == FamilyTreeViewModel.MergeResult.STALE) {
            return;
        }
        clearCompletedTreeRequestTracking();
        invalidateGenerationAvailability();
        updateBackButton();
        if (result == FamilyTreeViewModel.MergeResult.NOT_FOUND) {
            if (response.requestId() == pendingRecenterRequestId) {
                pendingRecenterRequestId = -1L;
            }
            return;
        }
        boolean recenter = response.requestId() == pendingRecenterRequestId;
        FamilyTreeLayout.Result previous = displayedLayout();
        transition = null;
        rebuildLayout();
        if (recenter) {
            if (response.found()) {
                viewport = focusViewportAfterResponse(layout, viewport, response.uuid());
            }
            pendingRecenterRequestId = -1L;
        } else if (!previous.cards().isEmpty()) {
            animateLayout(previous, viewModel.focusId());
        }
    }

    private void animateLayout(FamilyTreeLayout.Result previous, UUID anchor) {
        transition = new FamilyTreeTransition(previous, layout, anchor, viewport.zoom());
        transitionStartedMs = nowMs();
    }

    private FamilyTreeLayout.Result displayedLayout() {
        if (transition == null) return layout;
        long elapsed = Math.max(0, nowMs() - transitionStartedMs);
        if (elapsed >= GENERATION_TRANSITION_MS) {
            finishTransition();
            return layout;
        }
        return transition.at(elapsed / (double) GENERATION_TRANSITION_MS);
    }

    private void finishTransition() {
        if (transition != null) {
            viewport = transition.settledViewport(viewport);
            transition = null;
        }
    }

    private void clearCompletedTreeRequestTracking() {
        if (queuedTreeRequest != null && !viewModel.isPendingRequest(queuedTreeRequest.requestId())) {
            queuedTreeRequest = null;
        }
        if (!viewModel.loading()) {
            activeTreeRequestDeadlineMs = 0L;
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
        activeSearchDeadlineMs = 0L;
        searchPending = false;
        searchResults = List.copyOf(results);
        searchOpen = true;
    }

    private void rebuildLayout() {
        layout = FamilyTreeLayout.layout(viewModel.snapshot());
        refreshLayoutIndex();
    }

    private void refreshLayoutIndex() {
        Map<UUID, FamilyTreeLayout.Card> index = new LinkedHashMap<>();
        for (FamilyTreeLayout.Card card : layout.cards()) {
            index.put(card.uuid(), card);
        }
        cardsById = Map.copyOf(index);
        invalidateGenerationAvailability();
        refreshCardPresentations();
    }

    private void refreshCardPresentations() {
        cardPresentations = cardPresentations(
                font,
                viewModel.focusId(),
                viewModel.nodes(),
                viewModel.orphans(),
                viewModel.graves()
        );
    }

    private void requestFocus(UUID id, boolean recenter) {
        finishTransition();
        long requestId = viewModel.beginFocus(id, viewport);
        invalidateGenerationAvailability();
        pendingRecenterRequestId = recenter ? requestId : -1L;
        sendFocusRequest(id, requestId);
        updateBackButton();
    }

    boolean selectPerson(UUID id) {
        if (!cardsById.containsKey(id) || !viewModel.selectVisible(id, viewport)) {
            return false;
        }
        pendingRecenterRequestId = -1L;
        clearCompletedTreeRequestTracking();
        invalidateGenerationAvailability();
        refreshCardPresentations();
        updateBackButton();
        return true;
    }

    boolean goBack() {
        finishTransition();
        UUID currentRoot = viewModel.layoutRootId();
        Optional<FamilyTreeViewModel.HistoryEntry> previous = viewModel.back();
        if (previous.isEmpty()) {
            return false;
        }
        clearCompletedTreeRequestTracking();
        invalidateGenerationAvailability();
        viewport = previous.orElseThrow().viewport();
        pendingRecenterRequestId = -1L;
        if (!viewModel.layoutRootId().equals(currentRoot)) {
            FamilyTreeLayout.Result saved = previous.orElseThrow().layout();
            if (saved != null) {
                layout = saved;
                refreshLayoutIndex();
            } else {
                rebuildLayout();
            }
        } else {
            // A selection-only Back does not need a new graph layout.
            refreshLayoutIndex();
        }
        updateZoomLabel();
        updateBackButton();
        return true;
    }

    long startRootFocus(UUID id) {
        finishTransition();
        long requestId = viewModel.beginRootFocus(id, viewport, layout);
        invalidateGenerationAvailability();
        updateBackButton();
        return requestId;
    }

    private void updateBackButton() {
        if (backButton != null) {
            backButton.active = viewModel.canGoBack();
        }
    }

    private void sendFocusRequest(UUID id, long requestId) {
        dispatchOrQueueTreeRequest(new GetFamilyTreeRequest(
                id,
                GetFamilyTreeRequest.DEFAULT_ANCESTOR_DEPTH,
                GetFamilyTreeRequest.DEFAULT_DESCENDANT_DEPTH,
                requestId
        ));
    }

    private void requestGeneration(FamilyTreeView.Direction direction) {
        requestGeneration(direction, generationCandidates(direction));
    }

    private void requestGeneration(FamilyTreeView.Direction direction, Set<UUID> candidates) {
        viewModel.beginGeneration(direction, candidates).ifPresent(this::sendGenerationRequest);
    }

    private void requestBranch(BranchTarget target) {
        requestGeneration(target.direction(), Set.of(target.uuid()));
    }

    Optional<FamilyTreeLayout.Bounds> branchControlBounds(UUID anchor, FamilyTreeView.Direction direction) {
        return branchControls(generationAvailability(), displayedLayout(), framebufferScale()).stream()
                .filter(control -> control.target().equals(new BranchTarget(anchor, direction)))
                .map(BranchControl::bounds).findFirst();
    }

    private List<BranchControl> branchControls(GenerationAvailability availability, FamilyTreeLayout.Result displayed,
                                               float scale) {
        float zoom = viewport.zoom();
        double rowGap = FamilyTreeLayout.GENERATION_GAP * (double) zoom;
        // An up tab and a down tab can share the same gap. Leave a pixel between them.
        int tabHeight = Math.min(MAX_BRANCH_TAB_HEIGHT, ((int) Math.floor(rowGap) - 1) / 2);
        int tabHeightPixels = Math.round(tabHeight * scale);
        if (tabHeight < MIN_BRANCH_TAB_HEIGHT) {
            int gapPixels = (int) Math.floor(rowGap * scale);
            tabHeightPixels = Math.min(Math.round(MIN_BRANCH_TAB_HEIGHT * scale), (gapPixels - 1) / 2);
        }
        if (tabHeightPixels < Math.max(3, (int) Math.ceil(2 * scale)) || availability.branches().isEmpty()) {
            return List.of();
        }
        int tabWidth = tabHeightPixels + Math.round(2 * scale);
        List<BranchControl> controls = new ArrayList<>();
        for (FamilyTreeLayout.Card card : displayed.cards()) {
            Set<FamilyTreeView.Direction> directions = availability.branches().get(card.uuid());
            if (directions == null) {
                continue;
            }
            FamilyTreeLayout.Bounds cardPixels = cardFramebufferBounds(card, scale);
            int left = cardPixels.centerX() - tabWidth / 2;
            for (FamilyTreeView.Direction direction : directions) {
                boolean upwards = direction == FamilyTreeView.Direction.ANCESTORS;
                int top = upwards ? cardPixels.top() - tabHeightPixels : cardPixels.bottom();
                controls.add(new BranchControl(new BranchTarget(card.uuid(), direction),
                        new FamilyTreeLayout.Bounds(left, left + tabWidth, top, top + tabHeightPixels)));
            }
        }
        return controls;
    }

    Optional<BranchTarget> branchControlAt(double mouseX, double mouseY) {
        FamilyTreeLayout.Result displayed = displayedLayout();
        float scale = framebufferScale();
        return branchControlAt(mouseX, mouseY, branchControls(generationAvailability(), displayed, scale), displayed, scale);
    }

    private Optional<BranchTarget> branchControlAt(double mouseX, double mouseY, List<BranchControl> controls,
                                                  FamilyTreeLayout.Result displayed, float scale) {
        if (!insideCanvas(mouseX, mouseY)) {
            return Optional.empty();
        }
        int x = (int) Math.floor(mouseX * scale);
        int y = (int) Math.floor(mouseY * scale);
        for (int i = controls.size() - 1; i >= 0; i--) {
            BranchControl control = controls.get(i);
            if (control.bounds().contains(x, y)) {
                for (FamilyTreeLayout.Card card : displayed.cards()) {
                    if (cardFramebufferBounds(card, scale).contains(x, y)) {
                        return Optional.empty();
                    }
                }
                return Optional.of(control.target());
            }
        }
        return Optional.empty();
    }

    private void sendGenerationRequest(FamilyTreeViewModel.GenerationRequest request) {
        int ancestors = request.direction() == FamilyTreeView.Direction.ANCESTORS ? 1 : 0;
        int descendants = request.direction() == FamilyTreeView.Direction.DESCENDANTS ? 1 : 0;
        dispatchOrQueueTreeRequest(new GetFamilyTreeRequest(request.anchor(), ancestors, descendants,
                request.requestId(), true, request.after()));
    }

    private void dispatchOrQueueTreeRequest(GetFamilyTreeRequest request) {
        long now = nowMs();
        if (!hasSentTreeRequest || now - lastTreeRequestSentAtMs >= CLIENT_REQUEST_PACE_MS) {
            queuedTreeRequest = null;
            hasSentTreeRequest = true;
            lastTreeRequestSentAtMs = now;
            activeTreeRequestDeadlineMs = now + REQUEST_TIMEOUT_MS;
            Network.sendToServer(request);
        } else {
            queuedTreeRequest = request;
            activeTreeRequestDeadlineMs = 0L;
        }
    }

    private void searchFamily(String value) {
        Optional<String> query = integratedSearchQuery(value);
        if (query.isEmpty()) {
            searchDebouncer.clear();
            activeSearchDeadlineMs = 0L;
            expectedSearchRequest = null;
            searchOpen = false;
            searchPending = false;
            searchResults = List.of();
            return;
        }
        activeSearchDeadlineMs = 0L;
        searchOpen = true;
        searchPending = true;
        searchResults = List.of();
        expectedSearchRequest = searchDebouncer.schedule(query.orElseThrow(), nowMs());
    }

    private void dismissSearchPopup() {
        searchDebouncer.clear();
        activeSearchDeadlineMs = 0L;
        expectedSearchRequest = null;
        searchOpen = false;
        searchPending = false;
        searchResults = List.of();
        if (searchField != null && searchField.isFocused()) {
            searchField.setFocused(false);
        }
        if (getFocused() == searchField) {
            setFocused(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = nowMs();
        searchDebouncer.poll(now).ifPresent(request -> {
            if (request.equals(expectedSearchRequest)) {
                activeSearchDeadlineMs = now + REQUEST_TIMEOUT_MS;
                Network.sendToServer(new FamilyTreeUUIDLookup(request.requestId(), request.query()));
            }
        });
        if (queuedTreeRequest != null) {
            if (!viewModel.isPendingRequest(queuedTreeRequest.requestId())) {
                queuedTreeRequest = null;
            } else if (!hasSentTreeRequest || now - lastTreeRequestSentAtMs >= CLIENT_REQUEST_PACE_MS) {
                GetFamilyTreeRequest toSend = queuedTreeRequest;
                queuedTreeRequest = null;
                hasSentTreeRequest = true;
                lastTreeRequestSentAtMs = now;
                activeTreeRequestDeadlineMs = now + REQUEST_TIMEOUT_MS;
                Network.sendToServer(toSend);
            }
        }
        if (viewModel.loading()
                && queuedTreeRequest == null
                && activeTreeRequestDeadlineMs > 0L
                && now >= activeTreeRequestDeadlineMs) {
            activeTreeRequestDeadlineMs = 0L;
            pendingRecenterRequestId = -1L;
            if (viewModel.expirePendingRequests()) {
                invalidateGenerationAvailability();
                updateBackButton();
            }
        }
        if (searchPending
                && activeSearchDeadlineMs > 0L
                && now >= activeSearchDeadlineMs) {
            expectedSearchRequest = null;
            searchPending = false;
            activeSearchDeadlineMs = 0L;
        }
    }

    void selectSearchResult(FamilyTreeSearchEntry entry) {
        boolean alreadyVisible = cardsById.containsKey(entry.uuid());
        if (alreadyVisible) {
            selectPerson(entry.uuid());
            centerIfOffscreen(entry.uuid());
        } else {
            long requestId = startRootFocus(entry.uuid());
            pendingRecenterRequestId = requestId;
            sendFocusRequest(entry.uuid(), requestId);
        }
        dismissSearchPopup();
        if (searchField != null) {
            searchField.setValue("");
        }
    }

    private void centerIfOffscreen(UUID id) {
        layout.cards().stream()
                .filter(card -> card.uuid().equals(id))
                .findFirst()
                .filter(card -> !worldBoundsOnCanvas(card.bounds()))
                .ifPresent(card -> viewport = focusViewportAfterResponse(layout, viewport, id));
    }

    private boolean worldBoundsOnCanvas(FamilyTreeLayout.Bounds bounds) {
        double originX = width / 2.0 + viewport.panX();
        double originY = canvasCenterY() + viewport.panY();
        double zoom = viewport.zoom();
        return overlapsCanvas(originX + bounds.left() * zoom, originY + bounds.top() * zoom,
                originX + bounds.right() * zoom, originY + bounds.bottom() * zoom);
    }

    private boolean overlapsCanvas(double left, double top, double right, double bottom) {
        return right > 0 && left < width
                && bottom > HEADER_HEIGHT && top < height - FOOTER_HEIGHT;
    }

    private void setZoom(float targetZoom) {
        finishTransition();
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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE && searchOpen) {
            dismissSearchPopup();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (canvasDragging && event.button() == 0) {
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
        if (searchOpen) {
            if (button == 0) {
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
                        searchOverlayWidth(),
                        searchResults.size()
                )) {
                    return true;
                }
            }
            boolean clickedSearchField = searchField != null && searchField.isMouseOver(mouseX, mouseY);
            if (!clickedSearchField) {
                dismissSearchPopup();
                if (insideCanvas(mouseX, mouseY)) {
                    return true;
                }
                super.mouseClicked(event, doubleClick);
                return true;
            }
        }
        if (button == 0 && insideCanvas(mouseX, mouseY)) {
            GenerationAvailability availability = generationAvailability();
            Optional<FamilyTreeView.Direction> generation = generationControlAt(
                    mouseX, mouseY, width, height, availability.global());
            if (generation.isPresent()) {
                if (!viewModel.loading()) {
                    requestGeneration(generation.orElseThrow());
                }
                return true;
            }
            FamilyTreeLayout.Result displayed = displayedLayout();
            float scale = framebufferScale();
            Optional<BranchTarget> branch = branchControlAt(mouseX, mouseY,
                    branchControls(availability, displayed, scale), displayed, scale);
            if (branch.isPresent()) {
                if (!viewModel.loading()) {
                    requestBranch(branch.orElseThrow());
                }
                return true;
            }
            Optional<HitTarget> target = hitTargetAt(
                    displayed,
                    viewModel.nodes(),
                    viewModel.graves(),
                    worldX(mouseX),
                    worldY(mouseY)
            );
            if (target.isPresent()) {
                Minecraft client = Minecraft.getInstance();
                if (client != null && client.getSoundManager() != null) {
                    client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                }
                if (target.get() instanceof GraveTarget graveTarget) {
                    if (client != null && client.keyboardHandler != null) {
                        client.keyboardHandler.setClipboard(graveClipboardText(graveTarget.grave()));
                    }
                    actionFeedback = Component.translatable("gui.family_tree.grave.copied");
                    actionFeedbackUntilMs = nowMs() + COPY_FEEDBACK_MS;
                } else if (target.get() instanceof PersonTarget person) {
                    // Selection is local; only the generation controls fetch more relatives.
                    selectPerson(person.uuid());
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
        if (event.button() == 0) {
            canvasDragging = false;
        }
        return super.mouseReleased(event);
    }

    @Nullable
    private FamilyTreeSearchEntry searchResultAt(double mouseX, double mouseY) {
        if (!searchOpen || searchPending || mouseX < searchX || mouseX >= searchX + searchOverlayWidth()) {
            return null;
        }
        int top = HEADER_HEIGHT + 2;
        int index = (int) ((mouseY - top) / SEARCH_ROW_HEIGHT);
        if (mouseY < top || index < 0 || index >= Math.min(searchResults.size(), SEARCH_RESULT_LIMIT)) {
            return null;
        }
        return searchResults.get(index);
    }

    private int searchOverlayWidth() {
        if (searchPending || searchResults.isEmpty()) {
            if (font == null) {
                return searchWidth;
            }
            Component status = Component.translatable(
                    searchPending ? "gui.family_tree.loading_family" : "gui.family_tree.no_records"
            );
            int desired = Math.max(searchWidth, font.width(status) + SEARCH_ROW_HORIZONTAL_PADDING);
            return Math.max(1, Math.min(width - searchX - HEADER_MARGIN, desired));
        }
        int widest = 0;
        int count = Math.min(searchResults.size(), SEARCH_RESULT_LIMIT);
        for (int index = 0; index < count; index++) {
            widest = Math.max(widest, font.width(searchResultLabel(searchResults.get(index))));
        }
        return Math.max(1, Math.min(width - searchX - HEADER_MARGIN, widest + SEARCH_ROW_HORIZONTAL_PADDING));
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
        finishTransition();
        viewport = zoomAround(
                viewport,
                mouseX,
                mouseY,
                width / 2.0,
                canvasCenterY(),
                viewport.zoom() * (float) Math.pow(1.2, scrollY)
        );
        updateZoomLabel();
        return true;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        context.fill(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT, 0x66000000);

        FamilyTreeLayout.Result displayed = displayedLayout();
        Map<UUID, FamilyTreeLayout.Card> displayedCards = cardsById;
        if (displayed != layout) {
            displayedCards = new HashMap<>();
            for (FamilyTreeLayout.Card card : displayed.cards()) displayedCards.put(card.uuid(), card);
        }
        GenerationAvailability availability = generationAvailability();
        float guiScale = framebufferScale();
        List<BranchControl> branches = branchControls(availability, displayed, guiScale);

        if (searchOpen) {
            hovered = null;
        } else {
            Optional<FamilyTreeView.Direction> generationHover = generationControlAt(
                    mouseX, mouseY, width, height, availability.global());
            Optional<BranchTarget> branchHover = branchControlAt(mouseX, mouseY, branches, displayed, guiScale);
            hovered = generationHover.isPresent() ? new GenerationTarget(generationHover.orElseThrow())
                    : branchHover.isPresent() ? branchHover.orElseThrow()
                    : insideCanvas(mouseX, mouseY) ? hitTargetAt(
                            displayed,
                            viewModel.nodes(),
                            viewModel.graves(),
                            worldX(mouseX),
                            worldY(mouseY)
                    ).orElse(null)
                    : null;
        }

        context.enableScissor(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT);
        Matrix3x2fStack pose = context.pose();
        Map<UUID, FamilyTreeNode> nodes = viewModel.nodes();

        pose.pushMatrix();
        pose.scale(1 / guiScale, 1 / guiScale);
        renderEdgeStrokes(context, nodes, displayed, displayedCards, guiScale);
        renderBranchControls(context, branches, guiScale);
        renderCardFrames(context, nodes, displayed, guiScale);
        pose.popMatrix();

        pose.pushMatrix();
        pose.translate((float) (width / 2.0 + viewport.panX()), (float) (canvasCenterY() + viewport.panY()));
        pose.scale(viewport.zoom(), viewport.zoom());
        renderPartnerIcons(context, nodes, displayed, displayedCards);
        renderCards(context, nodes, displayed);
        pose.popMatrix();

        context.disableScissor();
        nextGuiStratum(context);

        renderGenerationControls(context, availability.global());
        renderFixedChrome(context, mouseX, mouseY, nodes);
        if (searchOpen) {
            nextGuiStratum(context);
            renderSearchOverlay(context, mouseX, mouseY);
        }
    }

    private static void nextGuiStratum(GuiGraphicsExtractor context) {
        context.nextStratum();
    }

    private void renderEdgeStrokes(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes,
                                   FamilyTreeLayout.Result displayed, Map<UUID, FamilyTreeLayout.Card> displayedCards, float scale) {
        for (FamilyTreeLayout.Edge edge : displayed.edges()) {
            FamilyTreeLayout.Card from = displayedCards.get(edge.from());
            FamilyTreeLayout.Card to = displayedCards.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            FamilyTreeLayout.Bounds fromBounds = from.bounds();
            FamilyTreeLayout.Bounds toBounds = to.bounds();
            int x1 = fromBounds.centerX();
            int y1 = fromBounds.centerY();
            int x2 = toBounds.centerX();
            if (edge.type() == FamilyTreeLayout.EdgeType.PARTNER) {
                if (edgeRelationshipState(edge, nodes).isPresent()) {
                    FamilyTreeLayout.Bounds iconBounds = partnerIconBounds(from.bounds(), to.bounds());
                    int left = Math.min(x1, x2);
                    int right = Math.max(x1, x2);
                    drawGuiHorizontal(context, left, iconBounds.left() + PARTNER_ICON_VISIBLE_EDGE_INSET,
                            y1, 0xFFE0E0E0, scale);
                    drawGuiHorizontal(context, iconBounds.right() - PARTNER_ICON_VISIBLE_EDGE_INSET,
                            right, y1, 0xFFE0E0E0, scale);
                } else {
                    drawGuiHorizontal(context, x1, x2, y1, 0xFFE0E0E0, scale);
                }
            }
        }
        for (FamilyTreeLayout.FamilyConnector connector : displayed.connectors()) {
            int color = FAMILY_CONNECTOR_COLORS[connector.track() % FAMILY_CONNECTOR_COLORS.length];
            FamilyTreeLayout.Bounds parent = displayedCards.get(connector.parent()).bounds();
            FamilyTreeLayout.Card secondParent = connector.secondParent() == null
                    ? null : displayedCards.get(connector.secondParent());
            int x = secondParent == null ? parent.centerX()
                    : (parent.centerX() + secondParent.bounds().centerX()) / 2;
            int y = secondParent == null ? parent.bottom() : parent.centerY();
            FamilyTreeLayout.Bounds firstChild = displayedCards.get(connector.children().getFirst()).bounds();
            int railY = connector.railY(parent, firstChild);
            int left = x;
            int right = x;
            drawGuiVertical(context, x, y, railY, color, scale);
            for (UUID child : connector.children()) {
                FamilyTreeLayout.Bounds childBounds = displayedCards.get(child).bounds();
                left = Math.min(left, childBounds.centerX());
                right = Math.max(right, childBounds.centerX());
                drawGuiVertical(context, childBounds.centerX(), railY, childBounds.top(), color, scale);
            }
            drawGuiHorizontal(context, left, right, railY, color, scale);
        }
    }

    private void renderPartnerIcons(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes,
                                    FamilyTreeLayout.Result displayed,
                                    Map<UUID, FamilyTreeLayout.Card> displayedCards) {
        for (FamilyTreeLayout.Edge edge : displayed.edges()) {
            if (edge.type() != FamilyTreeLayout.EdgeType.PARTNER) {
                continue;
            }
            FamilyTreeLayout.Card from = displayedCards.get(edge.from());
            FamilyTreeLayout.Card to = displayedCards.get(edge.to());
            if (from == null || to == null) {
                continue;
            }
            FamilyTreeLayout.Bounds iconBounds = partnerIconBounds(from.bounds(), to.bounds());
            if (!worldBoundsOnCanvas(iconBounds)) {
                continue;
            }
            Optional<RelationshipState> state = edgeRelationshipState(edge, nodes);
            if (state.isEmpty()) {
                continue;
            }
            Icon icon = MCAScreens.getInstance().getIcon(state.orElseThrow().getIcon());
            context.blit(RenderPipelines.GUI_TEXTURED, InteractScreen.ICON_TEXTURES, iconBounds.left(), iconBounds.top(),
                    icon.u(), icon.v(), 16, 16, 256, 256);
        }
    }

    private static Optional<RelationshipState> edgeRelationshipState(
            FamilyTreeLayout.Edge edge, Map<UUID, FamilyTreeNode> nodes) {
        FamilyTreeNode first = nodes.get(edge.from());
        FamilyTreeNode second = nodes.get(edge.to());
        if (first == null || second == null) {
            return Optional.empty();
        }
        if (!first.partner().equals(second.id()) && !second.partner().equals(first.id())) {
            return Optional.of(RelationshipState.MARRIED_TO_VILLAGER);
        }
        return partnerRelationshipState(first.getRelationshipState(), second.getRelationshipState());
    }

    private float framebufferScale() {
        return minecraft == null || minecraft.getWindow() == null ? 1 : (float) minecraft.getWindow().getGuiScale();
    }

    private void drawGuiHorizontal(GuiGraphicsExtractor context, int x1, int x2, int y, int color, float scale) {
        FamilyTreeLayout.Bounds stroke = guiHorizontalStroke(x1, x2, y,
                (width / 2.0 + viewport.panX()) * scale, (canvasCenterY() + viewport.panY()) * scale,
                viewport.zoom() * scale);
        if (framebufferBoundsOnCanvas(stroke, scale)) drawGuiFill(context, stroke, color);
    }

    private void drawGuiVertical(GuiGraphicsExtractor context, int x, int y1, int y2, int color, float scale) {
        FamilyTreeLayout.Bounds stroke = guiVerticalStroke(x, y1, y2,
                (width / 2.0 + viewport.panX()) * scale, (canvasCenterY() + viewport.panY()) * scale,
                viewport.zoom() * scale);
        if (framebufferBoundsOnCanvas(stroke, scale)) drawGuiFill(context, stroke, color);
    }

    private boolean framebufferBoundsOnCanvas(FamilyTreeLayout.Bounds bounds, float scale) {
        return overlapsCanvas(bounds.left() / scale, bounds.top() / scale,
                bounds.right() / scale, bounds.bottom() / scale);
    }

    private void renderCardFrames(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes,
                                  FamilyTreeLayout.Result displayed, float scale) {
        for (FamilyTreeLayout.Card card : displayed.cards()) {
            FamilyTreeNode node = nodes.get(card.uuid());
            if (node == null || !worldBoundsOnCanvas(card.bounds())) continue;
            FamilyTreeLayout.Bounds bounds = cardFramebufferBounds(card, scale);
            boolean focused = card.uuid().equals(viewModel.focusId());
            int border = focused ? 0xFFFFFFFF : CARD_BORDER;
            int background = hovered instanceof PersonTarget target && target.uuid().equals(card.uuid())
                    ? CARD_HOVER_BACKGROUND
                    : focused ? 0xFF334B63 : node.isDeceased() ? 0xFF3D3D3D : CARD_BACKGROUND;
            drawGuiFill(context, bounds, border);
            if (bounds.right() - bounds.left() > 2 && bounds.bottom() - bounds.top() > 2) {
                context.fill(bounds.left() + 1, bounds.top() + 1, bounds.right() - 1, bounds.bottom() - 1, background);
            }
        }
    }

    private FamilyTreeLayout.Bounds cardFramebufferBounds(FamilyTreeLayout.Card card, float scale) {
        return guiBounds(card.bounds(), (width / 2.0 + viewport.panX()) * scale,
                (canvasCenterY() + viewport.panY()) * scale, viewport.zoom() * scale);
    }

    private static void drawGuiFill(GuiGraphicsExtractor context, FamilyTreeLayout.Bounds bounds, int color) {
        context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), color);
    }

    private void renderGenerationControls(GuiGraphicsExtractor context, Set<FamilyTreeView.Direction> available) {
        for (FamilyTreeView.Direction direction : available) {
            FamilyTreeLayout.Bounds bounds = generationControlBounds(direction, width, height);
            boolean isHovered = this.hovered instanceof GenerationTarget target && target.direction() == direction;
            int color = viewModel.loading() ? 0xFF35434F : isHovered ? 0xFF6D8FB3 : 0xFF3F566D;
            context.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), color);
            context.outline(bounds.left(), bounds.top(), GENERATION_BUTTON_SIZE,
                    GENERATION_BUTTON_SIZE, 0xFF9AA7B2);
            context.centeredText(font, direction == FamilyTreeView.Direction.ANCESTORS ? "↑" : "↓",
                    bounds.centerX(), bounds.centerY() - font.lineHeight / 2, 0xFFFFFFFF);
        }
    }

    private void renderBranchControls(GuiGraphicsExtractor context, List<BranchControl> controls, float scale) {
        boolean active = !viewModel.loading();
        for (BranchControl control : controls) {
            FamilyTreeLayout.Bounds tab = control.bounds();
            if (!framebufferBoundsOnCanvas(tab, scale)) {
                continue;
            }
            boolean upwards = control.target().direction() == FamilyTreeView.Direction.ANCESTORS;
            boolean highlighted = active && control.target().equals(hovered);
            drawGuiFill(context, tab, highlighted ? CARD_HOVER_BACKGROUND : CARD_BACKGROUND);
            context.fill(tab.left(), tab.top(), tab.left() + 1, tab.bottom(), CARD_BORDER);
            context.fill(tab.right() - 1, tab.top(), tab.right(), tab.bottom(), CARD_BORDER);
            int outerY = upwards ? tab.top() : tab.bottom() - 1;
            context.fill(tab.left(), outerY, tab.right(), outerY + 1, CARD_BORDER);

            int unit = Math.max(1, (tab.bottom() - tab.top()) / 7);
            int x = tab.centerX() - unit / 2;
            int y = Math.max(tab.top() + (upwards ? 1 : 0), tab.centerY() - unit);
            int wingsY = upwards ? y + unit : y;
            int tipY = upwards ? y : y + unit;
            int arrowColor = highlighted ? 0xFFFFFFFF : active ? 0xFFD9E4EE : CARD_BORDER;
            context.fill(x - unit, wingsY, x, wingsY + unit, arrowColor);
            context.fill(x + unit, wingsY, x + 2 * unit, wingsY + unit, arrowColor);
            context.fill(x, tipY, x + unit, tipY + unit, arrowColor);
        }
    }

    private Set<FamilyTreeView.Direction> availableGenerationDirections() {
        return generationAvailability().global();
    }

    private record GenerationAvailability(Set<FamilyTreeView.Direction> global,
                                          Map<UUID, Set<FamilyTreeView.Direction>> branches) {
    }

    private record BranchControl(BranchTarget target, FamilyTreeLayout.Bounds bounds) {
    }

    private void invalidateGenerationAvailability() {
        cachedGenerationAvailability = null;
    }

    private GenerationAvailability generationAvailability() {
        if (cachedGenerationAvailability != null) {
            return cachedGenerationAvailability;
        }
        EnumSet<FamilyTreeView.Direction> global = EnumSet.noneOf(FamilyTreeView.Direction.class);
        Map<UUID, Set<FamilyTreeView.Direction>> branches = new HashMap<>();
        Set<FamilyTreeView.Continuation> continuations = viewModel.snapshot().continuations();
        for (FamilyTreeView.Direction direction : FamilyTreeView.Direction.values()) {
            Set<UUID> candidates = generationCandidates(direction);
            if (viewModel.hasGenerationFrontier(direction, candidates)) {
                global.add(direction);
            }
            for (FamilyTreeView.Continuation continuation : continuations) {
                UUID anchor = continuation.anchor();
                if (continuation.direction() == direction && cardsById.containsKey(anchor)
                        && !candidates.contains(anchor)) {
                    branches.computeIfAbsent(anchor, ignored -> EnumSet.noneOf(FamilyTreeView.Direction.class))
                            .add(direction);
                }
            }
        }
        cachedGenerationAvailability = new GenerationAvailability(global, Map.copyOf(branches));
        return cachedGenerationAvailability;
    }

    private Set<UUID> generationCandidates(FamilyTreeView.Direction direction) {
        FamilyTreeLayout.Card root = cardsById.get(viewModel.layoutRootId());
        if (root == null) {
            return Set.of();
        }
        Set<UUID> eligible = new HashSet<>();
        Deque<UUID> pending = new ArrayDeque<>();
        pending.add(root.uuid());
        while (!pending.isEmpty()) {
            UUID id = pending.removeFirst();
            if (!cardsById.containsKey(id) || !eligible.add(id)) {
                continue;
            }
            FamilyTreeNode node = viewModel.nodes().get(id);
            if (node != null) {
                if (direction == FamilyTreeView.Direction.ANCESTORS) {
                    node.streamParents().filter(cardsById::containsKey).forEach(pending::addLast);
                } else {
                    node.streamChildren().filter(cardsById::containsKey).forEach(pending::addLast);
                }
            }
        }
        return eligible;
    }

    private void renderCards(GuiGraphicsExtractor context, Map<UUID, FamilyTreeNode> nodes,
                             FamilyTreeLayout.Result displayed) {
        Map<UUID, GlobalPos> graves = viewModel.graves();
        int textInset = 5;
        for (FamilyTreeLayout.Card card : displayed.cards()) {
            FamilyTreeLayout.Bounds bounds = card.bounds();
            if (!worldBoundsOnCanvas(bounds)) {
                continue;
            }
            FamilyTreeNode node = nodes.get(card.uuid());
            if (node == null) {
                continue;
            }
            CardPresentation presentation = cardPresentations.get(card.uuid());
            if (presentation == null) {
                continue;
            }
            int textWidth = bounds.right() - bounds.left() - 12;
            int nameTextWidth = nameLineWidth(node, graves.containsKey(card.uuid()), textWidth);
            FormattedCardText formatted = presentation.formatted() != null
                    ? presentation.formatted()
                    : formatCardText(font, presentation, textWidth, nameTextWidth);

            int lineStep = font.lineHeight;
            int textY = bounds.top() + textInset;
            for (String nameLine : formatted.nameLines()) {
                drawCenteredLine(context, nameLine, bounds.centerX(), textY, presentation.nameColor());
                textY += lineStep;
            }
            drawCenteredLine(context, formatted.identityLine(), bounds.centerX(), textY, 0xFFBFC7CE);
            textY += lineStep;
            drawCenteredLine(context, formatted.relationshipLine(), bounds.centerX(), textY, 0xFFD9E4EE);
            textY += lineStep;
            if (formatted.relationshipStateLine() != null) {
                drawRelationshipStatus(context, formatted.relationshipStateLine(), node.getRelationshipState(),
                        bounds.centerX(), textY);
                textY += lineStep;
            }
            if (formatted.orphanLine() != null) {
                drawCenteredLine(context, formatted.orphanLine(), bounds.centerX(), textY, 0xFFB0B0B0);
            }

            if (node.isDeceased()) {
                context.text(font, DECEASED_MARKER, bounds.left() + 4, bounds.top() + 4,
                        DECEASED_MARKER_COLOR);
                if (graves.containsKey(card.uuid())) {
                    Icon icon = MCAScreens.getInstance().getIcon(GRAVE_ICON);
                    FamilyTreeLayout.Bounds graveBounds = graveIconBounds(bounds);
                    context.blit(
                            RenderPipelines.GUI_TEXTURED,
                            InteractScreen.ICON_TEXTURES,
                            graveBounds.left(),
                            graveBounds.top(),
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

    private void drawRelationshipStatus(
            GuiGraphicsExtractor context,
            String label,
            RelationshipState state,
            int centerX,
            int y
    ) {
        double left = centerX - (RELATIONSHIP_STATUS_ICON_SIZE + RELATIONSHIP_STATUS_ICON_GAP + font.width(label)) / 2.0;
        Icon icon = MCAScreens.getInstance().getIcon(state.getIcon());
        context.pose().pushMatrix();
        context.pose().translate((float) left, y);
        context.pose().scale(RELATIONSHIP_STATUS_ICON_SIZE / 16.0F, RELATIONSHIP_STATUS_ICON_SIZE / 16.0F);
        context.blit(RenderPipelines.GUI_TEXTURED, InteractScreen.ICON_TEXTURES, 0, 0, icon.u(), icon.v(), 16, 16, 256, 256);
        context.pose().popMatrix();
        context.text(font, label, (int) Math.round(left + RELATIONSHIP_STATUS_ICON_SIZE + RELATIONSHIP_STATUS_ICON_GAP), y, 0xFFD8C98C);
    }

    private void drawCenteredLine(GuiGraphicsExtractor context, String label, int centerX, int y, int color) {
        drawPixelAlignedText(context, label, centerX - font.width(label) / 2.0, y, color);
    }

    private void drawPixelAlignedText(GuiGraphicsExtractor context, String text, double x, double y, int color) {
        double guiScale = framebufferScale();
        double zoom = viewport.zoom();
        double screenX = width / 2.0 + viewport.panX() + x * zoom;
        double screenY = canvasCenterY() + viewport.panY() + y * zoom;

        Matrix3x2fStack pose = context.pose();
        pose.pushMatrix();
        pose.translate(
                (float) (x + (Math.round(screenX * guiScale) / guiScale - screenX) / zoom),
                (float) (y + (Math.round(screenY * guiScale) / guiScale - screenY) / zoom)
        );
        context.text(font, text, 0, 0, color);
        pose.popMatrix();
    }

    private static int nameLineWidth(FamilyTreeNode node, boolean hasGrave, int textWidth) {
        return node.isDeceased()
                ? textWidth - (hasGrave ? DECEASED_WITH_GRAVE_TEXT_INSET : DECEASED_MARKER_TEXT_INSET)
                : textWidth;
    }

    static FormattedCardText formatCardText(
            Font font,
            CardPresentation presentation,
            int textWidth,
            int nameTextWidth
    ) {
        List<String> nameLines = wrapName(font, presentation.name(), nameTextWidth);
        String identityLine = ellipsizedText(font, presentation.identity(), textWidth);
        String relationshipLine = ellipsizedText(font, presentation.relationship(), textWidth);
        String relationshipStateLine = presentation.relationshipState() == null
                ? null
                : ellipsizedText(
                        font,
                        presentation.relationshipState(),
                        Math.max(0, textWidth - RELATIONSHIP_STATUS_ICON_SIZE - RELATIONSHIP_STATUS_ICON_GAP)
                );
        String orphanLine = presentation.orphan()
                ? ellipsizedText(font, Component.translatable("gui.family_tree.label.orphan"), textWidth)
                : null;
        return new FormattedCardText(nameLines, identityLine, relationshipLine, relationshipStateLine, orphanLine);
    }

    private static String ellipsizedText(Font font, Component text, int maxWidth) {
        String value = text.getString();
        if (font.width(value) > maxWidth) {
            String ellipsis = "...";
            value = font.plainSubstrByWidth(value, Math.max(0, maxWidth - font.width(ellipsis))) + ellipsis;
        }
        return value;
    }

    private static List<String> wrapName(Font font, Component text, int maxWidth) {
        String name = text.getString();
        if (font.width(name) <= maxWidth) {
            return List.of(name);
        }
        String firstFit = font.plainSubstrByWidth(name, maxWidth);
        if (firstFit.isEmpty()) {
            return List.of(ellipsizedText(font, text, maxWidth));
        }
        int breakAt = firstFit.lastIndexOf(' ');
        if (breakAt <= 0) {
            breakAt = firstFit.length();
        }
        String firstLine = name.substring(0, breakAt).stripTrailing();
        String remainder = name.substring(breakAt).stripLeading();
        if (remainder.isEmpty()) {
            return List.of(firstLine);
        }
        return List.of(firstLine, ellipsizedText(font, Component.literal(remainder), maxWidth));
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
        HeaderLayout header = headerLayout(width);
        int titleLeft = HEADER_MARGIN + BACK_WIDTH + HEADER_GAP;
        int titleRight = header.doneX() - HEADER_GAP;
        int titleWidth = Math.max(0, titleRight - titleLeft);
        if (titleWidth > 0) {
            if (font.width(headerTitle) > titleWidth) {
                headerTitle = ellipsizedText(font, Component.literal(headerTitle), titleWidth);
            }
            int halfTitle = font.width(headerTitle) / 2;
            int titleCenterX = Math.max(titleLeft + halfTitle, Math.min(titleRight - halfTitle, width / 2));
            context.centeredText(font, headerTitle, titleCenterX, 10, 0xFFFFFFFF);
        }

        Optional<Component> status = statusMessage(viewModel, searchOpen && !searchPending, searchResults);
        if (actionFeedback != null) {
            if (nowMs() < actionFeedbackUntilMs) {
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
        } else if (hovered instanceof BranchTarget branch) {
            WidgetUtils.drawTooltip(context, font, viewModel.loading()
                            ? Component.translatable("gui.family_tree.loading_family")
                            : Component.translatable(branch.direction() == FamilyTreeView.Direction.ANCESTORS
                                    ? "gui.family_tree.explore_ancestors" : "gui.family_tree.explore_descendants",
                                    nodeDisplayName(nodes.get(branch.uuid()))),
                    mouseX, mouseY);
        } else if (hovered instanceof GenerationTarget generation) {
            Component label = generation.direction() == FamilyTreeView.Direction.ANCESTORS
                    ? Component.translatable("gui.family_tree.more_ancestors",
                            nodeDisplayName(nodes.get(viewModel.layoutRootId())))
                    : Component.translatable("gui.family_tree.more_descendants",
                            nodeDisplayName(nodes.get(viewModel.layoutRootId())));
            WidgetUtils.drawTooltip(context, font, label, mouseX, mouseY);
        }
    }

    private void renderSearchOverlay(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        if (!searchOpen) {
            return;
        }

        int top = HEADER_HEIGHT + 2;
        if (searchPending || searchResults.isEmpty()) {
            int rowWidth = searchOverlayWidth();
            Component status = Component.translatable(
                    searchPending ? "gui.family_tree.loading_family" : "gui.family_tree.no_records"
            );
            context.fill(searchX, top, searchX + rowWidth, top + SEARCH_ROW_HEIGHT, 0xFF20262C);
            context.text(
                    font,
                    ellipsizedText(font, status, Math.max(0, rowWidth - SEARCH_ROW_HORIZONTAL_PADDING)),
                    searchX + 6,
                    top + 7,
                    0xFFFFFFFF
            );
            return;
        }

        int count = Math.min(searchResults.size(), SEARCH_RESULT_LIMIT);
        int rowWidth = searchOverlayWidth();
        for (int index = 0; index < count; index++) {
            int rowTop = top + index * SEARCH_ROW_HEIGHT;
            FamilyTreeSearchEntry entry = searchResults.get(index);
            boolean rowHovered = mouseX >= searchX
                    && mouseX < searchX + rowWidth
                    && mouseY >= rowTop
                    && mouseY < rowTop + SEARCH_ROW_HEIGHT;
            context.fill(searchX, rowTop, searchX + rowWidth, rowTop + SEARCH_ROW_HEIGHT,
                    rowHovered ? 0xFF43586C : 0xFF20262C);
            context.text(
                    font,
                    ellipsizedText(font, searchResultLabel(entry), rowWidth - SEARCH_ROW_HORIZONTAL_PADDING),
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

    private int worldX(double mouseX) {
        return (int) Math.floor((mouseX - width / 2.0 - viewport.panX()) / viewport.zoom());
    }

    private int worldY(double mouseY) {
        return (int) Math.floor((mouseY - canvasCenterY() - viewport.panY()) / viewport.zoom());
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < width && mouseY >= HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT;
    }

    static FamilyTreeLayout.Bounds guiBounds(FamilyTreeLayout.Bounds bounds,
            double originX, double originY, float zoom) {
        int left = guiCoordinate(bounds.left(), originX, zoom);
        int top = guiCoordinate(bounds.top(), originY, zoom);
        return new FamilyTreeLayout.Bounds(
                left,
                Math.max(left + 1, guiCoordinate(bounds.right(), originX, zoom)),
                top,
                Math.max(top + 1, guiCoordinate(bounds.bottom(), originY, zoom))
        );
    }

    static FamilyTreeLayout.Bounds guiHorizontalStroke(int x1, int x2, int y,
            double originX, double originY, float zoom) {
        int start = guiCoordinate(x1, originX, zoom);
        int end = guiCoordinate(x2, originX, zoom);
        int row = guiCoordinate(y, originY, zoom);
        return new FamilyTreeLayout.Bounds(Math.min(start, end), Math.max(start, end) + 1, row, row + 1);
    }

    static FamilyTreeLayout.Bounds guiVerticalStroke(int x, int y1, int y2,
            double originX, double originY, float zoom) {
        int column = guiCoordinate(x, originX, zoom);
        int start = guiCoordinate(y1, originY, zoom);
        int end = guiCoordinate(y2, originY, zoom);
        return new FamilyTreeLayout.Bounds(column, column + 1, Math.min(start, end), Math.max(start, end) + 1);
    }

    private static int guiCoordinate(double world, double origin, float zoom) {
        return (int) Math.round(origin + world * zoom);
    }

    static Optional<FamilyTreeView.Direction> generationControlAt(
            double mouseX, double mouseY, int screenWidth, int screenHeight,
            Set<FamilyTreeView.Direction> available
    ) {
        for (FamilyTreeView.Direction direction : available) {
            FamilyTreeLayout.Bounds bounds = generationControlBounds(direction, screenWidth, screenHeight);
            if (mouseX >= bounds.left() && mouseX < bounds.right()
                    && mouseY >= bounds.top() && mouseY < bounds.bottom()) {
                return Optional.of(direction);
            }
        }
        return Optional.empty();
    }

    private static FamilyTreeLayout.Bounds generationControlBounds(
            FamilyTreeView.Direction direction, int screenWidth, int screenHeight
    ) {
        int x = screenWidth - GENERATION_BUTTON_SIZE - 8;
        int y = direction == FamilyTreeView.Direction.ANCESTORS
                ? HEADER_HEIGHT + 12 : screenHeight - FOOTER_HEIGHT - GENERATION_BUTTON_SIZE - 8;
        return new FamilyTreeLayout.Bounds(x, x + GENERATION_BUTTON_SIZE, y, y + GENERATION_BUTTON_SIZE);
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
        int doneX = Math.max(HEADER_MARGIN + BACK_WIDTH + HEADER_GAP, screenWidth - HEADER_MARGIN - DONE_WIDTH);
        int doneWidth = Math.max(28, Math.min(DONE_WIDTH, screenWidth - HEADER_MARGIN - doneX));

        int fixedControlsWidth = ZOOM_BUTTON_WIDTH
                + CONTROL_GAP
                + ZOOM_LABEL_WIDTH
                + CONTROL_GAP
                + ZOOM_BUTTON_WIDTH
                + CONTROL_GROUP_GAP
                + FIT_WIDTH
                + HEADER_GAP
                + CENTER_WIDTH;
        int rightAlignedControlsLeft = screenWidth - HEADER_MARGIN - fixedControlsWidth;
        if (rightAlignedControlsLeft >= HEADER_MARGIN + MIN_STANDARD_SEARCH_WIDTH + CONTROL_GROUP_GAP) {
            int controlsLeft = Math.max(
                    (screenWidth - fixedControlsWidth) / 2,
                    HEADER_MARGIN + MIN_STANDARD_SEARCH_WIDTH + CONTROL_GROUP_GAP
            );
            int searchWidth = controlsLeft - HEADER_MARGIN - CONTROL_GROUP_GAP;
            int zoomOutX = controlsLeft;
            int zoomLabelX = zoomOutX + ZOOM_BUTTON_WIDTH + CONTROL_GAP;
            int zoomInX = zoomLabelX + ZOOM_LABEL_WIDTH + CONTROL_GAP;
            int fitX = zoomInX + ZOOM_BUTTON_WIDTH + CONTROL_GROUP_GAP;
            int centerX = fitX + FIT_WIDTH + HEADER_GAP;
            return new HeaderLayout(
                    doneX,
                    doneWidth,
                    HEADER_MARGIN,
                    searchWidth,
                    zoomOutX,
                    zoomLabelX,
                    zoomInX,
                    fitX,
                    centerX,
                    ZOOM_BUTTON_WIDTH,
                    ZOOM_LABEL_WIDTH,
                    FIT_WIDTH,
                    CENTER_WIDTH
            );
        }

        int compactZoomButtonWidth = 16;
        int compactZoomLabelWidth = 34;
        int compactFitWidth = 28;
        int compactCenterWidth = 42;
        int compactGroupGap = 4;
        int compactFitGap = 2;
        int compactControlsWidth = compactZoomButtonWidth
                + CONTROL_GAP
                + compactZoomLabelWidth
                + CONTROL_GAP
                + compactZoomButtonWidth
                + compactGroupGap
                + compactFitWidth
                + compactFitGap
                + compactCenterWidth;
        int searchWidth = Math.max(
                MIN_COMPACT_SEARCH_WIDTH,
                screenWidth - HEADER_MARGIN * 2 - compactGroupGap - compactControlsWidth
        );
        int zoomOutX = Math.max(
                HEADER_MARGIN + searchWidth + compactGroupGap,
                screenWidth - HEADER_MARGIN - compactControlsWidth
        );
        int zoomLabelX = zoomOutX + compactZoomButtonWidth + CONTROL_GAP;
        int zoomInX = zoomLabelX + compactZoomLabelWidth + CONTROL_GAP;
        int fitX = zoomInX + compactZoomButtonWidth + compactGroupGap;
        int centerX = fitX + compactFitWidth + compactFitGap;
        return new HeaderLayout(
                doneX,
                doneWidth,
                HEADER_MARGIN,
                searchWidth,
                zoomOutX,
                zoomLabelX,
                zoomInX,
                fitX,
                centerX,
                compactZoomButtonWidth,
                compactZoomLabelWidth,
                compactFitWidth,
                compactCenterWidth
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
        return cardPresentation(null, relationshipFocusId, node, nodes, orphans, false);
    }

    private static CardPresentation cardPresentation(
            @Nullable Font font,
            UUID relationshipFocusId,
            FamilyTreeNode node,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> orphans,
            boolean hasGrave
    ) {
        Component identity = node.isPlayer()
                ? Component.translatable("gui.family_tree.player")
                : node.getProfessionText();
        FamilyTreeRelationshipResolver.Relation relation =
                FamilyTreeRelationshipResolver.resolve(relationshipFocusId, node.id(), nodes);
        Component relationshipState = node.getRelationshipState() == RelationshipState.SINGLE
                ? null
                : Component.translatable("marriage." + node.getRelationshipState().base().getIcon());
        CardPresentation raw = new CardPresentation(
                nodeDisplayName(node),
                0xFF000000 | node.gender().getColor(),
                identity,
                relationLabel(relation),
                relationshipState,
                orphans.contains(node.id()),
                null
        );
        if (font == null) {
            return raw;
        }
        FormattedCardText formatted = formatCardText(
                font,
                raw,
                CARD_TEXT_WIDTH,
                nameLineWidth(node, hasGrave, CARD_TEXT_WIDTH)
        );
        return new CardPresentation(
                raw.name(),
                raw.nameColor(),
                raw.identity(),
                raw.relationship(),
                raw.relationshipState(),
                raw.orphan(),
                formatted
        );
    }

    static Map<UUID, CardPresentation> cardPresentations(
            UUID relationshipFocusId,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> orphans
    ) {
        return cardPresentations(null, relationshipFocusId, nodes, orphans, Map.of());
    }

    static Map<UUID, CardPresentation> cardPresentations(
            @Nullable Font font,
            UUID relationshipFocusId,
            Map<UUID, FamilyTreeNode> nodes,
            Set<UUID> orphans,
            Map<UUID, GlobalPos> graves
    ) {
        Map<UUID, CardPresentation> presentations = new LinkedHashMap<>(nodes.size());
        nodes.forEach((id, node) -> presentations.put(
                id,
                cardPresentation(font, relationshipFocusId, node, nodes, orphans, graves.containsKey(id))
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

    static FamilyTreeViewModel.ViewportState anchorLockedViewport(
            FamilyTreeLayout.Result previous,
            FamilyTreeLayout.Result updated,
            FamilyTreeViewModel.ViewportState current,
            UUID anchorId
    ) {
        Optional<FamilyTreeLayout.Bounds> oldBounds = cardBounds(previous, anchorId);
        Optional<FamilyTreeLayout.Bounds> newBounds = cardBounds(updated, anchorId);
        if (oldBounds.isEmpty() || newBounds.isEmpty()) {
            return current;
        }
        return new FamilyTreeViewModel.ViewportState(
                current.panX() + (oldBounds.orElseThrow().centerX() - newBounds.orElseThrow().centerX()) * current.zoom(),
                current.panY() + (oldBounds.orElseThrow().centerY() - newBounds.orElseThrow().centerY()) * current.zoom(),
                current.zoom()
        );
    }

    private static Optional<FamilyTreeLayout.Bounds> cardBounds(FamilyTreeLayout.Result result, UUID id) {
        return result.cards().stream()
                .filter(card -> card.uuid().equals(id))
                .map(FamilyTreeLayout.Card::bounds)
                .findFirst();
    }

    static FamilyTreeViewModel.ViewportState focusViewportAfterResponse(
            FamilyTreeLayout.Result result,
            FamilyTreeViewModel.ViewportState current,
            UUID focusId
    ) {
        return cardBounds(result, focusId)
                .map(bounds -> new FamilyTreeViewModel.ViewportState(
                        -bounds.centerX() * current.zoom(),
                        -bounds.centerY() * current.zoom(),
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
        for (FamilyTreeLayout.Card card : result.cards()) {
            if (card.bounds().contains(worldX, worldY)) {
                return Optional.of(new PersonTarget(card.uuid()));
            }
        }
        return Optional.empty();
    }

    sealed interface HitTarget permits PersonTarget, GraveTarget, GenerationTarget, BranchTarget {
    }

    record PersonTarget(UUID uuid) implements HitTarget {
    }

    record GraveTarget(UUID uuid, GlobalPos grave) implements HitTarget {
    }

    record RelationshipDetail(UUID uuid, FamilyTreeRelationshipResolver.Relation relation) {
    }

    record FormattedCardText(
            List<String> nameLines,
            String identityLine,
            String relationshipLine,
            @Nullable String relationshipStateLine,
            @Nullable String orphanLine
    ) {
        FormattedCardText {
            nameLines = List.copyOf(nameLines);
        }
    }

    record CardPresentation(
            Component name,
            int nameColor,
            Component identity,
            Component relationship,
            @Nullable Component relationshipState,
            boolean orphan,
            @Nullable FormattedCardText formatted
    ) {
        CardPresentation(
                Component name,
                int nameColor,
                Component identity,
                Component relationship,
                @Nullable Component relationshipState,
                boolean orphan
        ) {
            this(name, nameColor, identity, relationship, relationshipState, orphan, null);
        }
    }

    record GenerationTarget(FamilyTreeView.Direction direction) implements HitTarget {
    }

    record BranchTarget(UUID uuid, FamilyTreeView.Direction direction) implements HitTarget {
    }

    record HeaderLayout(
            int doneX,
            int doneWidth,
            int searchX,
            int searchWidth,
            int zoomOutX,
            int zoomLabelX,
            int zoomInX,
            int fitX,
            int centerX,
            int zoomButtonWidth,
            int zoomLabelWidth,
            int fitWidth,
            int centerWidth
    ) {
    }
}
