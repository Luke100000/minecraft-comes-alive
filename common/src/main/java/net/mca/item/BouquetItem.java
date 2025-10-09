package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

public class BouquetItem extends RelationshipItem {
    public BouquetItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    protected int getHeartsRequired() {
        return Config.getInstance().bouquetHeartsRequirement;
    }

    @Override
    public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        String response;

        if (super.handle(player, villager)) {
            return false;
        } else {
            response = "interaction.promise.success";
            playerData.promise(villager);
            villager.getRelationships().promise(player);
            villager.getVillagerBrain().modifyMoodValue(5);
        }

        villager.sendChatMessage(player, response);
        return true;
    }
}
