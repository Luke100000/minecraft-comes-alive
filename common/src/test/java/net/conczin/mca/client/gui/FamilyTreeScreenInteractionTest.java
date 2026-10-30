package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.Window;
import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.util.ArrayList;
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

    @Test
    void lowZoomTextUsesWholeFramebufferPixels() throws Exception {
        RecordingGraphics graphics = renderCards(0.25F, 2.0, false);
        TextDraw name = graphics.text.getFirst();

        assertTrue(name.scale() * 2.0 >= 1.0, "Names must remain at least one framebuffer pixel per glyph pixel");
        assertEquals(Math.rint(name.scale() * 2.0), name.scale() * 2.0, 0.0001);
        assertEquals(Math.rint(name.x() * 2.0), name.x() * 2.0, 0.0001);
        assertEquals(Math.rint(name.y() * 2.0), name.y() * 2.0, 0.0001);
    }

    @Test
    void zoomingAcrossNinetyFivePercentKeepsOrphanAndFixedTextRows() throws Exception {
        for (float zoom : new float[]{0.9F, 0.95F, 1.0F, 1.05F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false);

            assertEquals(4, graphics.text.size(), "All four labels must remain visible at " + zoom);
            assertEquals("Émilie", graphics.text.get(0).text());
            assertEquals("Villager", graphics.text.get(1).text());
            assertEquals("Mother", graphics.text.get(2).text());
            assertRendersOrphan(graphics.text.get(3));

            for (int line = 0; line < 4; line++) {
                double expectedOffset = (5 + line * 12) * zoom;
                assertEquals(expectedOffset, graphics.text.get(line).y() - graphics.cardTop, 1.0,
                        "Logical text baselines must not jump as zoom changes, row " + line);
            }
        }
    }

    @Test
    void nearFullZoomKeepsOrphanWhenMarriageStatusAlsoOccupiesCard() throws Exception {
        for (float zoom : new float[]{0.95F, 1.0F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false, true);

            assertEquals(5, graphics.text.size(), "Marriage status must not evict orphan at " + zoom);
            assertRendersOrphan(graphics.text.get(4));
        }
    }

    @Test
    void sharpTextNeverSilentlyDropsFamilyLabelsAtDistantZoom() throws Exception {
        for (float zoom : new float[]{0.25F, 0.5F, 0.75F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false, true);

            assertEquals(5, graphics.text.size(), "All details remain represented at " + zoom);
            assertEquals("Émilie", graphics.text.getFirst().text());
            assertRendersOrphan(graphics.text.getLast());
        }
    }

    private static void assertRendersOrphan(TextDraw draw) {
        String expected = Component.translatable("gui.family_tree.label.orphan").getString();
        String actual = draw.text();
        assertTrue(expected.equals(actual) || actual.endsWith("...")
                        && expected.startsWith(actual.substring(0, actual.length() - 3)),
                "Orphan text must be drawn (possibly ellipsized): " + actual);
    }

    @Test
    void enlargedAccentedNameStaysBelowCardTop() throws Exception {
        RecordingGraphics graphics = renderCards(0.25F, 1.0, false);
        TextDraw name = graphics.text.getFirst();

        // Vanilla's accented bitmap has ascent 10: SheetGlyphInfo puts its top at 7 - 10.
        assertTrue(name.y() - 3 * name.scale() > graphics.cardTop,
                "Accented name pixels must stay inside the top border");
    }

    @Test
    void skullDoesNotGrowIntoExactGraveIcon() throws Exception {
        RecordingGraphics graphics = renderCards(0.25F, 2.0, true);
        TextDraw skull = graphics.text.stream()
                .filter(draw -> draw.text().equals(FamilyTreeScreen.DECEASED_MARKER)).findFirst().orElseThrow();

        assertTrue(skull.x() + 10 * skull.scale() <= graphics.graveLeft,
                "The skull including its shadow must not overlap the grave icon");
    }

    @Test
    void atReadableZoomTextIncludingShadowStaysInsideCard() throws Exception {
        for (float zoom : new float[]{0.95F, 1.0F, 2.0F}) {
            for (double guiScale : new double[]{1.0, 2.0, 3.0, 4.0}) {
                RecordingGraphics graphics = renderCards(zoom, guiScale, false);
                assertTrue(!graphics.text.isEmpty(), "Always retain the name");
                for (TextDraw draw : graphics.text) {
                    assertTrue(draw.y() - 3 * draw.scale() > graphics.cardTop,
                            "Accented glyph top must fit at zoom " + zoom + ", GUI scale " + guiScale);
                    assertTrue(draw.y() + 10 * draw.scale() < graphics.cardBottom,
                            "Accented glyph bottom plus shadow must fit at zoom " + zoom + ", GUI scale " + guiScale);
                }
            }
        }
    }

    @Test
    void quarterZoomHitTargetsFollowExpandedCardAndGrave() {
        FamilyTreeNode deceased = node(ROOT, "Émilie");
        deceased.setDeceased(true);
        FamilyTreeLayout.Bounds bounds = new FamilyTreeLayout.Bounds(-64, 64, -32, 32);
        FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), List.of(), bounds);
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 70, 8));

        assertEquals(Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -68, 0, 0.25F));
        assertEquals(Optional.of(new FamilyTreeScreen.GraveTarget(ROOT, grave)),
                FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -54, -35, 0.25F));
        assertTrue(FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -70, 0, 0.25F).isEmpty());
        assertTrue(FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -68, 0, 1.0F).isEmpty());
    }

    private record TextDraw(String text, float x, float y, float scale) {
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased) throws Exception {
        return renderCards(zoom, guiScale, deceased, false);
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased,
                                                 boolean withRelationshipStatus) throws Exception {
        // Allocate client owners without starting a window or changing Minecraft's singleton.
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        Window window = (Window) allocator.allocateInstance(Window.class);
        setField(Window.class, window, "guiScale", guiScale);
        setField(Minecraft.class, client, "window", window);
        FamilyTreeScreen screen = (FamilyTreeScreen) allocator.allocateInstance(FamilyTreeScreen.class);
        setField(Screen.class, screen, "minecraft", client);
        setField(Screen.class, screen, "font", new Font(ignored -> null, false) {
            @Override
            public int width(String text) {
                return text.equals(FamilyTreeScreen.DECEASED_MARKER) ? 9 : text.length() * 6;
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
            }
        });
        screen.width = 801;
        screen.height = 601;
        FamilyTreeNode person = node(ROOT, "Émilie");
        person.setDeceased(deceased);
        if (withRelationshipStatus) {
            person.setRelationshipState(RelationshipState.MARRIED_TO_VILLAGER);
        }
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, zoom));
        Map<UUID, GlobalPos> graves = deceased
                ? Map.of(ROOT, GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 70, 8))) : Map.of();
        model.accept(new GetFamilyTreeResponse(request, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, person), Set.of(), Set.of(), Set.of(), graves)));
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "viewport", new FamilyTreeViewModel.ViewportState(0.3, 0.7, zoom));
        FamilyTreeLayout.Bounds bounds = new FamilyTreeLayout.Bounds(-64, 64, -32, 32);
        setField(FamilyTreeScreen.class, screen, "layout", new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), List.of(), bounds));
        setField(FamilyTreeScreen.class, screen, "cardPresentations", Map.of(ROOT,
                new FamilyTreeScreen.CardPresentation(Component.literal("Émilie"), -1,
                        Component.literal("Villager"), Component.literal("Mother"),
                        withRelationshipStatus ? Component.literal("Married") : null, true)));
        RecordingGraphics graphics = new RecordingGraphics(client);
        graphics.pose().translate(400.8, 313.2, 0);
        graphics.pose().scale(zoom, zoom, 1);
        java.lang.reflect.Method render = FamilyTreeScreen.class.getDeclaredMethod("renderCards", GuiGraphics.class, Map.class);
        render.setAccessible(true);
        MCAScreens originalScreens = MCAScreens.getInstance();
        try {
            if (originalScreens == null) {
                new MCAScreens();
            }
            render.invoke(screen, graphics, Map.of(ROOT, person));
        } finally {
            setField(MCAScreens.class, null, "INSTANCE", originalScreens);
        }
        return graphics;
    }

    private static void setField(Class<?> owner, Object target, String name, Object value) throws Exception {
        java.lang.reflect.Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class RecordingGraphics extends GuiGraphics {
        final List<TextDraw> text = new ArrayList<>();
        float cardTop;
        float cardBottom;
        float graveLeft;

        RecordingGraphics(Minecraft client) {
            super(client, null);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            cardTop = pose().last().pose().transformPosition(new Vector3f(left, top, 0)).y;
            cardBottom = pose().last().pose().transformPosition(new Vector3f(right, bottom, 0)).y;
        }

        @Override
        public void renderOutline(int x, int y, int width, int height, int color) {
        }

        @Override
        public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
            Vector3f position = pose().last().pose().transformPosition(new Vector3f(x, y, 0));
            text.add(new TextDraw(value, position.x, position.y, pose().last().pose().m00()));
            return 0;
        }

        @Override
        public void blit(ResourceLocation texture, int x, int y, int z, float u, float v,
                         int width, int height, int textureWidth, int textureHeight) {
            graveLeft = pose().last().pose().transformPosition(new Vector3f(x, y, 0)).x;
        }
    }

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
    void headerExpandsSearchToAvailableSpaceBeforeCenteredControls() {
        FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(854);

        assertEquals(854 - 5 - 72, header.doneX());
        assertEquals(5, header.searchX());
        assertEquals(316, header.searchWidth());
        assertEquals(327, header.zoomOutX());
        assertEquals(473, header.centerX());
        assertEquals(6, header.zoomOutX() - (header.searchX() + header.searchWidth()));
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
    void searchOverlayConsumesClicksAcrossLoadingEmptyAndResultRows() {
        assertTrue(FamilyTreeScreen.searchOverlayContains(20, 60, true, true, 5, 180, 0));
        assertTrue(FamilyTreeScreen.searchOverlayContains(20, 60, true, false, 5, 180, 0));
        assertTrue(FamilyTreeScreen.searchOverlayContains(20, 100, true, false, 5, 180, 3));
        assertFalse(FamilyTreeScreen.searchOverlayContains(20, 123, true, false, 5, 180, 3));
        assertFalse(FamilyTreeScreen.searchOverlayContains(190, 60, true, false, 5, 180, 3));
        assertFalse(FamilyTreeScreen.searchOverlayContains(20, 60, false, false, 5, 180, 3));
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
    void cardPresentationCacheUsesCurrentFocusForRelationships() {
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

        Map<UUID, FamilyTreeScreen.CardPresentation> presentations =
                FamilyTreeScreen.cardPresentations(OTHER, nodes, Set.of());
        FamilyTreeScreen.CardPresentation motherCard = presentations.get(ROOT);
        FamilyTreeScreen.CardPresentation childCard = presentations.get(OTHER);

        assertEquals("Elita", motherCard.name().getString());
        assertEquals(0xFFA649A4, motherCard.nameColor());
        assertEquals(
                Component.translatable("entity.minecraft.villager.mca.none"),
                motherCard.identity()
        );
        assertEquals(Component.translatable("gui.family_tree.relation.father"), motherCard.relationship());
        assertEquals(Component.translatable("marriage.married"), motherCard.relationshipState());
        assertEquals(Component.translatable("gui.family_tree.player"), childCard.identity());
        assertEquals(Component.translatable("gui.family_tree.relation.selected"), childCard.relationship());
    }

    @Test
    void orphanCardStateUsesAuthoritativeViewMetadata() {
        FamilyTreeNode child = node(OTHER, "Sima");
        FamilyTreeNode livingParent = node(ROOT, "Sug");
        child.setFather(livingParent);
        Map<UUID, FamilyTreeNode> boundedNodes = Map.of(OTHER, child);

        assertFalse(FamilyTreeScreen.cardPresentation(ROOT, child, boundedNodes, Set.of()).orphan());
        assertTrue(FamilyTreeScreen.cardPresentation(ROOT, child, boundedNodes, Set.of(OTHER)).orphan());
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
                ResourceLocation.fromNamespaceAndPath("example", "forgotten_realm")
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
                Set.of(),
                Set.of(),
                Map.of()
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
        return new FamilyTreeView(Map.of(id, node(id, "Person")), Set.of(), Set.of(), Set.of(), Map.of());
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
