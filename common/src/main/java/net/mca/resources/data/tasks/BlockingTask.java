package net.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class BlockingTask extends Task {
    @Serial
    private static final long serialVersionUID = -211723796850841823L;

    public BlockingTask(JsonObject json) {
        super(json);
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
