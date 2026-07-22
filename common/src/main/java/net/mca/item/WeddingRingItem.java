package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

public class WeddingRingItem extends RelationshipItem {
    public WeddingRingItem(Item.Settings properties) {
        super(properties);
    }

     @Override
    protected int getHeartsRequired() {
        return Config.getInstance().marriageHeartsRequirement;
    }

    @Override
    public ActionResult handle(ServerPlayerEntity player, VillagerEntityMCA villager) {
        ActionResult result = validate(player, villager);
        if (result != ActionResult.PASS) {
            return result;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.marry(villager);
        villager.getRelationships().marry(player);
        villager.getVillagerBrain().modifyMoodValue(15);
        villager.sendChatMessage(player, "interaction.marry.success");
        return ActionResult.CONSUME;
    }
}
