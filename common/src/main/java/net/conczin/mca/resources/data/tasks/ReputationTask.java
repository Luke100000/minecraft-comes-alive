package net.conczin.mca.resources.data.tasks;

import com.google.gson.JsonObject;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import java.io.Serial;

public class ReputationTask extends Task {
    @Serial
    private static final long serialVersionUID = -7232675787774372089L;

    private final int reputation;

    public ReputationTask(int reputation) {
        super("reputation_" + reputation);
        this.reputation = reputation;
    }

    public ReputationTask(JsonObject json) {
        this(GsonHelper.getAsInt(json, "reputation"));
    }

    @Override
    public boolean isCompleted(Village village, ServerPlayer player) {
        return village.getReputation(player) >= reputation;
    }

    @Override
    public boolean isRequired() {
        return true;
    }

    @Override
    public MutableComponent getTranslatable() {
        return Component.translatable("task.reputation", reputation);
    }
}
