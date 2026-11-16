package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.Window;
import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static org.junit.jupiter.api.Assertions.*;

class FamilyTreeScreenInteractionTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID OTHER = uuid(2);

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

        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        FamilyTreeScreen screen = (FamilyTreeScreen) allocator.allocateInstance(FamilyTreeScreen.class);
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
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
        assertNull(field(screen, "transition"));
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
    void searchingOffscreenVisiblePersonPansSmoothlyWithoutLoading() throws Exception {
        FamilyTreeNode parent = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        parent.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState viewport = new FamilyTreeViewModel.ViewportState(-2000, 0, 1.0F);
        long initial = model.beginFocus(ROOT, viewport);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, parent, OTHER, child), Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result positions = FamilyTreeLayout.layout(model.snapshot());
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        FamilyTreeScreen screen = (FamilyTreeScreen) allocator.allocateInstance(FamilyTreeScreen.class);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "layout", positions);
        setField(FamilyTreeScreen.class, screen, "viewport", viewport);
        setField(FamilyTreeScreen.class, screen, "cardsById", positions.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        screen.selectSearchResult(new FamilyTreeSearchEntry(OTHER, "Child", false, "", false, ""));
        assertEquals(OTHER, model.focusId());
        assertFalse(model.loading());
        assertSame(positions, field(screen, "layout"));
        assertNotNull(field(screen, "cameraTo"));
        long started = (long) field(screen, "cameraStartedMs");
        screen.updateCameraMotion(started + 125);
        FamilyTreeViewModel.ViewportState halfway =
                (FamilyTreeViewModel.ViewportState) field(screen, "viewport");
        assertTrue(halfway.panX() > viewport.panX(), "Camera should travel towards the offscreen result");
        assertEquals(viewport.zoom(), halfway.zoom());
        screen.updateCameraMotion(started + 750);
        assertNull(field(screen, "cameraTo"));
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
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FamilyTreeScreen screen = (FamilyTreeScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(FamilyTreeScreen.class);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "layout", positions);
        setField(FamilyTreeScreen.class, screen, "viewport", viewport);
        setField(FamilyTreeScreen.class, screen, "cardsById", positions.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        screen.selectSearchResult(new FamilyTreeSearchEntry(OTHER, "Child", false, "", false, ""));
        assertEquals(OTHER, model.focusId());
        assertEquals(viewport, field(screen, "viewport"));
        assertNull(field(screen, "cameraTo"));
    }

    @Test
    void generationControlsIgnoreUnfinishedBranchesOnTheOppositeSideOfTheRoot() throws Exception {
        UUID childId = uuid(303);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode parent = node(OTHER, "Parent");
        FamilyTreeNode child = node(childId, "Child");
        parent.addChild(uuid(304));
        child.setFather(node(uuid(305), "Other parent"));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, childId, child), Set.of(
                new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS),
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
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertEquals(ROOT, model.focusId());
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isPresent(),
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
        first.addChild(uuid(309));
        second.addChild(uuid(310));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingA, first, siblingB, second),
                Set.of(new FamilyTreeView.Continuation(siblingA, FamilyTreeView.Direction.DESCENDANTS),
                        new FamilyTreeView.Continuation(siblingB, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        FamilyTreeLayout.Bounds firstButton = screen.branchControlBounds(siblingA,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        FamilyTreeLayout.Bounds secondButton = screen.branchControlBounds(siblingB,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertNotEquals(firstButton, secondButton);
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(siblingA, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(firstButton.centerX(), firstButton.centerY()));
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(siblingB, FamilyTreeView.Direction.DESCENDANTS)),
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
        UUID uncleId = uuid(306);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uncleId);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertTrue(screen.selectPerson(OTHER));
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isPresent(),
                "The selected ancestor must have an attached descendant branch control");
        assertTrue(model.beginGeneration(FamilyTreeView.Direction.DESCENDANTS, Set.of(OTHER)).isPresent(),
                "The action must load only the selected ancestor's unfinished descendants");
    }

    @Test
    void selectedDescendantBranchControlFollowsTheCardThroughZoomAndPan() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(request, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);

        FamilyTreeLayout.Bounds initial = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertEquals(8, initial.right() - initial.left(),
                "The attached action should be smaller than the person card at normal zoom");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(initial.centerX(), initial.centerY()));
        assertTrue(screen.branchControlAt(initial.centerX(), initial.top() - 1).isEmpty(),
                "Only the actual button should activate a branch");
        assertTrue(screen.children().stream().noneMatch(child -> child instanceof ButtonWidget button
                        && button.getMessage().equals(Component.translatable("gui.family_tree.explore_descendants"))),
                "Explore descendants must be attached to its person on the canvas, not the footer");

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(111, -47, 0.5F));
        FamilyTreeLayout.Bounds moved = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertEquals(8, moved.right() - moved.left(),
                "The attached action should remain the same readable pixel size across zoom");
        assertNotEquals(initial, moved);
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(moved.centerX(), moved.centerY()));
        assertTrue(screen.branchControlAt(initial.centerX(), initial.centerY()).isEmpty());

        FamilyTreeLayout.Result before = (FamilyTreeLayout.Result) field(screen, "layout");
        List<FamilyTreeLayout.Card> shifted = before.cards().stream().map(card -> {
            FamilyTreeLayout.Bounds bounds = card.bounds();
            if (!card.uuid().equals(OTHER)) {
                return card;
            }
            return new FamilyTreeLayout.Card(card.uuid(), new FamilyTreeLayout.Bounds(
                    bounds.left() + 120, bounds.right() + 120, bounds.top(), bounds.bottom()));
        }).toList();
        FamilyTreeLayout.Result target = new FamilyTreeLayout.Result(shifted, before.edges(),
                before.contentBounds());
        setField(FamilyTreeScreen.class, screen, "transition",
                new FamilyTreeTransition(before, target, ROOT, 0.5F));
        setField(FamilyTreeScreen.class, screen, "transitionStartedMs", Util.getMillis() - 90);
        FamilyTreeLayout.Bounds animated = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertTrue(animated.centerX() > moved.centerX(),
                "The button must move with its card during the transition");
        assertTrue(animated.centerX() <= moved.centerX() + 60,
                "The button cannot travel beyond its card's final position");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(animated.centerX(), animated.centerY()));
    }

    @Test
    void attachedTabKeepsItsFullHeightFromTheCardEdgeDuringFractionalPan() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long request = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(request, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        double guiScale = 6.0;
        FamilyTreeScreen screen = initializedScreen(model, guiScale);
        FamilyTreeLayout.Result layout = (FamilyTreeLayout.Result) field(screen, "layout");
        FamilyTreeLayout.Bounds ancestorBounds = layout.cards().stream()
                .filter(card -> card.uuid().equals(OTHER)).findFirst().orElseThrow().bounds();
        double originY = 54 + (screen.height - 54 - 30) / 2.0;
        double centerX = screen.width / 2.0 + ancestorBounds.centerX();
        double height = 6.0;
        FamilyTreeScreen.BranchTarget expected = new FamilyTreeScreen.BranchTarget(OTHER,
                FamilyTreeView.Direction.DESCENDANTS);

        for (double pan : new double[]{0.0, 0.18, 0.36, 0.55, 0.72, 0.9}) {
            setField(FamilyTreeScreen.class, screen, "viewport",
                    new FamilyTreeViewModel.ViewportState(0, pan, 1.0F));
            double cardBottom = Math.round((originY + ancestorBounds.bottom() + pan) * guiScale) / guiScale;
            double lastTabPixel = cardBottom + height - 0.5 / guiScale;
            assertEquals(Optional.of(expected), screen.branchControlAt(centerX, lastTabPixel),
                    "The tab must remain attached at pan " + pan);
            assertTrue(screen.branchControlAt(centerX, lastTabPixel + 1.0 / guiScale).isEmpty(),
                    "The click target must end with the tab at pan " + pan);
        }
    }

    @Test
    void clickingAttachedDescendantControlRequestsOnlyThatBranchAndBlocksRepeatWhileLoading() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        // Unsafe allocation bypasses field initializers; mouseClicked reads this overlay list.
        setField(FamilyTreeScreen.class, screen, "searchResults", List.of());
        FamilyTreeLayout.Bounds button = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();

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
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        float screenshotZoom = 0.162F;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, screenshotZoom));
        FamilyTreeLayout.Bounds button = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        FamilyTreeLayout.Result layout = (FamilyTreeLayout.Result) field(screen, "layout");
        FamilyTreeLayout.Bounds parent = layout.cards().stream().filter(card -> card.uuid().equals(OTHER))
                .findFirst().orElseThrow().bounds();
        FamilyTreeLayout.Bounds child = layout.cards().stream().filter(card -> card.uuid().equals(ROOT))
                .findFirst().orElseThrow().bounds();
        assertEquals(parent.centerX(), child.centerX(), "Reproduce the next card directly under the control");
        double originY = 54 + (screen.height - 54 - 30) / 2.0 - 65;
        assertTrue(button.bottom() <= originY + child.top() * (double) screenshotZoom,
                "The branch control must clear the card in the next row");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(button.centerX(), button.centerY()));
        assertTrue(screen.branchControlAt(button.left() - 1, button.centerY()).isEmpty(),
                "The click target must not exceed the rendered button");

        float lowerZoom = 0.14F;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, lowerZoom));
        FamilyTreeLayout.Bounds smaller = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow(
                        () -> new AssertionError("The tab should still be usable at 14% zoom"));
        assertTrue(smaller.bottom() <= originY + child.top() * (double) lowerZoom,
                "The smaller branch tab must clear the next row");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(smaller.centerX(), smaller.centerY()));

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65, 0.01F));
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isEmpty(),
                "A subpixel row gap cannot contain a usable attached action");

        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113, -65 + 52 - button.top(), screenshotZoom));
        FamilyTreeLayout.Bounds clipped = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertEquals(52, clipped.top());
        assertTrue(screen.branchControlAt(clipped.centerX(), 53).isEmpty(),
                "A button pixel clipped by the header must not receive input");
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                screen.branchControlAt(clipped.centerX(), 55));

        // A moved card can also push its button into the footer scissor.
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(113,
                        -65 + (screen.height - 32) - button.top(), screenshotZoom));
        FamilyTreeLayout.Bounds footerClipped = screen.branchControlBounds(OTHER,
                FamilyTreeView.Direction.DESCENDANTS).orElseThrow();
        assertEquals(screen.height - 32, footerClipped.top());
        assertEquals(Optional.of(new FamilyTreeScreen.BranchTarget(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
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
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);
        screen.selectPerson(ROOT);

        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isPresent());
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
        sibling.addChild(uuid(309));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingId, sibling),
                Set.of(new FamilyTreeView.Continuation(siblingId, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        java.lang.reflect.Method available = FamilyTreeScreen.class.getDeclaredMethod(
                "availableGenerationDirections");
        available.setAccessible(true);
        assertFalse(((Set<?>) available.invoke(screen)).contains(FamilyTreeView.Direction.DESCENDANTS),
                "The global button must not expand a sibling's separate family branch");
        assertTrue(screen.selectPerson(siblingId));
        assertTrue(screen.branchControlBounds(siblingId, FamilyTreeView.Direction.DESCENDANTS).isPresent());
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

        java.lang.reflect.Method available = FamilyTreeScreen.class.getDeclaredMethod(
                "availableGenerationDirections");
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
        cousin.addChild(uuid(311));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, parent, siblingId, sibling, cousinId, cousin),
                Set.of(new FamilyTreeView.Continuation(cousinId, ANCESTORS),
                        new FamilyTreeView.Continuation(cousinId, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);

        assertTrue(screen.selectPerson(cousinId));
        assertTrue(screen.branchControlBounds(cousinId, ANCESTORS).isPresent());
        assertTrue(screen.branchControlBounds(cousinId, FamilyTreeView.Direction.DESCENDANTS).isPresent());
    }

    @Test
    void fullyLoadedSelectedBranchHidesItsContextualAction() throws Exception {
        UUID uncleId = uuid(306);
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uncleId);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(
                FamilyTreeView.Direction.DESCENDANTS, Set.of(OTHER)).orElseThrow();
        FamilyTreeNode uncle = node(uncleId, "Uncle");
        uncle.setFather(ancestor);
        model.acceptGeneration(new GetFamilyTreeResponse(request.requestId(), OTHER, true,
                new FamilyTreeView(Map.of(OTHER, ancestor, uncleId, uncle), Set.of(), Set.of(), Set.of(), Map.of()),
                Optional.empty()));
        screen.selectPerson(ROOT);
        screen.selectPerson(OTHER);

        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isEmpty());
    }

    @Test
    void missingGenerationAnchorImmediatelyHidesItsContextualActionWithoutReflow() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode ancestor = node(OTHER, "Ancestor");
        root.setFather(ancestor);
        ancestor.addChild(ROOT);
        ancestor.addChild(uuid(306));
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, ancestor),
                Set.of(new FamilyTreeView.Continuation(OTHER, FamilyTreeView.Direction.DESCENDANTS)),
                Set.of(), Set.of(), Map.of())));
        FamilyTreeScreen screen = initializedScreen(model);
        screen.selectPerson(OTHER);
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isPresent());
        FamilyTreeLayout.Result before = (FamilyTreeLayout.Result) field(screen, "layout");

        FamilyTreeViewModel.GenerationRequest request = model.beginGeneration(
                FamilyTreeView.Direction.DESCENDANTS, Set.of(OTHER)).orElseThrow();
        screen.acceptFamilyData(new GetFamilyTreeResponse(request.requestId(), OTHER, false,
                FamilyTreeView.empty(), Optional.empty()));

        assertFalse(model.loading());
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isEmpty(),
                "Retired model frontiers must stop exposing the arrow on the very next screen query");
        assertSame(before, field(screen, "layout"),
                "No new family records should preserve card geometry without a transition");
    }

    @Test
    void generationAvailabilityIsReusedAcrossRedrawAndZoomButRefreshedOnStateChanges() throws Exception {
        FamilyTreeNode root = node(ROOT, "Root");
        FamilyTreeNode child = node(OTHER, "Child");
        root.addChild(OTHER);
        root.addChild(uuid(307));
        child.setFather(root);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        long initial = model.beginFocus(ROOT, new FamilyTreeViewModel.ViewportState(0, 0, 1));
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true, new FamilyTreeView(
                Map.of(ROOT, root, OTHER, child),
                Set.of(new FamilyTreeView.Continuation(ROOT, FamilyTreeView.Direction.DESCENDANTS)),
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
                FamilyTreeView.Direction.DESCENDANTS, Set.of(ROOT)).orElseThrow();
        screen.acceptFamilyData(new GetFamilyTreeResponse(request.requestId(), ROOT, false,
                FamilyTreeView.empty(), Optional.empty()));
        assertNotSame(selectedAvailability, availability.invoke(screen),
                "A terminal generation response without a layout change must refresh availability");
        assertTrue(screen.branchControlBounds(OTHER, FamilyTreeView.Direction.DESCENDANTS).isEmpty());
    }

    private static FamilyTreeScreen initializedScreen(FamilyTreeViewModel model) throws Exception {
        return initializedScreen(model, 1.0);
    }

    private static FamilyTreeScreen initializedScreen(FamilyTreeViewModel model, double guiScale) throws Exception {
        FamilyTreeScreen screen = allocatedScreen(model);
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        Window window = (Window) allocator.allocateInstance(Window.class);
        setField(Window.class, window, "guiScale", guiScale);
        setField(Minecraft.class, client, "window", window);
        setField(Screen.class, screen, "minecraft", client);
        screen.width = 800;
        screen.height = 600;
        setField(Screen.class, screen, "children", new ArrayList<>());
        setField(Screen.class, screen, "renderables", new ArrayList<>());
        setField(Screen.class, screen, "narratables", new ArrayList<>());
        setField(Screen.class, screen, "font", new Font(ignored -> null, false));
        setField(FamilyTreeScreen.class, screen, "viewport", new FamilyTreeViewModel.ViewportState(0, 0, 1));
        screen.init();
        return screen;
    }

    private static FamilyTreeScreen allocatedScreen(FamilyTreeViewModel model) throws Exception {
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FamilyTreeScreen screen = (FamilyTreeScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(FamilyTreeScreen.class);
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        return screen;
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
        assertNotEquals(canonical.cards(), anchoredExpandedView.cards(),
                "Simulate the changed positions retained after an earlier generation expansion");

        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FamilyTreeScreen screen = (FamilyTreeScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(FamilyTreeScreen.class);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "layout", anchoredExpandedView);
        setField(FamilyTreeScreen.class, screen, "viewport", camera);
        setField(FamilyTreeScreen.class, screen, "pendingRecenterRequestId", -1L);
        setField(FamilyTreeScreen.class, screen, "rootLayoutHistory", new ArrayDeque<>());

        UUID unrelated = uuid(999);
        long search = screen.startRootFocus(unrelated);
        screen.acceptFamilyData(new GetFamilyTreeResponse(search, unrelated, true,
                new FamilyTreeView(Map.of(unrelated, node(unrelated, "Other family")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertEquals(unrelated, model.layoutRootId());
        assertTrue(screen.goBack());

        assertEquals(ROOT, model.layoutRootId());
        assertSame(anchoredExpandedView, field(screen, "layout"),
                "Back must restore the prior layout, not recalculate its card coordinates");
        assertEquals(camera, field(screen, "viewport"));
        assertEquals(anchoredExpandedView.cards(),
                ((FamilyTreeLayout.Result) field(screen, "layout")).cards());
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

        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FamilyTreeScreen screen = (FamilyTreeScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(FamilyTreeScreen.class);
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "layout", original);
        setField(FamilyTreeScreen.class, screen, "viewport", camera);
        setField(FamilyTreeScreen.class, screen, "pendingRecenterRequestId", -1L);
        setField(FamilyTreeScreen.class, screen, "rootLayoutHistory", new ArrayDeque<>());
        setField(FamilyTreeScreen.class, screen, "cardsById", original.cards().stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));

        UUID missing = uuid(301);
        long failed = screen.startRootFocus(missing);
        screen.acceptFamilyData(new GetFamilyTreeResponse(failed, missing, false, FamilyTreeView.empty()));
        assertSame(original, field(screen, "layout"));
        assertEquals(camera, field(screen, "viewport"));
        assertEquals(ROOT, model.layoutRootId());
        assertFalse(model.canGoBack());
        assertTrue(((Deque<?>) field(screen, "rootLayoutHistory")).isEmpty());

        long cancelled = screen.startRootFocus(uuid(302));
        assertTrue(screen.selectPerson(OTHER));
        screen.acceptFamilyData(new GetFamilyTreeResponse(cancelled, uuid(302), true,
                new FamilyTreeView(Map.of(uuid(302), node(uuid(302), "Unrelated")),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        assertEquals(ROOT, model.layoutRootId());
        assertEquals(OTHER, model.focusId());
        assertSame(original, field(screen, "layout"));
        assertTrue(((Deque<?>) field(screen, "rootLayoutHistory")).isEmpty());
        assertTrue(screen.goBack());
        assertEquals(ROOT, model.focusId());
        assertEquals(camera, field(screen, "viewport"));
    }

    @Test
    void offscreenSearchWaitsForGenerationToSettleBeforePanningToFinalPosition() throws Exception {
        FamilyTreeNode root = node(ROOT, "Parent");
        FamilyTreeNode child = node(OTHER, "Child");
        root.addChild(OTHER);
        FamilyTreeViewModel model = new FamilyTreeViewModel(ROOT);
        FamilyTreeViewModel.ViewportState offscreenCamera =
                new FamilyTreeViewModel.ViewportState(-2000, 0, 1.0F);
        long initial = model.beginFocus(ROOT, offscreenCamera);
        model.accept(new GetFamilyTreeResponse(initial, ROOT, true,
                new FamilyTreeView(Map.of(ROOT, root, OTHER, child),
                        Set.of(), Set.of(), Set.of(), Map.of())));
        FamilyTreeLayout.Result before = FamilyTreeLayout.layout(model.snapshot());
        List<FamilyTreeLayout.Card> translated = before.cards().stream()
                .map(card -> new FamilyTreeLayout.Card(card.uuid(),
                        new FamilyTreeLayout.Bounds(card.bounds().left() + 400,
                                card.bounds().right() + 400,
                                card.bounds().top(), card.bounds().bottom())))
                .toList();
        FamilyTreeLayout.Result target = new FamilyTreeLayout.Result(
                translated, before.edges(), before.contentBounds());
        FamilyTreeTransition generation = new FamilyTreeTransition(before, target, ROOT, 1.0F);

        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        FamilyTreeScreen screen = (FamilyTreeScreen) ((Unsafe) unsafeField.get(null))
                .allocateInstance(FamilyTreeScreen.class);
        screen.width = 800;
        screen.height = 600;
        setField(FamilyTreeScreen.class, screen, "viewModel", model);
        setField(FamilyTreeScreen.class, screen, "layout", target);
        setField(FamilyTreeScreen.class, screen, "viewport", offscreenCamera);
        setField(FamilyTreeScreen.class, screen, "cardsById", translated.stream()
                .collect(java.util.stream.Collectors.toMap(FamilyTreeLayout.Card::uuid, card -> card)));
        setField(FamilyTreeScreen.class, screen, "transition", generation);
        setField(FamilyTreeScreen.class, screen, "transitionStartedMs", Util.getMillis() - 100);

        screen.selectSearchResult(new FamilyTreeSearchEntry(OTHER, "Child", false, "", false, ""));
        assertEquals(OTHER, model.focusId());
        assertSame(generation, field(screen, "transition"), "Selecting a search hit must not end generation motion");
        assertNull(field(screen, "cameraTo"), "Camera cannot target an intermediate moving card");
        assertEquals(offscreenCamera, field(screen, "viewport"));

        setField(FamilyTreeScreen.class, screen, "transitionStartedMs", Util.getMillis() - 750);
        java.lang.reflect.Method shown = FamilyTreeScreen.class.getDeclaredMethod("displayedLayout");
        shown.setAccessible(true);
        shown.invoke(screen);
        assertNull(field(screen, "transition"));
        assertNotNull(field(screen, "cameraTo"), "Camera should begin panning once the final card has settled");
        long cameraStarted = (long) field(screen, "cameraStartedMs");
        screen.updateCameraMotion(cameraStarted + 125);
        assertNotEquals(offscreenCamera.panX(), ((FamilyTreeViewModel.ViewportState) field(screen, "viewport")).panX());
        screen.updateCameraMotion(cameraStarted + 750);
        assertNull(field(screen, "cameraTo"));
        assertEquals(FamilyTreeScreen.focusViewportAfterResponse(target, offscreenCamera, OTHER),
                field(screen, "viewport"), "Final camera must center the settled card without snapping");
    }

    private static Object field(FamilyTreeScreen screen, String name) throws Exception {
        java.lang.reflect.Field field = FamilyTreeScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(screen);
    }

    @Test
    void cardTextScalesContinuouslyWithGraph() throws Exception {
        for (float zoom : new float[]{0.1F, 0.25F, 0.5F, 0.75F, 0.95F, 1.05F}) {
            RecordingGraphics graphics = renderCards(zoom, 2.0, false);
            assertEquals(zoom, graphics.text.getFirst().scale(), 0.0001,
                    "Text and cards must share the same zoom at " + zoom);
        }
    }

    @Test
    void physicalStrokeProjectionPreservesVisibleOutlineAndConnectedLinesAcrossZooms() {
        FamilyTreeLayout.Bounds card = new FamilyTreeLayout.Bounds(0, 128, 0, 64);
        for (float zoom : new float[]{0.01F, 0.25F, 0.75F, 1.0F, 2.0F}) {
            for (double guiScale : new double[]{1.0, 2.5, 4.0}) {
                FamilyTreeLayout.Bounds outline = FamilyTreeScreen.physicalBounds(
                        card, 400.3, 300.7, zoom, guiScale);
                FamilyTreeLayout.Bounds horizontal = FamilyTreeScreen.physicalHorizontalStroke(
                        0, 128, 0, 400.3, 300.7, zoom, guiScale);
                FamilyTreeLayout.Bounds vertical = FamilyTreeScreen.physicalVerticalStroke(
                        0, 0, 64, 400.3, 300.7, zoom, guiScale);

                assertTrue(outline.right() > outline.left() && outline.bottom() > outline.top());
                assertEquals(1, horizontal.bottom() - horizontal.top(), "Horizontal line must be one framebuffer pixel");
                assertEquals(1, vertical.right() - vertical.left(), "Vertical line must be one framebuffer pixel");
                assertEquals(outline.left(), horizontal.left());
                assertEquals(outline.top(), horizontal.top());
                assertEquals(outline.left(), vertical.left());
                assertEquals(outline.top(), vertical.top());
            }
        }
        assertEquals(new FamilyTreeLayout.Bounds(400, 402, 301, 302),
                FamilyTreeScreen.physicalBounds(new FamilyTreeLayout.Bounds(0, 128, 0, 64),
                        400.3, 300.7, 0.01F, 1.0));
    }

    @Test
    void rendererActuallyUsesPhysicalPixelStrokesAtOverviewZoom() throws Exception {
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        FamilyTreeScreen screen = (FamilyTreeScreen) allocator.allocateInstance(FamilyTreeScreen.class);
        screen.width = 801;
        screen.height = 601;
        setField(FamilyTreeScreen.class, screen, "viewport",
                new FamilyTreeViewModel.ViewportState(0.3, 0.7, 0.01F));
        setField(FamilyTreeScreen.class, screen, "viewModel", new FamilyTreeViewModel(ROOT));

        FamilyTreeLayout.Card parent = new FamilyTreeLayout.Card(ROOT,
                new FamilyTreeLayout.Bounds(-64, 64, -32, 32));
        FamilyTreeLayout.Card child = new FamilyTreeLayout.Card(OTHER,
                new FamilyTreeLayout.Bounds(64, 192, 88, 152));
        FamilyTreeLayout.Result layout = new FamilyTreeLayout.Result(List.of(parent, child),
                List.of(new FamilyTreeLayout.Edge(ROOT, OTHER, FamilyTreeLayout.EdgeType.PARENT_CHILD, null)),
                new FamilyTreeLayout.Bounds(-64, 192, -32, 152));

        Minecraft client = (Minecraft) allocator.allocateInstance(Minecraft.class);
        RecordingGraphics graphics = new RecordingGraphics(client);
        graphics.pose().scale(0.4F, 0.4F, 1.0F); // A 2.5x GUI scale, in physical-pixel coordinates.
        java.lang.reflect.Method lines = FamilyTreeScreen.class.getDeclaredMethod("renderEdgeStrokes",
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, Map.class, double.class);
        java.lang.reflect.Method borders = FamilyTreeScreen.class.getDeclaredMethod("renderCardOutlines",
                GuiGraphics.class, FamilyTreeLayout.Result.class, double.class);
        lines.setAccessible(true);
        borders.setAccessible(true);
        lines.invoke(screen, graphics, Map.of(), layout, Map.of(ROOT, parent, OTHER, child), 2.5);
        borders.invoke(screen, graphics, layout, 2.5);

        assertEquals(11, graphics.fills.size(), "Three connector segments plus four border strokes per card");
        assertTrue(graphics.fills.stream().allMatch(rect -> rect.right() > rect.left()
                && rect.bottom() > rect.top()), "No stroke may vanish to a zero-area rectangle");
        assertTrue(graphics.fills.stream().anyMatch(rect -> rect.right() - rect.left() == 1
                && rect.bottom() - rect.top() > 1));
        assertTrue(graphics.fills.stream().anyMatch(rect -> rect.bottom() - rect.top() == 1
                && rect.right() - rect.left() > 1));
    }

    @Test
    void unrelatedFamiliesNeverShareAnOverlappingHorizontalConnectorRail() throws Exception {
        java.lang.reflect.Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe allocator = (Unsafe) unsafeField.get(null);
        FamilyTreeScreen screen = (FamilyTreeScreen) allocator.allocateInstance(FamilyTreeScreen.class);
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
                GuiGraphics.class, Map.class, FamilyTreeLayout.Result.class, Map.class, double.class);
        lines.setAccessible(true);
        for (float zoom : new float[]{0.16F, 0.28F, 1.0F}) {
            setField(FamilyTreeScreen.class, screen, "viewport",
                    new FamilyTreeViewModel.ViewportState(0, 0, zoom));
            RecordingGraphics graphics = new RecordingGraphics((Minecraft) allocator.allocateInstance(Minecraft.class));
            lines.invoke(screen, graphics, Map.of(), layout,
                    Map.of(ROOT, parentA, OTHER, parentB, childA, firstChild, childB, secondChild), 6.0);
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
                Map.of(ROOT, parentA, OTHER, parentB, childA, firstChild, childB, secondChild), 1.0);
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
            render.invoke(screen, graphics, Map.of(ROOT, person),
                    new FamilyTreeLayout.Result(List.of(new FamilyTreeLayout.Card(ROOT, bounds)),
                            List.of(), bounds));
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
        Set<FamilyTreeView.Direction> available = Set.of(ANCESTORS, FamilyTreeView.Direction.DESCENDANTS);

        assertEquals(Optional.of(ANCESTORS),
                FamilyTreeScreen.generationControlAt(970, 77, 1000, 720, available));
        assertEquals(Optional.of(FamilyTreeView.Direction.DESCENDANTS),
                FamilyTreeScreen.generationControlAt(970, 675, 1000, 720, available));
        assertEquals(Optional.empty(), FamilyTreeScreen.generationControlAt(970, 77, 1000, 720,
                Set.of(FamilyTreeView.Direction.DESCENDANTS)));
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
        return new FamilyTreeLayout.Result(List.of(), List.of(), bounds);
    }

}
