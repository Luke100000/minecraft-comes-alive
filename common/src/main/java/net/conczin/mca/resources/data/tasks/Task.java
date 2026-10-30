package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Set;

public abstract class Task {
    private final String id;

    public Task(JsonObject json) {
        this(GsonHelper.getAsString(json, "id"));
    }

    public Task(String id) {
        this.id = id;
    }

    abstract public boolean isCompleted(Village village, ServerPlayer player);

    public boolean isCompleted(CompletionContext context) {
        return isCompleted(context.village, context.player);
    }

    /** Derived building answers live only as long as this task evaluation. */
    public static final class CompletionContext {
        private final Village village;
        private final ServerPlayer player;
        private Set<String> buildingTypes;

        public CompletionContext(Village village, ServerPlayer player) {
            this.village = village;
            this.player = player;
        }

        public boolean hasBuilding(String type) {
            if (buildingTypes == null) buildingTypes = village.getPresentBuildingTypes();
            return buildingTypes.contains(type);
        }
    }

    abstract public JsonObject serialize();

    public boolean isRequired() {
        return false;
    }

    public MutableComponent getTranslatable() {
        return Component.translatable("task." + id);
    }

    public String getId() {
        return id;
    }
}
