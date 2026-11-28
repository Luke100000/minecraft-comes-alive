package net.conczin.mca.client.gui;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.NativeImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.immersive_library.Auth;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.gui.immersive_library.Workspace;
import net.conczin.mca.client.gui.immersive_library.responses.ContentResponse;
import net.conczin.mca.client.gui.immersive_library.types.Content;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.conczin.mca.client.gui.widget.ToggleableTooltipIconButtonWidget;
import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
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
    private Integer uploadFixtureId;
    private Boolean formerCatalogOutdated;
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
        if (formerCatalogOutdated != null) {
            field(ClientSkinCatalog.class, "customSkinsOutdated").setBoolean(null, formerCatalogOutdated);
        }
        for (Workspace workspace : workspaces) {
            workspace.close();
        }
        if (uploadFixtureId != null) {
            SkinCache.enforceSync(uploadFixtureId);
            Files.deleteIfExists(Path.of("immersive_library", uploadFixtureId + ".json"));
            Files.deleteIfExists(Path.of("immersive_library", uploadFixtureId + ".png"));
        }
    }

    @Test
    void earlierUploadCannotNavigateOrRetireNewerEditorWorkspace() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.EDITOR);
        Workspace original = workspace("Original");
        Content uploaded = serveUpload(original, true);
        field(SkinLibraryScreen.class, "workspace").set(screen, original);
        invoke(screen, "publish");
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS), "Upload never reached the test server");

        Workspace replacement = workspace("Replacement");
        field(SkinLibraryScreen.class, "workspace").set(screen, replacement);
        field(SkinLibraryScreen.class, "uploading").setBoolean(screen, true);
        releaseResponse.countDown();
        awaitClientTask();
        assertTrue(Files.exists(uploadVersionMarker()), "The cache should commit before the client handles completion");
        assertNull(screen.lastNavigation);
        drainClientTasks();

        assertEquals(uploaded.meta(), Files.readString(Path.of("immersive_library", uploaded.content_id() + ".json")));
        assertNull(screen.lastNavigation, "Old upload must not navigate the replacement editor");
        assertSame(replacement, field(SkinLibraryScreen.class, "workspace").get(screen));
        assertTrue(field(SkinLibraryScreen.class, "uploading").getBoolean(screen),
                "Old upload must not retire another upload's in-progress flag");
        assertEquals(SkinLibraryScreen.Page.EDITOR, field(SkinLibraryScreen.class, "page").get(screen));
    }

    @Test
    void completedUploadOpensDetailForTheFetchedVersion() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.EDITOR);
        Workspace original = workspace("Original");
        Content uploaded = serveUpload(original, true);
        field(SkinLibraryScreen.class, "workspace").set(screen, original);
        invoke(screen, "publish");
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));

        releaseResponse.countDown();
        awaitClientTask();
        assertNull(screen.lastNavigation);
        drainClientTasks();

        assertEquals(SkinLibraryScreen.Page.DETAIL, screen.lastNavigation);
        assertEquals(uploaded.lite(), field(SkinLibraryScreen.class, "focusedContent").get(screen));
        assertFalse(field(SkinLibraryScreen.class, "uploading").getBoolean(screen));
    }

    @Test
    void failedFollowUpFetchInvalidatesTheCacheBeforeReturningToLibrary() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.EDITOR);
        Workspace original = workspace("Original");
        SkinCache.store(serveUpload(original, false));
        assertTrue(Files.exists(uploadVersionMarker()));
        field(SkinLibraryScreen.class, "workspace").set(screen, original);
        invoke(screen, "publish");
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));

        releaseResponse.countDown();
        awaitClientTask();
        assertFalse(Files.exists(uploadVersionMarker()), "The stale version should be invalidated before the client handles completion");
        assertNull(screen.lastNavigation);
        drainClientTasks();

        assertEquals(SkinLibraryScreen.Page.LIBRARY, screen.lastNavigation);
        assertFalse(field(SkinLibraryScreen.class, "uploading").getBoolean(screen));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void priorAccountUploadCannotPopulateTheCache(boolean invalidateScreenAccount) throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.EDITOR);
        Workspace original = workspace("Original");
        serveUpload(original, true);
        field(SkinLibraryScreen.class, "workspace").set(screen, original);
        invoke(screen, "publish");
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));

        if (invalidateScreenAccount) {
            invoke(screen, "invalidateAccountRequests");
        } else {
            field(Auth.class, "currentToken").set(null, "replacement-token");
        }
        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();

        assertFalse(Files.exists(uploadVersionMarker()));
        assertNull(screen.lastNavigation);
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
        prepareLibrary(screen, SkinLibraryScreen.SubscriptionFilter.LIBRARY, List.of(first, second));
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
    void debouncedSearchOnlyFetchesWhileLibraryIsOpen() throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.LIBRARY);
        prepareLibrary(screen, SkinLibraryScreen.SubscriptionFilter.LIBRARY, List.of());
        screen.navigate(SkinLibraryScreen.Page.LIBRARY);
        EditBox search = (EditBox) field(SkinLibraryScreen.class, "searchWidget").get(screen);
        CompletableFuture<String> requestQuery = new CompletableFuture<>();
        server.createContext("/v3/content/mca", exchange -> {
            requestQuery.complete(exchange.getRequestURI().getQuery());
            try {
                releaseResponse.await(5, TimeUnit.SECONDS);
                respond(exchange, 200, "{\"contents\":[],\"has_more\":false}");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });

        search.setValue("winter");
        screen.navigate(SkinLibraryScreen.Page.EDITOR_LOCKED);
        field(SkinLibraryScreen.class, "searchDueAt").setLong(screen, 1);
        screen.tick();
        assertNull(field(SkinLibraryScreen.class, "pendingQuery").get(screen),
                "An expired debounce must not start a request on the editor tab");

        screen.navigate(SkinLibraryScreen.Page.LIBRARY);
        screen.tick();
        assertTrue(requestQuery.get(4, TimeUnit.SECONDS).contains("q=winter"));
        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();

        assertEquals(invoke(screen, "createQuery"), field(SkinLibraryScreen.class, "loadedQuery").get(screen));
        assertSame(search, field(SkinLibraryScreen.class, "searchWidget").get(screen));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void likeCompletingAfterLeavingDetailUpdatesCurrentLibrary(boolean like) throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.DETAIL);
        LiteContent skin = new LiteContent(42, 7, "owner", 8, Set.of("clothing"), "Skin", 1, !like, Set.of());
        prepareLibrary(screen, like ? SkinLibraryScreen.SubscriptionFilter.LIBRARY : SkinLibraryScreen.SubscriptionFilter.LIKED,
                List.of(skin));
        field(SkinLibraryScreen.class, "focusedContent").set(screen, skin);
        AtomicBoolean obsoleteViewCalled = new AtomicBoolean();
        invoke(screen, "setLike", new Class<?>[]{LiteContent.class, boolean.class, Consumer.class},
                skin, like, (Consumer<Boolean>) success -> obsoleteViewCalled.set(true));
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS));

        screen.navigate(SkinLibraryScreen.Page.LIBRARY);
        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();

        assertFalse(obsoleteViewCalled.get());
        assertEquals(like, invoke(screen, "isLiked", new Class<?>[]{LiteContent.class}, skin));
        assertEquals(like ? 9 : 7, invoke(screen, "getLikes", new Class<?>[]{LiteContent.class}, skin));
        assertEquals(like ? List.of(skin) : List.of(), field(SkinLibraryScreen.class, "contents").get(screen));
    }

    @ParameterizedTest
    @CsvSource({"true, false", "false, false", "true, true", "false, true"})
    void reopenedDetailHeartReflectsOnlyCurrentAccountLikeCompletion(boolean like, boolean invalidateAccount) throws Exception {
        ObservedScreen screen = screen(SkinLibraryScreen.Page.DETAIL);
        field(SkinLibraryScreen.class, "authenticated").setBoolean(screen, true);
        LiteContent skin = new LiteContent(42, 7, "owner", 8, Set.of("clothing"), "Skin", 1, !like, Set.of());
        field(SkinLibraryScreen.class, "focusedContent").set(screen, skin);
        screen.navigate(SkinLibraryScreen.Page.DETAIL);
        ToggleableTooltipIconButtonWidget original = likeButton(screen);
        assertEquals(!like, original.toggle);

        original.onPress();
        assertTrue(requestEntered.await(4, TimeUnit.SECONDS), "Like mutation never reached the test server");
        screen.navigate(SkinLibraryScreen.Page.EDITOR_LOCKED);
        if (invalidateAccount) {
            invoke(screen, "invalidateAccountRequests");
        }
        screen.navigate(SkinLibraryScreen.Page.DETAIL);
        ToggleableTooltipIconButtonWidget replacement = likeButton(screen);
        assertNotSame(original, replacement);
        assertEquals(!like, replacement.toggle, "Pending mutation has not completed yet");

        releaseResponse.countDown();
        awaitClientTask();
        drainClientTasks();

        boolean expected = invalidateAccount ? !like : like;
        assertEquals(expected, invoke(screen, "isLiked", new Class<?>[]{LiteContent.class}, skin));
        assertEquals(expected, likeButton(screen).toggle,
                "The visible heart must reflect the accepted account state after reopening the detail");
        assertEquals(1, requestCount.get(), "Reopening the detail must not send another mutation");
    }

    private static ToggleableTooltipIconButtonWidget likeButton(SkinLibraryScreen screen) throws Exception {
        Field tooltipMessage = field(Tooltip.class, "message");
        for (var child : screen.children()) {
            if (child instanceof ToggleableTooltipIconButtonWidget button && button.getTooltip() != null
                    && Component.translatable("gui.skin_library.like").equals(tooltipMessage.get(button.getTooltip()))) {
                return button;
            }
        }
        throw new AssertionError("No mounted skin like button");
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
        field(SkinLibraryScreen.class, "requestEpoch").set(screen, new SkinLibraryScreen.RequestEpoch());
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

    private void prepareLibrary(ObservedScreen screen, SkinLibraryScreen.SubscriptionFilter filter, List<LiteContent> cached) throws Exception {
        Field catalogOutdated = field(ClientSkinCatalog.class, "customSkinsOutdated");
        formerCatalogOutdated = catalogOutdated.getBoolean(null);
        catalogOutdated.setBoolean(null, false);
        field(SkinLibraryScreen.class, "authenticated").setBoolean(screen, true);
        field(SkinLibraryScreen.class, "subscriptionFilter").set(screen, filter);
        field(SkinLibraryScreen.class, "sortingMode").set(screen, SkinLibraryScreen.SortingMode.RECOMMENDATIONS);
        field(SkinLibraryScreen.class, "filteredString").set(screen, "");
        field(SkinLibraryScreen.class, "remoteContents").set(screen, new ArrayList<>(cached));
        field(SkinLibraryScreen.class, "serverContent").set(screen, new ArrayList<>());
        field(SkinLibraryScreen.class, "contents").set(screen, new ArrayList<>());
        field(SkinLibraryScreen.class, "loadedQuery").set(screen, invoke(screen, "createQuery"));
    }

    private Workspace workspace(String title) {
        Workspace workspace = new Workspace(new NativeImage(64, 64, false));
        workspace.skinType = SkinLibraryScreen.SkinType.CLOTHING;
        workspace.tags = "one, two, three";
        workspace.title = title;
        workspaces.add(workspace);
        return workspace;
    }

    private Content serveUpload(Workspace workspace, boolean fetchSucceeds) throws IOException {
        uploadFixtureId = ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1_000_000);
        workspace.contentid = uploadFixtureId;
        Content content = new Content(uploadFixtureId, 7, "owner", 0, Set.of("clothing"), workspace.title, 3, false,
                Set.of(), "{\"temperature\":3}", Base64.getEncoder().encodeToString(workspace.currentImage.asByteArray()));
        String response = new Gson().toJson(new ContentResponse(fetchSucceeds ? content : null));
        server.createContext("/v3/content/mca/" + uploadFixtureId, exchange -> respond(exchange, 200, response));
        return content;
    }

    private Path uploadVersionMarker() {
        return Path.of("immersive_library", uploadFixtureId + ".version");
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

    private static Object invoke(Object object, String methodName) throws Exception {
        return invoke(object, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object object, String methodName, Class<?>[] types, Object... args) throws Exception {
        Method method = SkinLibraryScreen.class.getDeclaredMethod(methodName, types);
        method.setAccessible(true);
        return method.invoke(object, args);
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

        void navigate(Page page) {
            super.setPage(page);
        }

        @Override
        public void setPage(Page page) {
            lastNavigation = page;
        }
    }
}
