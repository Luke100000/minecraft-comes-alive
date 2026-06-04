package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Hair;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HairList extends SimpleJsonResourceReloadListener<JsonElement> {
    protected static final Identifier ID = MCA.locate("skins/hair");
    private static HairList INSTANCE;
    public final HashMap<String, Hair> hair = new HashMap<>();

    public HairList() {
        super(Resources.JSON_ELEMENT_CODEC, FileToIdConverter.json("skins/hair"));
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

            for (String key : file.getAsJsonObject().keySet()) {
                JsonObject object = file.getAsJsonObject().get(key).getAsJsonObject();

                for (int i = 0; i < GsonHelper.getAsInt(object, "count", 1); i++) {
                    String identifier = String.format(Locale.ROOT, key, i);

                    Hair c = new Hair(identifier, gender, GsonHelper.getAsFloat(object, "chance", 1.0f));

                    if (!hair.containsKey(identifier) || !object.has("count")) {
                        hair.put(identifier, c);
                    }
                }
            }
        });
    }

    public WeightedPool<String> getPool(Gender gender) {
        return hair.values().stream()
                .filter(c -> c.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || c.getGender() == gender)
                .collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> {
                            a.entries.addAll(b.entries);
                        });
    }
}
