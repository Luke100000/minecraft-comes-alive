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

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
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
            Gender fileGender = BodySkinList.getGenderFromPath(id);

            for (SkinListJson.Entry entry : SkinListJson.entries(id, file)) {
                Gender entryGender = SkinListJson.resolveGender(fileGender, entry);
                if (entryGender == Gender.UNASSIGNED) {
                    MCA.LOGGER.warn("Invalid gender for clothing entry {} in {}", entry.identifier(), id);
                    continue;
                }

                JsonObject metadata = entry.metadata();
                String profession = metadata.has("profession") && !metadata.get("profession").isJsonNull() ? GsonHelper.getAsString(metadata, "profession", null) : null;
                boolean exclude = GsonHelper.getAsBoolean(metadata, "exclude", false);
                int temperature = GsonHelper.getAsInt(metadata, "temperature", 0);

                clothing.put(entry.identifier(), new Clothing(entry.identifier(), profession, temperature, exclude, entryGender));
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
        return SkinSelection.clothingForVillager(available, villager, Config.getInstance().professionConversionsMap);
    }

    private List<Clothing> getOptions(Gender gender, @Nullable VillagerProfession profession, Collection<Clothing> available) {
        Map<String, String> map = Config.getInstance().professionConversionsMap;
        String currentValue = profession == null ? "minecraft:none" : BuiltInRegistries.VILLAGER_PROFESSION.getKey(profession).toString();
        return getOptions(gender, SkinSelection.mapProfession(currentValue, map), available);
    }

    public WeightedPool<String> getPool(Gender gender, @Nullable String profession) {
        return toPool(getOptions(gender, profession, allClothing()));
    }

    public WeightedPool<String> getEditorPool(Gender gender) {
        return toPool(getEditorOptions(gender, allClothing()));
    }

    public List<Clothing> getEditorOptions(Gender gender, Collection<Clothing> available) {
        return SkinSelection.editorClothing(available, gender);
    }

    private List<Clothing> getOptions(Gender gender, @Nullable String profession, Collection<Clothing> available) {
        return SkinSelection.clothingForProfession(available, gender, profession);
    }

    private List<Clothing> allClothing() {
        return Stream.concat(clothing.values().stream(), CustomClothingManager.getClothing().getEntries().values().stream()).toList();
    }

    private WeightedPool<String> toPool(List<Clothing> options) {
        return SkinSelection.toPool(options, "mca:missing");
    }
}
