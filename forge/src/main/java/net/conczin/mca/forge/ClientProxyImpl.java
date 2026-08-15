package net.conczin.mca.forge;

import net.conczin.mca.ClientProxyAbstractImpl;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

public class ClientProxyImpl extends ClientProxyAbstractImpl {
    @Override
    public Player getClientPlayer() {
        return Minecraft.getInstance().player;
    }
}
