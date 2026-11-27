package net.conczin.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.util.AtomicFileMove;
import net.conczin.mca.client.gui.immersive_library.responses.ContentResponse;
import net.conczin.mca.client.gui.immersive_library.responses.Response;
import net.conczin.mca.client.gui.immersive_library.types.Content;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.client.resources.SkinMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

import static net.conczin.mca.client.gui.immersive_library.Api.request;

public class SkinCache {
    static final Path CACHE_DIR = Path.of("immersive_library");
    static final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();
    static final Map<Integer, Object> loading = new ConcurrentHashMap<>();
    static final Map<Integer, Integer> cachedVersions = new ConcurrentHashMap<>();
    static final Map<Integer, Identifier> textureIdentifiers = new ConcurrentHashMap<>();
    static final Map<Integer, SkinMeta> metas = new ConcurrentHashMap<>();
    private static final Identifier DEFAULT_SKIN = MCA.locate("skins/empty.png");
    private static final Gson gson = new Gson();
    private static final Object[] FILE_LOCKS = new Object[64];

    static {
        for (int i = 0; i < FILE_LOCKS.length; i++) {
            FILE_LOCKS[i] = new Object();
        }
    }

    private static Path getPath(String key) {
        return CACHE_DIR.resolve(key);
    }

    public static void writeAtomic(Path target, byte[] bytes) throws IOException {
        Path normalized = target.toAbsolutePath().normalize();
        synchronized (fileLock(normalized)) {
            Path parent = normalized.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, normalized.getFileName().toString() + ".", ".tmp");
            try {
                Files.write(tempFile, bytes);
                AtomicFileMove.replace(tempFile, normalized);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    static byte[] readBytes(Path path) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        // A Windows reader can temporarily prevent replacing the target file.
        synchronized (fileLock(normalized)) {
            return Files.readAllBytes(normalized);
        }
    }

    private static Object fileLock(Path normalized) {
        return FILE_LOCKS[Math.floorMod(normalized.hashCode(), FILE_LOCKS.length)];
    }

    static String readUtf8(Path path) throws IOException {
        return new String(readBytes(path), StandardCharsets.UTF_8);
    }

    private static String read(String file) throws IOException {
        return readUtf8(getPath(file));
    }

    private static int readDiskVersion(int contentid) {
        Path file = getPath(contentid + ".version");
        if (Files.exists(file)) {
            try {
                return Integer.parseInt(readUtf8(file).trim());
            } catch (IOException | NumberFormatException e) {
                MCA.LOGGER.warn("Invalid cached version for immersive library asset {}", contentid, e);
            }
        }
        return -1;
    }

    /**
     * @param contentid The content id
     *                  Enforces re downloading the assets, mostly when local files appear to be corrupted
     */
    public static synchronized void enforceSync(int contentid) {
        try {
            Files.deleteIfExists(getPath(contentid + ".version"));
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to delete corrupted version file for {}", contentid, e);
        } finally {
            cachedVersions.remove(contentid);
            invalidateLoadedResources(contentid);
        }
    }

    private static void invalidateLoadedResources(int contentid) {
        loading.remove(contentid);
        textureIdentifiers.remove(contentid);
        metas.remove(contentid);
    }

    public static void sync(LiteContent content) {
        sync(content.content_id(), content.version());
    }

    /**
     * @param contentid      The content id
     * @param currentVersion The current version, used to invalidate the cache
     *                       Downloads the assets if they are not up to date
     */
    public static void sync(int contentid, int currentVersion) {
        // Fetch the version identifier which we have on disk, or -1
        int version = cachedVersions.computeIfAbsent(contentid, SkinCache::readDiskVersion);

        // A committed cached version can render while its replacement downloads.
        if (version >= 0 && !textureIdentifiers.containsKey(contentid)) {
            loadResources(contentid, version);
        }

        if (currentVersion != version && (currentVersion > version || !textureIdentifiers.containsKey(contentid))
                && requested.putIfAbsent(contentid, true) == null) {
            CompletableFuture.runAsync(() -> {
                try {
                    Response response = request(Api.HttpMethod.GET, ContentResponse.class, "v3/content/mca/" + contentid, Map.of("version", String.valueOf(version)));
                    if (response instanceof ContentResponse(Content content) && content != null) {
                        store(content);
                    }
                } catch (RuntimeException e) {
                    MCA.LOGGER.warn("Unable to sync immersive library asset {}", contentid, e);
                } finally {
                    requested.remove(contentid);
                }
            });
        }
    }

    public static synchronized void updateAfterUpload(int contentId, Content content, BooleanSupplier isCurrentAccount) {
        // The account can change while the upload is waiting for another cache write.
        if (!isCurrentAccount.getAsBoolean()) return;
        if (content == null) {
            enforceSync(contentId);
        } else {
            store(content);
        }
    }

    public static synchronized void store(Content content) {
        int contentid = content.content_id();
        int cachedVersion = cachedVersions.computeIfAbsent(contentid, SkinCache::readDiskVersion);
        if (content.version() <= cachedVersion) {
            return;
        }
        byte[] png = Base64.getDecoder().decode(content.data());
        byte[] json = content.meta().getBytes(StandardCharsets.UTF_8);
        Path marker = getPath(contentid + ".version");
        try {
            // An interrupted replacement can leave assets from different versions.
            Files.deleteIfExists(marker);
            cachedVersions.put(contentid, -1);
            invalidateLoadedResources(contentid);
            writeAtomic(getPath(contentid + ".png"), png);
            writeAtomic(getPath(contentid + ".json"), json);
            writeAtomic(marker, Integer.toString(content.version()).getBytes(StandardCharsets.UTF_8));
            cachedVersions.put(contentid, content.version());
        } catch (IOException e) {
            MCA.LOGGER.warn("Failed to store immersive library asset {}", contentid, e);
        }
    }

    private static void loadResources(int contentid, int expectedVersion) {
        Object load = new Object();
        if (loading.putIfAbsent(contentid, load) != null) {
            return;
        }
        CompletableFuture.runAsync(() -> decodeResources(contentid, expectedVersion, load));
    }

    private static boolean isCurrentLoad(int contentid, int expectedVersion, Object load) {
        return loading.get(contentid) == load && cachedVersions.getOrDefault(contentid, -1) == expectedVersion;
    }

    private static void decodeResources(int contentid, int expectedVersion, Object load) {
        NativeImage unscheduledImage = null;
        boolean scheduled = false;
        try {
            if (!isCurrentLoad(contentid, expectedVersion, load)) {
                return;
            }
            SkinMeta meta = gson.fromJson(read(contentid + ".json"), SkinMeta.class);
            if (meta == null) {
                throw new JsonParseException("Empty skin metadata for " + contentid);
            }

            NativeImage image;
            byte[] pngBytes = readBytes(getPath(contentid + ".png"));
            try (InputStream stream = new ByteArrayInputStream(pngBytes)) {
                image = NativeImage.read(stream);
            }
            unscheduledImage = image;
            Minecraft client = Minecraft.getInstance();
            if (client != null) {
                client.execute(() -> registerTexture(client, contentid, expectedVersion, load, meta, image));
                scheduled = true;
            }
        } catch (JsonParseException | IOException e) {
            MCA.LOGGER.warn("Failed to load immersive library asset {}", contentid, e);
            synchronized (SkinCache.class) {
                if (isCurrentLoad(contentid, expectedVersion, load)) {
                    enforceSync(contentid);
                }
            }
        } finally {
            if (!scheduled) {
                if (unscheduledImage != null) {
                    unscheduledImage.close();
                }
                loading.remove(contentid, load);
            }
        }
    }

    private static synchronized void registerTexture(Minecraft client, int contentid, int expectedVersion,
                                                     Object load, SkinMeta meta, NativeImage image) {
        DynamicTexture texture = null;
        boolean registered = false;
        try {
            if (!isCurrentLoad(contentid, expectedVersion, load)) {
                return;
            }
            TextureManager textureManager = client.getTextureManager();
            if (textureManager == null) {
                return;
            }
            Identifier identifier = Identifier.fromNamespaceAndPath("immersive_library", String.valueOf(contentid));
            texture = new DynamicTexture(() -> "immersive_library/" + contentid, image);
            DynamicSkinCache.invalidateSourceTexture(identifier);
            textureManager.register(identifier, texture);
            registered = true;
            metas.put(contentid, meta);
            textureIdentifiers.put(contentid, identifier);
        } finally {
            if (!registered) {
                if (texture == null) {
                    image.close();
                } else {
                    texture.close();
                }
            }
            loading.remove(contentid, load);
        }
    }

    public static Optional<SkinMeta> getMeta(LiteContent content) {
        sync(content);
        return Optional.ofNullable(metas.get(content.content_id()));
    }

    public static Optional<NativeImage> getImage(LiteContent content) {
        sync(content);
        Minecraft client = Minecraft.getInstance();
        if (client == null || !client.isSameThread()) {
            return Optional.empty();
        }
        Identifier identifier = textureIdentifiers.get(content.content_id());
        if (identifier == null) {
            return Optional.empty();
        }
        TextureManager textureManager = client.getTextureManager();
        if (textureManager == null) {
            return Optional.empty();
        }
        AbstractTexture texture = textureManager.getTexture(identifier);
        if (texture instanceof DynamicTexture dynamicTexture) {
            return Optional.ofNullable(dynamicTexture.getPixels());
        }
        return Optional.empty();
    }

    public static Identifier getTextureIdentifier(LiteContent content) {
        sync(content);
        return textureIdentifiers.getOrDefault(content.content_id(), DEFAULT_SKIN);
    }

    /**
     * @param contentid The content id
     * @return The texture identifier
     * Unlike the other getters this function will sync at least once no matter the local state of the cache, as it lacks the current version
     */
    public static Identifier getTextureIdentifier(int contentid) {
        sync(contentid, -2);
        return textureIdentifiers.getOrDefault(contentid, DEFAULT_SKIN);
    }

    public static boolean isLoaded(int contentid) {
        return textureIdentifiers.containsKey(contentid);
    }
}
