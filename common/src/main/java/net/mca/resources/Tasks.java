package net.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.MCA;
import net.mca.resources.data.tasks.*;
import net.mca.server.world.data.Village;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tasks extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = MCA.locate("tasks");

    private static Tasks INSTANCE;

    public static Tasks getInstance() {
        return INSTANCE;
    }

    public final Map<Rank, List<Task>> tasks = new HashMap<>();

    public Tasks() {
        super(Resources.GSON, ID.getPath());
        INSTANCE = this;
    }

    public static final Map<String, Function<JsonObject, Task>> TASK_TYPES = new HashMap<>();

    static {
        TASK_TYPES.put("blocking", BlockingTask::new);
        TASK_TYPES.put("building", BuildingTask::new);
        TASK_TYPES.put("population", PopulationTask::new);
        TASK_TYPES.put("reputation", ReputationTask::new);
        TASK_TYPES.put("advancement", AdvancementTask::new);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        tasks.clear();
        for (Rank r : Rank.values()) {
            tasks.put(r, new LinkedList<>());
        }

        data.forEach((id, file) -> {
            Rank rank = Rank.fromName(id.getPath().split("\\.")[0]);
            file.getAsJsonArray().forEach(entry -> {
                String type = GsonHelper.getAsString(entry.getAsJsonObject(), "type");
                Function<JsonObject, Task> myNew = TASK_TYPES.get(type);
                Task task = myNew.apply(entry.getAsJsonObject());
                tasks.get(rank).add(task);
            });
        });
    }

    public static Set<String> getCompletedIds(Village village, ServerPlayer player) {
        return getInstance().tasks.values().stream().flatMap(Collection::stream)
                .filter(t -> t.isCompleted(village, player)).map(Task::getId).collect(Collectors.toSet());
    }

    public static Rank getRank(Village village, ServerPlayer player) {
        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            if (getInstance().tasks.get(ranks[i]).stream().allMatch(t -> !t.isRequired() || t.isCompleted(village, player))) {
                return ranks[i];
            }
        }
        return Rank.OUTLAW;
    }
}
