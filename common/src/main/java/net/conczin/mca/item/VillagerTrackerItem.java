package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.s2c.OpenGuiRequest;
import net.conczin.mca.server.world.data.VillagerTrackerManager;
import net.conczin.mca.util.NbtHelper;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Vanishable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class VillagerTrackerItem extends Item implements Vanishable {
    public VillagerTrackerItem(Item.Properties settings) {
        super(settings);
    }

    @Override
    public final InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHandler.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.VILLAGER_TRACKER), serverPlayer);
        }

        return InteractionResultHolder.success(stack);
    }

    public static GlobalPos getTargetPos(ItemStack stack) {
        CompoundTag position = stack.getTagElement("position");
        return position != null ? NbtHelper.decodeGlobalPos(position) : null;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return CompassItem.isLodestoneCompass(stack) || super.isFoil(stack);
    }

    @Override
    public void inventoryTick(ItemStack stack, Level world, Entity entity, int slot, boolean selected) {
        if (world instanceof ServerLevel serverWorld) {
            if (world.getGameTime() % Config.getInstance().trackVillagerPositionEveryNTicks == 0 && stack.getOrCreateTag().contains("targetUUID")) {
                UUID uuid = stack.getOrCreateTag().getUUID("targetUUID");
                GlobalPos pos = VillagerTrackerManager.get(serverWorld).get(uuid);
                if (pos != null) {
                    stack.getOrCreateTag().put("position", NbtHelper.encodeGlobalPosition(pos));
                }
            }
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level world, List<Component> tooltip, TooltipFlag context) {
        if (stack.getOrCreateTag().contains("targetName")) {
            //noinspection ConstantConditions
            tooltip.add(Component.translatable(this.getDescriptionId(stack) + ".active", stack.getOrCreateTag().get("targetName").getAsString()).withStyle(ChatFormatting.GREEN));

            GlobalPos pos = getTargetPos(stack);
            if (pos != null && world != null && pos.dimension() == world.dimension()) {
                LocalPlayer player = Minecraft.getInstance().player;
                if (player != null) {
                    int precision = 5;
                    int distance = ((int)Math.sqrt(pos.pos().distToCenterSqr(player.position()))) / precision * precision;
                    tooltip.add(Component.translatable(this.getDescriptionId(stack) + ".distance", distance).withStyle(ChatFormatting.ITALIC));
                }
            }
        }
        tooltip.addAll(FlowingText.wrap(Component.translatable(getDescriptionId(stack) + ".tooltip").withStyle(ChatFormatting.GRAY), 160));
    }
}

