package net.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.resources.data.skin.Clothing;
import net.mca.server.world.data.CustomClothingManager;
import net.minecraft.registry.Registries;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;
import net.minecraft.village.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

public class ClothingList extends JsonDataLoader {
    protected static final Identifier ID = MCA.locate("skins/clothing");

    public final HashMap<String, Clothing> clothing = new HashMap<>();

    private static ClothingList INSTANCE;

    public static ClothingList getInstance() {
        return INSTANCE;
    }

    public ClothingList() {
        super(Resources.GSON, "skins/clothing");
        INSTANCE = this;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        clothing.clear();

        data.forEach((id, file) -> {
            Gender gender = Gender.byName(id.getPath().split("\\.")[0]);

            if (gender == Gender.UNASSIGNED) {
                MCA.LOGGER.warn("Invalid gender for clothing pool: {}", id);
                return;
            }

            for (String key : file.getAsJsonObject().keySet()) {
                JsonObject object = file.getAsJsonObject().get(key).getAsJsonObject();

                for (int i = 0; i < JsonHelper.getInt(object, "count", 1); i++) {
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
                WeightedPool<String> pool = getPool(gender, villager.getVillagerData().getProfession());
                if (pool.entries.isEmpty()) {
                    pool = getPool(gender, VillagerProfession.NONE);
                }
                yield pool;
            }
        };
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable VillagerProfession profession) {
        Map<String, String> map = Config.getInstance().professionConversionsMap;
        String currentValue = profession == null ? "minecraft:none" : Registries.VILLAGER_PROFESSION.getId(profession).toString();
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
