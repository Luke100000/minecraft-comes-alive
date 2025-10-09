package net.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import java.io.Serial;

public class BuildingTask extends Task {
    @Serial
    private static final long serialVersionUID = -6660910729161211245L;

    private final String type;

    public BuildingTask(String type) {
        super(type);
        this.type = type;
    }

    public BuildingTask(JsonObject json) {
        this(GsonHelper.getAsString(json, "building"));
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        return village.getBuildings().values().stream()
                .anyMatch(b -> b.getType().equals(type));
    }

    @Override
    public MutableComponent getTranslatable() {
        return Component.translatable("task.build", Component.translatable("buildingType." + type));
    }
}
