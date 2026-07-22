package net.mca.item;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Relationship;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.server.network.ServerPlayerEntity;

public class EngagementRingItem extends RelationshipItem {
    public EngagementRingItem(Settings properties) {
        super(properties);
    }

    @Override
    protected int getHeartsRequired() {
        return Config.getInstance().engagementHeartsRequirement;
    }

    @Override
    public Result handle(ServerPlayerEntity player, VillagerEntityMCA villager) {
        Result result = validate(player, villager);
        if (result != Result.PASS) {
            return result;
        }

        if (Relationship.IS_ENGAGED.test(villager, player)) {
            villager.sendChatMessage(player, "interaction.engage.fail.engaged");
            return Result.HANDLED;
        }

        PlayerSaveData playerData = PlayerSaveData.get(player);
        playerData.engage(villager);
        villager.getRelationships().engage(player);
        villager.getVillagerBrain().modifyMoodValue(10);
        villager.sendChatMessage(player, "interaction.engage.success");
        return Result.CONSUME;
    }
}
