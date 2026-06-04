package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetFamilyResponse;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;

import java.util.stream.Stream;

public record GetFamilyRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_request"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyRequest> STREAM_CODEC = StreamCodec.unit(new GetFamilyRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        CompoundTag familyData = new CompoundTag();
        PlayerSaveData playerData = PlayerSaveData.get(player);
        ServerLevel level = (ServerLevel) player.level();
        Stream.concat(
                        playerData.getFamilyEntry().getAllRelatives(4),
                        playerData.getPartnerUUID().stream()
                ).distinct()
                .map(level::getEntity)
                .filter(e -> e instanceof VillagerLike<?>)
                .limit(100)
                .forEach(e -> {
                    Entity entity = e;
                    var output = WorldUtils.createValueOutput(level.registryAccess());
                    ((Mob) entity).saveWithoutId(output);
                    CompoundTag nbt = WorldUtils.getCompoundTag(output);
                    nbt.remove("Brain");
                    nbt.remove("Memories");
                    nbt.remove("Inventory");
                    familyData.put(entity.getUUID().toString(), nbt);
                });
        Network.sendToPlayer(new GetFamilyResponse(familyData), player);
    }

    @Override
    public Type<GetFamilyRequest> type() {
        return TYPE;
    }
}
