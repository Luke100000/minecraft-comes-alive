package net.mca.resources;

import com.google.gson.JsonElement;
import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.Nationality;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Names extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = MCA.locate("mca_names");

    public static final Map<String, Map<Gender, WeightedPool<String>>> NAMES_MAP = new HashMap<>();
    public static final List<String> REGION_NAMES = new LinkedList<>();

    public Names() {
        super(Resources.GSON, ID.getPath());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        NAMES_MAP.clear();
        for (Map.Entry<ResourceLocation, JsonElement> entry : prepared.entrySet()) {
            String[] split = entry.getKey().getPath().split("/");
            Gender gender = Gender.byName(split[1]);

            Map<Gender, WeightedPool<String>> map = NAMES_MAP.computeIfAbsent(split[0], a -> new HashMap<>());

            WeightedPool.Mutable<String> names = new WeightedPool.Mutable<>("?");
            for (Map.Entry<String, JsonElement> elementEntry : entry.getValue().getAsJsonObject().entrySet()) {
                names.add(elementEntry.getKey(), (float)Math.pow(elementEntry.getValue().getAsInt(), 0.5));
            }

            map.put(gender, names);
        }

        REGION_NAMES.clear();
        Arrays.stream(NAMES_MAP.keySet().toArray()).sorted().forEach(n -> REGION_NAMES.add((String)n));
    }

    static final RandomSource random = RandomSource.create();

    public static String getCitizenNation(Entity entity) {
        if (Config.getInstance().useModernUSANamesOnly) {
            return "modernusa";
        } else {
            int i = Nationality.get((ServerLevel)entity.level()).getRegionId(entity.blockPosition());
            return REGION_NAMES.get(Math.floorMod(i, REGION_NAMES.size()));
        }
    }

    public static String pickCitizenName(@NotNull Gender gender, Entity entity) {
        return NAMES_MAP.isEmpty() ? "Unnamed" : NAMES_MAP.get(getCitizenNation(entity)).get(gender.binary()).pickOne();
    }

    public static String pickCitizenName(@NotNull Gender gender) {
        return NAMES_MAP.isEmpty() ? "Unnamed" : NAMES_MAP.get(REGION_NAMES.get(random.nextInt(REGION_NAMES.size()))).get(gender.binary()).pickOne();
    }
}
