package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

public class PopulationTask extends Task {
    public static final String TYPE = "population";

    private final int population;

    public PopulationTask(int population) {
        super("population_" + population);
        this.population = population;
    }

    public PopulationTask(JsonObject json) {
        this(GsonHelper.getAsInt(json, "population"));
    }

    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("population", population);
        return json;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        return village.getPopulation() >= population;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public MutableComponent getTranslatable() {
        return Component.translatable("task.population", population);
    }
}
