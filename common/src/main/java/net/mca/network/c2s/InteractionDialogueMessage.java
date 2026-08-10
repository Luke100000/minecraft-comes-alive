package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.resources.Dialogues;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.io.Serial;
import java.util.UUID;

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
    public void receive(ServerPlayer player) {
        Entity v = player.serverLevel().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            Dialogues.getInstance().selectAnswer(villager, player, question, answer);
        }
    }
}
