package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import java.io.Serial;
import java.io.Serializable;

public abstract class Task implements Serializable {
    @Serial
    private static final long serialVersionUID = 6029812512760976500L;

    private final String id;

    public Task(JsonObject json) {
        this(GsonHelper.getAsString(json, "id"));
    }

    public Task(String id) {
        this.id = id;
    }

    abstract public boolean isCompleted(Village village, ServerPlayer player);

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
