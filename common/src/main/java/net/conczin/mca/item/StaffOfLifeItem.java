package net.conczin.mca.item;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;

import java.util.function.Consumer;

public class StaffOfLifeItem extends TooltippedItem {
    public StaffOfLifeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        InteractionResult result = ScytheItem.use(context, true);
        if (result == InteractionResult.SUCCESS) {
            if (context.getPlayer() instanceof ServerPlayer serverPlayer) {
                context.getItemInHand().hurtAndBreak(1, (net.minecraft.server.level.ServerLevel) context.getLevel(), serverPlayer, item -> {
                });
            }
            return result;
        }
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatable(getDescriptionId() + ".uses", stack.getMaxDamage() - stack.getDamageValue()));

        super.appendHoverText(stack, context, tooltipDisplay, tooltip, flag);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
