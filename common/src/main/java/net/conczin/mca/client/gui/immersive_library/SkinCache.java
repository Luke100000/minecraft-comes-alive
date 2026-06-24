package net.conczin.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.responses.ContentResponse;
import net.conczin.mca.client.gui.immersive_library.responses.Response;
import net.conczin.mca.client.gui.immersive_library.types.Content;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.conczin.mca.client.resources.SkinMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static net.conczin.mca.client.gui.immersive_library.Api.request;

public class SkinCache {
    static final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();
    static final Map<Integer, Integer> cachedVersions = new ConcurrentHashMap<>();
    static final Map<Integer, Identifier> textureIdentifiers = new ConcurrentHashMap<>();
    static final Map<Integer, NativeImage> images = new ConcurrentHashMap<>();
    static final Map<Integer, SkinMeta> metas = new ConcurrentHashMap<>();
    private static final Identifier DEFAULT_SKIN = MCA.locate("skins/empty.png");
    private static final Gson gson = new Gson();

    private static File getFile(String key) {
        //noinspection ResultOfMethodCallIgnored
        new File("./immersive_library/").mkdirs();

        return new File("./immersive_library/" + key);
    }

    private static void write(String file, String content) {
        try {
            FileUtils.writeStringToFile(getFile(file), content, StandardCharsets.UTF_8, false);
        } catch (IOException e) {
            MCA.LOGGER.warn("Unable to write immersive library cache file {}", file, e);
        }
    }

    private static void write(String file, byte[] content) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(file)))) {
            out.write(content);
        } catch (IOException e) {
            MCA.LOGGER.warn("Unable to write immersive library cache file {}", file, e);
        }
    }

    private static String read(String file) throws IOException {
        return FileUtils.readFileToString(getFile(file), StandardCharsets.UTF_8);
    }

    /**
     * @param contentid The content id
     *                  Enforces re downloading the assets, mostly when local files appear to be corrupted
     */
    public static void enforceSync(int contentid) {
        try {
            Files.delete(getFile(contentid + ".version").toPath());
            cachedVersions.remove(contentid);
        } catch (IOException e) {
            MCA.LOGGER.warn(e);
        }
    }

    public static void sync(LiteContent content) {
        sync(content.contentid(), content.version());
    }

    /**
     * @param contentid      The content id
     * @param currentVersion The current version, used to invalidate the cache
     *                       Downloads the assets if they are not up to date
     */
    public static void sync(int contentid, int currentVersion) {
        // Fetch the version identifier which we have on disk, or -1
        int version = cachedVersions.computeIfAbsent(contentid, id -> {
            File file = getFile(contentid + ".version");
            if (file.exists()) {
                try {
                    String s = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
                    return Integer.parseInt(s);
                } catch (Exception e) {
                    MCA.LOGGER.warn("Unable to read immersive library cache version for {}", contentid, e);
                }
            }
            return -1;
        });

        if (currentVersion == version) {
            // Up to date! Only load a resource if it's not loaded yet
            if (!textureIdentifiers.containsKey(contentid)) {
                loadResources(contentid);
            }
        } else {
            // Outdated, but we have a cached version, lets use that while we wait for the result
            if (version >= 0 && !textureIdentifiers.containsKey(contentid)) {
                loadResources(contentid);
            }

            // Download assets when versions mismatch
            if ((currentVersion > version || !textureIdentifiers.containsKey(contentid)) && requested.putIfAbsent(contentid, true) == null) {
                CompletableFuture.runAsync(() -> {
                    try {
                        logger("Requested asset " + contentid + " with version " + version + " and current version " + currentVersion);
                        Response response = request(Api.HttpMethod.GET, ContentResponse.class, "content/mca/" + contentid, Map.of("version", String.valueOf(version)));
                        if (response instanceof ContentResponse(Content content)) {
                            int newVersion = content.version();
                            write(contentid + ".png", Base64.getDecoder().decode(content.data()));
                            write(contentid + ".json", content.meta());
                            write(contentid + ".version", Integer.toString(newVersion));
                            cachedVersions.put(contentid, newVersion);
                            textureIdentifiers.remove(contentid);
                            logger("Received " + contentid);
                        }
                    } catch (RuntimeException e) {
                        MCA.LOGGER.warn("Unable to sync immersive library asset {}", contentid, e);
                    } finally {
                        requested.remove(contentid);
                    }
                });
            }
        }
    }

    /**
     * @param contentid The content id
     *                  Loads the resources from the disk and creates the texture identifier
     */
    private static void loadResources(int contentid) {
        logger("Loaded asset " + contentid);

        // Load meta
        try {
            String json = read(contentid + ".json");
            SkinMeta meta = gson.fromJson(json, SkinMeta.class);
            metas.put(contentid, meta);
        } catch (JsonSyntaxException | IOException e) {
            MCA.LOGGER.warn("Unable to load immersive library metadata for {}", contentid, e);
            enforceSync(contentid);
            return;
        }

        // Load texture
        try (FileInputStream stream = new FileInputStream(getFile(contentid + ".png").getPath())) {
            // Load new
            NativeImage image = NativeImage.read(stream);
            Identifier identifier = Identifier.fromNamespaceAndPath("immersive_library", String.valueOf(contentid));

            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            textureManager.register(identifier, new DynamicTexture(() -> "immersive_library/" + contentid, image));

            textureIdentifiers.put(contentid, identifier);
            images.put(contentid, image);
        } catch (IOException e) {
            MCA.LOGGER.warn("Unable to load immersive library texture for {}", contentid, e);
            enforceSync(contentid);
        }
    }

    private static void logger(String s) {
        //noinspection ConstantConditions
        if (false) {
            MCA.LOGGER.info(s);
        }
    }

    public static Optional<SkinMeta> getMeta(LiteContent content) {
        sync(content);
        return Optional.ofNullable(metas.get(content.contentid()));
    }

    public static Optional<NativeImage> getImage(LiteContent content) {
        sync(content);
        return Optional.ofNullable(images.get(content.contentid()));
    }

    public static Identifier getTextureIdentifier(LiteContent content) {
        sync(content);
        return textureIdentifiers.getOrDefault(content.contentid(), DEFAULT_SKIN);
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
