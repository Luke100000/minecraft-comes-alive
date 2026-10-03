package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeScreenInteractionTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID OTHER = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void zoomAroundCursorKeepsWorldPointUnderCursor() {
        FamilyTreeViewModel.ViewportState before = new FamilyTreeViewModel.ViewportState(20, -10, 1.0F);
        double cursorX = 500;
        double cursorY = 360;
        double centerX = 400;
        double centerY = 300;
        double worldX = (cursorX - centerX - before.panX()) / before.zoom();
        double worldY = (cursorY - centerY - before.panY()) / before.zoom();

        FamilyTreeViewModel.ViewportState after =
                FamilyTreeScreen.zoomAround(before, cursorX, cursorY, centerX, centerY, 1.5F);

        assertEquals(cursorX, centerX + after.panX() + worldX * after.zoom(), 0.0001);
        assertEquals(cursorY, centerY + after.panY() + worldY * after.zoom(), 0.0001);
    }

    @Test
    void zoomClampsToQuarterAndDoubleScale() {
        FamilyTreeViewModel.ViewportState state = new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);

        assertEquals(0.25F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 0.01F).zoom());
        assertEquals(2.0F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 5.0F).zoom());
    }

    @Test
    void headerFloatsSearchLeftAndCentersControlsIndependently() {
        FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(854);

        assertEquals(5, header.backX());
        assertEquals(854 - 5 - 72, header.doneX());
        assertEquals(5, header.searchX());
        assertEquals(180, header.searchWidth());
        assertEquals(327, header.zoomOutX());
        assertEquals(473, header.centerX());
    }

    @Test
    void headerShrinksSearchBeforeOverlappingCenteredControls() {
        FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(320);

        assertEquals(5, header.searchX());
        assertEquals(49, header.searchWidth());
        assertEquals(60, header.zoomOutX());
        assertEquals(206, header.centerX());
        assertTrue(header.searchX() + header.searchWidth() < header.zoomOutX());
    }

    @Test
    void fitViewKeepsContentInsideCanvasWithPadding() {
        FamilyTreeLayout.Result result = result(new FamilyTreeLayout.Bounds(-200, 200, -100, 100));

        FamilyTreeViewModel.ViewportState state = FamilyTreeScreen.fitView(result, 300, 200, 20);

        FamilyTreeLayout.Bounds bounds = result.contentBounds();
        double left = 150 + state.panX() + bounds.left() * state.zoom();
        double right = 150 + state.panX() + bounds.right() * state.zoom();
        double top = 100 + state.panY() + bounds.top() * state.zoom();
        double bottom = 100 + state.panY() + bounds.bottom() * state.zoom();
        assertTrue(left >= 20);
        assertTrue(right <= 280);
        assertTrue(top >= 20);
        assertTrue(bottom <= 180);
    }

    @Test
    void centerViewPreservesZoom() {
        FamilyTreeLayout.Result result = result(new FamilyTreeLayout.Bounds(40, 140, -30, 70));
        FamilyTreeViewModel.ViewportState current = new FamilyTreeViewModel.ViewportState(18, 22, 1.5F);

        FamilyTreeViewModel.ViewportState centered = FamilyTreeScreen.centerView(result, current);

        assertEquals(1.5F, centered.zoom());
        assertEquals(-135.0, centered.panX(), 0.0001);
        assertEquals(-30.0, centered.panY(), 0.0001);
    }

    @Test
    void focusTransitionRecentersOnlyAfterTheNewLayoutArrives() {
        FamilyTreeLayout.Card focused = new FamilyTreeLayout.Card(
                OTHER,
                new FamilyTreeLayout.Bounds(40, 140, -30, 70)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(focused),
                List.of(),
                List.of(),
                focused.bounds()
        );
        FamilyTreeViewModel.ViewportState current = new FamilyTreeViewModel.ViewportState(18, 22, 1.5F);

        assertEquals(
                new FamilyTreeViewModel.ViewportState(-135, -30, 1.5F),
                FamilyTreeScreen.focusViewportAfterResponse(result, current, OTHER)
        );
    }

    @Test
    void deceasedCardsUseSkullAndMutedPersimmonRed() {
        assertEquals("☠", FamilyTreeScreen.DECEASED_MARKER);
        assertEquals(0xFFA94A3A, FamilyTreeScreen.DECEASED_MARKER_COLOR);
    }

    @Test
    void partnerIconIsCenteredOnPartnerConnection() {
        FamilyTreeLayout.Bounds left = new FamilyTreeLayout.Bounds(-123, -13, -20, 20);
        FamilyTreeLayout.Bounds right = new FamilyTreeLayout.Bounds(13, 123, -20, 20);

        assertEquals(
                new FamilyTreeLayout.Bounds(-8, 8, -8, 8),
                FamilyTreeScreen.partnerIconBounds(left, right)
        );
        assertEquals(3, FamilyTreeScreen.PARTNER_ICON_VISIBLE_EDGE_INSET);
    }

    @Test
    void partnerConnectionUsesRelationshipStateIcon() {
        assertEquals(
                Optional.of(RelationshipState.MARRIED_TO_PLAYER),
                FamilyTreeScreen.partnerRelationshipState(
                        RelationshipState.MARRIED_TO_VILLAGER,
                        RelationshipState.MARRIED_TO_PLAYER
                )
        );
        assertEquals(
                Optional.of(RelationshipState.ENGAGED),
                FamilyTreeScreen.partnerRelationshipState(RelationshipState.ENGAGED, RelationshipState.ENGAGED)
        );
        assertEquals(
                Optional.of(RelationshipState.PROMISED),
                FamilyTreeScreen.partnerRelationshipState(RelationshipState.PROMISED, RelationshipState.PROMISED)
        );
        assertEquals(
                Optional.of(RelationshipState.WIDOW),
                FamilyTreeScreen.partnerRelationshipState(RelationshipState.WIDOW, RelationshipState.SINGLE)
        );
        assertTrue(FamilyTreeScreen.partnerRelationshipState(
                RelationshipState.SINGLE,
                RelationshipState.SINGLE
        ).isEmpty());
    }

    @Test
    void cardPresentationRestoresOriginDetailsAndUsesStableRootRelationship() {
        FamilyTreeNode mother = new FamilyTreeNode(
                null,
                ROOT,
                "Elita",
                false,
                Gender.FEMALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        FamilyTreeNode child = new FamilyTreeNode(
                null,
                OTHER,
                "Alex",
                true,
                Gender.MALE,
                ROOT,
                Util.NIL_UUID
        );
        mother.addChild(OTHER);
        mother.setRelationshipState(RelationshipState.MARRIED_TO_PLAYER);
        Map<UUID, FamilyTreeNode> nodes = Map.of(ROOT, mother, OTHER, child);

        FamilyTreeScreen.CardPresentation motherCard = FamilyTreeScreen.cardPresentation(ROOT, mother, nodes);
        FamilyTreeScreen.CardPresentation childCard = FamilyTreeScreen.cardPresentation(ROOT, child, nodes);

        assertEquals("Elita", motherCard.name().getString());
        assertEquals(0xFFA649A4, motherCard.nameColor());
        assertEquals(
                mother.getProfessionText().copy()
                        .append(" · ")
                        .append(Component.translatable("gui.family_tree.villager")),
                motherCard.identity()
        );
        assertEquals(Component.translatable("gui.family_tree.relation.self"), motherCard.relationship());
        assertEquals(Component.translatable("marriage.married"), motherCard.relationshipState());
        assertEquals(Component.translatable("gui.family_tree.player"), childCard.identity());
        assertEquals(Component.translatable("gui.family_tree.relation.child"), childCard.relationship());
    }

    @Test
    void orphanCardStateMatchesOriginMissingOrDeceasedParentRule() {
        FamilyTreeNode child = node(OTHER, "Sima");
        assertTrue(FamilyTreeScreen.isOrphan(child, Map.of(OTHER, child)));

        FamilyTreeNode livingParent = node(ROOT, "Sug");
        child.setFather(livingParent);
        assertFalse(FamilyTreeScreen.isOrphan(child, Map.of(ROOT, livingParent, OTHER, child)));

        livingParent.setDeceased(true);
        assertTrue(FamilyTreeScreen.isOrphan(child, Map.of(ROOT, livingParent, OTHER, child)));
    }

    @Test
    void richerCardsReserveEnoughFixedSpaceForFiveLines() {
        assertEquals(128, FamilyTreeLayout.CARD_WIDTH);
        assertEquals(64, FamilyTreeLayout.CARD_HEIGHT);
    }

    @Test
    void backRestoresPreviousViewport() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previous = new FamilyTreeViewModel.ViewportState(12, -8, 1.25F);
        long requestId = model.beginFocus(OTHER, previous);
        model.accept(new GetFamilyTreeResponse(requestId, OTHER, true, FamilyTreeView.empty()));

        Optional<FamilyTreeViewModel.HistoryEntry> back = model.back();

        assertTrue(back.isPresent());
        assertEquals(ROOT, model.focusId());
        assertEquals(previous, back.orElseThrow().viewport());
    }

    @Test
    void restoredViewportDisplaysItsRestoredZoom() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState previous = new FamilyTreeViewModel.ViewportState(12, -8, 1.0F);
        long requestId = model.beginFocus(OTHER, previous);
        model.accept(new GetFamilyTreeResponse(requestId, OTHER, true, FamilyTreeView.empty()));

        FamilyTreeViewModel.ViewportState restored = model.back().orElseThrow().viewport();

        assertEquals(Component.literal("100%"), FamilyTreeScreen.zoomLabel(restored));
    }

    @Test
    void layoutHitTestIdentifiesKnownCardByUuid() {
        FamilyTreeLayout.Card card = new FamilyTreeLayout.Card(
                ROOT,
                new FamilyTreeLayout.Bounds(-55, 55, -20, 20)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(card),
                List.of(),
                List.of(),
                card.bounds()
        );

        assertEquals(
                Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(result, 0, 0)
        );
    }

    @Test
    void layoutHitTestIdentifiesContinuationByAnchorAndDirection() {
        FamilyTreeLayout.ContinuationControl control = new FamilyTreeLayout.ContinuationControl(
                ROOT,
                ANCESTORS,
                new FamilyTreeLayout.Bounds(-9, 9, -80, -62)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(),
                List.of(),
                List.of(control),
                control.bounds()
        );

        assertEquals(
                Optional.of(new FamilyTreeScreen.ContinuationTarget(ROOT, ANCESTORS)),
                FamilyTreeScreen.hitTargetAt(result, 0, -70)
        );
    }

    @Test
    void graveClipboardTextUsesPlainCoordinatesOnly() {
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(123, 64, -456));

        assertEquals("123 64 -456", FamilyTreeScreen.graveClipboardText(grave));
    }

    @Test
    void graveIconHitTargetWinsOverPersonCard() {
        FamilyTreeNode deceased = node(ROOT, "Sug");
        deceased.setDeceased(true);
        FamilyTreeLayout.Card card = new FamilyTreeLayout.Card(
                ROOT,
                new FamilyTreeLayout.Bounds(-64, 64, -32, 32)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(card),
                List.of(),
                List.of(),
                card.bounds()
        );
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 70, 8));
        FamilyTreeLayout.Bounds graveBounds = FamilyTreeScreen.graveIconBounds(card.bounds());

        assertEquals(
                Optional.of(new FamilyTreeScreen.GraveTarget(ROOT, grave)),
                FamilyTreeScreen.hitTargetAt(
                        result,
                        Map.of(ROOT, deceased),
                        Map.of(ROOT, grave),
                        graveBounds.centerX(),
                        graveBounds.centerY()
                )
        );
    }

    @Test
    void cardWithoutExactGraveHasNoGraveTarget() {
        FamilyTreeNode deceased = node(ROOT, "Sug");
        deceased.setDeceased(true);
        FamilyTreeLayout.Card card = new FamilyTreeLayout.Card(
                ROOT,
                new FamilyTreeLayout.Bounds(-64, 64, -32, 32)
        );
        FamilyTreeLayout.Result result = new FamilyTreeLayout.Result(
                List.of(card),
                List.of(),
                List.of(),
                card.bounds()
        );
        FamilyTreeLayout.Bounds graveBounds = FamilyTreeScreen.graveIconBounds(card.bounds());

        assertEquals(
                Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(
                        result,
                        Map.of(ROOT, deceased),
                        Map.of(),
                        graveBounds.centerX(),
                        graveBounds.centerY()
                )
        );
    }

    @Test
    void dimensionLabelUsesReadableFallbackForCustomDimension() {
        ResourceKey<Level> custom = ResourceKey.create(
                Registries.DIMENSION,
                Identifier.fromNamespaceAndPath("example", "forgotten_realm")
        );

        assertEquals("example:forgotten_realm", FamilyTreeScreen.graveDimensionLabel(custom).getString());
    }

    @Test
    void selectedPersonKeepsRelationshipDetailAfterHoverEnds() {
        FamilyTreeNode parent = node(ROOT, "Sug");
        FamilyTreeNode child = node(OTHER, "Sima");
        child.setFather(parent);
        parent.addChild(OTHER);
        FamilyTreeView family = new FamilyTreeView(
                Map.of(ROOT, parent, OTHER, child),
                Set.of(),
                Set.of()
        );
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
        long initial = model.beginFocus(ROOT, viewport);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, family));
        long selected = model.beginFocus(OTHER, viewport);
        model.accept(new GetFamilyTreeResponse(selected, OTHER, true, family));

        Optional<FamilyTreeScreen.RelationshipDetail> selectedDetail = Optional.of(
                new FamilyTreeScreen.RelationshipDetail(OTHER, FamilyTreeRelationshipResolver.Relation.CHILD)
        );
        assertEquals(selectedDetail, FamilyTreeScreen.relationshipDetail(null, model));
        assertEquals(
                selectedDetail,
                FamilyTreeScreen.relationshipDetail(new FamilyTreeScreen.PersonTarget(OTHER), model)
        );
        assertEquals(
                Optional.of(new FamilyTreeScreen.RelationshipDetail(
                        ROOT,
                        FamilyTreeRelationshipResolver.Relation.FATHER
                )),
                FamilyTreeScreen.relationshipDetail(new FamilyTreeScreen.PersonTarget(ROOT), model)
        );
    }

    @Test
    void emptyIntegratedQueryHidesResultsAndDoesNotSeedPlayerName() {
        assertTrue(FamilyTreeScreen.integratedSearchQuery("  ").isEmpty());
        assertEquals(Optional.of("Alex"), FamilyTreeScreen.integratedSearchQuery(" Alex "));
    }

    @Test
    void emptyResultsExposeNoFamilyRecordsState() {
        FamilyTreeViewModel model = loadedModel();

        assertEquals(
                Optional.of(Component.translatable("gui.family_tree.no_records")),
                FamilyTreeScreen.statusMessage(model, true, List.of())
        );
    }

    @Test
    void unavailableLatestFocusLeavesGraphAndExposesUnavailableState() {
        FamilyTreeViewModel model = loadedModel();
        long requestId = model.beginFocus(OTHER, new FamilyTreeViewModel.ViewportState(0, 0, 1.0F));

        model.accept(new GetFamilyTreeResponse(requestId, OTHER, false, FamilyTreeView.empty()));

        assertEquals(ROOT, model.focusId());
        assertTrue(model.nodes().containsKey(ROOT));
        assertEquals(
                Optional.of(Component.translatable("gui.family_tree.family_record_unavailable")),
                FamilyTreeScreen.statusMessage(model, false, List.of())
        );
    }

    @Test
    void blankNodeDisplayNeverUsesQuestionMarkPlaceholder() {
        FamilyTreeNode node = new FamilyTreeNode(
                null,
                ROOT,
                " ",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );

        Component display = FamilyTreeScreen.nodeDisplayName(node);

        assertEquals(Component.translatable("gui.family_tree.unnamed_villager"), display);
        assertTrue(!display.getString().contains("???"));
    }

    private static FamilyTreeViewModel loadedModel() {
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long requestId = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1.0F));
        model.accept(new GetFamilyTreeResponse(requestId, ROOT, true, view(ROOT)));
        return model;
    }

    private static FamilyTreeView view(UUID id) {
        return new FamilyTreeView(Map.of(id, node(id, "Person")), Set.of(), Set.of());
    }

    private static FamilyTreeNode node(UUID id, String name) {
        return new FamilyTreeNode(
                null,
                id,
                name,
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
    }

    private static FamilyTreeSearchEntry searchEntry(UUID id, String name) {
        return new FamilyTreeSearchEntry(id, name, false, "", false, "");
    }

    private static FamilyTreeLayout.Result result(FamilyTreeLayout.Bounds bounds) {
        return new FamilyTreeLayout.Result(List.of(), List.of(), List.of(), bounds);
    }

}
