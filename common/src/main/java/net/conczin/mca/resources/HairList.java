package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

public class HairList extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("skins/hair");
    private static HairList INSTANCE;
    public final HashMap<String, Hair> hair = new HashMap<>();

    public HairList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/hair"));
        INSTANCE = this;
    }

    public static HairList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        hair.clear();

        data.forEach((id, file) -> {
            Gender gender = Gender.byName(id.getPath().split("\\.")[0]);

            if (gender == Gender.UNASSIGNED) {
                MCA.LOGGER.warn("Invalid gender for clothing pool: {}", id);
                return;
            }

            for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
                JsonObject object = entry.metadata();

                float chance = object.has("chance") ? object.get("chance").getAsFloat() : 1.0f;
                Hair c = new Hair(entry.identifier(), gender, chance);
                hair.put(entry.identifier(), c);
            }
        });
    }

    public WeightedPool<String> getPool(Gender gender) {
        return hair.values().stream()
                .filter(c -> c.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || c.getGender() == gender)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> {
                            a.entries.addAll(b.entries);
                        });
    }
}
