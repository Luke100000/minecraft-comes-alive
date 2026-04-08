package net.conczin.mca.item;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class WhistleItem extends TooltippedItem {
    public WhistleItem(Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.WHISTLE), serverPlayer);
        }

        return world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }
}

