package net.mca.resources;

import net.mca.MCA;
import net.mca.client.render.layer.FaceLayer;
import net.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.profiler.Profiler;

import java.util.*;

public class FaceList extends SinglePreparationResourceReloader<Map<Identifier, List<String>>> {
    public static final Identifier ID = MCA.locate("eyes");
    private static FaceList INSTANCE;
    private final HashMap<String, List<String>> faces = new HashMap<>();

    public FaceList() {
        INSTANCE = this;
    }

    public static FaceList getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<Identifier, List<String>> prepare(ResourceManager manager, Profiler profiler) {
        return SkinListJson.textureCollections(manager, "eyes");
    }

    @Override
    protected void apply(Map<Identifier, List<String>> data, ResourceManager manager, Profiler profiler) {
        FaceLayer.clearGeneratedEyeTextureCache();
        faces.clear();
        data.forEach(this::addEntries);
        sortPools();
    }

    private void addEntries(Identifier id, List<String> textures) {
        String variant = id.getPath().toLowerCase(Locale.ROOT);
        textures.forEach(identifier -> {
            Identifier parsed;
            try {
                parsed = new Identifier(identifier);
            } catch (InvalidIdentifierException exception) {
                MCA.LOGGER.warn("Invalid face texture identifier {}", identifier, exception);
                return;
            }
            if (!parsed.getPath().startsWith("skins/face/")) {
                MCA.LOGGER.warn("Invalid face texture path {}", identifier);
                return;
            }
            faces.computeIfAbsent(key(variant), ignored -> new ArrayList<>()).add(identifier);
        });
    }

    public Identifier pick(String variant, float faceGene) {
        List<String> pool = faces.get(key(variant));
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant));
        }

        int index = (int) Math.min(pool.size() - 1, Math.max(0, faceGene * pool.size()));
        return new Identifier(pool.get(index));
    }

    public int count(String variant) {
        List<String> pool = faces.get(key(variant));
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant));
        }
        return pool.size();
    }

    private void sortPools() {
        faces.values().forEach(pool -> pool.sort(SkinListEntry::compareIdentifiers));
    }

    private static String key(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }
}
