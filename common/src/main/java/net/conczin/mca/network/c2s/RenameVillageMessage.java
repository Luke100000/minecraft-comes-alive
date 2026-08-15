package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class RenameVillageMessage implements Message {
    @Serial
    private static final long serialVersionUID = -7194992618247743620L;

    private final int id;
    private final String name;

    public RenameVillageMessage(int id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public void receive(ServerPlayer player) {
        VillageManager.get(player.serverLevel()).getOrEmpty(id).ifPresent(v -> v.setName(name));
    }
}
