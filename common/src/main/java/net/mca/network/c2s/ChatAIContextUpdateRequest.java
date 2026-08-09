package net.mca.network.c2s;

import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAIContext;
import net.mca.server.world.data.PlayerSaveData;
import net.mca.server.world.data.VillageManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.UUID;

public class ChatAIContextUpdateRequest implements Message {
    public static final int MAX_PROMPT_LENGTH = 4096;

    private final Target target;
    private final String dimension;
    private final UUID villagerUuid;
    private final int villageId;
    private final String prompt;
    private final String nickname;

    public ChatAIContextUpdateRequest(Target target, String dimension, UUID villagerUuid, int villageId, String prompt, String nickname) {
        this.target = target;
        this.dimension = dimension;
        this.villagerUuid = villagerUuid;
        this.villageId = villageId;
        this.prompt = prompt;
        this.nickname = nickname;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        if (target == null || target == Target.UNKNOWN || prompt == null || nickname == null
                || !ChatAIContext.canEdit(player) || prompt.length() > MAX_PROMPT_LENGTH
                || (target == Target.VILLAGER && nickname.length() > VillagerEntityMCA.MAX_NICKNAME_LENGTH)) {
            return;
        }

        ServerWorld targetWorld = findWorld(player, dimension);
        switch (target) {
            case VILLAGER -> {
                if (targetWorld != null && targetWorld.getEntity(villagerUuid) instanceof VillagerEntityMCA villager) {
                    villager.setChatAIPrompt(prompt);
                    villager.setNickname(player.getUuid(), nickname);
                }
            }
            case PLAYER -> PlayerSaveData.get(player).setChatAIPrompt(prompt);
            case VILLAGE -> {
                if (targetWorld != null) {
                    VillageManager.get(targetWorld).getOrEmpty(villageId).ifPresent(village -> village.setChatAIPrompt(prompt));
                }
            }
            case WORLD -> {
                Config.getInstance().villagerChatAISystemPrompt = prompt;
                Config.getInstance().save();
            }
        }
    }

    private static ServerWorld findWorld(ServerPlayerEntity player, String dimension) {
        if (dimension == null) {
            return null;
        }
        for (ServerWorld world : player.getServer().getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(dimension)) {
                return world;
            }
        }
        return null;
    }

    public enum Target {
        VILLAGER,
        PLAYER,
        VILLAGE,
        WORLD,
        UNKNOWN
    }
}
