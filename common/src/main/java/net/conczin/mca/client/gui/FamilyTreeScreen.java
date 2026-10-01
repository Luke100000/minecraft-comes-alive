package net.conczin.mca.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.Network;
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
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public class FamilyTreeScreen extends Screen {
    private static final int HEADER_HEIGHT = 30;
    private static final int FOOTER_HEIGHT = 30;
    private static final int FIT_PADDING = 24;
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
        int y = 5;
        addRenderableWidget(new ButtonWidget(5, y, 44, 20, Component.translatable("gui.back"), button -> goBack()));

        searchField = addRenderableWidget(new EditBox(
                font,
                Math.max(54, width / 2 - 90),
                y + 1,
                120,
                18,
                Component.translatable("gui.family_tree.search")
        ));
        searchField.setMaxLength(32);

        int controlsX = Math.max(width - 278, width / 2 + 36);
        addRenderableWidget(new ButtonWidget(controlsX, y, 20, 20, Component.literal("-"), button -> setZoom(viewport.zoom() - 0.1F)));
        zoomLabel = addRenderableWidget(new ButtonWidget(controlsX + 22, y, 48, 20, zoomLabel(), button -> {
        }));
        zoomLabel.active = false;
        addRenderableWidget(new ButtonWidget(controlsX + 72, y, 20, 20, Component.literal("+"), button -> setZoom(viewport.zoom() + 0.1F)));
        addRenderableWidget(new ButtonWidget(controlsX + 94, y, 44, 20, Component.translatable("gui.family_tree.fit"), button -> {
            viewport = fitView(layout, width, canvasHeight(), FIT_PADDING);
        }));
        addRenderableWidget(new ButtonWidget(controlsX + 140, y, 54, 20, Component.translatable("gui.family_tree.center"), button -> {
            viewport = centerView(layout, viewport);
        }));
        addRenderableWidget(new ButtonWidget(controlsX + 196, y, 72, 20, Component.translatable("gui.done"), button -> onClose()));

        if (viewModel.nodes().isEmpty() && viewModel.pendingFocusId().isEmpty()) {
            requestFocus(viewModel.focusId(), false);
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
        if (result == FamilyTreeViewModel.MergeResult.APPLIED && response.found()) {
            viewport = centerView(layout, viewport);
        }
    }

    private void rebuildLayout() {
        layout = FamilyTreeLayout.layout(viewModel.focusId(), viewModel.snapshot());
    }

    private void requestFocus(UUID id, boolean recenter) {
        long requestId = viewModel.beginFocus(id, viewport);
        if (recenter) {
            viewport = new FamilyTreeViewModel.ViewportState(0, 0, viewport.zoom());
        }
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
            viewport = entry.viewport();
            rebuildLayout();
            if (!viewModel.nodes().containsKey(entry.focusId())) {
                requestFocus(entry.focusId(), false);
            }
        });
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
        if (button == 0 && insideCanvas(mouseX, mouseY)) {
            Optional<HitTarget> target = hitTargetAt(layout, worldX(mouseX), worldY(mouseY));
            if (target.isPresent()) {
                Minecraft.getInstance().getSoundManager()
                        .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1));
                if (target.get() instanceof PersonTarget person) {
                    viewModel.select(person.uuid());
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
                height / 2.0,
                viewport.zoom() + (float) scrollY * 0.1F
        );
        updateZoomLabel();
        return true;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        context.fill(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT, 0x66000000);

        hovered = insideCanvas(mouseX, mouseY)
                ? hitTargetAt(layout, worldX(mouseX), worldY(mouseY)).orElse(null)
                : null;

        context.enableScissor(0, HEADER_HEIGHT, width, height - FOOTER_HEIGHT);
        PoseStack pose = context.pose();
        pose.pushPose();
        pose.translate(width / 2.0 + viewport.panX(), height / 2.0 + viewport.panY(), 0);
        pose.scale(viewport.zoom(), viewport.zoom(), 1.0F);

        renderEdges(context);
        renderContinuations(context);
        renderCards(context);

        pose.popPose();
        context.disableScissor();

        renderFixedChrome(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
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
            boolean selected = viewModel.selection().filter(card.uuid()::equals).isPresent();
            boolean isHovered = hovered instanceof PersonTarget target && target.uuid().equals(card.uuid());
            int background = card.role() == FamilyTreeLayout.Role.FOCUS
                    ? 0xFF334B63
                    : node.isDeceased() ? 0xFF3D3D3D : 0xFF252D35;
            if (selected) {
                background = 0xFF526D87;
            } else if (isHovered) {
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
        context.drawCenteredString(
                font,
                Component.translatable("gui.family_tree.formatted_title", focusName),
                width / 2,
                10,
                0xFFFFFFFF
        );

        PersonTarget detailTarget = hovered instanceof PersonTarget person
                ? person
                : viewModel.selection().map(PersonTarget::new).orElse(null);
        if (detailTarget != null) {
            FamilyTreeNode node = viewModel.nodes().get(detailTarget.uuid());
            if (node != null) {
                FamilyTreeRelationshipResolver.Relation relation =
                        FamilyTreeRelationshipResolver.resolve(viewModel.focusId(), detailTarget.uuid(), viewModel.nodes());
                Component detail = nodeDisplayName(node).copy()
                        .append(" · ")
                        .append(Component.literal(relation.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
                context.drawCenteredString(font, detail, width / 2, height - 20, 0xFFFFFFFF);
                if (node.isDeceased() && hovered instanceof PersonTarget) {
                    context.renderTooltip(font, Component.translatable("gui.family_tree.label.deceased"), mouseX, mouseY);
                }
            }
        }
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
        return (int) Math.floor((mouseY - height / 2.0 - viewport.panY()) / viewport.zoom());
    }

    private boolean insideCanvas(double mouseX, double mouseY) {
        return mouseX >= 0 && mouseX < width && mouseY >= HEADER_HEIGHT && mouseY < height - FOOTER_HEIGHT;
    }

    private int canvasHeight() {
        return Math.max(1, height - HEADER_HEIGHT - FOOTER_HEIGHT);
    }

    private Component zoomLabel() {
        return Component.literal(Math.round(viewport.zoom() * 100.0F) + "%");
    }

    private void updateZoomLabel() {
        if (zoomLabel != null) {
            zoomLabel.setMessage(zoomLabel());
        }
    }

    private static Component nodeDisplayName(FamilyTreeNode node) {
        return MCA.isBlankString(node.getName())
                ? Component.translatable("gui.family_tree.unnamed_villager")
                : Component.literal(node.getName());
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
}
