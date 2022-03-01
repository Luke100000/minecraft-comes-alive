package mca.network;

import java.io.Serial;
import java.util.UUID;
import mca.cobalt.network.Message;
import mca.entity.VillagerEntityMCA;
import mca.resources.Dialogues;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class InteractionDialogueMessage implements Message {
    @Serial
    private static final long serialVersionUID = 1462101145658166706L;

    private final UUID villagerUUID;
    private final String question;
    private final String answer;

    public InteractionDialogueMessage(UUID uuid, String question, String answer) {
        villagerUUID = uuid;
        this.question = question;
        this.answer = answer;
    }

    @Override
    public void receive(PlayerEntity player) {
        Entity villager = ((ServerWorld)player.world).getEntity(villagerUUID);
        if (villager instanceof VillagerEntityMCA v) {
            Dialogues.getInstance().selectAnswer(v, (ServerPlayerEntity)player, question, answer);
        }
    }
}
