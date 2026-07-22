package net.mca.item;

import net.mca.entity.Status;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.mca.util.WorldUtils;
import net.minecraft.item.Item;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Comparator;
import java.util.Optional;

public class MatchmakersRingItem extends Item implements SpecialCaseGift {
    public MatchmakersRingItem(Settings properties) {
        super(properties);
    }

    @Override
    public Result handle(ServerPlayerEntity player, VillagerEntityMCA villager) {
        // ensure two rings are in the inventory
        if (player.getMainHandStack().getCount() < 2) {
            villager.sendChatMessage(player, "interaction.matchmaker.fail.needtwo");
            return Result.HANDLED;
        }

        // ensure our target isn't married already or young
        if (villager.getRelationships().isMarried() || villager.getAgeState() != AgeState.ADULT) {
            villager.sendChatMessage(player, "interaction.matchmaker.fail.married");
            return Result.HANDLED;
        }

        // look for partner
        Optional<VillagerEntityMCA> target = WorldUtils.getCloseEntities(villager.getWorld(), villager, 5.0).stream()
                .filter(v -> v != villager && v instanceof VillagerEntityMCA)
                .map(VillagerEntityMCA.class::cast)
                .filter(v -> !v.isBaby() && !v.getRelationships().isMarried())
                .filter(v -> !v.getRelationships().getFamilyEntry().isRelative(villager.getUuid()))
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
        player.getWorld().sendEntityStatus(villager, Status.VILLAGER_HEARTS);

        // remove the rings for survival mode (only one because the other one is gifted)
        if (!player.isCreative()) {
            player.getMainHandStack().decrement(1);
        }

        return Result.CONSUME;
    }
}
