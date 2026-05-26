package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class ClothingList extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("skins/clothing");
    private static ClothingList INSTANCE;
    public final HashMap<String, Clothing> clothing = new HashMap<>();

    public ClothingList() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("skins/clothing"));
        INSTANCE = this;
    }

    public static ClothingList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        clothing.clear();

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

                    object.addProperty("gender", gender.getId());

                    Clothing c = new Clothing(identifier, object);

                    if (!clothing.containsKey(identifier) || !object.has("count")) {
                        clothing.put(identifier, c);
                    }
                }
            }
        });
    }

    /**
     * Gets a pool of clothing options valid for this entity's gender and profession.
     */
    public WeightedPool<String> getPool(VillagerLike<?> villager) {
        Gender gender = villager.getGenetics().getGender();
        return switch (villager.getAgeState()) {
            case BABY -> getPool(gender, MCA.locate("baby").toString());
            case TODDLER -> getPool(gender, MCA.locate("toddler").toString());
            case CHILD, TEEN -> getPool(gender, MCA.locate("child").toString());
            default -> {
                WeightedPool<String> pool = getPool(gender, villager.getVillagerData().profession().value());
                if (pool.entries.isEmpty()) {
                    pool = getPool(gender, (VillagerProfession) null);
                }
                yield pool;
            }
        };
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable VillagerProfession profession) {
        Map<String, String> map = Config.getInstance().professionConversionsMap;
        String currentValue = profession == null ? "minecraft:none" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();
        String identifier = map.getOrDefault(currentValue, map.getOrDefault("default", currentValue));
        return getPool(gender, identifier);
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable String profession) {
        return Stream.concat(clothing.values().stream(), CustomClothingManager.getClothing().getEntries().values().stream())
                .filter(c -> c.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || c.getGender() == gender)
                .filter(c -> c.profession == null || profession == null && !c.exclude || c.profession.equals(profession) || profession != null && c.profession.equals(profession.replace(":", ".")))
                .collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                        (list, entry) -> list.add(entry.getIdentifier(), entry.getChance()),
                        (a, b) -> {
                            a.entries.addAll(b.entries);
                        });
    }

}
