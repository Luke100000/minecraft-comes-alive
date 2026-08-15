package net.conczin.mca.entity.ai.chatAI.modules;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;

public class EnvironmentModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        if (player.level().isRaining()) {
            input.add("It is raining. ");
        }
        if (player.level().isThundering()) {
            input.add("It is thundering. ");
        }
        if (player.level().isNight()) {
            input.add("It is night. ");
        }
    }
}
