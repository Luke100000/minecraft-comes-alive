package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.s2c.GetFamilyResponse;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import java.io.Serial;
import java.util.stream.Stream;

public class GetFamilyRequest implements Message {
    @Serial
    private static final long serialVersionUID = -4415670234855916259L;

    @Override
    public void receive(ServerPlayer player) {
        CompoundTag familyData = new CompoundTag();

        PlayerSaveData playerData = PlayerSaveData.get(player);

        //fetches all members
        //de-loaded members are excluded as they can't teleport anyway

        Stream.concat(
                        playerData.getFamilyEntry().getAllRelatives(4),
                        playerData.getPartnerUUID().stream()
                ).distinct()
                .map(player.serverLevel()::getEntity)
                .filter(e -> e instanceof VillagerLike<?>)
                .limit(100)
                .forEach(e -> {
                    CompoundTag nbt = new CompoundTag();
                    ((Mob)e).addAdditionalSaveData(nbt);
                    nbt.remove("Brain");
                    nbt.remove("Memories");
                    nbt.remove("memories");
                    nbt.remove("Inventory");
                    familyData.put(e.getUUID().toString(), nbt);
                });

        NetworkHandler.sendToPlayer(new GetFamilyResponse(familyData), player);
    }
}
