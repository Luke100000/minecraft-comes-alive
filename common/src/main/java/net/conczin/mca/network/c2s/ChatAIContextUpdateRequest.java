package net.conczin.mca.network.c2s;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.chatAI.ChatAI;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ChatAIContextUpdateRequest(Target target, String prompt) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ChatAIContextUpdateRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("chat_ai_context_update"));
    public static final StreamCodec<FriendlyByteBuf, ChatAIContextUpdateRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, request -> request.target.id,
            ByteBufCodecs.STRING_UTF8, ChatAIContextUpdateRequest::prompt,
            (target, prompt) -> new ChatAIContextUpdateRequest(Target.fromId(target), prompt)
    );

    @Override
    public void handleServer(ServerPlayer player) {
        if (target == Target.UNKNOWN || !player.hasPermissions(Config.getInstance().villagerChatAIContextPermissionLevel)
            || prompt.length() > MAX_PROMPT_LENGTH) {
            return;
        }

        switch (target) {
            case VILLAGER -> ChatAI.findClosestVillager(player).ifPresent(villager -> villager.setChatAIPrompt(prompt));
            case PLAYER -> PlayerSaveData.get(player).setChatAIPrompt(prompt);
            case VILLAGE ->
                    VillageManager.get(player.serverLevel()).findNearestVillage(player).ifPresent(village -> village.setChatAIPrompt(prompt));
            case WORLD -> {
                Config.getInstance().villagerChatAISystemPrompt = prompt;
                Config.getInstance().save();
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<ChatAIContextUpdateRequest> type() {
        return TYPE;
    }

    public static final int MAX_PROMPT_LENGTH = 4096;

    public enum Target {
        VILLAGER(0),
        PLAYER(1),
        VILLAGE(2),
        WORLD(3),
        UNKNOWN(-1);

        private final int id;

        Target(int id) {
            this.id = id;
        }

        private static final Target[] VALUES = values();

        private static Target fromId(int id) {
            return id >= 0 && id < UNKNOWN.ordinal() ? VALUES[id] : UNKNOWN;
        }

        private int id() {
            return ordinal();
        }
    }
}
