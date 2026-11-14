package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerPlayNetworkHandler {
    @Shadow
    public ServerPlayer player;

    @ModifyReturnValue(method = "getSignedMessage", at = @At("RETURN"))
    private PlayerChatMessage mca$handleAcceptedChat(PlayerChatMessage message) {
        if (!Config.getInstance().enableVillagerChatAI) {
            return message;
        }

        String msg = StringUtils.normalizeSpace(message.signedContent());
        MinecraftServer server = player.getServer();
        if (server != null) {
            mca$handleChatAI(player, msg, server);
        }

        return message;
    }

    @Unique
    private void mca$handleChatAI(ServerPlayer player, String msg, MinecraftServer server) {
        if (player.isRemoved()) {
            return;
        }

        Optional<VillagerEntityMCA> villager = ChatAI.getVillagerForConversation(player, msg);
        villager.ifPresent(villagerEntityMCA -> mca$runAsyncAnswerRequest(player, villagerEntityMCA, msg, server));
    }

    @Unique
    private void mca$runAsyncAnswerRequest(ServerPlayer player, VillagerEntityMCA villager, String msg, MinecraftServer server) {
        ChatAI.selectVillagerForConversation(player, villager);
        ChatAI.answerAsync(player, villager, msg).thenAcceptAsync(answer -> {
            if (!player.isRemoved() && !villager.isRemoved() && player.level() == villager.level()) {
                answer.ifPresent(a -> villager.conversationManager.addMessage(player, Component.literal(a)));
            }
        }, server);
    }
}

