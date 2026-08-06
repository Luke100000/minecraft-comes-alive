package net.conczin.mca.network.c2s;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAIContext;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record ChatAIContextUpdateRequest(Target target, ResourceKey<Level> dimension, UUID villagerUuid,
                                         int villageId, String prompt, String nickname) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ChatAIContextUpdateRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("chat_ai_context_update"));
    private static final StreamCodec<ByteBuf, Target> TARGET_CODEC =
            ByteBufCodecs.VAR_INT.map(Target::fromId, Target::id);
    public static final StreamCodec<FriendlyByteBuf, ChatAIContextUpdateRequest> STREAM_CODEC = StreamCodec.composite(
            TARGET_CODEC, ChatAIContextUpdateRequest::target,
            ResourceKey.streamCodec(Registries.DIMENSION), ChatAIContextUpdateRequest::dimension,
            UUIDUtil.STREAM_CODEC, ChatAIContextUpdateRequest::villagerUuid,
            ByteBufCodecs.VAR_INT, ChatAIContextUpdateRequest::villageId,
            ByteBufCodecs.STRING_UTF8, ChatAIContextUpdateRequest::prompt,
            ByteBufCodecs.STRING_UTF8, ChatAIContextUpdateRequest::nickname,
            ChatAIContextUpdateRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        if (target == Target.UNKNOWN || !ChatAIContext.canEdit(player) || prompt.length() > MAX_PROMPT_LENGTH
                || (target == Target.VILLAGER && nickname.length() > VillagerEntityMCA.MAX_NICKNAME_LENGTH)) {
            return;
        }

        ServerLevel targetLevel = player.serverLevel().getServer().getLevel(dimension);
        switch (target) {
            case VILLAGER -> {
                if (targetLevel != null && targetLevel.getEntity(villagerUuid) instanceof VillagerEntityMCA villager) {
                    villager.setChatAIPrompt(prompt);
                    villager.setNickname(player.getUUID(), nickname);
                }
            }
            case PLAYER -> PlayerSaveData.get(player).setChatAIPrompt(prompt);
            case VILLAGE -> {
                if (targetLevel != null) {
                    VillageManager.get(targetLevel).getOrEmpty(villageId).ifPresent(village -> village.setChatAIPrompt(prompt));
                }
            }
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
            return id;
        }
    }
}
