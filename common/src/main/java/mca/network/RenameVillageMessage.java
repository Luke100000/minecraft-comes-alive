package mca.network;

import mca.cobalt.network.Message;
import mca.server.world.data.VillageManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
    public void receive(PlayerEntity e) {
        VillageManager.get((ServerWorld)e.world).getOrEmpty(id).ifPresent(v -> v.setName(name));
    }
}
