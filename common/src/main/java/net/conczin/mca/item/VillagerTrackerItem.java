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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

public class VillagerTrackerItem extends Item {
    public VillagerTrackerItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public final InteractionResult use(Level world, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_TRACKER), serverPlayer);
        }

        return world.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel world, Entity entity, EquipmentSlot slot) {
        if (world.getGameTime() % Config.getInstance().trackVillagerPositionEveryNTicks == 0 && stack.has(DataComponentsMCA.TRACKER_UUID)) {
            UUID uuid = stack.get(DataComponentsMCA.TRACKER_UUID);
            GlobalPos pos = VillagerTrackerManager.get(world).get(uuid);
            if (pos != null) {
                stack.set(DataComponentsMCA.TRACKER_POS, pos);
            }
        }

        GlobalPos pos = stack.get(DataComponentsMCA.TRACKER_POS);
        if (pos != null) {
            stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(pos), false));
        } else {
            stack.remove(DataComponents.LODESTONE_TRACKER);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> consumer, TooltipFlag flag) {
        if (stack.has(DataComponentsMCA.TRACKER_NAME)) {
            //noinspection ConstantConditions
            consumer.accept(Component.translatable(this.getDescriptionId() + ".active", stack.get(DataComponentsMCA.TRACKER_NAME)).withStyle(ChatFormatting.GREEN));

            GlobalPos pos = stack.get(DataComponentsMCA.TRACKER_POS);
            if (pos != null) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    int precision = 5;
                    int distance = ((int) Math.sqrt(pos.pos().distToCenterSqr(player.position()))) / precision * precision;
                    consumer.accept(Component.translatable(this.getDescriptionId() + ".distance", distance).withStyle(ChatFormatting.ITALIC));
                }
            }
        }
        FlowingText.wrap(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY), 160).forEach(consumer);
    }
}

