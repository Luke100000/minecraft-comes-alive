package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class BodySkinList extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation ID = MCA.locate("skins/body");
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
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        skins.clear();
        data.forEach(this::addEntries);
    }

    private void addEntries(ResourceLocation id, JsonElement file) {
        Gender fileGender = getGenderFromPath(id);
        SkinListJson.entries(id, file).forEach(entry -> {
            Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
            float chance = GsonHelper.getAsFloat(entry.metadata(), "chance", 1.0f);
            skins.put(entry.identifier(), new BodySkin(entry.identifier(), entryGender, chance));
        });
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

    public static Gender getGenderFromPath(ResourceLocation id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return Gender.byName(slash >= 0 ? path.substring(slash + 1) : path);
    }
}
