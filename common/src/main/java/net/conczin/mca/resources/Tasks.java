package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.data.tasks.Task;
import net.conczin.mca.resources.data.tasks.TaskRegistry;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;

import net.conczin.mca.server.world.data.PlayerSaveData;

import java.util.*;
import java.util.stream.Collectors;

public class Tasks extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("tasks");

    private static Tasks INSTANCE;
    public final Map<Rank, List<Task>> tasks = new HashMap<>();

    public Tasks() {
        super(ExtraCodecs.JSON, FileToIdConverter.json(ID.getPath()));
        INSTANCE = this;
    }

    public static Tasks getInstance() {
        return INSTANCE;
    }

    public static Set<String> getCompletedIds(Village village, ServerPlayer player) {
        return getInstance().tasks.values().stream().flatMap(Collection::stream)
                .filter(t -> t.isCompleted(village, player)).map(Task::getId).collect(Collectors.toSet());
    }

    public static Rank getRank(Village village, ServerPlayer player) {
        if (player != null && PlayerSaveData.get(player).isOverrideVillageRequirements()) {
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

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        tasks.clear();
        for (Rank r : Rank.values()) {
            tasks.put(r, new LinkedList<>());
        }

        data.forEach((id, file) -> {
            Rank rank = Rank.fromName(id.getPath().split("\\.")[0]);
            file.getAsJsonArray().forEach(entry -> {
                Task task = TaskRegistry.fromJson(entry.getAsJsonObject());
                tasks.get(rank).add(task);
            });
        });
    }
}
