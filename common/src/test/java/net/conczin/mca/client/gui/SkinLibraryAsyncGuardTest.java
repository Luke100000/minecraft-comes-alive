package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.immersive_library.Auth;
import net.conczin.mca.client.gui.immersive_library.Workspace;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class SkinLibraryAsyncGuardTest {
    private HttpServer server;
    private ExecutorService serverExecutor;
    private String formerUrl;
    private String formerToken;
    private Minecraft formerMinecraft;
    private Queue<Runnable> clientTasks;
    private CountDownLatch releaseResponse;
    private CountDownLatch requestEntered;
    private AtomicInteger requestCount;
    private final ArrayList<Workspace> workspaces = new ArrayList<>();

    @BeforeEach
    void prepare() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        formerUrl = Config.getInstance().immersiveLibraryUrl;
        formerToken = (String) field(Auth.class, "currentToken").get(null);
        formerMinecraft = (Minecraft) field(Minecraft.class, "instance").get(null);
        field(Auth.class, "currentToken").set(null, "skin-async-test-token");

        Minecraft minecraft = allocate(Minecraft.class);
        field(Minecraft.class, "gameThread").set(minecraft, Thread.currentThread());
        clientTasks = new ConcurrentLinkedQueue<>();
        field(BlockableEventLoop.class, "pendingRunnables").set(minecraft, clientTasks);
        field(Minecraft.class, "instance").set(null, minecraft);

        releaseResponse = new CountDownLatch(1);
        requestEntered = new CountDownLatch(1);
        requestCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverExecutor = Executors.newCachedThreadPool(r -> {
            Thread thread = new Thread(r, "skin-library-test-server");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(serverExecutor);
        server.createContext("/", exchange -> {
            if (exchange.getRequestURI().getPath().equals("/v3/content/mca/42")) {
                respond(exchange, 200, "null");
                return;
            }
            requestCount.incrementAndGet();
            requestEntered.countDown();
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "{}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        Config.getInstance().immersiveLibraryUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void cleanup() throws Exception {
        if (releaseResponse == null) {
            return;
        }
        releaseResponse.countDown();
        for (int i = 0; i < 25; i++) {
            drainClientTasks();
            if (clientTasks.isEmpty()) {
                Thread.sleep(10);
            }
        }
        server.stop(0);
        serverExecutor.shutdownNow();
        Config.getInstance().immersiveLibraryUrl = formerUrl;
        field(Auth.class, "currentToken").set(null, formerToken);
        field(Minecraft.class, "instance").set(null, formerMinecraft);
        for (Workspace workspace : workspaces) {
            workspace.close();
        }
    }

    @Test
    void earlierUploadCannotNavigateOrRetireNewerEditorWorkspace() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.EDITOR);
        Workspace original = workspace("Original");
        field(SkinLibraryScreen.class, "workspace").set(screen, original);
        invoke(screen, "publish");
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS), "Upload never reached the test server");

        Workspace replacement = workspace("Replacement");
        field(SkinLibraryScreen.class, "workspace").set(screen, replacement);
        field(SkinLibraryScreen.class, "uploading").setBoolean(screen, true);
        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();

        assertNull(screen.lastNavigation, "Old upload must not navigate the replacement editor");
        assertSame(replacement, field(SkinLibraryScreen.class, "workspace").get(screen));
        assertTrue(field(SkinLibraryScreen.class, "uploading").getBoolean(screen),
                "Old upload must not retire another upload's in-progress flag");
        assertEquals(SkinLibraryScreen.Page.EDITOR, field(SkinLibraryScreen.class, "page").get(screen));
    }

    @Test
    void doubleClickOnSameTargetSendsOnlyOneMutationWhilePending() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.DETAIL);
        LiteContent content = content();
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                content, true, (Consumer<Boolean>) success -> {});
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS), "Like mutation never reached the test server");
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                content, true, (Consumer<Boolean>) success -> {});
        Thread.sleep(350);
        assertEquals(1, requestCount.get(), "Concurrent same-target clicks sent duplicate requests");
    }

    @Test
    void differentActionsForSameContentCannotOverlapWhilePending() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.DETAIL);
        LiteContent content = content();
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                content, true, (Consumer<Boolean>) success -> {});
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS), "Like mutation never reached the test server");
        invoke(screen, "setReport", new Class<?>[]{LiteContent.class, String.class, boolean.class},
                content, "INVALID", true);
        Thread.sleep(350);
        assertEquals(1, requestCount.get(),
                "Two simultaneous actions against the same content must not overlap");
    }

    @Test
    void independentContentTargetsCanMutateWhileOneRequestIsPending() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.LIBRARY);
        LiteContent first = content();
        LiteContent second = new LiteContent(43, 7, "owner", 0, Set.of("clothing"), "Second Skin", 1, false, Set.of());
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                first, true, (Consumer<Boolean>) success -> {});
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                second, true, (Consumer<Boolean>) success -> {});
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (requestCount.get() < 2 && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertEquals(2, requestCount.get(), "Different content targets must remain independent");
    }

    @Test
    void reportCompletingAfterSameAccountNavigationDoesNotReopenDetail() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.REPORT);
        field(SkinLibraryScreen.class, "reportConfirmationContent").set(screen, content());
        invoke(screen, "setReport", new Class<?>[]{LiteContent.class, String.class, boolean.class},
                content(), "INVALID", true);
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));
        field(SkinLibraryScreen.class, "page").set(screen, SkinLibraryScreen.Page.LIBRARY);

        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();
        assertNull(screen.lastNavigation, "Old report completion must not reopen a page the user left");
    }

    @Test
    void deleteConfirmationDisablesItsConfirmButtonWhileRequestPending() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.DELETE);
        field(SkinLibraryScreen.class, "deleteConfirmationContent").set(screen, content());
        invoke(screen, "removeContent", new Class<?>[]{int.class}, 42);
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));

        invoke(screen, "buildDelete");
        @SuppressWarnings("unchecked")
        var widgets = (ArrayList<AbstractWidget>) field(Screen.class, "children").get(screen);
        assertEquals(2, widgets.size());
        assertFalse(widgets.get(1).active, "Confirm must be disabled until DELETE completes");
    }

    private ObservedScreen screen(SkinLibraryScreen.Page page) throws Exception {
        ObservedScreen screen = allocate(ObservedScreen.class);
        field(SkinLibraryScreen.class, "page").set(screen, page);
        field(SkinLibraryScreen.class, "thread").set(screen, Thread.currentThread());
        field(Screen.class, "children").set(screen, new ArrayList<>());
        field(Screen.class, "renderables").set(screen, new ArrayList<>());
        field(Screen.class, "narratables").set(screen, new ArrayList<>());
        field(Screen.class, "minecraft").set(screen, field(Minecraft.class, "instance").get(null));
        field(SkinLibraryScreen.class, "likeOverrides").set(screen, new HashMap<>());
        field(SkinLibraryScreen.class, "pendingMutations").set(screen, new HashSet<>());
        field(SkinLibraryScreen.class, "remoteContents").set(screen, new ArrayList<>());
        screen.width = 640;
        screen.height = 480;
        return screen;
    }

    private Workspace workspace(String title) {
        Workspace workspace = new Workspace(new NativeImage(64, 64, false));
        workspace.skinType = SkinLibraryScreen.SkinType.CLOTHING;
        workspace.contentid = 42;
        workspace.tags = "one, two, three";
        workspace.title = title;
        workspaces.add(workspace);
        return workspace;
    }

    private static LiteContent content() {
        return new LiteContent(42, 7, "owner", 0, Set.of("clothing"), "Skin", 1, false, Set.of());
    }

    private void awaitClientTask() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(4);
        while (clientTasks.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(clientTasks.isEmpty(), "The asynchronous request never completed");
    }

    private void drainClientTasks() {
        Runnable task;
        while ((task = clientTasks.poll()) != null) {
            task.run();
        }
    }

    private static void respond(HttpExchange exchange, int code, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static void invoke(Object object, String methodName) throws Exception {
        invoke(object, methodName, new Class<?>[0]);
    }

    private static void invoke(Object object, String methodName, Class<?>[] types, Object... args) throws Exception {
        Method method = SkinLibraryScreen.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        method.invoke(object, args);
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = field(Unsafe.class, "theUnsafe");
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static class ObservedScreen extends SkinLibraryScreen {
        private Page lastNavigation;

        @Override
        public void setPage(Page page) {
            lastNavigation = page;
        }
    }
}
