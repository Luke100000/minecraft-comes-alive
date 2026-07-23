package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAIContext;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.ChatAIContextData;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.UUID;

public record ChatAIContextUpdateRequest(Target target, ResourceKey<Level> dimension, UUID villagerUuid,
                                         int villageId, String prompt) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ChatAIContextUpdateRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("chat_ai_context_update"));
    public static final StreamCodec<FriendlyByteBuf, ChatAIContextUpdateRequest> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ChatAIContextUpdateRequest decode(FriendlyByteBuf buffer) {
            return new ChatAIContextUpdateRequest(
                    Target.fromId(buffer.readVarInt()),
                    buffer.readResourceKey(Registries.DIMENSION),
                    buffer.readUUID(),
                    buffer.readVarInt(),
                    buffer.readUtf()
            );
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ChatAIContextUpdateRequest request) {
            buffer.writeVarInt(request.target.id);
            buffer.writeResourceKey(request.dimension);
            buffer.writeUUID(request.villagerUuid);
            buffer.writeVarInt(request.villageId);
            buffer.writeUtf(request.prompt);
        }
    };

    @Override
    public void handleServer(ServerPlayer player) {
        if (target == Target.UNKNOWN || !ChatAIContext.canEdit(player) || prompt.length() > MAX_PROMPT_LENGTH) {
            return;
        }

        ServerLevel targetLevel = player.serverLevel().getServer().getLevel(dimension);
        switch (target) {
            case VILLAGER -> {
                if (targetLevel != null && targetLevel.getEntity(villagerUuid) instanceof VillagerEntityMCA villager) {
                    villager.setChatAIPrompt(prompt);
                }
            }
            case PLAYER -> PlayerSaveData.get(player).setChatAIPrompt(prompt);
            case VILLAGE -> {
                if (targetLevel != null) {
                    VillageManager.get(targetLevel).getOrEmpty(villageId).ifPresent(village -> village.setChatAIPrompt(prompt));
                }
            }
            case WORLD -> ChatAIContextData.get(player.serverLevel().getServer()).setWorldPrompt(prompt);
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
