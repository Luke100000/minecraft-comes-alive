package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Relationship;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

public class BouquetItem extends RelationshipItem {
    public BouquetItem(Item.Settings properties) {
        super(properties);
    }

    @Override
    protected int getHeartsRequired() {
        return Config.getInstance().bouquetHeartsRequirement;
    }

    @Override
    public Result handle(ServerPlayerEntity player, VillagerEntityMCA villager) {
        if (Relationship.IS_ROMANTIC_PARTNER.test(villager, player)) {
            return Result.PASS;
        }

        Result result = validate(player, villager);
        if (result != Result.PASS) {
            return result;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.promise(villager);
        villager.getRelationships().promise(player);
        villager.getVillagerBrain().modifyMoodValue(5);
        villager.sendChatMessage(player, "interaction.promise.success");
        return Result.CONSUME;
    }
}
