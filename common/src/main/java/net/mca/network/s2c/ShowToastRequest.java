package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.network.chat.Component;
import java.io.Serial;

public class ShowToastRequest implements Message {
    @Serial
    private static final long serialVersionUID = 1055734972572313374L;

    private final String title;
    private final String message;

    public ShowToastRequest(String title, String message) {
        this.title = title;
        this.message = message;
    }

    public Component getTitle() {
        return Component.translatable(title);
    }

    public Component getMessage() {
        return Component.translatable(message);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleToastMessage(this);
    }
}
