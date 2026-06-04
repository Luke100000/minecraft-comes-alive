package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.item.RelationshipItem;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public record PlayerRingSlotRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<PlayerRingSlotRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("player_ring_slot_request"));
    public static final StreamCodec<FriendlyByteBuf, PlayerRingSlotRequest> STREAM_CODEC = StreamCodec.unit(new PlayerRingSlotRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        if (player.containerMenu == null) {
            return;
        }

        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty() && !RelationshipItem.isRing(carried)) {
            return;
        }

        PlayerSaveData data = PlayerSaveData.get(player);
        ItemStack equipped = data.getEquippedRing();
        if (carried.isEmpty() && equipped.isEmpty()) {
            return;
        }

        ItemStack newEquipped = ItemStack.EMPTY;
        ItemStack remainder = ItemStack.EMPTY;
        if (!carried.isEmpty()) {
            newEquipped = carried.copy();
            newEquipped.setCount(1);
            if (carried.getCount() > 1) {
                remainder = carried.copy();
                remainder.shrink(1);
            }
        }

        data.setEquippedRing(newEquipped);

        ItemStack newCarried = equipped.copy();
        if (!remainder.isEmpty()) {
            if (newCarried.isEmpty()) {
                newCarried = remainder;
            } else if (!player.addItem(remainder.copy())) {
                player.drop(remainder.copy(), false);
            }
        }

        player.containerMenu.setCarried(newCarried);
        player.containerMenu.broadcastChanges();
        PlayerSaveData.sync(player);
    }

    @Override
    public CustomPacketPayload.Type<PlayerRingSlotRequest> type() {
        return TYPE;
    }
}
