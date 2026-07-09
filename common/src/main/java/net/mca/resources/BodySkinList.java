package net.mca.resources;

import com.google.gson.JsonElement;
import net.mca.MCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.BodySkin;
import net.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.HashMap;
import java.util.Map;

public class BodySkinList extends JsonDataLoader {
    public static final Identifier ID = MCA.locate("skins/body");
    private static BodySkinList INSTANCE;
    public final HashMap<String, BodySkin> skins = new HashMap<>();

    public BodySkinList() {
        super(Resources.GSON, ID.getPath());
        INSTANCE = this;
    }

    public static BodySkinList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        skins.clear();
        data.forEach(this::addEntries);
    }

    private void addEntries(Identifier id, JsonElement file) {
        SkinCatalogLoader.addBodySkins(skins, id, file);
    }

    public BodySkin get(String identifier) {
        return skins.get(identifier);
    }

    public WeightedPool<String> getPool(Gender gender) {
        return skins.values().stream()
                .filter(s -> s.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || s.getGender() == gender)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .collect(() -> new WeightedPool.Mutable<>(""),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> a.entries.addAll(b.entries));
    }

    public static Gender getGenderFromPath(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return Gender.byName(slash >= 0 ? path.substring(slash + 1) : path);
    }
}
