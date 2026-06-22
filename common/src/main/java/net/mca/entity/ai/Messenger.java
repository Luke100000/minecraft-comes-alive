package net.mca.entity.ai;

import net.mca.ClientProxy;
import net.mca.Config;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.EntityWrapper;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.VillagerMessage;
import net.mca.resources.API;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.Locale;

public interface Messenger extends EntityWrapper {
    TargetPredicate CAN_RECEIVE = TargetPredicate.createNonAttackable();

    default boolean isSpeechImpaired() {
        return false;
    }

    default boolean isToYoungToSpeak() {
        return false;
    }

    default void playSpeechEffect() {

    }

    default DialogueType getDialogueType(PlayerEntity receiver) {
        return DialogueType.UNASSIGNED;
    }

    default MutableText getTranslatable(PlayerEntity target, String phraseId, Object... params) {
        String genderString = "";

        String targetName;
        if (target.getWorld() instanceof ServerWorld world) {
            //todo won't work on a few client side use cases
            targetName = FamilyTree.get(world)
                    .getOrEmpty(target.getUuid())
                    .map(FamilyTreeNode::getName)
                    .filter(n -> !MCA.isBlankString(n))
                    .orElse(target.getName().getString());

            //player gender
            genderString = "#G" + PlayerSaveData.get((ServerPlayerEntity)target).getGender().name().toLowerCase(Locale.ROOT) + ".";
        } else {
            targetName = target.getName().getString();
        }
        Object[] newParams = new Object[params.length + 1];
        System.arraycopy(params, 0, newParams, 1, params.length);
        newParams[0] = targetName;

        //also pass profession
        String professionString = "";
        if (!asEntity().isBaby() && asEntity() instanceof VillagerEntityMCA v) {
            professionString = "#P" + Registries.VILLAGER_PROFESSION.getId(v.getProfession()).getPath() + ".";
        }

        //and personality
        String personalityString = "";
        if (asEntity() instanceof VillagerEntityMCA v) {
            personalityString = "#E" + v.getVillagerBrain().getPersonality().name() + ".";
        }

        return Text.translatable(genderString + personalityString + professionString + "#T" + getDialogueType(target).name() + "." + phraseId, newParams);
    }

    default void sendChatToAllAround(MutableText phrase) {
        for (PlayerEntity player : asEntity().getWorld().getPlayers(CAN_RECEIVE, asEntity(), asEntity().getBoundingBox().expand(20))) {
            float dist = player.distanceTo(asEntity());
            sendChatMessage(phrase.formatted(dist < 10 ? Formatting.WHITE : Formatting.GRAY), player);
        }
    }

    default void sendChatToAllAround(String phrase, Object... params) {
        for (PlayerEntity player : asEntity().getWorld().getPlayers(CAN_RECEIVE, asEntity(), asEntity().getBoundingBox().expand(20))) {
            float dist = player.distanceTo(asEntity());
            sendChatMessage(getTranslatable(player, phrase, params).formatted(dist < 10 ? Formatting.WHITE : Formatting.GRAY), player);
        }
    }

    default void sendChatMessage(PlayerEntity target, String phraseId, Object... params) {
        sendChatMessage(getTranslatable(target, phraseId, params), target);
    }

    default MutableText transformMessage(MutableText message) {
        if (isSpeechImpaired()) {
            return Text.literal(API.getRandomSentence("zombie", message.getString()));
        } else if (isToYoungToSpeak()) {
            return Text.literal(API.getRandomSentence("baby", message.getString()));
        }
        return message;
    }

    default MutableText sendChatMessage(MutableText message, Entity receiver) {
        message = transformMessage(message);

        MutableText prefix = Text.literal(Config.getInstance().villagerChatPrefix)
                .append(asEntity().getDisplayName())
                .append(": ");

        //use custom packet to have access to sender UUID, and maybe future extra information
        VillagerMessage msg = new VillagerMessage(prefix, message, asEntity().getUuid());
        if (receiver instanceof ServerPlayerEntity serverPlayer) {
            NetworkHandler.sendToPlayer(msg, serverPlayer);
        } else {
            ClientProxy.getNetworkHandler().handleVillagerMessage(msg);
        }

        playSpeechEffect();

        return prefix.append(message);
    }

    default void sendEventMessage(Text message, PlayerEntity receiver) {
        receiver.sendMessage(message, true);
    }

    default void sendEventMessage(Text message) {
        if (!(this instanceof Entity)) {
            return; // Can't tell all
        }
        sendEventMessage(((Entity)this).getWorld(), message);
    }

    static void sendEventMessage(World world, Text message) {
        world.getPlayers().forEach(player -> player.sendMessage(message, true));
    }
}
