package net.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.mca.server.world.data.Village;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.JsonHelper;

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
        this(JsonHelper.getString(json, "building"));
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayerEntity player) {
        return village.hasBuilding(type);
    }

    @Override
    public MutableText getTranslatable() {
        return Text.translatable("task.build", Text.translatable("buildingType." + type));
    }
}
