package net.conczin.mca.entity.ai;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.EntityWrapper;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.VillagerMessage;
import net.conczin.mca.resources.API;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.Locale;

public interface Messenger extends EntityWrapper {
    TargetingConditions CAN_RECEIVE = TargetingConditions.forNonCombat();

    static void sendEventMessage(Level world, Component message) {
        world.players().forEach(player -> player.sendSystemMessage(message));
    }

    default boolean isSpeechImpaired() {
        return false;
    }

    default boolean isToYoungToSpeak() {
        return false;
    }

    default void playSpeechEffect() {

    }

    default DialogueType getDialogueType(Player receiver) {
        return DialogueType.UNASSIGNED;
    }

    static String getName(ServerPlayer target) {
        if (target.level() instanceof ServerLevel world) {
            return FamilyTree.get(world)
                    .getOrEmpty(target.getUUID())
                    .map(FamilyTreeNode::getName)
                    .filter(n -> !MCA.isBlankString(n))
                    .orElse(target.getName().getString());
        } else {
            return target.getName().getString();
        }
    }

    default MutableComponent getTranslatable(Player target, String phraseId, Object... params) {
        String genderString = "";

        String targetName;
        if (target instanceof ServerPlayer serverPlayer) {
            targetName = getName(serverPlayer);

            //player gender
            genderString = "#G" + PlayerSaveData.get(serverPlayer).getGender().name().toLowerCase(Locale.ROOT) + ".";
        } else {
            targetName = target.getName().getString();
        }
        Object[] newParams = new Object[params.length + 1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = targetName;

        //also pass profession
        String professionString = "";
        if (!asEntity().isBaby() && asEntity() instanceof VillagerEntityMCA v) {
            professionString = "#P" + BuiltInRegistries.VILLAGER_PROFESSION.getKey(v.getProfession()).getPath() + ".";
        }

        //and personality
        String personalityString = "";
        if (asEntity() instanceof VillagerEntityMCA v) {
            personalityString = "#E" + Personality.encodeDialogueId(
                    v.getVillagerBrain().getPersonalityId()
            ) + ".";
        }

        return Component.translatable(genderString + personalityString + professionString + "#T" + getDialogueType(target).name() + "." + phraseId, newParams);
    }

    default void sendChatToAllAround(MutableComponent phrase) {
        for (Player player : asEntity().level().getEntitiesOfClass(Player.class, asEntity().getBoundingBox().inflate(20))) {
            float dist = player.distanceTo(asEntity());
            sendChatMessage(phrase.withStyle(dist < 10 ? ChatFormatting.WHITE : ChatFormatting.GRAY), player);
        }
    }

    default void sendChatToAllAround(String phrase, Object... params) {
        for (Player player : asEntity().level().getEntitiesOfClass(Player.class, asEntity().getBoundingBox().inflate(20))) {
            float dist = player.distanceTo(asEntity());
            sendChatMessage(getTranslatable(player, phrase, params).withStyle(dist < 10 ? ChatFormatting.WHITE : ChatFormatting.GRAY), player);
        }
    }

    default void sendChatMessage(Player target, String phraseId, Object... params) {
        sendChatMessage(getTranslatable(target, phraseId, params), target);
    }

    default Component transformMessage(Component message) {
        if (isSpeechImpaired()) {
            return Component.literal(API.getRandomSentence("zombie", message.getString()));
        } else if (isToYoungToSpeak()) {
            return Component.literal(API.getRandomSentence("baby", message.getString()));
        }
        return message;
    }

    default Component sendChatMessage(Component message, Entity receiver) {
        message = transformMessage(message);

        MutableComponent prefix = Component.literal(Config.getInstance().villagerChatPrefix)
                .append(asEntity().getDisplayName())
                .append(": ");

        //use custom packet to have access to sender UUID, and maybe future extra information
        VillagerMessage msg = new VillagerMessage(prefix, message, asEntity().getUUID());
        if (receiver instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(msg, serverPlayer);
        } else {
            ClientProxy.getNetworkHandler().handleVillagerMessage(msg);
        }

        playSpeechEffect();

        return message;
    }

    default void sendEventMessage(Component message, Player receiver) {
        receiver.sendSystemMessage(message);
    }

    default void sendEventMessage(Component message) {
        if (!(this instanceof Entity)) {
            return; // Can't tell all
        }
        sendEventMessage(((Entity) this).level(), message);
    }
}
