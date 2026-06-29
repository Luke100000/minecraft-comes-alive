package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class FaceList extends SimplePreparableReloadListener<Map<ResourceLocation, List<String>>> {
    public static final ResourceLocation ID = MCA.locate("eyes");
    private static FaceList INSTANCE;
    private final HashMap<String, List<String>> faces = new HashMap<>();

    public FaceList() {
        INSTANCE = this;
    }

    public static FaceList getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<ResourceLocation, List<String>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return SkinListJson.textureCollections(manager, "eyes");
    }

    @Override
    protected void apply(Map<ResourceLocation, List<String>> data, ResourceManager manager, ProfilerFiller profiler) {
        faces.clear();
        data.forEach(this::addEntries);
        sortPools();
    }

    private void addEntries(ResourceLocation id, List<String> textures) {
        String variant = id.getPath().toLowerCase(Locale.ROOT);
        textures.forEach(identifier -> {
            ResourceLocation parsed;
            try {
                parsed = ResourceLocation.parse(identifier);
            } catch (ResourceLocationException exception) {
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

    public ResourceLocation pick(String variant, float faceGene) {
        List<String> pool = faces.get(key(variant));
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant));
        }

        int index = (int) Math.min(pool.size() - 1, Math.max(0, faceGene * pool.size()));
        return ResourceLocation.parse(pool.get(index));
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
