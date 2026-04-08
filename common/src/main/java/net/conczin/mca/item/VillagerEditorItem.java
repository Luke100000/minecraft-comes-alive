package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class VillagerEditorItem extends TooltippedItem {
    public VillagerEditorItem(Properties settings) {
        super(settings);
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity, InteractionHand hand) {
        if (entity instanceof VillagerLike<?> villager && !entity.level().isClientSide() && player instanceof ServerPlayer serverPlayer) {
            if (player.isShiftKeyDown()) {
                villager.getInteractions().handle(serverPlayer, "inventory");
            } else {
                Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_EDITOR, entity), serverPlayer);
            }
            return InteractionResult.SUCCESS;
        } else {
            return InteractionResult.CONSUME;
        }
    }
}

