package net.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.MCA;
import net.mca.resources.data.tasks.*;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.Village;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.JsonHelper;
import net.minecraft.util.profiler.Profiler;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Tasks extends JsonDataLoader {
    protected static final Identifier ID = MCA.locate("tasks");

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
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        tasks.clear();
        for (Rank r : Rank.values()) {
            tasks.put(r, new LinkedList<>());
        }

        data.forEach((id, file) -> {
            Rank rank = Rank.fromName(id.getPath().split("\\.")[0]);
            file.getAsJsonArray().forEach(entry -> {
                String type = JsonHelper.getString(entry.getAsJsonObject(), "type");
                Function<JsonObject, Task> myNew = TASK_TYPES.get(type);
                Task task = myNew.apply(entry.getAsJsonObject());
                tasks.get(rank).add(task);
            });
        });
    }

    public static Set<String> getCompletedIds(Village village, ServerPlayerEntity player) {
        return getInstance().tasks.values().stream().flatMap(Collection::stream)
                .filter(t -> t.isCompleted(village, player)).map(Task::getId).collect(Collectors.toSet());
    }

    public static Rank getRank(Village village, ServerPlayerEntity player) {
        if (PlayerSaveData.get(player).shouldOverrideVillageRequirements()) {
            return Rank.MONARCH;
        }

        Rank[] ranks = Rank.values();
        for (int i = ranks.length - 1; i >= 0; i--) {
            if (getInstance().tasks.get(ranks[i]).stream().allMatch(t -> !t.isRequired() || t.isCompleted(village, player))) {
                return ranks[i];
            }
        }
        return Rank.OUTLAW;
    }
}
