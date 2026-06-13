package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class NeedleAndThreadItem extends TooltippedItem {
    public NeedleAndThreadItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.NEEDLE_AND_THREAD), serverPlayer);
            return InteractionResult.SUCCESS;
        }
        return super.use(world, player, hand);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof VillagerLike && !entity.level().isClientSide && player instanceof ServerPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.NEEDLE_AND_THREAD, entity), (ServerPlayer) player);
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }
}
