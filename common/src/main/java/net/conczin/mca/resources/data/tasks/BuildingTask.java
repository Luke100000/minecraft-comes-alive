package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

public class BuildingTask extends Task {
    public static final String TYPE = "building";

    private final String building;

    public BuildingTask(String type) {
        super(type);
        this.building = type;
    }

    public BuildingTask(JsonObject json) {
        this(GsonHelper.getAsString(json, "building"));
    }

    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("building", building);
        return json;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        return village.hasBuilding(building);
    }

    @Override
    public MutableComponent getTranslatable() {
        return Component.translatable("task.build", Component.translatable("buildingType." + building));
    }
}
