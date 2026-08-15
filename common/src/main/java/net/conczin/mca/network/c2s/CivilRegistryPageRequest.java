package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.s2c.CivilRegistryResponse;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.List;

public class CivilRegistryPageRequest implements Message {
    @Serial
    private static final long serialVersionUID = 7108115056986169352L;

    private final int index;
    private final int from;
    private final int to;

    public CivilRegistryPageRequest(int index, int from, int to) {
        this.index = index;
        this.from = from;
        this.to = to;
    }

    @Override
    public void receive(ServerPlayer player) {
        PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel)player.level())).flatMap(Village::getCivilRegistry).ifPresentOrElse(c -> {
            List<Component> page = c.getPage(from, to);
            NetworkHandler.sendToPlayer(new CivilRegistryResponse(index, page), player);
        }, () -> {
            NetworkHandler.sendToPlayer(new CivilRegistryResponse(index, List.of(Component.translatable("civil_registry.empty"))), player);
        });
    }
}
