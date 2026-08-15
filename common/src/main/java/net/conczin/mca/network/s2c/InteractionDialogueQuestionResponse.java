package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.io.Serial;

public class InteractionDialogueQuestionResponse implements Message {
    @Serial
    private static final long serialVersionUID = 1371939319244994642L;

    public final String questionText;
    public final boolean silent;

    public InteractionDialogueQuestionResponse(boolean silent, Component questionText) {
        this.questionText = Component.Serializer.toJson(questionText);
        this.silent = silent;
    }

    public MutableComponent getQuestionText() {
        return Component.Serializer.fromJson(questionText);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDialogueQuestionResponse(this);
    }
}
