package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

import java.util.Objects;

public class AdvancementTask extends Task {
    public static final String TYPE = "advancement";

    private final String identifier;

    public AdvancementTask(String identifier) {
        super("advancement_" + identifier);
        this.identifier = identifier;
    }

    public AdvancementTask(JsonObject json) {
        this(GsonHelper.getAsString(json, "id"));
    }

    @Override
    public JsonObject serialize() {
        JsonObject json = new JsonObject();
        json.addProperty("type", TYPE);
        json.addProperty("id", identifier);
        return json;
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        AdvancementHolder advancement = Objects.requireNonNull(player.getServer()).getAdvancements().get(ResourceLocation.parse(identifier));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
