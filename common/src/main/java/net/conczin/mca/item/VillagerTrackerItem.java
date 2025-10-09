package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.registry.DataComponentsMCA;
import net.conczin.mca.server.world.data.VillagerTrackerManager;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.UUID;

public class VillagerTrackerItem extends Item {
    public VillagerTrackerItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public final InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_TRACKER), serverPlayer);
        }

        return InteractionResultHolder.success(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (world instanceof ServerLevel serverWorld) {
            if (world.getGameTime() % Config.getInstance().trackVillagerPositionEveryNTicks == 0 && stack.has(DataComponentsMCA.TRACKER_UUID)) {
                UUID uuid = stack.get(DataComponentsMCA.TRACKER_UUID);
                GlobalPos pos = VillagerTrackerManager.get(serverWorld).get(uuid);
                if (pos != null) {
                    stack.set(DataComponentsMCA.TRACKER_POS, pos);
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        if (stack.has(DataComponentsMCA.TRACKER_NAME)) {
            //noinspection ConstantConditions
            tooltip.add(Component.translatable(this.getDescriptionId(stack) + ".active", stack.get(DataComponentsMCA.TRACKER_NAME)).withStyle(ChatFormatting.GREEN));

            GlobalPos pos = stack.get(DataComponentsMCA.TRACKER_POS);
            if (pos != null) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    int precision = 5;
                    int distance = ((int) Math.sqrt(pos.pos().distToCenterSqr(player.position()))) / precision * precision;
                    tooltip.add(Component.translatable(this.getDescriptionId(stack) + ".distance", distance).withStyle(ChatFormatting.ITALIC));
                }
            }
        }
        tooltip.addAll(FlowingText.wrap(Component.translatable(getDescriptionId(stack) + ".tooltip").withStyle(ChatFormatting.GRAY), 160));
    }
}
