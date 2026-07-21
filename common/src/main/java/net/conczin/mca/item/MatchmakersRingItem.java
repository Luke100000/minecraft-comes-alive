package net.conczin.mca.item;

import net.conczin.mca.entity.Status;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;

import java.util.Comparator;
import java.util.Optional;

public class MatchmakersRingItem extends Item implements SpecialCaseGift {
    public MatchmakersRingItem(Properties properties) {
        super(properties);
    }

    @Override
    public Result handle(ServerPlayer player, VillagerEntityMCA villager) {
        // ensure two rings are in the inventory
        if (player.getMainHandItem().getCount() < 2) {
            villager.sendChatMessage(player, "interaction.matchmaker.fail.needtwo");
            return Result.HANDLED;
        }

        // ensure our target isn't married already or young
        if (villager.getRelationships().isMarried() || villager.getAgeState() != AgeState.ADULT) {
            villager.sendChatMessage(player, "interaction.matchmaker.fail.married");
            return Result.HANDLED;
        }

        // look for partner
        Optional<VillagerEntityMCA> target = WorldUtils.getCloseEntities(villager.level(), villager, 5.0).stream()
                .filter(v -> v != villager && v instanceof VillagerEntityMCA)
                .map(VillagerEntityMCA.class::cast)
                .filter(v -> !v.isBaby() && !v.getRelationships().isMarried())
                .filter(v -> !v.getRelationships().getFamilyEntry().isRelative(villager.getUUID()))
                .filter(villager::canBeAttractedTo)
                .min(Comparator.comparingDouble(villager::distanceTo));

        // ensure we found a nearby villager
        if (target.isEmpty()) {
            villager.sendChatMessage(player, "interaction.matchmaker.fail.novillagers");
            return Result.HANDLED;
        }

        // set up the marriage by assigning spouse UUIDs
        VillagerEntityMCA spouse = target.get();
        villager.getRelationships().marry(spouse);
        spouse.getRelationships().marry(villager);

        // show a reaction
        player.level().broadcastEntityEvent(villager, Status.VILLAGER_HEARTS);

        // remove the rings for survival mode (only one because the other one is gifted)
        if (!player.isCreative()) {
            player.getMainHandItem().shrink(1);
        }

        return Result.CONSUME;
    }
}
