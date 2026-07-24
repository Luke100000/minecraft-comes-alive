package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
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
    public InteractionResult handle(ServerPlayer player, VillagerEntityMCA villager) {
        if (Relationship.IS_ROMANTIC_PARTNER.test(villager, player)) {
            return InteractionResult.PASS;
        }

        InteractionResult result = validate(player, villager);
        if (result != InteractionResult.PASS) {
            return result;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.promise(villager);
        villager.getRelationships().promise(player);
        villager.getVillagerBrain().modifyMoodValue(5);
        villager.sendChatMessage(player, "interaction.promise.success");
        return InteractionResult.CONSUME;
    }
}
