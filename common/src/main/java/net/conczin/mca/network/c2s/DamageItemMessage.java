package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;

public record DamageItemMessage(String itemIdentifier) implements HandleablePayload {
    public static final CustomPacketPayload.Type<DamageItemMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("damage_item"));
    public static final StreamCodec<FriendlyByteBuf, DamageItemMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DamageItemMessage::itemIdentifier,
            DamageItemMessage::new
    );

    public DamageItemMessage(ResourceLocation identifier) {
        this(identifier.toString());
    }

    @Override
    public void handleServer(ServerPlayer player) {
        Arrays.stream(InteractionHand.values()).forEach(hand -> {
            ItemStack stack = player.getItemInHand(hand);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemIdentifier)) {
                stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
        });
    }

    @Override
    public CustomPacketPayload.Type<DamageItemMessage> type() {
        return TYPE;
    }
}
