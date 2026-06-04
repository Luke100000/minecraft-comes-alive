package net.conczin.mca.network.c2s;

import java.util.UUID;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.server.level.ServerPlayer;

public record PlayerDataRequest(UUID uuid) implements HandleablePayload {
   public static final Type<PlayerDataRequest> TYPE = new Type(MCA.locate("player_data_request"));
   public static final StreamCodec<FriendlyByteBuf, PlayerDataRequest> STREAM_CODEC = StreamCodec.composite(
      UUIDUtil.STREAM_CODEC, PlayerDataRequest::uuid, PlayerDataRequest::new
   );

   @Override
   public void handleServer(ServerPlayer player) {
      if (player.level().getPlayerByUUID(this.uuid) instanceof ServerPlayer serverTarget) {
         PlayerSaveData data = PlayerSaveData.get(serverTarget);
         if (data.isEntityDataSet()) {
            CompoundTag nbt = data.createNetworkData();
            Network.sendToPlayer(new PlayerDataMessage(this.uuid, nbt), player);
         }
      }
   }

   public Type<PlayerDataRequest> type() {
      return TYPE;
   }
}
