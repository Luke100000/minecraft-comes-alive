package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class ClothingList extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = MCA.locate("skins/clothing");
    private static ClothingList INSTANCE;
    public final HashMap<String, Clothing> clothing = new HashMap<>();

    public ClothingList() {
        super(Resources.GSON, "skins/clothing");
        INSTANCE = this;
    }

    public static ClothingList getInstance() {
        return INSTANCE;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        clothing.clear();

        data.forEach((id, file) -> {
            Gender gender = Gender.byName(id.getPath().split("\\.")[0]);

            if (gender == Gender.UNASSIGNED) {
                MCA.LOGGER.warn("Invalid gender for clothing pool: {}", id);
                return;
            }

            for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
                JsonObject object = entry.metadata();
                object.addProperty("gender", gender.getId());

                Clothing c = new Clothing(entry.identifier(), object);
                clothing.put(entry.identifier(), c);
            }
        });
    }

    /**
     * Gets a pool of clothing options valid for this entity's gender and profession.
     */
    public WeightedPool<String> getPool(VillagerLike<?> villager) {
        return toPool(getOptions(villager, allClothing()));
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable VillagerProfession profession) {
        return toPool(getOptions(gender, profession, allClothing()));
    }

    private List<Clothing> getOptions(VillagerLike<?> villager, Collection<Clothing> available) {
        Gender gender = villager.getGenetics().getGender();
        String agePool = switch (villager.getAgeState()) {
            case BABY -> MCA.locate("baby").toString();
            case TODDLER -> MCA.locate("toddler").toString();
            case CHILD, TEEN -> MCA.locate("child").toString();
            default -> null;
        };
        if (agePool != null) {
            return getOptions(gender, agePool, available);
        }

        List<Clothing> options = getOptions(gender, villager.getVillagerData().getProfession(), available);
        return options.isEmpty() ? getOptions(gender, (VillagerProfession) null, available) : options;
    }

    private List<Clothing> getOptions(Gender gender, @Nullable VillagerProfession profession, Collection<Clothing> available) {
        Map<String, String> map = Config.getInstance().professionConversionsMap;
        String currentValue = profession == null ? "minecraft:none" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();
        String identifier = map.getOrDefault(currentValue, map.getOrDefault("default", currentValue));
        return getOptions(gender, identifier, available);
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable String profession) {
        return toPool(getOptions(gender, profession, allClothing()));
    }

    public WeightedPool<String> getEditorPool(Gender gender) {
        return toPool(getEditorOptions(gender, allClothing()));
    }

    public List<Clothing> getEditorOptions(Gender gender, Collection<Clothing> available) {
        return available.stream()
                .filter(c -> c.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || c.getGender() == gender)
                .filter(c -> !c.exclude)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    private List<Clothing> getOptions(Gender gender, @Nullable String profession, Collection<Clothing> available) {
        return available.stream()
                .filter(c -> c.getGender() == Gender.NEUTRAL || gender == Gender.NEUTRAL || c.getGender() == gender)
                .filter(c -> c.profession == null || profession == null && !c.exclude || c.profession.equals(profession) || profession != null && c.profession.equals(profession.replace(":", ".")))
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    private List<Clothing> allClothing() {
        return Stream.concat(clothing.values().stream(), CustomClothingManager.getClothing().getEntries().values().stream()).toList();
    }

    private WeightedPool<String> toPool(List<Clothing> options) {
        return options.stream().collect(() -> new WeightedPool.Mutable<>("mca:missing"),
                (pool, entry) -> pool.add(entry.getIdentifier(), entry.getChance()),
                (a, b) -> a.entries.addAll(b.entries));
    }
}
