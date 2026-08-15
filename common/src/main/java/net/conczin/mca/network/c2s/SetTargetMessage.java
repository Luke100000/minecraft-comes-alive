
package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import java.io.Serial;
import java.util.Arrays;
import java.util.UUID;


public class SetTargetMessage implements Message {
    @Serial
    private static final long serialVersionUID = 7257172480717481644L;

    private final String itemIdentifier;
    private final String targetName;
    private final String targetUUID;

    public SetTargetMessage(ResourceLocation identifier, String targetName, UUID targetUUID) {
        this.itemIdentifier = identifier.toString();
        this.targetName = targetName;
        this.targetUUID = targetUUID.toString();
    }

    @Override
    public void receive(ServerPlayer player) {
        Arrays.stream(InteractionHand.values()).forEach(hand -> {
            ItemStack stack = player.getItemInHand(hand);
            if (BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(itemIdentifier)) {
                stack.getOrCreateTag().putString("targetName", targetName);
                stack.getOrCreateTag().putUUID("targetUUID", UUID.fromString(targetUUID));
            }
        });
    }
}
