package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.client.gui.SkinLibraryScreen;
import net.conczin.mca.client.gui.immersive_library.responses.ContentListResponse;
import net.conczin.mca.client.gui.immersive_library.responses.Response;
import net.conczin.mca.client.gui.immersive_library.types.Content;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.util.thread.BlockableEventLoop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sun.misc.Unsafe;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTextureDevice.class)
class SkinCacheVersionTest {
    @Test
    void olderCompletionCannotReplaceNewerCachedVersion() throws Exception {
        int id = uniqueTestId();
        try {
            SkinCache.store(content(id, 5));
            Identifier newerTexture = Identifier.fromNamespaceAndPath("mca", "version_test");
            SkinCache.textureIdentifiers.put(id, newerTexture);

            SkinCache.store(content(id, 4));

            assertVersionFive(id);
            assertEquals(newerTexture, SkinCache.textureIdentifiers.get(id));
            SkinCache.store(content(id, 5));
            assertVersionFive(id);
        } finally {
            cleanup(id);
        }
    }

    @Test
    void higherCompletionReplacesEarlierVersion() throws Exception {
        int id = uniqueTestId();
        try {
            SkinCache.store(content(id, 4));
            SkinCache.store(content(id, 5));
            assertVersionFive(id);
        } finally {
            cleanup(id);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {".png", ".json"})
    void failedAssetWriteDoesNotAdvanceVersion(String suffix) throws Exception {
        int id = uniqueTestId();
        Path blockedAsset = SkinCache.CACHE_DIR.resolve(id + suffix);
        Path child = blockedAsset.resolve("sentinel");
        Path marker = SkinCache.CACHE_DIR.resolve(id + ".version");
        try {
            SkinCache.store(content(id, 4));
            assertEquals("4", Files.readString(marker));
            Files.delete(blockedAsset);
            Files.createDirectory(blockedAsset);
            Files.createFile(child);

            SkinCache.store(content(id, 5));

            assertAll(
                    () -> assertFalse(Files.exists(marker), "Incomplete assets must be unversioned on disk"),
                    () -> assertEquals(-1, SkinCache.cachedVersions.getOrDefault(id, -1)));

            Files.delete(child);
            Files.delete(blockedAsset);
            SkinCache.store(content(id, 5));
            assertVersionFive(id);
        } finally {
            if (Files.isDirectory(blockedAsset)) {
                Files.deleteIfExists(child);
            }
            cleanup(id);
        }
    }

    @Test
    void missingVersionCannotBeUsedToLoadAssets() throws Exception {
        int id = uniqueTestId();
        synchronized (SkinCache.class) {
            try {
                SkinCache.store(content(id, 1));
                SkinCache.enforceSync(id);

                SkinCache.sync(id, -1);

                assertFalse(SkinCache.loading.containsKey(id), "Unversioned files must not be scheduled for decoding");
            } finally {
                cleanup(id);
            }
        }
    }

    @Test
    void concurrentAtomicWritesNeverExposeTruncatedOrCorruptedFiles(@TempDir Path tempDir) throws Exception {
        Path target = tempDir.resolve("concurrent_skin.json");
        byte[] payloadA = ("{\"title\":\"alpha-żółć\",\"pad\":\"" + "a".repeat(2048) + "\"}").getBytes(StandardCharsets.UTF_8);
        byte[] payloadB = ("{\"title\":\"beta-ümlaut\",\"pad\":\"" + "b".repeat(4096) + "\"}").getBytes(StandardCharsets.UTF_8);
        SkinCache.writeAtomic(target, payloadA);

        int writers = 4;
        int readers = 4;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(writers + readers)) {
            for (int w = 0; w < writers; w++) {
                final int writerId = w;
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 60; i++) {
                        SkinCache.writeAtomic(target, ((writerId + i) & 1) == 0 ? payloadA : payloadB);
                    }
                    return null;
                }));
            }
            for (int r = 0; r < readers; r++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < 120; i++) {
                        byte[] observed = SkinCache.readBytes(target);
                        assertTrue(Arrays.equals(observed, payloadA) || Arrays.equals(observed, payloadB),
                                "Observed truncated or torn write of length " + observed.length);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        }

        try (var residual = Files.list(tempDir)) {
            assertEquals(List.of(target), residual.toList(), "Temporary sibling files must be cleaned up");
        }
    }

    @Test
    void replacingDynamicTextureFreesOldPixelsWhileEditorReadsLiveUpdatedPixels() throws Exception {
        assertFalse(Arrays.stream(SkinCache.class.getDeclaredFields())
                .anyMatch(f -> f.getName().equals("images")),
                "SkinCache must not retain a dual-owned NativeImage map");

        int id = uniqueTestId();
        Minecraft formerMinecraft = (Minecraft) field(Minecraft.class, "instance").get(null);
        Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
        TextureManager textureManager = new TextureManager(null);
        try {
            Minecraft minecraft = allocate(Minecraft.class);
            field(Minecraft.class, "gameThread").set(minecraft, Thread.currentThread());
            field(BlockableEventLoop.class, "pendingRunnables").set(minecraft, clientTasks);
            field(Minecraft.class, "textureManager").set(minecraft, textureManager);
            field(Minecraft.class, "instance").set(null, minecraft);

            Content v1 = pngContent(id, 1, 0xFF112233);
            SkinCache.store(v1);
            LiteContent v1Lite = v1.lite();
            SkinCache.sync(v1Lite);
            awaitAndDrainClientTasks(clientTasks);

            NativeImage firstPixels = SkinCache.getImage(v1Lite).orElseThrow();
            assertEquals(0xFF112233, firstPixels.getPixel(0, 0));

            Content v2 = pngContent(id, 2, 0xFF445566);
            SkinCache.store(v2);
            LiteContent v2Lite = v2.lite();
            SkinCache.sync(v2Lite);
            awaitAndDrainClientTasks(clientTasks);

            assertThrows(IllegalStateException.class, () -> firstPixels.getPixel(0, 0),
                    "Replacing a DynamicTexture closes its previous NativeImage");

            NativeImage livePixels = SkinCache.getImage(v2Lite).orElseThrow();
            assertEquals(0xFF445566, livePixels.getPixel(0, 0));
            try (Workspace workspace = new Workspace(livePixels, SkinCache.getMeta(v2Lite).orElseThrow(), v2Lite)) {
                assertEquals(0xFF445566, workspace.currentImage.getPixel(0, 0));
            }
        } finally {
            drain(clientTasks);
            field(Minecraft.class, "instance").set(null, formerMinecraft);
            textureManager.close();
            cleanup(id);
        }
    }

    @Test
    void olderDecodeCannotPublishTextureAfterNewerVersion() throws Exception {
        int id = uniqueTestId();
        Minecraft formerMinecraft = (Minecraft) field(Minecraft.class, "instance").get(null);
        Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
        TextureManager textureManager = new TextureManager(null);
        try {
            Minecraft minecraft = allocate(Minecraft.class);
            field(Minecraft.class, "gameThread").set(minecraft, Thread.currentThread());
            field(BlockableEventLoop.class, "pendingRunnables").set(minecraft, clientTasks);
            field(Minecraft.class, "textureManager").set(minecraft, textureManager);
            field(Minecraft.class, "instance").set(null, minecraft);

            Content v1 = pngContent(id, 1, 0xFF112233);
            SkinCache.store(v1);
            SkinCache.sync(v1.lite());
            awaitAndDrainClientTasks(clientTasks);
            Identifier identifier = SkinCache.getTextureIdentifier(v1.lite());
            var registeredTexture = textureManager.getTexture(identifier);
            NativeImage firstPixels = SkinCache.getImage(v1.lite()).orElseThrow();

            Content v2 = pngContent(id, 2, 0xFF445566);
            SkinCache.store(v2);
            SkinCache.sync(v2.lite());
            awaitClientTasks(clientTasks);
            var earlyMeta = SkinCache.metas.get(id);
            Runnable staleRegistration = clientTasks.remove();
            NativeImage stalePixels = capturedImage(staleRegistration);

            Content v3 = pngContent(id, 3, 0xFF778899);
            SkinCache.store(v3);
            staleRegistration.run();

            assertAll(
                    () -> assertTrue(earlyMeta == null || earlyMeta.getTemperature() == 1,
                            "Decoded metadata must wait for texture registration"),
                    () -> assertSame(registeredTexture, textureManager.getTexture(identifier),
                            "An obsolete completion must leave the texture manager entry alone"),
                    () -> assertEquals(0xFF112233, firstPixels.getPixel(0, 0)),
                    () -> assertThrows(IllegalStateException.class, () -> stalePixels.getPixel(0, 0),
                            "An obsolete completion must free its unregistered image"));

            SkinCache.sync(v3.lite());
            awaitAndDrainClientTasks(clientTasks);
            assertEquals(0xFF778899, SkinCache.getImage(v3.lite()).orElseThrow().getPixel(0, 0));
            assertEquals(3, SkinCache.getMeta(v3.lite()).orElseThrow().getTemperature());
        } finally {
            drain(clientTasks);
            field(Minecraft.class, "instance").set(null, formerMinecraft);
            textureManager.close();
            cleanup(id);
        }
    }

    @Test
    void invalidatedDecodeCannotPublishAfterSameVersionIsStoredAgain() throws Exception {
        int id = uniqueTestId();
        Minecraft formerMinecraft = (Minecraft) field(Minecraft.class, "instance").get(null);
        Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
        TextureManager textureManager = new TextureManager(null);
        try {
            Minecraft minecraft = allocate(Minecraft.class);
            field(Minecraft.class, "gameThread").set(minecraft, Thread.currentThread());
            field(BlockableEventLoop.class, "pendingRunnables").set(minecraft, clientTasks);
            field(Minecraft.class, "textureManager").set(minecraft, textureManager);
            field(Minecraft.class, "instance").set(null, minecraft);

            Content first = pngContent(id, 2, 0xFF112233);
            SkinCache.store(first);
            SkinCache.sync(first.lite());
            awaitClientTasks(clientTasks);
            Runnable invalidatedRegistration = clientTasks.remove();
            NativeImage invalidatedPixels = capturedImage(invalidatedRegistration);

            SkinCache.enforceSync(id);
            Content repaired = pngContent(id, 2, 0xFF445566, 3);
            SkinCache.store(repaired);
            SkinCache.sync(repaired.lite());
            invalidatedRegistration.run();

            Identifier identifier = Identifier.fromNamespaceAndPath("immersive_library", String.valueOf(id));
            assertAll(
                    () -> assertFalse(((Map<?, ?>) field(TextureManager.class, "byPath").get(textureManager)).containsKey(identifier)),
                    () -> assertNull(SkinCache.metas.get(id)),
                    () -> assertThrows(IllegalStateException.class, () -> invalidatedPixels.getPixel(0, 0)),
                    () -> assertTrue(SkinCache.loading.containsKey(id), "Cancellation must leave the replacement load active"));

            awaitAndDrainClientTasks(clientTasks);
            assertEquals(0xFF445566, SkinCache.getImage(repaired.lite()).orElseThrow().getPixel(0, 0));
            assertEquals(3, SkinCache.getMeta(repaired.lite()).orElseThrow().getTemperature());
        } finally {
            drain(clientTasks);
            field(Minecraft.class, "instance").set(null, formerMinecraft);
            textureManager.close();
            cleanup(id);
        }
    }

    @Test
    void windowsPathsDroppedPathsAndNullContentArrayAreHandledSafely(@TempDir Path tempDir) throws Exception {
        assertTrue(SkinLibraryScreen.isLocalPathInput("C:/Users/dev/skin.png"));
        assertTrue(SkinLibraryScreen.isLocalPathInput("D:\\skins\\villager.png"));
        assertTrue(SkinLibraryScreen.isLocalPathInput("relative/skin.png"));
        assertFalse(SkinLibraryScreen.isLocalPathInput("https://example.com/skin.png"));
        assertFalse(SkinLibraryScreen.isLocalPathInput("http://127.0.0.1:8080/skin.png"));

        byte[] pngBytes = createSolidPng(0xFFABCDEF);
        Path localSkin = tempDir.resolve("skin.png");
        Files.write(localSkin, pngBytes);
        String forwardSlashPath = localSkin.toAbsolutePath().toString().replace('\\', '/');
        try (InputStream stream = invokeOpenImage(forwardSlashPath)) {
            assertArrayEquals(pngBytes, stream.readAllBytes());
        }

        // Verify onFilesDrop reads Path directly without URI scheme failure
        Minecraft formerMinecraft = (Minecraft) field(Minecraft.class, "instance").get(null);
        Queue<Runnable> clientTasks = new ConcurrentLinkedQueue<>();
        Workspace importedWorkspace = null;
        try {
            Minecraft minecraft = allocate(Minecraft.class);
            field(Minecraft.class, "gameThread").set(minecraft, Thread.currentThread());
            field(BlockableEventLoop.class, "pendingRunnables").set(minecraft, clientTasks);
            field(Minecraft.class, "instance").set(null, minecraft);

            DropTestScreen dropScreen = allocate(DropTestScreen.class);
            initializeRequestEpoch(dropScreen);
            field(SkinLibraryScreen.class, "page").set(dropScreen, SkinLibraryScreen.Page.EDITOR_PREPARE);
            field(Minecraft.class, "screen").set(minecraft, dropScreen);

            dropScreen.onFilesDrop(List.of(localSkin));
            // If decoding finished early, Minecraft can run the completion inline on this thread.
            if (dropScreen.navigatedPage == null) {
                awaitAndDrainClientTasks(clientTasks);
            }

            importedWorkspace = (Workspace) field(SkinLibraryScreen.class, "workspace").get(dropScreen);
            assertNotNull(importedWorkspace, "onFilesDrop must load valid 64x64 Path directly into workspace");
            assertEquals(0xFFABCDEF, importedWorkspace.currentImage.getPixel(0, 0));
            assertEquals(SkinLibraryScreen.Page.EDITOR_TYPE, dropScreen.navigatedPage);
        } finally {
            drain(clientTasks);
            field(Minecraft.class, "instance").set(null, formerMinecraft);
            if (importedWorkspace != null) {
                importedWorkspace.close();
            }
        }

        // Verify null Content[] (from "{}" response) does not throw NPE in acceptContents
        SkinLibraryScreen listScreen = allocate(SkinLibraryScreen.class);
        initializeRequestEpoch(listScreen);
        field(SkinLibraryScreen.class, "page").set(listScreen, SkinLibraryScreen.Page.LIBRARY);
        field(SkinLibraryScreen.class, "filteredString").set(listScreen, "");
        field(SkinLibraryScreen.class, "sortingMode").set(listScreen, SkinLibraryScreen.SortingMode.RECOMMENDATIONS);
        field(SkinLibraryScreen.class, "subscriptionFilter").set(listScreen, SkinLibraryScreen.SubscriptionFilter.LIBRARY);
        field(SkinLibraryScreen.class, "remoteContents").set(listScreen, new ArrayList<>());
        Method createQuery = SkinLibraryScreen.class.getDeclaredMethod("createQuery");
        createQuery.setAccessible(true);
        Object query = createQuery.invoke(listScreen);
        field(SkinLibraryScreen.class, "pendingQuery").set(listScreen, query);

        Method acceptContents = SkinLibraryScreen.class.getDeclaredMethod(
                "acceptContents", query.getClass(), long.class, long.class, String.class, Response.class, Throwable.class);
        acceptContents.setAccessible(true);
        assertDoesNotThrow(() -> acceptContents.invoke(
                listScreen, query, 0L, 0L, Auth.getToken(), new ContentListResponse(null, false), null));
        assertNull(field(SkinLibraryScreen.class, "pendingQuery").get(listScreen));
        assertNull(field(SkinLibraryScreen.class, "loadedQuery").get(listScreen));
        assertNotNull(field(SkinLibraryScreen.class, "error").get(listScreen));
    }

    private static void initializeRequestEpoch(SkinLibraryScreen screen) throws Exception {
        Field epoch = field(SkinLibraryScreen.class, "requestEpoch");
        var constructor = epoch.getType().getDeclaredConstructor();
        constructor.setAccessible(true);
        epoch.set(screen, constructor.newInstance());
    }

    private static final class DropTestScreen extends SkinLibraryScreen {
        private Page navigatedPage;

        @Override
        public void setPage(Page page) {
            this.navigatedPage = page;
        }
    }

    private static InputStream invokeOpenImage(String path) throws Exception {
        Method method = SkinLibraryScreen.class.getDeclaredMethod("openImage", String.class);
        method.setAccessible(true);
        try {
            return (InputStream) method.invoke(null, path);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw e;
        }
    }

    private static Content pngContent(int id, int version, int pixelColor) throws IOException {
        return pngContent(id, version, pixelColor, version);
    }

    private static Content pngContent(int id, int version, int pixelColor, int temperature) throws IOException {
        byte[] png = createSolidPng(pixelColor);
        String meta = "{\"temperature\":" + temperature + ",\"gender\":0,\"chance\":1.0}";
        return new Content(id, 1, "tester", 0, Set.of("clothing"), "skin-" + version, version, false, Set.of(),
                meta, Base64.getEncoder().encodeToString(png));
    }

    private static byte[] createSolidPng(int pixelColor) throws IOException {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, pixelColor);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", bytes)) {
            throw new IOException("No PNG image writer available");
        }
        return bytes.toByteArray();
    }

    private static void awaitAndDrainClientTasks(Queue<Runnable> tasks) throws InterruptedException {
        awaitClientTasks(tasks);
        drain(tasks);
    }

    private static void awaitClientTasks(Queue<Runnable> tasks) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (tasks.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
        assertFalse(tasks.isEmpty(), "Expected render-thread task to be queued");
    }

    private static NativeImage capturedImage(Runnable task) throws IllegalAccessException {
        Field field = Arrays.stream(task.getClass().getDeclaredFields())
                .filter(candidate -> candidate.getType() == NativeImage.class)
                .findFirst().orElseThrow();
        field.setAccessible(true);
        return (NativeImage) field.get(task);
    }

    private static void drain(Queue<Runnable> tasks) {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    private static Field field(Class<?> owner, String name) throws Exception {
        Field f = owner.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = field(Unsafe.class, "theUnsafe");
        return type.cast(((Unsafe) unsafeField.get(null)).allocateInstance(type));
    }

    private static int uniqueTestId() {
        return ThreadLocalRandom.current().nextInt(Integer.MIN_VALUE, -1_000_000);
    }

    private static Content content(int id, int version) {
        return new Content(id, 1, "test", 0, Set.of(), "asset", version, false, Set.of(),
                "{\"version\":" + version + "}", Base64.getEncoder().encodeToString(new byte[]{(byte) version}));
    }

    private static void assertVersionFive(int id) throws Exception {
        Path directory = Path.of("immersive_library");
        assertEquals("5", Files.readString(directory.resolve(id + ".version"), StandardCharsets.UTF_8));
        assertEquals("{\"version\":5}", Files.readString(directory.resolve(id + ".json"), StandardCharsets.UTF_8));
        assertArrayEquals(new byte[]{5}, Files.readAllBytes(directory.resolve(id + ".png")));
        assertEquals(5, SkinCache.cachedVersions.get(id));
    }

    private static void cleanup(int id) throws Exception {
        SkinCache.cachedVersions.remove(id);
        SkinCache.textureIdentifiers.remove(id);
        SkinCache.loading.remove(id);
        SkinCache.metas.remove(id);
        SkinCache.requested.remove(id);
        Path directory = Path.of("immersive_library");
        Files.deleteIfExists(directory.resolve(id + ".png"));
        Files.deleteIfExists(directory.resolve(id + ".json"));
        Files.deleteIfExists(directory.resolve(id + ".version"));
    }
}
