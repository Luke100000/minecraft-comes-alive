package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.conczin.mca.client.gui.immersive_library.Workspace;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SkinLibraryEditorInputTest {
    @Test
    void widgetPressAndDragNeverStartsCanvasPainting() throws Exception {
        SkinLibraryScreen screen = editor();
        Workspace workspace = getWorkspace(screen);
        try {
            var button = new TestClickWidget(300, 220, 40, 40);
            @SuppressWarnings("unchecked")
            List<Object> children = (List<Object>) field(Screen.class, "children").get(screen);
            children.add(button);

            screen.mouseClicked(320, 240, 0);
            screen.mouseDragged(350, 250, 0, 30, 10);
            screen.mouseReleased(350, 250, 0);

            assertTrue(workspace.history.isEmpty());
            assertFalse(workspace.isDirty());
            assertEquals(-1, field(SkinLibraryScreen.class, "activeMouseButton").getInt(screen));
            assertEquals(0, workspace.currentImage.getPixelRGBA(32, 32));
        } finally {
            workspace.close();
        }
    }

    @Test
    void genuineCanvasPressCreatesOneUndoSnapshot() throws Exception {
        SkinLibraryScreen screen = editor();
        Workspace workspace = getWorkspace(screen);
        try {
            screen.mouseClicked(320, 240, 0);
            assertEquals(1, workspace.history.size());
            assertTrue(workspace.isDirty());
            screen.mouseReleased(320, 240, 0);
        } finally {
            workspace.close();
        }
    }

    @Test
    void draggingWithDifferentButtonDoesNotExtendAnActiveCanvasStroke() throws Exception {
        SkinLibraryScreen screen = editor();
        Workspace workspace = getWorkspace(screen);
        try {
            screen.mouseClicked(320, 240, 0);
            MouseHandler mouse = (MouseHandler) field(Minecraft.class, "mouseHandler")
                    .get(field(Screen.class, "minecraft").get(screen));
            set(mouse, "xpos", 325.0);

            screen.mouseDragged(325, 240, 1, 5, 0);

            assertEquals(0, workspace.currentImage.getPixelRGBA(34, 32),
                    "Right-button motion must not extend a left-button stroke");
            assertEquals(1, workspace.history.size());
        } finally {
            workspace.close();
        }
    }

    @Test
    void largeAndNonfiniteScrollCannotReverseOrCollapseViewport() throws Exception {
        SkinLibraryScreen screen = editor();
        Workspace workspace = getWorkspace(screen);
        try {
            double[] deltas = {-1_000_000, 1_000_000, Double.NaN, Double.POSITIVE_INFINITY,
                    Double.NEGATIVE_INFINITY, -500, 500};
            for (double delta : deltas) {
                assertDoesNotThrow(() -> screen.mouseScrolled(320, 240, 0, delta));
                float x0 = field(SkinLibraryScreen.class, "x0").getFloat(screen);
                float x1 = field(SkinLibraryScreen.class, "x1").getFloat(screen);
                float y0 = field(SkinLibraryScreen.class, "y0").getFloat(screen);
                float y1 = field(SkinLibraryScreen.class, "y1").getFloat(screen);
                assertTrue(Float.isFinite(x0) && Float.isFinite(x1));
                assertTrue(Float.isFinite(y0) && Float.isFinite(y1));
                assertTrue(x1 > x0 && y1 > y0, "viewport must remain finite and positive");
            }
        } finally {
            workspace.close();
        }
    }

    @Test
    void libraryClickUsesActualPointerRatherThanStaleHoverFromLastRender() throws Exception {
        TestSkinLibraryScreen screen = library();
        set(screen, "hoveredContent", content(1));
        set(screen, "contents", new ArrayList<>(List.of(content(1))));

        assertFalse(screen.mouseClicked(20, 20, 0));
        assertNull(field(SkinLibraryScreen.class, "focusedContent").get(screen));
        assertNull(screen.openedPage);
    }

    @Test
    void librarySelectsOnlyActualLeftClickedCard() throws Exception {
        TestSkinLibraryScreen screen = library();
        LiteContent first = content(1);
        LiteContent second = content(2);
        set(screen, "contents", new ArrayList<>(List.of(first, second)));
        set(screen, "hoveredContent", second);

        // At a 640x480 GUI, the first card's hit rectangle is centered at (155, 190).
        assertFalse(screen.mouseClicked(155, 190, 1), "Right click must not open a card");
        assertNull(field(SkinLibraryScreen.class, "focusedContent").get(screen));
        assertNull(screen.openedPage);

        screen.mouseClicked(155, 190, 0);
        assertSame(first, field(SkinLibraryScreen.class, "focusedContent").get(screen));
        assertEquals(SkinLibraryScreen.Page.DETAIL, screen.openedPage);
    }

    @Test
    void libraryWidgetConsumesClickBeforeUnderlyingCard() throws Exception {
        TestSkinLibraryScreen screen = library();
        LiteContent coveredCard = content(1);
        set(screen, "contents", new ArrayList<>(List.of(coveredCard)));
        set(screen, "hoveredContent", coveredCard);
        var button = new TestClickWidget(145, 180, 30, 30);
        @SuppressWarnings("unchecked")
        List<Object> children = (List<Object>) field(Screen.class, "children").get(screen);
        children.add(button);

        assertTrue(screen.mouseClicked(155, 190, 0));
        assertNull(field(SkinLibraryScreen.class, "focusedContent").get(screen));
        assertNull(screen.openedPage);
    }

    private static TestSkinLibraryScreen library() throws Exception {
        TestSkinLibraryScreen screen = allocate(TestSkinLibraryScreen.class);
        set(screen, "page", SkinLibraryScreen.Page.LIBRARY);
        set(screen, "timeSinceLastRebuild", 10);
        set(screen, "thread", Thread.currentThread());
        prepareScreen(screen);
        screen.width = 640;
        screen.height = 480;
        return screen;
    }

    /** Bypasses real screen reconstruction so the click target can be tested in isolation. */
    private static final class TestSkinLibraryScreen extends SkinLibraryScreen {
        private Page openedPage;

        @Override
        public void setPage(Page page) {
            openedPage = page;
        }
    }

    /** Consuming test control without a global Minecraft sound manager. */
    private static final class TestClickWidget extends AbstractWidget {
        private TestClickWidget(int x, int y, int width, int height) {
            super(x, y, width, height, Component.literal("Overlay"));
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return button == 0 && isMouseOver(mouseX, mouseY);
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
        }
    }

    private static LiteContent content(int id) {
        return new LiteContent(id, -1, "Item", -1, Set.of("clothing"), "test", 1, false, Set.of());
    }

    private static SkinLibraryScreen editor() throws Exception {
        SkinLibraryScreen screen = allocate(SkinLibraryScreen.class);
        set(screen, "page", SkinLibraryScreen.Page.EDITOR);
        set(screen, "timeSinceLastRebuild", 10);
        set(screen, "workspace", new Workspace(new NativeImage(64, 64, false)));
        set(screen, "x0", 0f);
        set(screen, "x1", 1f);
        set(screen, "y0", 0f);
        set(screen, "y1", 1f);
        set(screen, "activeMouseButton", -1);
        set(screen, "color", new ColorSelector());
        prepareScreen(screen);
        screen.width = 640;
        screen.height = 480;
        getWorkspace(screen).setDirty(false);
        return screen;
    }

    private static Workspace getWorkspace(SkinLibraryScreen screen) throws Exception {
        return (Workspace) field(SkinLibraryScreen.class, "workspace").get(screen);
    }

    private static void prepareScreen(SkinLibraryScreen screen) throws Exception {
        set(screen, "children", new ArrayList<>());
        set(screen, "narratables", new ArrayList<>());
        set(screen, "renderables", new ArrayList<>());

        Minecraft minecraft = allocate(Minecraft.class);
        Window window = allocate(Window.class);
        set(window, "width", 640);
        set(window, "height", 480);
        set(window, "guiScaledWidth", 640);
        set(window, "guiScaledHeight", 480);
        set(minecraft, "window", window);
        MouseHandler mouse = allocate(MouseHandler.class);
        set(mouse, "xpos", 320.0);
        set(mouse, "ypos", 240.0);
        set(minecraft, "mouseHandler", mouse);
        set(screen, "minecraft", minecraft);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field f = field(target.getClass(), name);
        if (f.getType() == float.class) {
            f.setFloat(target, (float) value);
        } else if (f.getType() == int.class) {
            f.setInt(target, (int) value);
        } else if (f.getType() == double.class) {
            f.setDouble(target, (double) value);
        } else {
            f.set(target, value);
        }
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field f = null;
        for (Class<?> owner = type; owner != null && f == null; owner = owner.getSuperclass()) {
            try {
                f = owner.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Search inherited fields as well.
            }
        }
        if (f == null) throw new NoSuchFieldException(name);
        f.setAccessible(true);
        return f;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        T instance = type.cast(((Unsafe) f.get(null)).allocateInstance(type));
        if (instance instanceof SkinLibraryScreen screen) {
            set(screen, "requestEpoch", new SkinLibraryScreen.RequestEpoch());
        }
        return instance;
    }
}
