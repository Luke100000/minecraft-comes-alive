package net.conczin.mca.resources;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class Supporters extends SimpleJsonResourceReloadListener<JsonElement> {
    protected static final ResourceLocation ID = MCA.locate("api/supporters");
    static final RandomSource rng = RandomSource.create();
    private static Supporters INSTANCE;
    private final List<String> supporters = new ArrayList<>();
    private final Map<String, List<String>> supporterGroups = new HashMap<>();

    public Supporters() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(ID.getPath()));
        INSTANCE = this;
    }

    public Supporters(Gson gson, String dataType) {
        super(ExtraCodecs.JSON, FileToIdConverter.json(dataType));
    }

    public static String getRandomSupporter() {
        return INSTANCE.pickSupporter();
    }

    public static List<String> getSupporterGroup(String group) {
        return INSTANCE.supporterGroups.getOrDefault(group, new LinkedList<>());
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> prepared, ResourceManager manager, ProfilerFiller profiler) {
        for (Map.Entry<ResourceLocation, JsonElement> pair : prepared.entrySet()) {
            List<String> strings = supporterGroups.computeIfAbsent(pair.getKey().toString(), x -> new LinkedList<>());
            for (JsonElement e : pair.getValue().getAsJsonArray()) {
                supporters.add(e.getAsString());
                strings.add(e.getAsString());
            }
        }
    }

    public String pickSupporter() {
        return PoolUtil.pickOne(supporters, "nobody", rng);
    }
}
