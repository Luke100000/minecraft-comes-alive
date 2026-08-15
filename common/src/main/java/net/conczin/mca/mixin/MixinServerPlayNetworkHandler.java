package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.ChatAI;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerGamePacketListenerImpl.class)
public class MixinServerPlayNetworkHandler {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    public void sendMessage(ServerboundChatPacket message, CallbackInfo ci) {
        if (Config.getInstance().enableVillagerChatAI) {
            String msg = StringUtils.normalizeSpace(message.message());
            if (!msg.startsWith("/")) {
                // Check if there's an eligible villager for the conversation
                Optional<VillagerEntityMCA> villager = ChatAI.getVillagerForConversation(player, msg);
                // Yes? => Talk to it
                villager.ifPresent(villagerEntityMCA -> mca$runAsyncAnswerRequest(player, villagerEntityMCA, msg));
            }
        }
    }

    @Unique
    private void mca$runAsyncAnswerRequest(ServerPlayer player, VillagerEntityMCA villager, String msg) {
        CompletableFuture.runAsync(() -> {
            Optional<String> answer = ChatAI.answer(player, villager, msg);
            answer.ifPresent(a -> villager.conversationManager.addMessage(player, Component.literal(a)));
        });
    }
}

