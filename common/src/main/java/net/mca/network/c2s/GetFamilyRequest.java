package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerLike;
import net.mca.network.s2c.GetFamilyResponse;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.stream.Stream;

public class GetFamilyRequest implements Message {
    @Serial
    private static final long serialVersionUID = -4415670234855916259L;

    @Override
    public void receive(ServerPlayerEntity player) {
        NbtCompound familyData = new NbtCompound();

        PlayerSaveData playerData = PlayerSaveData.get(player);

        //fetches all members
        //de-loaded members are excluded as they can't teleport anyway

        Stream.concat(
                        playerData.getFamilyEntry().getAllRelatives(4),
                        playerData.getPartnerUUID().stream()
                ).distinct()
                .map(player.getServerWorld()::getEntity)
                .filter(e -> e instanceof VillagerLike<?>)
                .limit(100)
                .forEach(e -> {
                    NbtCompound nbt = new NbtCompound();
                    ((MobEntity)e).writeCustomDataToNbt(nbt);
                    nbt.remove("Brain");
                    nbt.remove("Memories");
                    nbt.remove("memories");
                    nbt.remove("Inventory");
                    familyData.put(e.getUuid().toString(), nbt);
                });

        NetworkHandler.sendToPlayer(new GetFamilyResponse(familyData), player);
    }
}
