package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
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
    public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        String response;

        if (super.handle(player, villager)) {
            return false;
        } else {
            response = "interaction.marry.success";
            playerData.marry(villager);
            villager.getRelationships().marry(player);
            villager.getVillagerBrain().modifyMoodValue(15);
        }

        villager.sendChatMessage(player, response);
        return true;
    }
}
