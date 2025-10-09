package net.conczin.mca.item;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.level.ServerPlayer;

public class EngagementRingItem extends RelationshipItem {
    public EngagementRingItem(Properties properties) {
        super(properties);
    }

    @Override
    protected int getHeartsRequired() {
        return Config.getInstance().engagementHeartsRequirement;
    }

    @Override
    public boolean handle(ServerPlayer player, VillagerEntityMCA villager) {
        PlayerSaveData playerData = PlayerSaveData.get(player);
        String response;
        boolean consume = false;

        if (super.handle(player, villager)) {
            return false;
        } else if (Relationship.IS_ENGAGED.test(villager, player)) {
            response = "interaction.engage.fail.engaged";
        } else {
            response = "interaction.engage.success";
            playerData.engage(villager);
            villager.getRelationships().engage(player);
            villager.getVillagerBrain().modifyMoodValue(10);
            consume = true;
        }

        villager.sendChatMessage(player, response);
        return consume;
    }
}
