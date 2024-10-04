package net.mca.mixin;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.ChatAI;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Mixin(ServerPlayNetworkHandler.class)
public class MixinServerPlayNetworkHandler {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    public void sendMessage(ChatMessageC2SPacket message, CallbackInfo ci) {
        if (Config.getInstance().enableVillagerChatAI) {
            String msg = StringUtils.normalizeSpace(message.chatMessage());
            if (!msg.startsWith("/")) {
                // Check if there's an eligible villager for the conversation
                Optional<VillagerEntityMCA> villager = ChatAI.getVillagerForConversation(player, msg);
                // Yes? => Talk to it
                villager.ifPresent(villagerEntityMCA -> mca$runAsyncAnswerRequest(player, villagerEntityMCA, msg));
            }
        }
    }

    @Unique
    private void mca$runAsyncAnswerRequest(ServerPlayerEntity player, VillagerEntityMCA villager, String msg) {
        CompletableFuture.runAsync(() -> {
            Optional<String> answer = ChatAI.answer(player, villager, msg);
            answer.ifPresent(a -> villager.conversationManager.addMessage(player, Text.literal(a)));
        });
    }
}

