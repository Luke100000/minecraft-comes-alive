package net.mca.client.gui.immersive_library;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.platform.NativeImage;
import net.mca.MCA;
import net.mca.client.gui.immersive_library.responses.ContentResponse;
import net.mca.client.gui.immersive_library.responses.Response;
import net.mca.client.gui.immersive_library.types.LiteContent;
import net.mca.client.resources.SkinMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static net.mca.client.gui.immersive_library.Api.request;

public class SkinCache {
    private static final ResourceLocation DEFAULT_SKIN = MCA.locate("skins/empty.png");

    private static final Gson gson = new Gson();

    static final Map<Integer, Boolean> requested = new ConcurrentHashMap<>();
    static final Map<Integer, Integer> cachedVersions = new ConcurrentHashMap<>();

    static final Map<Integer, ResourceLocation> textureIdentifiers = new HashMap<>();
    static final Map<Integer, NativeImage> images = new HashMap<>();
    static final Map<Integer, SkinMeta> metas = new HashMap<>();

    private static File getFile(String key) {
        //noinspection ResultOfMethodCallIgnored
        new File("./immersive_library/").mkdirs();

        return new File("./immersive_library/" + key);
    }

    private static void write(String file, String content) {
        try {
            FileUtils.writeStringToFile(getFile(file), content, Charset.defaultCharset(), false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void write(String file, byte[] content) {
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(getFile(file)))) {
            out.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String read(String file) throws IOException {
        return FileUtils.readFileToString(getFile(file), Charset.defaultCharset());
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
                    String s = FileUtils.readFileToString(file, Charset.defaultCharset());
                    return Integer.parseInt(s);
                } catch (Exception e) {
                    MCA.LOGGER.warn(e);
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
            if (!requested.containsKey(contentid) && (currentVersion > version || !textureIdentifiers.containsKey(contentid))) {
                requested.put(contentid, true);
                CompletableFuture.runAsync(() -> {
                    logger("Requested asset " + contentid + " with version " + version + " and current version " + currentVersion);
                    Response response = request(Api.HttpMethod.GET, ContentResponse.class, "content/mca/" + contentid, Map.of("version", String.valueOf(version)));
                    if (response instanceof ContentResponse contentResponse) {
                        int newVersion = contentResponse.content().version();
                        write(contentid + ".png", Base64.getDecoder().decode(contentResponse.content().data()));
                        write(contentid + ".json", contentResponse.content().meta());
                        write(contentid + ".version", Integer.toString(newVersion));
                        cachedVersions.put(contentid, newVersion);
                        requested.remove(contentid);
                        textureIdentifiers.remove(contentid);
                        logger("Received " + contentid);
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
            e.printStackTrace();
            enforceSync(contentid);
            return;
        }

        // Load texture
        try (FileInputStream stream = new FileInputStream(getFile(contentid + ".png").getPath())) {
            // Load new
            NativeImage image = NativeImage.read(stream);
            ResourceLocation identifier = new ResourceLocation("immersive_library", String.valueOf(contentid));

            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            textureManager.register(identifier, new DynamicTexture(image));

            textureIdentifiers.put(contentid, identifier);
            images.put(contentid, image);
        } catch (IOException e) {
            e.printStackTrace();
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

    public static ResourceLocation getTextureIdentifier(LiteContent content) {
        sync(content);
        return textureIdentifiers.getOrDefault(content.contentid(), DEFAULT_SKIN);
    }

    /**
     * @param contentid The content id
     * @return The texture identifier
     * Unlike the other getters this function will sync at least once no matter the local state of the cache, as it lacks the current version
     */
    public static ResourceLocation getTextureIdentifier(int contentid) {
        sync(contentid, -2);
        return textureIdentifiers.getOrDefault(contentid, DEFAULT_SKIN);
    }
}
