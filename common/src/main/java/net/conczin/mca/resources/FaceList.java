package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.IdentifierException;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FaceList extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("skins/face");
    private static FaceList INSTANCE;
    private final HashMap<String, List<String>> faces = new HashMap<>();

    public FaceList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/face"));
        INSTANCE = this;
    }

    public static FaceList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        faces.clear();

        data.forEach(this::addEntries);
        sortPools();
    }

    private void addEntries(Identifier id, JsonElement file) {
        Gender fileGender = BodySkinList.getGenderFromPath(id);
        file.getAsJsonObject().keySet().forEach(key -> {
            int count = GsonHelper.getAsInt(file.getAsJsonObject().get(key).getAsJsonObject(), "count", -1);
            for (String identifier : CountedSkinIds.expand(key, count)) {
                Identifier parsed;
                try {
                    parsed = Identifier.parse(identifier);
                } catch (IdentifierException exception) {
                    MCA.LOGGER.warn("Invalid face texture identifier {}", identifier, exception);
                    continue;
                }
                String[] parts = parsed.getPath().split("/");
                if (parts.length < 3) {
                    MCA.LOGGER.warn("Invalid face texture path {}", identifier);
                    continue;
                }
                String mapKey = key(parts[2], fileGender);
                faces.computeIfAbsent(mapKey, ignored -> new ArrayList<>()).add(identifier);
            }
        });
    }

    public Identifier pick(String variant, Gender gender, float faceGene) {
        List<String> pool = faces.get(key(variant, gender));
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant, gender));
        }

        int index = (int) Math.min(pool.size() - 1, Math.max(0, faceGene * pool.size()));
        return Identifier.parse(pool.get(index));
    }

    public int count(String variant, Gender gender) {
        List<String> pool = faces.get(key(variant, gender));
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant, gender));
        }
        return pool.size();
    }

    private void sortPools() {
        faces.values().forEach(pool -> pool.sort(SkinListEntry::compareIdentifiers));
    }

    private static String key(String variant, Gender gender) {
        return variant.toLowerCase(Locale.ROOT) + "/" + gender.getDataName();
    }
}
