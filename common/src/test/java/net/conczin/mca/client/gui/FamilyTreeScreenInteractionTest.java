package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeScreenInteractionTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID OTHER = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void selectingVisibleRelativeDoesNotLoadOrReflowTheTree() throws Exception {
        FamilyTreeNode parent = node(ROOT, "Parent");
        FamilyTreeNode child = new FamilyTreeNode(null, OTHER, "Child", false,
                Gender.MALE, ROOT, Util.NIL_UUID);
        parent.children().add(OTHER);
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(12, -17, 1.25F);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initialRequest = model.beginFocus(ROOT, viewport);
        model.accept(new GetFamilyTreeResponse(initialRequest, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, parent, OTHER, child), Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result previous = FamilyTreeLayout.layout(model.snapshot());

        FamilyTreeScreen screen = allocatedScreen(model);
        setField(FamilyTreeScreen.class, screen, "layout", previous);
        setField(FamilyTreeScreen.class, screen, "viewport", viewport);
        setField(FamilyTreeScreen.class, screen, "cardsById", Map.of(
                ROOT, previous.cards().stream().filter(card -> card.uuid().equals(ROOT)).findFirst().orElseThrow(),
                OTHER, previous.cards().stream().filter(card -> card.uuid().equals(OTHER)).findFirst().orElseThrow()));
        setField(FamilyTreeScreen.class, screen, "pendingRecenterRequestId", -1L);

        assertTrue(screen.selectPerson(OTHER));
        assertEquals(OTHER, model.focusId());
        assertFalse(model.loading(), "A click on an existing card must not issue a focus request");
        assertSame(previous, field(screen, "layout"), "Selection must retain the exact layout instance");
        assertEquals(viewport, field(screen, "viewport"), "Selection must not move the camera");
        @SuppressWarnings("unchecked")
        Map<UUID, FamilyTreeScreen.CardPresentation> cards =
                (Map<UUID, FamilyTreeScreen.CardPresentation>) field(screen, "cardPresentations");
        assertEquals(Component.translatable("gui.family_tree.relation.selected"), cards.get(OTHER).relationship());
        assertFalse(screen.selectPerson(OTHER), "Repeated selection has no effect");
        assertTrue(screen.goBack());
        assertEquals(ROOT, model.focusId());
        assertEquals(viewport, field(screen, "viewport"));
        assertFalse(model.canGoBack());
    }

    @Test
    void searchingOffscreenLoadedPersonCentersImmediatelyWithoutLoading() throws Exception {
        FamilyTreeNode parent = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        parent.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(-2000, 0, 1.0F);
        long initial = model.beginFocus(ROOT, viewport);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, parent, OTHER, child), Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result positions = FamilyTreeLayout.layout(model.snapshot());
        FamilyTreeScreen screen = allocatedScreen(model);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "searchDebouncer", new FamilyTreeSearchDebouncer(150L));
        setField(FamilyTreeScreen.class, screen, "layout", positions);
        setField(FamilyTreeScreen.class, screen, "viewport", viewport);
        setField(FamilyTreeScreen.class, screen, "cardsById", positions.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        screen.selectSearchResult(new FamilyTreeSearchEntry(OTHER, "Child", false, "", false, ""));
        assertEquals(OTHER, model.focusId());
        assertFalse(model.loading());
        assertSame(positions, field(screen, "layout"));
        assertEquals(FamilyTreeScreen.focusViewportAfterResponse(positions, viewport, OTHER),
                field(screen, "viewport"));
    }

    @Test
    void searchingAlreadyOnscreenPersonDoesNotChangeCamera() throws Exception {
        FamilyTreeNode parent = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        parent.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);
        long initial = model.beginFocus(ROOT, viewport);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, parent, OTHER, child), Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result positions = FamilyTreeLayout.layout(model.snapshot());
        FamilyTreeScreen screen = allocatedScreen(model);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "searchDebouncer", new FamilyTreeSearchDebouncer(150L));
        setField(FamilyTreeScreen.class, screen, "layout", positions);
        setField(FamilyTreeScreen.class, screen, "viewport", viewport);
        setField(FamilyTreeScreen.class, screen, "cardsById", positions.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        screen.selectSearchResult(new FamilyTreeSearchEntry(OTHER, "Child", false, "", false, ""));
        assertEquals(OTHER, model.focusId());
        assertEquals(viewport, field(screen, "viewport"));
    }

    @Test
    void expandingBranchLocksClickedCardScreenPositionUnderCanonicalLayout() throws Exception {
        UUID siblingA = uuid(307);
        UUID siblingB = uuid(308);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode father = node(OTHER, "Father");
        FamilyTreeNode firstSibling = node(siblingA, "Sibling A");
        FamilyTreeNode secondSibling = node(siblingB, "Sibling B");
        root.setFather(father);
        firstSibling.setFather(father);
        secondSibling.setFather(father);
        father.addChild(ROOT);
        father.addChild(siblingA);
        father.addChild(siblingB);

        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState initialCamera = new FamilyTreeViewModel.ViewportState(24.5, -13.0, 0.8F);
        long initial = model.beginFocus(ROOT, initialCamera);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, father, siblingA, firstSibling, siblingB, secondSibling),
                Set.of(new FamilyTreeView.Continuation(siblingB, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));

        FamilyTreeScreen screen = initializedScreen(model);
        setField(FamilyTreeScreen.class, screen, "viewport", initialCamera);
        FamilyTreeLayout.Result beforeLayout = (FamilyTreeLayout.Result) field(screen, "layout");
        FamilyTreeLayout.Bounds oldBounds = beforeLayout.cards().stream()
                .filter(card -> card.uuid().equals(siblingB)).findFirst().orElseThrow().bounds();
        double centerY = 54 + (screen.height - 54 - 30) / 2.0;
        double oldScreenX = screen.width / 2.0 + initialCamera.panX() + oldBounds.centerX() * initialCamera.zoom();
        double oldScreenY = centerY + initialCamera.panY() + oldBounds.centerY() * initialCamera.zoom();

        FamilyTreeViewModel.GenerationRequest req = model.beginGeneration(DESCENDANTS, Set.of(siblingB)).orElseThrow();
        Map<UUID, FamilyTreeNode> expansionNodes = new LinkedHashMap<>();
        FamilyTreeNode expandedSiblingB = secondSibling.detachedCopy();
        for (int i = 0; i < 6; i++) {
            UUID nieceId = uuid(400 + i);
            FamilyTreeNode niece = node(nieceId, "Niece " + i);
            niece.setFather(expandedSiblingB);
            expansionNodes.put(nieceId, niece);
        }
        expansionNodes.put(siblingB, expandedSiblingB);

        screen.acceptFamilyData(new GetFamilyTreeResponse(req.requestId(), siblingB, true,
                new FamilyTreeView(expansionNodes, Set.of(), Set.of(), Set.of(), Map.of())));

        FamilyTreeLayout.Result afterLayout = (FamilyTreeLayout.Result) field(screen, "layout");
        assertEquals(FamilyTreeLayout.layout(model.snapshot()), afterLayout,
                "Expansion must use canonical single-pass layout");
        FamilyTreeViewModel.ViewportState afterCamera = (FamilyTreeViewModel.ViewportState) field(screen, "viewport");
        FamilyTreeLayout.Bounds newBounds = afterLayout.cards().stream()
                .filter(card -> card.uuid().equals(siblingB)).findFirst().orElseThrow().bounds();
        double newScreenX = screen.width / 2.0 + afterCamera.panX() + newBounds.centerX() * afterCamera.zoom();
        double newScreenY = centerY + afterCamera.panY() + newBounds.centerY() * afterCamera.zoom();
        assertEquals(oldScreenX, newScreenX, 0.0001, "Clicked branch anchor X must remain locked on screen");
        assertEquals(oldScreenY, newScreenY, 0.0001, "Clicked branch anchor Y must remain locked on screen");
    }

    @Test
    void resizingDuringPendingOrCompletedSearchPreservesQueryFocusAndAcceptsResults() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, root), Set.of(), Set.of(), Set.of(), Map.of())));

        FamilyTreeScreen screen = initializedScreen(model);
        java.lang.reflect.Field mcField = Screen.class.getDeclaredField("minecraft");
        mcField.setAccessible(true);
        Minecraft client = (Minecraft) mcField.get(screen);

        EditBox beforeResize = (EditBox) field(screen, "searchField");
        screen.setFocused(beforeResize);
        beforeResize.setFocused(true);
        beforeResize.setValue("Alex");
        beforeResize.moveCursorTo(1, false);
        beforeResize.moveCursorTo(3, true);
        assertEquals(3, beforeResize.getCursorPosition());
        assertEquals("le", beforeResize.getHighlighted());
        assertTrue((Boolean) field(screen, "searchOpen"));
        assertTrue((Boolean) field(screen, "searchPending"));
        FamilyTreeSearchDebouncer.Request pending =
                (FamilyTreeSearchDebouncer.Request) field(screen, "expectedSearchRequest");
        assertNotNull(pending);

        screen.resize(client, 640, 480);

        EditBox afterResize = (EditBox) field(screen, "searchField");
        assertEquals("Alex", afterResize.getValue(), "Resize must preserve typed search text");
        assertEquals(3, afterResize.getCursorPosition(), "Resize must preserve search caret position");
        assertEquals("le", afterResize.getHighlighted(), "Resize must preserve selected search text");
        assertTrue(afterResize.isFocused(), "Real Screen.resize must preserve searchField focus");
        assertSame(afterResize, screen.getFocused());
        assertSame(pending, field(screen, "expectedSearchRequest"),
                "Resize must not discard or restart the in-flight search request");

        FamilyTreeSearchEntry match = new FamilyTreeSearchEntry(OTHER, "Alexandra", false, "", false, "");
        screen.setSearchResults(pending.requestId(), "Alex", List.of(match));
        assertFalse((Boolean) field(screen, "searchPending"), "Search response after resize must clear Loading state");
        assertEquals(List.of(match), field(screen, "searchResults"));

        screen.resize(client, 900, 540);
        EditBox afterSecondResize = (EditBox) field(screen, "searchField");
        assertEquals("Alex", afterSecondResize.getValue());
        assertEquals(3, afterSecondResize.getCursorPosition(), "Second resize must preserve search caret position");
        assertEquals("le", afterSecondResize.getHighlighted(), "Second resize must preserve selected search text");
        assertTrue(afterSecondResize.isFocused());
        assertEquals(List.of(match), field(screen, "searchResults"));
        assertTrue((Boolean) field(screen, "searchOpen"));
    }

    @Test
    void clientPacesRapidTreeRequestsAndRecoversFromDroppedServerReplies() throws Exception {
        UUID siblingA = uuid(501);
        UUID siblingB = uuid(502);
        UUID nieceA = uuid(503);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode father = node(OTHER, "Father");
        FamilyTreeNode firstSibling = node(siblingA, "Sibling A");
        FamilyTreeNode secondSibling = node(siblingB, "Sibling B");
        root.setFather(father);
        firstSibling.setFather(father);
        secondSibling.setFather(father);
        father.addChild(ROOT);
        father.addChild(siblingA);
        father.addChild(siblingB);

        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, father, siblingA, firstSibling, siblingB, secondSibling),
                Set.of(new FamilyTreeView.Continuation(siblingA, DESCENDANTS),
                        new FamilyTreeView.Continuation(siblingB, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));

        AtomicLong fakeClock = new AtomicLong(1_000L);
        FamilyTreeScreen screen = initializedScreen(model);
        screen.setClockForTest(fakeClock::get);

        java.lang.reflect.Field senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> requests = new ArrayList<>();
        try {
            Network.registerClientSender(requests::add);

            FamilyTreeLayout.Bounds firstTab = screen.branchControlBounds(siblingA, DESCENDANTS).orElseThrow();
            assertTrue(screen.mouseClicked(firstTab.centerX(), firstTab.centerY(), 0));
            assertEquals(1, requests.size(), "First tree request sends immediately");
            GetFamilyTreeRequest req1 = assertInstanceOf(GetFamilyTreeRequest.class, requests.getFirst());
            assertEquals(siblingA, req1.uuid());

            fakeClock.set(1_005L);
            FamilyTreeNode expandedFirst = firstSibling.detachedCopy();
            FamilyTreeNode niece = node(nieceA, "Niece A");
            niece.setFather(expandedFirst);
            screen.acceptFamilyData(new GetFamilyTreeResponse(req1.requestId(), siblingA, true,
                    new FamilyTreeView(Map.of(siblingA, expandedFirst, nieceA, niece),
                            Set.of(), Set.of(), Set.of(), Map.of())));
            assertFalse(model.loading());
            var displayed = FamilyTreeScreen.class.getDeclaredMethod("displayedLayout");
            displayed.setAccessible(true);
            FamilyTreeLayout.Result firstFrame = (FamilyTreeLayout.Result) displayed.invoke(screen);
            FamilyTreeLayout.Bounds nieceStart = firstFrame.cards().stream().filter(card -> card.uuid().equals(nieceA))
                    .findFirst().orElseThrow().bounds();

            // Click second branch only 15ms after #1 was sent (< 100ms server rate-limit).
            fakeClock.set(1_015L);
            FamilyTreeLayout.Bounds secondTab = screen.branchControlBounds(siblingB, DESCENDANTS).orElseThrow();
            assertTrue(screen.mouseClicked(secondTab.centerX(), secondTab.centerY(), 0));
            assertTrue(model.loading());
            assertEquals(1, requests.size(),
                    "Second tree request inside 150ms pacing window must be queued instead of sent immediately");

            fakeClock.set(1_149L);
            FamilyTreeLayout.Result midFrame = (FamilyTreeLayout.Result) displayed.invoke(screen);
            assertNotEquals(nieceStart, midFrame.cards().stream().filter(card -> card.uuid().equals(nieceA))
                    .findFirst().orElseThrow().bounds(), "The arriving relative must move during branch expansion");
            assertEquals(firstFrame.cards().stream().filter(card -> card.uuid().equals(siblingA)).findFirst(),
                    midFrame.cards().stream().filter(card -> card.uuid().equals(siblingA)).findFirst(),
                    "The expanded branch stays anchored while other cards move");
            screen.tick();
            assertEquals(1, requests.size(), "Queued tree request must wait for full 150ms interval");

            fakeClock.set(1_150L);
            screen.tick();
            assertEquals(2, requests.size(), "Queued tree request flushes once 150ms pacing interval elapses");
            GetFamilyTreeRequest req2 = assertInstanceOf(GetFamilyTreeRequest.class, requests.get(1));
            assertEquals(siblingB, req2.uuid());

            // Simulate dropped response: UI must recover after 5_000ms without losing the frontier.
            fakeClock.set(6_149L);
            screen.tick();
            assertTrue(model.loading());

            fakeClock.set(6_150L);
            screen.tick();
            assertFalse(model.loading(), "Unanswered tree request must time out after 5000ms");
            assertTrue(screen.branchControlBounds(siblingB, DESCENDANTS).isPresent(),
                    "Timed-out branch expansion must keep its continuation frontier retryable");
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    @Test
    void searchDebouncesRapidEditsAndTimesOutTheLatestUnansweredLookup() throws Exception {
        AtomicLong fakeClock = new AtomicLong(10_000L);
        FamilyTreeScreen screen = initializedScreen(loadedModel());
        screen.setClockForTest(fakeClock::get);

        java.lang.reflect.Field senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> requests = new ArrayList<>();
        try {
            Network.registerClientSender(requests::add);
            EditBox search = (EditBox) field(screen, "searchField");
            search.setValue("Al");
            fakeClock.set(10_050L);
            search.setValue("Alex");

            fakeClock.set(10_199L);
            screen.tick();
            assertTrue(requests.isEmpty(), "Each edit restarts the full 150ms debounce interval");

            fakeClock.set(10_200L);
            screen.tick();
            assertEquals(1, requests.size());
            FamilyTreeUUIDLookup first = assertInstanceOf(FamilyTreeUUIDLookup.class, requests.getFirst());
            assertEquals("Alex", first.search());

            search.setValue("Alexa");
            fakeClock.set(10_349L);
            screen.tick();
            assertEquals(1, requests.size());

            fakeClock.set(10_350L);
            screen.tick();
            assertEquals(2, requests.size());
            FamilyTreeUUIDLookup latest = assertInstanceOf(FamilyTreeUUIDLookup.class, requests.get(1));
            assertEquals("Alexa", latest.search());
            assertNotEquals(first.requestId(), latest.requestId());

            FamilyTreeSearchEntry match = new FamilyTreeSearchEntry(OTHER, "Alexandra", false, "", false, "");
            screen.setSearchResults(first.requestId(), first.search(), List.of(match));
            assertEquals(List.of(), field(screen, "searchResults"));
            assertTrue((Boolean) field(screen, "searchPending"));

            fakeClock.set(15_349L);
            screen.tick();
            assertTrue((Boolean) field(screen, "searchPending"));

            fakeClock.set(15_350L);
            screen.tick();
            assertFalse((Boolean) field(screen, "searchPending"),
                    "Unanswered search lookup times out 5000ms after the latest send");

            screen.setSearchResults(latest.requestId(), latest.search(), List.of(match));
            assertEquals(List.of(), field(screen, "searchResults"), "A reply after timeout is ignored");
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    @Test
    void escapeAndOutsideClickDismissSearchPopupBeforeClosingScreen() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, root), Set.of(), Set.of(), Set.of(), Map.of())));

        boolean[] closed = {false};
        FamilyTreeScreen screen = new FamilyTreeScreen(ROOT) {
            @Override
            public void onClose() {
                closed[0] = true;
            }
        };
        Unsafe allocator = unsafe();
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        Window window = (Window) allocator.allocateInstance(Window.class);
        setField(Window.class, window, "guiScale", 1.0);
        setField(Minecraft.class, client, "window", window);
        setField(Screen.class, screen, "minecraft", client);
        setField(Screen.class, screen, "font", testFont());
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        screen.width = 800;
        screen.height = 600;
        screen.init();

        EditBox search = (EditBox) field(screen, "searchField");
        search.setValue("Alex");
        assertTrue((Boolean) field(screen, "searchOpen"));

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertFalse((Boolean) field(screen, "searchOpen"), "First Escape must dismiss the search popup");
        assertFalse((Boolean) field(screen, "searchPending"));
        assertFalse(closed[0], "First Escape must not close FamilyTreeScreen while popup was open");

        assertTrue(screen.keyPressed(InputConstants.KEY_ESCAPE, 0, 0));
        assertTrue(closed[0], "Second Escape after popup dismissal must close the screen");

        closed[0] = false;
        search.setValue("Bob");
        assertTrue((Boolean) field(screen, "searchOpen"));
        assertTrue(screen.mouseClicked(400, 300, 0), "Clicking outside on canvas must dismiss popup");
        assertFalse((Boolean) field(screen, "searchOpen"));
        assertFalse(closed[0]);
    }

    @Test
    void generationControlsIgnoreUnfinishedBranchesOnTheOppositeSideOfTheRoot() throws Exception {
        UUID childId = uuid(303);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Parent");
        FamilyTreeNode child = node(childId, "Child");
        child.setFather(node(uuid(305), "Other parent"));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, childId, child), Set.of(
                new FamilyTreeView.Continuation(OTHER, DESCENDANTS),
                new FamilyTreeView.Continuation(childId, ANCESTORS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = allocatedScreen(model);
        Map<UUID, FamilyTreeLayout.Card> cards = Map.of(
                ROOT, new FamilyTreeLayout.Card(ROOT, new FamilyTreeLayout.Bounds(-64, 64, -32, 32)),
                OTHER, new FamilyTreeLayout.Card(OTHER, new FamilyTreeLayout.Bounds(-64, 64, -152, -88)),
                childId, new FamilyTreeLayout.Card(childId, new FamilyTreeLayout.Bounds(-64, 64, 88, 152)));
        setField(FamilyTreeScreen.class, screen, "cardsById", cards);
        java.lang.reflect.Method directions = FamilyTreeScreen.class.getDeclaredMethod("availableGenerationDirections");
        directions.setAccessible(true);

        assertEquals(Set.of(), directions.invoke(screen),
                "Descendants of newly loaded ancestors and ancestors of descendants should not activate opposite controls");
    }

    @Test
    void unfinishedSideBranchIsAvailableBeforeSelectingItsCard() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertEquals(ROOT, model.focusId());
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isPresent(),
                "An unfinished side-branch action must be visible before a person is selected");
    }

    @Test
    void clickingOneOfTwoUnselectedSideBranchesUsesItsOwnCardAsAnchor() throws Exception {
        UUID siblingA = uuid(307);
        UUID siblingB = uuid(308);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Parent");
        FamilyTreeNode first = node(siblingA, "Sibling A");
        FamilyTreeNode second = node(siblingB, "Sibling B");
        root.setFather(parent);
        first.setFather(parent);
        second.setFather(parent);
        parent.addChild(ROOT);
        parent.addChild(siblingA);
        parent.addChild(siblingB);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingA, first, siblingB, second),
                Set.of(new FamilyTreeView.Continuation(siblingA, DESCENDANTS),
                        new FamilyTreeView.Continuation(siblingB, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        FamilyTreeLayout.Bounds firstButton = screen.branchControlBounds(siblingA, DESCENDANTS).orElseThrow();
        FamilyTreeLayout.Bounds secondButton = screen.branchControlBounds(siblingB, DESCENDANTS).orElseThrow();
        assertNotEquals(firstButton, secondButton);
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(siblingA, DESCENDANTS)),
                screen.branchControlAt(firstButton.centerX(), firstButton.centerY()));
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(siblingB, DESCENDANTS)),
                screen.branchControlAt(secondButton.centerX(), secondButton.centerY()));

        java.lang.reflect.Field senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> requests = new ArrayList<>();
        try {
            Network.registerClientSender(requests::add);
            assertTrue(screen.mouseClicked(secondButton.centerX(), secondButton.centerY(), 0));
            assertEquals(1, requests.size());
            assertEquals(siblingB, assertInstanceOf(GetFamilyTreeRequest.class, requests.getFirst()).uuid());
            assertEquals(ROOT, model.focusId(), "Exploring a side branch must preserve the current selection");
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    @Test
    void selectingAncestorExposesOnlyItsUnloadedDescendantBranch() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertTrue(screen.selectPerson(OTHER));
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isPresent(),
                "The selected ancestor must have an attached descendant branch control");
        assertTrue(model.beginGeneration(DESCENDANTS, Set.of(OTHER)).isPresent(),
                "The action must load only the selected ancestor's unfinished descendants");
    }

    @Test
    void selectedDescendantBranchControlFollowsTheCardThroughZoomAndPan() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(request, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);

        FamilyTreeLayout.Bounds initial = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
        assertEquals(8, initial.right() - initial.left(),
                "The attached action should be smaller than the person card at normal zoom");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(initial.centerX(), initial.centerY()));
        assertTrue(screen.branchControlAt(initial.centerX(), initial.top() - 1).isEmpty(),
                "Only the actual button should activate a branch");
        assertTrue(screen.children().stream().noneMatch(child -> child instanceof ButtonWidget button
                        && button.getMessage().equals(Component.translatable("gui.family_tree.explore_descendants"))),
                "Explore descendants must be attached to its person on the canvas, not the footer");

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(111, -47, 0.5F));
        FamilyTreeLayout.Bounds moved = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
        assertEquals(8, moved.right() - moved.left(),
                "The attached action should remain the same readable pixel size across zoom");
        assertNotEquals(initial, moved);
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(moved.centerX(), moved.centerY()));
        assertTrue(screen.branchControlAt(initial.centerX(), initial.centerY()).isEmpty());
    }

    @Test
    void attachedTabKeepsItsFullHeightFromTheCardEdgeDuringFractionalPan() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(request, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model, 6.0);
        FamilyTreeLayout.Result layout = (FamilyTreeLayout.Result) field(screen, "layout");
        FamilyTreeLayout.Bounds ancestorBounds = layout.cards().stream()
                .filter(card -> card.uuid().equals(OTHER)).findFirst().orElseThrow().bounds();
        double originY = 54 + (screen.height - 54 - 30) / 2.0;
        double originX = screen.width / 2.0;
        float zoom = 0.162F;
        int scale = 6;
        int height = 4 * scale;
        FamilyTreeScreen.BranchTarget expected = new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS);

        for (double pan : new double[]{0.0, 0.18, 0.36, 0.55, 0.72, 0.9}) {
            setField(FamilyTreeScreen.class, screen, "viewport",
                    new FamilyTreeViewModel.ViewportState(0.35, pan, zoom));
            FamilyTreeLayout.Bounds card = FamilyTreeScreen.guiBounds(ancestorBounds,
                    (originX + 0.35) * scale, (originY + pan) * scale, zoom * scale);
            FamilyTreeLayout.Bounds tab = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
            assertEquals(card.centerX(), tab.centerX());
            assertEquals(card.bottom(), tab.top());
            assertEquals(height, tab.bottom() - tab.top());
            double centerX = tab.centerX() / (double) scale;
            double lastTabPixel = (tab.bottom() - 0.5) / scale;
            assertEquals(Optional.of(expected), screen.branchControlAt(centerX, lastTabPixel),
                    "The tab must remain attached in GUI coordinates at pan " + pan);
            assertTrue(screen.branchControlAt(centerX, (tab.bottom() + 0.5) / scale).isEmpty(),
                    "The click target must end with the tab at pan " + pan);
        }
    }

    @Test
    void clickingAttachedDescendantControlRequestsOnlyThatBranchAndBlocksRepeatWhileLoading() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        FamilyTreeLayout.Bounds button = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();

        java.lang.reflect.Field senderField = Network.class.getDeclaredField("clientSender");
        senderField.setAccessible(true);
        Network.ClientSender previousSender = (Network.ClientSender) senderField.get(null);
        List<HandleablePayload> requests = new ArrayList<>();
        try {
            Network.registerClientSender(requests::add);
            assertTrue(screen.mouseClicked(button.centerX(), button.centerY(), 0));
            assertEquals(1, requests.size());
            GetFamilyTreeRequest request = assertInstanceOf(GetFamilyTreeRequest.class, requests.getFirst());
            assertEquals(OTHER, request.uuid(), "The clicked card is the sole expansion anchor");
            assertEquals(0, request.ancestorDepth());
            assertEquals(1, request.descendantDepth());
            assertTrue(request.oneGeneration());
            assertTrue(model.loading());
            assertEquals(Optional.of(Component.translatable("gui.family_tree.loading_family")),
                    FamilyTreeScreen.statusMessage(model, false, List.of()),
                    "Blocked repeat clicks need a visible loading explanation");

            assertTrue(screen.mouseClicked(button.centerX(), button.centerY(), 0));
            assertEquals(1, requests.size(), "The disabled button must not send a second request");
        } finally {
            Network.registerClientSender(previousSender);
        }
    }

    @Test
    void attachedControlFitsBetweenRowsAndClippedPixelsCannotBeClicked() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        float screenshotZoom = 0.162F;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, screenshotZoom));
        FamilyTreeLayout.Bounds button = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
        FamilyTreeLayout.Result layout = (FamilyTreeLayout.Result) field(screen, "layout");
        FamilyTreeLayout.Bounds parent = layout.cards().stream().filter(card -> card.uuid().equals(OTHER))
                .findFirst().orElseThrow().bounds();
        FamilyTreeLayout.Bounds child = layout.cards().stream().filter(card -> card.uuid().equals(ROOT))
                .findFirst().orElseThrow().bounds();
        assertEquals(parent.centerX(), child.centerX(), "Reproduce the next card directly under the control");
        double originY = 54 + (screen.height - 54 - 30) / 2.0 - 65;
        assertTrue(button.bottom() <= originY + child.top() * (double) screenshotZoom,
                "The branch control must clear the card in the next row");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(button.centerX(), button.centerY()));
        assertTrue(screen.branchControlAt(button.left() - 1, button.centerY()).isEmpty(),
                "The click target must not exceed the rendered button");

        float lowerZoom = 0.14F;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, lowerZoom));
        FamilyTreeLayout.Bounds smaller = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow(
                () -> new AssertionError("The tab should still be usable at 14% zoom"));
        assertTrue(smaller.bottom() <= originY + child.top() * (double) lowerZoom,
                "The smaller branch tab must clear the next row");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(smaller.centerX(), smaller.centerY()));

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, 0.01F));
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isEmpty(),
                "A subpixel row gap cannot contain a usable attached action");

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65 + 52 - button.top(), screenshotZoom));
        FamilyTreeLayout.Bounds clipped = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
        assertEquals(52, clipped.top());
        assertTrue(screen.branchControlAt(clipped.centerX(), 53).isEmpty(),
                "A button pixel clipped by the header must not receive input");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(clipped.centerX(), 55));

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113,
                        -65 + (screen.height - 32) - button.top(), screenshotZoom));
        FamilyTreeLayout.Bounds footerClipped = screen.branchControlBounds(OTHER, DESCENDANTS).orElseThrow();
        assertEquals(screen.height - 32, footerClipped.top());
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, DESCENDANTS)),
                screen.branchControlAt(footerClipped.centerX(), screen.height - 31));
        assertTrue(screen.branchControlAt(footerClipped.centerX(), screen.height - 29).isEmpty(),
                "A button pixel clipped by the footer must not receive input");
    }

    @Test
    void selectingRootLeavesUnfinishedSideBranchAvailable() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);
        screen.selectPerson(ROOT);

        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isPresent());
    }

    @Test
    void descendantCanExploreMissingAncestorsWithoutChangingTheGlobalAncestorsControl() throws Exception {
        UUID childId = uuid(307);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode child = node(childId, "Child");
        root.addChild(childId);
        child.setMother(node(OTHER, "Other parent"));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, childId, child),
                Set.of(new FamilyTreeView.Continuation(childId, ANCESTORS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertTrue(screen.selectPerson(childId));
        assertTrue(screen.branchControlBounds(childId, ANCESTORS).isPresent());
        assertFalse(model.hasGenerationFrontier(ANCESTORS, Set.of(ROOT)));
    }

    @Test
    void siblingsChildrenUseSelectedBranchInsteadOfGlobalDescendants() throws Exception {
        UUID siblingId = uuid(308);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Parent");
        FamilyTreeNode sibling = node(siblingId, "Sibling");
        root.setFather(parent);
        sibling.setFather(parent);
        parent.addChild(ROOT);
        parent.addChild(siblingId);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingId, sibling),
                Set.of(new FamilyTreeView.Continuation(siblingId, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        java.lang.reflect.Method available = FamilyTreeScreen.class.getDeclaredMethod("availableGenerationDirections");
        available.setAccessible(true);
        assertFalse(((Set<?>) available.invoke(screen)).contains(DESCENDANTS),
                "The global button must not expand a sibling's separate family branch");
        assertTrue(screen.selectPerson(siblingId));
        assertTrue(screen.branchControlBounds(siblingId, DESCENDANTS).isPresent());
    }

    @Test
    void halfSiblingsOtherParentUsesSelectedBranchInsteadOfGlobalAncestors() throws Exception {
        UUID siblingId = uuid(312);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Shared parent");
        FamilyTreeNode sibling = node(siblingId, "Half sibling");
        root.setFather(parent);
        sibling.setFather(parent);
        sibling.setMother(node(uuid(313), "Other parent"));
        parent.addChild(ROOT);
        parent.addChild(siblingId);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingId, sibling),
                Set.of(new FamilyTreeView.Continuation(siblingId, ANCESTORS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        java.lang.reflect.Method available = FamilyTreeScreen.class.getDeclaredMethod("availableGenerationDirections");
        available.setAccessible(true);
        assertFalse(((Set<?>) available.invoke(screen)).contains(ANCESTORS),
                "The global button must follow the root's ancestry, not a half sibling's other parent");
        assertTrue(screen.selectPerson(siblingId));
        assertTrue(screen.branchControlBounds(siblingId, ANCESTORS).isPresent());
    }

    @Test
    void selectedSideBranchCanExposeBothUnfinishedDirections() throws Exception {
        UUID siblingId = uuid(308);
        UUID cousinId = uuid(309);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Parent");
        FamilyTreeNode sibling = node(siblingId, "Sibling");
        FamilyTreeNode cousin = node(cousinId, "Cousin");
        root.setFather(parent);
        sibling.setFather(parent);
        parent.addChild(ROOT);
        parent.addChild(siblingId);
        sibling.addChild(cousinId);
        cousin.setFather(sibling);
        cousin.setMother(node(uuid(310), "Unloaded parent"));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingId, sibling, cousinId, cousin),
                Set.of(new FamilyTreeView.Continuation(cousinId, ANCESTORS),
                        new FamilyTreeView.Continuation(cousinId, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertTrue(screen.selectPerson(cousinId));
        assertTrue(screen.branchControlBounds(cousinId, ANCESTORS).isPresent());
        assertTrue(screen.branchControlBounds(cousinId, DESCENDANTS).isPresent());
    }

    @Test
    void fullyLoadedSelectedBranchHidesItsContextualAction() throws Exception {
        UUID uncleId = uuid(306);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(
                DESCENDANTS, Set.of(OTHER)).orElseThrow();
        FamilyTreeNode uncle = node(uncleId, "Uncle");
        uncle.setFather(ancestor);
        FamilyTreeNode updatedAncestor = ancestor.detachedCopy();
        updatedAncestor.addChild(uncleId);
        model.acceptGeneration(new GetFamilyTreeResponse(request.requestId(), OTHER, true,
                new FamilyTreeView(Map.of(OTHER, updatedAncestor, uncleId, uncle), Set.of(), Set.of(), Set.of(), Map.of())));
        screen.selectPerson(ROOT);
        screen.selectPerson(OTHER);

        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isEmpty());
    }

    @Test
    void missingGenerationAnchorImmediatelyHidesItsContextualActionWithoutReflow() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isPresent());
        FamilyTreeLayout.Result before = (FamilyTreeLayout.Result) field(screen, "layout");

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(
                DESCENDANTS, Set.of(OTHER)).orElseThrow();
        screen.acceptFamilyData(new GetFamilyTreeResponse(request.requestId(), OTHER, false, FamilyTreeView.empty()));

        assertFalse(model.loading());
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isEmpty(),
                "Retired model frontiers must stop exposing the arrow on the very next screen query");
        assertSame(before, field(screen, "layout"),
                "No new family records should preserve card geometry without a layout rebuild");
    }

    @Test
    void generationResponseRefreshesCardNameDuringAnExistingLayoutTransition() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root), Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        AtomicLong clock = new AtomicLong(1_000L);
        screen.setClockForTest(clock::get);

        FamilyTreeViewModel.GenerationRequest expandRoot = model.beginGeneration(
                DESCENDANTS, Set.of(ROOT)).orElseThrow();
        FamilyTreeNode child = node(OTHER, "Child");
        root.addChild(OTHER);
        child.setFather(root);
        screen.acceptFamilyData(new GetFamilyTreeResponse(expandRoot.requestId(), ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, child), Set.of(new FamilyTreeView.Continuation(OTHER, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));

        clock.addAndGet(120L);
        Object layout = field(screen, "layout");
        Object transition = field(screen, "transition");
        assertNotNull(transition);
        java.lang.reflect.Method displayedLayout = FamilyTreeScreen.class.getDeclaredMethod("displayedLayout");
        displayedLayout.setAccessible(true);
        Object displayed = displayedLayout.invoke(screen);
        java.lang.reflect.Method render = FamilyTreeScreen.class.getDeclaredMethod(
                "renderCards", GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class);
        render.setAccessible(true);
        RecordingGraphics graphics = new RecordingGraphics((Minecraft) unsafe().allocateInstance(Minecraft.class));
        render.invoke(screen, graphics, model.nodes(), displayed);
        assertTrue(graphics.text.stream().map(TextDraw::text).anyMatch("Child"::equals));

        FamilyTreeViewModel.GenerationRequest expandChild = model.beginGeneration(
                DESCENDANTS, Set.of(OTHER)).orElseThrow();
        child.setName("Beatrice");
        screen.acceptFamilyData(new GetFamilyTreeResponse(expandChild.requestId(), OTHER, true, new FamilyTreeView(
                Map.of(OTHER, child), Set.of(), Set.of(), Set.of(), Map.of())));

        assertEquals("Beatrice", model.nodes().get(OTHER).getName());
        graphics.text.clear();
        render.invoke(screen, graphics, model.nodes(), displayedLayout.invoke(screen));
        List<String> labels = graphics.text.stream().map(TextDraw::text).toList();
        assertTrue(labels.contains("Beatrice"), () -> "Expected updated name in rendered labels: " + labels);
        assertFalse(labels.contains("Child"));
        assertSame(layout, field(screen, "layout"));
        assertSame(transition, field(screen, "transition"));
        assertEquals(displayed, displayedLayout.invoke(screen));
    }

    @Test
    void generationAvailabilityIsReusedAcrossRedrawAndZoomButRefreshedOnStateChanges() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode child = node(OTHER, "Child");
        root.addChild(OTHER);
        child.setFather(root);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, child),
                Set.of(new FamilyTreeView.Continuation(ROOT, DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        java.lang.reflect.Method availability = FamilyTreeScreen.class.getDeclaredMethod("generationAvailability");
        availability.setAccessible(true);

        Object initialAvailability = availability.invoke(screen);
        assertSame(initialAvailability, availability.invoke(screen),
                "Redraw and hover should reuse the relationship traversal result");
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(84, -20, 0.01F));
        assertSame(initialAvailability, availability.invoke(screen),
                "Pan and zoom cannot change which family branches need loading");

        assertTrue(screen.selectPerson(OTHER));
        Object selectedAvailability = availability.invoke(screen);
        assertNotSame(initialAvailability, selectedAvailability,
                "Selecting another card should refresh the contextual branch availability");
        assertSame(selectedAvailability, availability.invoke(screen));

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(
                DESCENDANTS, Set.of(ROOT)).orElseThrow();
        screen.acceptFamilyData(new GetFamilyTreeResponse(request.requestId(), ROOT, false, FamilyTreeView.empty()));
        assertNotSame(selectedAvailability, availability.invoke(screen),
                "A terminal generation response without a layout change must refresh availability");
        assertTrue(screen.branchControlBounds(OTHER, DESCENDANTS).isEmpty());
    }

    private static FamilyTreeScreen initializedScreen(FamilyTreeViewModel model) throws Exception {
        return initializedScreen(model, 1.0);
    }

    private static FamilyTreeScreen initializedScreen(FamilyTreeViewModel model, double guiScale) throws Exception {
        FamilyTreeScreen screen = allocatedScreen(model);
        Unsafe allocator = unsafe();
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        Window window = (Window) allocator.allocateInstance(Window.class);
        setField(Window.class, window, "guiScale", guiScale);
        setField(Minecraft.class, client, "window", window);
        setField(Minecraft.class, client, "lastInputType", net.minecraft.client.InputType.KEYBOARD_TAB);
        Font font = testFont();
        setField(Minecraft.class, client, "font", font);
        setField(Screen.class, screen, "minecraft", client);
        screen.width = 800;
        screen.height = 600;
        setField(Screen.class, screen, "children", new ArrayList<>());
        setField(Screen.class, screen, "renderables", new ArrayList<>());
        setField(Screen.class, screen, "narratables", new ArrayList<>());
        setField(Screen.class, screen, "font", font);
        setField(FamilyTreeScreen.class, screen, "searchDebouncer", new FamilyTreeSearchDebouncer(150L));
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        setField(FamilyTreeScreen.class, screen, "viewport", new FamilyTreeViewModel.ViewportState(0, 0, 1));
        screen.init();
        return screen;
    }

    private static Font testFont() {
        return new Font(ignored -> null, false) {
            @Override
            public int width(String text) {
                return text.length() * 6;
            }

            @Override
            public int width(net.minecraft.network.chat.FormattedText text) {
                return text.getString().length() * 6;
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
            }
        };
    }

    private static FamilyTreeScreen allocatedScreen(FamilyTreeViewModel model) throws Exception {
        FamilyTreeScreen screen = (FamilyTreeScreen) unsafe().allocateInstance(FamilyTreeScreen.class);
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        return screen;
    }

    private static Unsafe unsafe() throws Exception {
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }

    @Test
    void backAfterExternalSearchRestoresExactExpandedCardPositionsAndViewport() throws Exception {
        FamilyTreeNode root = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        root.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState camera =
                new FamilyTreeViewModel.ViewportState(73, -41, 0.8F);
        long initial = model.beginFocus(ROOT, camera);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, root, OTHER, child),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result canonical = FamilyTreeLayout.layout(model.snapshot());
        List<FamilyTreeLayout.Card> expanded = canonical.cards().stream()
                .map(card -> card.uuid().equals(OTHER) ? new FamilyTreeLayout.Card(OTHER,
                        new FamilyTreeLayout.Bounds(card.bounds().left() + 256,
                                card.bounds().right() + 256,
                                card.bounds().top(), card.bounds().bottom())) : card)
                .toList();
        FamilyTreeLayout.Result anchoredExpandedView = new FamilyTreeLayout.Result(
                expanded, canonical.edges(), canonical.contentBounds());

        FamilyTreeScreen screen = allocatedScreen(model);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "layout", anchoredExpandedView);
        setField(FamilyTreeScreen.class, screen, "viewport", camera);
        setField(FamilyTreeScreen.class, screen, "pendingRecenterRequestId", -1L);

        UUID unrelated = uuid(999);
        UUID superseded = uuid(998);
        long earlierSearch = screen.startRootFocus(superseded);
        long search = screen.startRootFocus(unrelated);
        screen.acceptFamilyData(new GetFamilyTreeResponse(earlierSearch, superseded, true,
                new FamilyTreeView(Map.of(superseded, node(superseded, "Superseded family")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertSame(anchoredExpandedView, field(screen, "layout"));
        assertEquals(ROOT, model.layoutRootId());
        screen.acceptFamilyData(new GetFamilyTreeResponse(search, unrelated, true,
                new FamilyTreeView(Map.of(unrelated, node(unrelated, "Other family")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertEquals(unrelated, model.layoutRootId());
        assertTrue(screen.goBack());

        assertEquals(ROOT, model.layoutRootId());
        assertSame(anchoredExpandedView, field(screen, "layout"),
                "Back must restore the prior layout");
        assertEquals(camera, field(screen, "viewport"));
        assertFalse(model.canGoBack(), "Replacing a pending search should save the original view once");
    }

    @Test
    void failedOrCancelledRootSearchLeavesNoStrayLayoutHistory() throws Exception {
        FamilyTreeNode parent = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        parent.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState camera = new FamilyTreeViewModel.ViewportState(14, -21, 1.2F);
        long firstRequest = model.beginFocus(ROOT, camera);
        model.accept(new GetFamilyTreeResponse(firstRequest, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, parent, OTHER, child),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result original = FamilyTreeLayout.layout(model.snapshot());

        FamilyTreeScreen screen = allocatedScreen(model);
        setField(FamilyTreeScreen.class, screen, "layout", original);
        setField(FamilyTreeScreen.class, screen, "viewport", camera);
        setField(FamilyTreeScreen.class, screen, "pendingRecenterRequestId", -1L);
        setField(FamilyTreeScreen.class, screen, "cardsById", original.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        UUID missing = uuid(301);
        long failed = screen.startRootFocus(missing);
        screen.acceptFamilyData(new GetFamilyTreeResponse(failed, missing, false, FamilyTreeView.empty()));
        assertSame(original, field(screen, "layout"));
        assertEquals(camera, field(screen, "viewport"));
        assertEquals(ROOT, model.layoutRootId());
        assertFalse(model.canGoBack());

        long cancelled = screen.startRootFocus(uuid(302));
        assertTrue(screen.selectPerson(OTHER));
        screen.acceptFamilyData(new GetFamilyTreeResponse(cancelled, uuid(302), true,
                new FamilyTreeView(Map.of(uuid(302), node(uuid(302), "Unrelated")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertEquals(ROOT, model.layoutRootId());
        assertEquals(OTHER, model.focusId());
        assertSame(original, field(screen, "layout"));
        assertTrue(screen.goBack());
        assertEquals(ROOT, model.focusId());
        assertEquals(camera, field(screen, "viewport"));
        assertFalse(model.canGoBack());

        UUID unrelated = uuid(303);
        long search = screen.startRootFocus(unrelated);
        screen.acceptFamilyData(new GetFamilyTreeResponse(search, unrelated, true,
                new FamilyTreeView(Map.of(unrelated, node(unrelated, "Another family")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertTrue(screen.goBack());
        assertSame(original, field(screen, "layout"));
        assertEquals(camera, field(screen, "viewport"));
        assertFalse(model.canGoBack());
    }

    private static Object field(FamilyTreeScreen screen, String name) throws Exception {
        java.lang.reflect.Field field = FamilyTreeScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    @Test
    void cardTextScalesContinuouslyThroughQuarterZoom() throws Exception {
        for (float zoom : new float[]{0.05F, 0.1F, 0.24F, 0.25F, 0.5F, 0.75F, 0.95F, 1.05F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false);
            assertEquals(2, graphics.fills.size(), "Card border and background must remain visible at " + zoom);
            assertEquals("Émilie", graphics.text.getFirst().text());
            assertEquals(zoom, graphics.text.getFirst().scale(), 0.0001,
                    "Text and cards must share the same zoom at " + zoom);
        }
    }

    @Test
    void cardPresentationCachesWrappedStringsAcrossRepeatedFrames() throws Exception {
        AtomicInteger substrCalls = new AtomicInteger();
        Font countingFont = new Font(ignored -> null, false) {
            @Override
            public int width(String text) {
                return text.length() * 6;
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                substrCalls.incrementAndGet();
                return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
            }
        };

        FamilyTreeNode person = node(ROOT, "Alexandra Maria Louise");
        Map<UUID, FamilyTreeScreen.CardPresentation> cached =
                FamilyTreeScreen.cardPresentations(countingFont, ROOT, Map.of(ROOT, person), Set.of(ROOT), Map.of());
        FamilyTreeScreen.CardPresentation presentation = cached.get(ROOT);
        assertNotNull(presentation.formatted(), "CardPresentation must pre-compute wrapped/ellipsized strings");
        assertEquals(List.of("Alexandra Maria", "Louise"), presentation.formatted().nameLines());
        int callsDuringBuild = substrCalls.get();
        assertTrue(callsDuringBuild > 0);

        Unsafe allocator = unsafe();
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        FamilyTreeScreen screen = allocatedScreen(loadedModel());
        screen.width = 800;
        screen.height = 600;
        setField(Screen.class, screen, "font", countingFont);
        setField(FamilyTreeScreen.class, screen, "viewport", new FamilyTreeViewModel.ViewportState(0, 0, 1.0F));
        setField(FamilyTreeScreen.class, screen, "cardPresentations", cached);
        FamilyTreeLayout.Bounds bounds = new FamilyTreeLayout.Bounds(-64, 64, -32, 32);
        FamilyTreeLayout.Result singleCard = new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), bounds);

        java.lang.reflect.Method render = FamilyTreeScreen.class.getDeclaredMethod(
                "renderCards", GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class);
        render.setAccessible(true);
        for (int frame = 0; frame < 3; frame++) {
            RecordingGraphics graphics = new RecordingGraphics(client);
            render.invoke(screen, graphics, Map.of(ROOT, person), singleCard);
            assertFalse(graphics.text.isEmpty());
        }
        assertEquals(callsDuringBuild, substrCalls.get(),
                "Repeated renderCards frames must reuse cached FormattedCardText without re-wrapping");
    }

    @Test
    void guiStrokeProjectionPreservesVisibleOutlineAndConnectedLinesAcrossZooms() {
        FamilyTreeLayout.Bounds card = new FamilyTreeLayout.Bounds(0, 128, 0, 64);
        for (float zoom : new float[]{0.01F, 0.25F, 0.75F, 1.0F, 2.0F}) {
            FamilyTreeLayout.Bounds outline = FamilyTreeScreen.guiBounds(card, 400.3, 300.7, zoom);
            FamilyTreeLayout.Bounds horizontal = FamilyTreeScreen.guiHorizontalStroke(
                    0, 128, 0, 400.3, 300.7, zoom);
            FamilyTreeLayout.Bounds vertical = FamilyTreeScreen.guiVerticalStroke(
                    0, 0, 64, 400.3, 300.7, zoom);

            assertTrue(outline.right() > outline.left() && outline.bottom() > outline.top());
            assertEquals(1, horizontal.bottom() - horizontal.top(), "Horizontal line must be one GUI pixel");
            assertEquals(1, vertical.right() - vertical.left(), "Vertical line must be one GUI pixel");
            assertEquals(outline.left(), horizontal.left());
            assertEquals(outline.top(), horizontal.top());
            assertEquals(outline.left(), vertical.left());
            assertEquals(outline.top(), vertical.top());
        }
        assertEquals(new FamilyTreeLayout.Bounds(400, 402, 301, 302),
                FamilyTreeScreen.guiBounds(new FamilyTreeLayout.Bounds(0, 128, 0, 64),
                        400.3, 300.7, 0.01F));
    }

    @Test
    void cardFillsBordersAndConnectorsShareTheFramebufferGridAtOverviewZoom() throws Exception {
        Unsafe allocator = unsafe();
        FamilyTreeScreen screen = allocatedScreen(new FamilyTreeViewModel(ROOT));
        screen.width = 801;
        screen.height = 601;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(0.3, 0.7, 0.04F));

        FamilyTreeLayout.Card parent = new FamilyTreeLayout.Card(ROOT,
                new FamilyTreeLayout.Bounds(-64, 64, -32, 32));
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(OTHER,
                new FamilyTreeLayout.Bounds(64, 192, 88, 152));
        FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(List.of(parent, child),
                List.of(new FamilyTreeLayout.Edge(ROOT, OTHER, FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 192, -32, 152));

        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        RecordingGraphics graphics = new RecordingGraphics(client);
        java.lang.reflect.Method lines = FamilyTreeScreen.class.getDeclaredMethod("renderEdgeStrokes",
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, Map.class, float.class);
        java.lang.reflect.Method frames = FamilyTreeScreen.class.getDeclaredMethod("renderCardFrames",
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, float.class);
        lines.setAccessible(true);
        frames.setAccessible(true);
        Map<UUID, FamilyTreeNode> nodes = Map.of(ROOT, node(ROOT, "Parent"), OTHER, node(OTHER, "Child"));
        lines.invoke(screen, graphics, nodes, layout, Map.of(ROOT, parent, OTHER, child), 4.0F);
        frames.invoke(screen, graphics, nodes, layout, 4.0F);

        assertEquals(7, graphics.fills.size(), "Three connector segments plus a border and background per card");
        assertTrue(graphics.fills.stream().allMatch(rect -> rect.right() > rect.left()
                && rect.bottom() > rect.top()), "No stroke may vanish to a zero-area rectangle");
        assertTrue(graphics.fills.stream().anyMatch(rect -> rect.right() - rect.left() == 1
                && rect.bottom() - rect.top() > 1));
        assertTrue(graphics.fills.stream().anyMatch(rect -> rect.bottom() - rect.top() == 1
                && rect.right() - rect.left() > 1));
        for (int frame : new int[]{3, 5}) {
            FamilyTreeLayout.Bounds outer = graphics.fills.get(frame);
            assertEquals(new FamilyTreeLayout.Bounds(outer.left() + 1, outer.right() - 1,
                    outer.top() + 1, outer.bottom() - 1), graphics.fills.get(frame + 1));
        }
        FamilyTreeLayout.Bounds parentFrame = graphics.fills.get(3);
        assertTrue(graphics.fills.subList(0, 3).stream().anyMatch(stroke -> stroke.top() == parentFrame.bottom()
                && stroke.left() >= parentFrame.left() && stroke.right() <= parentFrame.right()));
    }

    @Test
    void unrelatedFamiliesNeverShareAnOverlappingHorizontalConnectorRail() throws Exception {
        Unsafe allocator = unsafe();
        FamilyTreeScreen screen = allocatedScreen(new FamilyTreeViewModel(ROOT));
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(0, 0, 1));
        UUID childA = uuid(601);
        UUID childB = uuid(602);
        FamilyTreeLayout.Card parentA = new FamilyTreeLayout.Card(ROOT,
                new FamilyTreeLayout.Bounds(-64, 64, -32, 32));
        FamilyTreeLayout.Card parentB = new FamilyTreeLayout.Card(OTHER,
                new FamilyTreeLayout.Bounds(336, 464, -32, 32));
        FamilyTreeLayout.Card firstChild = new FamilyTreeLayout.Card(childA,
                new FamilyTreeLayout.Bounds(536, 664, 88, 152));
        FamilyTreeLayout.Card secondChild = new FamilyTreeLayout.Card(childB,
                new FamilyTreeLayout.Bounds(136, 264, 88, 152));
        FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
                List.of(parentA, parentB, firstChild, secondChild),
                List.of(new FamilyTreeLayout.Edge(ROOT, childA, FamilyTreeLayout.EdgeType.PARENT_CHILD, null),
                        new FamilyTreeLayout.Edge(OTHER, childB, FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 664, -32, 152));
        java.lang.reflect.Method lines = FamilyTreeScreen.class.getDeclaredMethod("renderEdgeStrokes",
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, Map.class, float.class);
        lines.setAccessible(true);
        for (float zoom : new float[]{0.16F, 0.28F, 1.0F}) {
            setField(FamilyTreeScreen.class, screen, "viewport",
                    new FamilyTreeViewModel.ViewportState(0, 0, zoom));
            RecordingGraphics graphics = new RecordingGraphics((Minecraft) allocator.allocateInstance(Minecraft.class));
            lines.invoke(screen, graphics, Map.of(), layout,
                    Map.of(ROOT, parentA, OTHER, parentB, childA, firstChild, childB, secondChild), 1.0F);
            List<FamilyTreeLayout.Bounds> horizontal = graphics.fills.stream()
                    .filter(rect -> rect.right() - rect.left() > 1 && rect.bottom() - rect.top() == 1).toList();
            assertEquals(2, horizontal.size());
            assertNotEquals(horizontal.get(0).top(), horizontal.get(1).top(),
                    "Unrelated family rails must remain separate at zoom " + zoom);
            assertEquals(2, graphics.fillColors.stream().distinct().count(),
                    "Overlapping family routes should use distinguishable shades");
        }

        screen.width = 200;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(-200, 0, 1));
        RecordingGraphics graphics = new RecordingGraphics((Minecraft) allocator.allocateInstance(Minecraft.class));
        lines.invoke(screen, graphics, Map.of(), layout,
                Map.of(ROOT, parentA, OTHER, parentB, childA, firstChild, childB, secondChild), 1.0F);
        assertTrue(graphics.fills.stream().anyMatch(rect -> rect.left() < 0 && rect.right() >= 200),
                "A connector crossing the canvas must remain visible with both end cards offscreen");
        assertTrue(graphics.fills.stream().allMatch(rect -> rect.left() < 200 && rect.right() > 0),
                "Only connector segments that reach the canvas should be submitted");
    }

    @Test
    void rendererSkipsOffscreenCardFillsAndTextButDrawsPartiallyVisibleCards() throws Exception {
        RecordingGraphics offscreen = renderCards(1, 2, false, false, "Émilie", -500);
        assertTrue(offscreen.fills.isEmpty(), "Offscreen cards should submit no background");
        assertTrue(offscreen.text.isEmpty(), "Offscreen cards should submit no text");

        RecordingGraphics partlyVisible = renderCards(1, 2, false, false, "Émilie", -415);
        assertFalse(partlyVisible.fills.isEmpty(), "The visible portion of a card still needs a background");
        assertFalse(partlyVisible.text.isEmpty(), "Canvas clipping can keep the visible portion of the card text");
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
                double expectedOffset = (5 + line * 9) * zoom;
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

    @Test
    void longPersonNameWrapsToTwoRowsWithoutObscuringTheOtherFamilyLabels() throws Exception {
        RecordingGraphics graphics = renderCards(1.0F, 2.0, false, true, "Alexandra Maria Louise");

        assertEquals(List.of("Alexandra Maria", "Louise"),
                graphics.text.subList(0, 2).stream().map(TextDraw::text).toList());
        assertEquals("Villager", graphics.text.get(2).text());
        assertEquals("Mother", graphics.text.get(3).text());
        assertEquals(6, graphics.text.size(), "Relationship status and orphan label must still fit");
        for (TextDraw draw : graphics.text) {
            assertTrue(draw.y() - 3 * draw.scale() > graphics.cardTop);
            assertTrue(draw.y() + 10 * draw.scale() < graphics.cardBottom);
        }
    }

    @Test
    void allSixTextRowsFitInCompactCardsAcrossZoomLevels() throws Exception {
        for (float zoom : new float[]{0.95F, 1.0F, 2.0F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false, true, "Alexandra Maria Louise");
            assertEquals(6, graphics.text.size());
            for (TextDraw draw : graphics.text) {
                assertTrue(draw.y() + 10 * draw.scale() < graphics.cardBottom,
                        "All wrapped name and relationship text must fit within the card at zoom " + zoom);
            }
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
                assertFalse(graphics.text.isEmpty(), "Always retain the name");
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
    void cardAndGraveHitTargetsMatchRenderedGeometry() {
        FamilyTreeNode deceased = node(ROOT, "Émilie");
        deceased.setDeceased(true);
        FamilyTreeLayout.Bounds bounds = new FamilyTreeLayout.Bounds(-64, 64, -32, 32);
        FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), bounds);
        GlobalPos grave = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 70, 8));

        assertEquals(Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -63, 0));
        assertEquals(Optional.of(new FamilyTreeScreen.GraveTarget(ROOT, grave)),
                FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -49, -30));
        assertTrue(FamilyTreeScreen.hitTargetAt(layout, Map.of(ROOT, deceased), Map.of(ROOT, grave), -65, 0).isEmpty());
    }

    private record TextDraw(String text, float x, float y, float scale) {
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased) throws Exception {
        return renderCards(zoom, guiScale, deceased, false);
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased,
                                                 boolean withRelationshipStatus) throws Exception {
        return renderCards(zoom, guiScale, deceased, withRelationshipStatus, "Émilie");
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased,
                                                 boolean withRelationshipStatus, String name) throws Exception {
        return renderCards(zoom, guiScale, deceased, withRelationshipStatus, name, 0.3);
    }

    private static RecordingGraphics renderCards(float zoom, double guiScale, boolean deceased,
                                                 boolean withRelationshipStatus, String name, double panX) throws Exception {
        Unsafe allocator = unsafe();
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
        FamilyTreeNode person = node(ROOT, name);
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
        setField(FamilyTreeScreen.class, screen, "viewport", new FamilyTreeViewModel.ViewportState(panX, 0.7, zoom));
        int halfHeight = FamilyTreeLayout.CARD_HEIGHT / 2;
        FamilyTreeLayout.Bounds bounds = new FamilyTreeLayout.Bounds(-64, 64, -halfHeight, halfHeight);
        setField(FamilyTreeScreen.class, screen, "layout", new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), bounds));
        setField(FamilyTreeScreen.class, screen, "cardPresentations", Map.of(ROOT,
                new FamilyTreeScreen.CardPresentation(Component.literal(name), -1,
                        Component.literal("Villager"), Component.literal("Mother"),
                        withRelationshipStatus ? Component.literal("Married") : null, true)));
        RecordingGraphics graphics = new RecordingGraphics(client);
        FamilyTreeLayout.Result displayed = new FamilyTreeLayout.Result(
                List.of(new FamilyTreeLayout.Card(ROOT, bounds)), List.of(), bounds);
        java.lang.reflect.Method frames = FamilyTreeScreen.class.getDeclaredMethod("renderCardFrames",
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, float.class);
        frames.setAccessible(true);
        graphics.pose().pushPose();
        graphics.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale), 1);
        frames.invoke(screen, graphics, Map.of(ROOT, person), displayed, (float) guiScale);
        graphics.pose().popPose();
        graphics.pose().translate(screen.width / 2.0 + panX, 313.2, 0);
        graphics.pose().scale(zoom, zoom, 1);
        java.lang.reflect.Method render = FamilyTreeScreen.class.getDeclaredMethod(
                "renderCards", GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class);
        render.setAccessible(true);
        MCAScreens originalScreens = MCAScreens.getInstance();
        try {
            if (originalScreens == null) {
                new MCAScreens();
            }
            render.invoke(screen, graphics, Map.of(ROOT, person), displayed);
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
        final List<FamilyTreeLayout.Bounds> fills = new ArrayList<>();
        final List<Integer> fillColors = new ArrayList<>();
        final List<Float> fillDepths = new ArrayList<>();
        final List<Float> textDepths = new ArrayList<>();
        float cardTop;
        float cardBottom;
        float graveLeft;

        RecordingGraphics(Minecraft client) {
            super(client, null);
        }

        @Override
        public void fill(int left, int top, int right, int bottom, int color) {
            fills.add(new FamilyTreeLayout.Bounds(left, right, top, bottom));
            fillColors.add(color);
            fillDepths.add(pose().last().pose().m32());
            if (fills.size() == 1) {
                cardTop = pose().last().pose().transformPosition(new Vector3f(left, top, 0)).y;
                cardBottom = pose().last().pose().transformPosition(new Vector3f(right, bottom, 0)).y;
            }
        }

        @Override
        public void renderOutline(int x, int y, int width, int height, int color) {
        }

        @Override
        public int drawString(Font font, String value, int x, int y, int color, boolean shadow) {
            Vector3f position = pose().last().pose().transformPosition(new Vector3f(x, y, 0));
            textDepths.add(position.z);
            text.add(new TextDraw(value, position.x, position.y, pose().last().pose().m00()));
            return 0;
        }

        @Override
        public int drawString(Font font, Component value, int x, int y, int color) {
            return drawString(font, value.getString(), x, y, color, true);
        }

        @Override
        public void blit(ResourceLocation texture, int x, int y, int z, float u, float v,
                         int width, int height, int textureWidth, int textureHeight) {
            graveLeft = pose().last().pose().transformPosition(new Vector3f(x, y, 0)).x;
        }
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
    void zoomAllowsFullTreeOverviewAndClampsToDoubleScale() {
        FamilyTreeViewModel.ViewportState state = new FamilyTreeViewModel.ViewportState(0, 0, 1.0F);

        assertEquals(0.01F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 0.001F).zoom());
        assertEquals(0.05F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 0.05F).zoom());
        assertEquals(2.0F, FamilyTreeScreen.zoomAround(state, 0, 0, 0, 0, 5.0F).zoom());
    }

    @Test
    void fitViewCanContainTallFamilyTreeBelowQuarterZoom() {
        FamilyTreeLayout.Result tree = result(new FamilyTreeLayout.Bounds(-1000, 1000, -1400, 1400));
        FamilyTreeViewModel.ViewportState fitted = FamilyTreeScreen.fitView(tree, 800, 400, 20);

        assertTrue(fitted.zoom() < 0.25F);
        assertTrue(400 + fitted.panX() + tree.contentBounds().left() * fitted.zoom() >= 19.99);
        assertTrue(400 + fitted.panX() + tree.contentBounds().right() * fitted.zoom() <= 780.01);
        assertTrue(200 + fitted.panY() + tree.contentBounds().top() * fitted.zoom() >= 19.99);
        assertTrue(200 + fitted.panY() + tree.contentBounds().bottom() * fitted.zoom() <= 380.01);
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
    void narrowHeaderKeepsSearchAndControlsNonOverlappingWithinScreen() {
        for (int screenWidth : new int[]{264, 256, 213, 180, 160}) {
            FamilyTreeScreen.HeaderLayout header = FamilyTreeScreen.headerLayout(screenWidth);

            assertTrue(header.searchWidth() >= 24, "Search field must remain usable at width " + screenWidth);
            assertTrue(header.searchX() + header.searchWidth() < header.zoomOutX(),
                    "Search field must never overlap zoom-out at width " + screenWidth);
            assertTrue(header.zoomOutX() + header.zoomButtonWidth() < header.zoomLabelX());
            assertTrue(header.zoomLabelX() + header.zoomLabelWidth() < header.zoomInX());
            assertTrue(header.zoomInX() + header.zoomButtonWidth() < header.fitX());
            assertTrue(header.fitX() + header.fitWidth() < header.centerX());
            assertTrue(header.doneX() >= 5 + 54 + 4, "Done button must not overlap Back button");
            if (screenWidth >= 213) {
                assertTrue(header.centerX() + header.centerWidth() <= screenWidth - 5,
                        "All controls must fit inside 854x480 GUI scale 4 width " + screenWidth);
            }
        }
    }

    @Test
    void searchResultsShareTheWidestVisibleRowInFlat2dGuiSpace() throws Exception {
        FamilyTreeScreen screen = allocatedScreen(new FamilyTreeViewModel(ROOT));
        Minecraft client = (Minecraft) unsafe().allocateInstance(Minecraft.class);
        Font font = new Font(ignored -> null, false) {
            @Override
            public int width(String text) {
                return text.length() * 6;
            }

            @Override
            public int width(net.minecraft.network.chat.FormattedText text) {
                return width(text.getString());
            }

            @Override
            public String plainSubstrByWidth(String text, int width) {
                return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
            }
        };
        setField(Screen.class, screen, "font", font);
        setField(FamilyTreeScreen.class, screen, "searchX", 5);
        setField(FamilyTreeScreen.class, screen, "searchOpen", true);
        FamilyTreeSearchEntry shortName = new FamilyTreeSearchEntry(ROOT, "Al", true, "", true, "");
        FamilyTreeSearchEntry longName = new FamilyTreeSearchEntry(OTHER, "Alexandra", true, "", true, "");
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of(shortName, longName));
        java.lang.reflect.Method render = FamilyTreeScreen.class.getDeclaredMethod(
                "renderSearchOverlay", GuiGraphics.class, int.class, int.class);
        java.lang.reflect.Method hit = FamilyTreeScreen.class.getDeclaredMethod("searchResultAt", double.class, double.class);
        render.setAccessible(true);
        hit.setAccessible(true);
        for (int screenWidth : new int[]{800, 60}) {
            screen.width = screenWidth;
            int expectedWidth = Math.max(1, Math.min(screenWidth - 10, font.width("Alexandra") + 12));
            RecordingGraphics graphics = new RecordingGraphics(client);
            render.invoke(screen, graphics, 0, 0);
            assertEquals(2, graphics.fills.size());
            for (FamilyTreeLayout.Bounds row : graphics.fills) {
                assertEquals(expectedWidth, row.right() - row.left());
            }
            assertSame(shortName, hit.invoke(screen, 5 + expectedWidth - 1.0D, 60.0D));
            assertSame(longName, hit.invoke(screen, 5 + expectedWidth - 1.0D, 82.0D));
            assertNull(hit.invoke(screen, 5 + expectedWidth, 60.0D));
            assertEquals(List.of(0.0F, 0.0F), graphics.fillDepths,
                    "Search popup must draw in standard 2D GUI space without z = 500 PoseStack tricks");
            assertEquals(List.of(0.0F, 0.0F), graphics.textDepths);
            assertEquals(0.0F, graphics.pose().last().pose().m32());
        }
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        setField(FamilyTreeScreen.class, screen, "searchWidth", 40);
        for (boolean pending : new boolean[]{false, true}) {
            setField(FamilyTreeScreen.class, screen, "searchPending", pending);
            RecordingGraphics graphics = new RecordingGraphics(client);
            render.invoke(screen, graphics, 0, 0);
            assertEquals(List.of(0.0F), graphics.fillDepths);
            assertEquals(List.of(0.0F), graphics.textDepths);
            assertEquals(0.0F, graphics.pose().last().pose().m32());
        }
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
                card.bounds()
        );

        assertEquals(
                Optional.of(new FamilyTreeScreen.PersonTarget(ROOT)),
                FamilyTreeScreen.hitTargetAt(result, 0, 0)
        );
    }

    @Test
    void globalGenerationControlsStayClickableIndependentOfWorldZoom() {
        Set<FamilyTreeView.Direction> available = Set.of(ANCESTORS, DESCENDANTS);

        assertEquals(Optional.of(ANCESTORS),
                FamilyTreeScreen.generationControlAt(970, 77, 1000, 720, available));
        assertEquals(Optional.of(DESCENDANTS),
                FamilyTreeScreen.generationControlAt(970, 675, 1000, 720, available));
        assertEquals(Optional.empty(), FamilyTreeScreen.generationControlAt(970, 77, 1000, 720,
                Set.of(DESCENDANTS)));
        assertEquals(Optional.empty(), FamilyTreeScreen.generationControlAt(100, 100, 1000, 720, available));
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
        assertFalse(display.getString().contains("???"));
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

    private static FamilyTreeLayout.Result result(FamilyTreeLayout.Bounds bounds) {
        return new FamilyTreeLayout.Result(List.of(), List.of(), bounds);
    }

}
