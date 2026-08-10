package net.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.mca.server.world.data.Village;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import java.util.Objects;

public class AdvancementTask extends Task {
    private final String identifier;

    public AdvancementTask(String identifier) {
        super("advancement_" + identifier);
        this.identifier = identifier;
    }

    public AdvancementTask(JsonObject json) {
        this(GsonHelper.getAsString(json, "id"));
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        Advancement advancement = Objects.requireNonNull(player.getServer()).getAdvancements().getAdvancement(new ResourceLocation(identifier));
        return player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
}
