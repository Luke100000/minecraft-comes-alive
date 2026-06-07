package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public abstract class RelationshipItem extends TooltippedItem implements SpecialCaseGift {
    public RelationshipItem(Properties properties) {
        super(properties);
    }

    public static boolean isRing(ItemStack stack) {
        return stack.getItem() instanceof WeddingRingItem || stack.getItem() instanceof EngagementRingItem;
    }

    public static void moveEquippedRingToInventory(ServerPlayer player) {
        ItemStack equippedRing = PlayerSaveData.get(player).getEquippedRing();
        if (equippedRing.isEmpty()) {
            return;
        }

        ItemStack displacedStack = equippedRing.copy();
        if (!player.addItem(displacedStack)) {
            player.drop(displacedStack, false);
        }
        PlayerSaveData.get(player).setEquippedRing(ItemStack.EMPTY);
    }

    public static void equipRing(ServerPlayer player, ItemStack stack) {
        if (!isRing(stack)) {
            return;
        }

        moveEquippedRingToInventory(player);

        ItemStack equippedRing = stack.copy();
        equippedRing.setCount(1);
        if (!player.isCreative()) {
            stack.shrink(1);
        }

        PlayerSaveData.get(player).setEquippedRing(equippedRing);
        PlayerSaveData.sync(player);
    }

    abstract int getHeartsRequired();

    @Override
    public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        String response;

        if (villager.isBaby()) {
            response = "interaction.relationship.fail.isbaby";
        } else if (Relationship.IS_PARENT.test(villager, player)) {
            response = "interaction.relationship.fail.isparent";
        } else if (Relationship.IS_MARRIED.test(villager, player)) {
            response = "interaction.relationship.fail.marriedtogiver";
        } else if (villager.getRelationships().isMarried()) {
            response = "interaction.relationship.fail.married";
        } else if (villager.getRelationships().isEngaged() && !Relationship.IS_ENGAGED.test(villager, player)) {
            response = "interaction.relationship.fail.engaged";
        } else if (playerData.isMarried()) {
            response = "interaction.relationship.fail.playermarried";
        } else if (memory.getHearts() < getHeartsRequired()) {
            response = "interaction.relationship.fail.lowhearts";
        } else if (!villager.canBeAttractedTo(playerData)) {
            response = "interaction.relationship.fail.incompatible";
        } else {
            return false;
        }

        villager.sendChatMessage(player, response);
        return true;
    }
}
