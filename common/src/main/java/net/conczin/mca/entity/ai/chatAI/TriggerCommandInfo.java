package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public class TriggerCommandInfo {
    public String command;
    public String description;
    public BiPredicate<ServerPlayer, VillagerEntityMCA> isActive;
    public BiConsumer<ServerPlayer, VillagerEntityMCA> call;

    public TriggerCommandInfo(String command, String description, BiConsumer<ServerPlayer, VillagerEntityMCA> call, BiPredicate<ServerPlayer, VillagerEntityMCA> isActive) {
        this.command = command;
        this.description = description;
        this.call = call;
        this.isActive = isActive;
    }

    public TriggerCommandInfo(String command, String description, BiConsumer<ServerPlayer, VillagerEntityMCA> call) {
        this(command, description, call, (p, v) -> true);
    }
}
