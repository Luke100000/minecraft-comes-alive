package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public record BabyNamingVillagerMessage(int slot, String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BabyNamingVillagerMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("craft_request"));
    public static final StreamCodec<FriendlyByteBuf, BabyNamingVillagerMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, BabyNamingVillagerMessage::slot,
            ByteBufCodecs.STRING_UTF8, BabyNamingVillagerMessage::name,
            BabyNamingVillagerMessage::new
    );

    @Override
    public void handle(Player player) {
        ItemStack stack = player.getInventory().getItem(slot);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
    }

    @Override
    public Type<BabyNamingVillagerMessage> type() {
        return TYPE;
    }
}
