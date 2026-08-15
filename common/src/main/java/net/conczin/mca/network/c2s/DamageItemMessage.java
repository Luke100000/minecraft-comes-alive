package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import java.io.Serial;
import java.util.Arrays;

public class DamageItemMessage implements Message {
    @Serial
    private static final long serialVersionUID = -8975978126445189429L;

    private final String itemIdentifier;

    public DamageItemMessage(ResourceLocation identifier) {
        itemIdentifier = identifier.toString();
    }

    @Override
    public void receive(ServerPlayer player) {
        Arrays.stream(InteractionHand.values()).forEach(hand -> {
            ItemStack stack = player.getItemInHand(hand);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemIdentifier)) {
                stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));
            }
        });
    }
}
