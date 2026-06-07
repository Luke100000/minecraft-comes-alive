package net.conczin.mca.item;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;

public abstract class RelationshipItem extends TooltippedItem implements SpecialCaseGift {
    public RelationshipItem(Properties properties) {
        super(properties);
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
