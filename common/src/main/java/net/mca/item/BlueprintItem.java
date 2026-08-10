package net.mca.item;

import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class BlueprintItem extends TooltippedItem {
    public BlueprintItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.BLUEPRINT), serverPlayer);
        }

        return InteractionResultHolder.success(stack);
    }
}
