package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.BodySkin;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Map;

public class BodySkinList extends SimpleJsonResourceReloadListener<JsonElement> {
    private static final Codec<Map<String, BodySkin.Definition>> FILE_CODEC = Codec.unboundedMap(Codec.STRING, BodySkin.DEFINITION_CODEC);
    public static final Identifier ID = MCA.locate("skins/body");
    private static BodySkinList INSTANCE;
    public final HashMap<String, BodySkin> skins = new HashMap<>();

    public BodySkinList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/body"));
        INSTANCE = this;
    }

    public static BodySkinList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        skins.clear();

        data.forEach((id, file) -> FILE_CODEC.parse(JsonOps.INSTANCE, file)
                .resultOrPartial(error -> MCA.LOGGER.warn("Invalid body skin list {}: {}", id, error))
                .ifPresent(entries -> addEntries(id, entries)));
    }

    private void addEntries(Identifier id, Map<String, BodySkin.Definition> entries) {
        Gender fileGender = getGenderFromPath(id);
        entries.forEach((key, definition) -> {
            for (String identifier : CountedSkinIds.expand(key, definition.count())) {
                BodySkin skin = definition.create(identifier, fileGender);
                skins.put(identifier, skin);
            }
        });
    }

    public BodySkin get(String identifier) {
        return skins.get(identifier);
    }

    public WeightedPool<String> getPool(Gender gender) {
        return skins.values().stream()
                .filter(s -> s.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || s.getGender() == gender)
                .sorted(Comparator.comparing(BodySkin::getIdentifier))
                .collect(() -> new WeightedPool.Mutable<>(""),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> a.entries.addAll(b.entries));
    }

    static Gender getGenderFromPath(Identifier id) {
        String path = id.getPath();
        int slash = path.lastIndexOf('/');
        return Gender.byName(slash >= 0 ? path.substring(slash + 1) : path);
    }
}
