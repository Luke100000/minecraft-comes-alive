package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.PlayerSaveData;
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
    public Result handle(ServerPlayer player, VillagerEntityMCA villager) {
        Result result = validate(player, villager);
        if (result != Result.PASS) {
            return result;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.marry(villager);
        villager.getRelationships().marry(player);
        villager.getVillagerBrain().modifyMoodValue(15);
        villager.sendChatMessage(player, "interaction.marry.success");
        return Result.CONSUME;
    }
}
