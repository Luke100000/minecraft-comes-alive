package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.mca.resources.data.dialogue.Question;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.List;

public class InteractionDialogueResponse implements Message {
    @Serial
    private static final long serialVersionUID = 1371939319244994642L;

    public final String question;
    public final List<String> answers;

    public InteractionDialogueResponse(Question question, ServerPlayer player, VillagerEntityMCA villager) {
        this.question = question.getName();
        this.answers = question.getValidAnswers(player, villager);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDialogueResponse(this);
    }
}
