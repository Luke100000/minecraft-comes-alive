package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;

public class WeddingRingItem extends RelationshipItem {
    public WeddingRingItem(Item.Properties properties) {
        super(properties);
    }

     @Override
    protected int getHeartsRequired() {
        return Config.getInstance().marriageHeartsRequirement;
    }

    @Override
    public InteractionResult handle(ServerPlayer player, VillagerEntityMCA villager) {
        InteractionResult result = validate(player, villager);
        if (result != InteractionResult.PASS) {
            return result;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.marry(villager);
        villager.getRelationships().marry(player);
        villager.getVillagerBrain().modifyMoodValue(15);
        villager.sendChatMessage(player, "interaction.marry.success");
        return InteractionResult.CONSUME;
    }
}
