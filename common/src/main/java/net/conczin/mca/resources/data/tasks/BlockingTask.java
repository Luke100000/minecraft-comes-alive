package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;

public class BlockingTask extends Task {
    public static final String TYPE = "blocking";

    public BlockingTask(JsonObject json) {
        super(json);
    }

    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        return json;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        return false;
    }

    @Override
    public boolean isRequired() {
        return true;
    }
}
