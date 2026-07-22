package net.mca.item;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.entity.ai.Relationship;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;

public abstract class RelationshipItem extends TooltippedItem implements SpecialCaseGift {
    public RelationshipItem(Settings properties) {
        super(properties);
    }

    abstract int getHeartsRequired();

    protected Result validate(ServerPlayerEntity player, VillagerEntityMCA villager) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);
        String response;

        if (villager.isBaby()) {
            response = "interaction.relationship.fail.isbaby";
        } else if (Relationship.IS_RELATIVE.test(villager, player)) {
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
            return Result.PASS;
        }

        villager.sendChatMessage(player, response);
        return Result.HANDLED;
    }
}
