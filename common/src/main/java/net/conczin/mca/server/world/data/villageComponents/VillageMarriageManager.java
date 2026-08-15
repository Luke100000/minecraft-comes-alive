package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class VillageMarriageManager {
    private final Village village;

    public VillageMarriageManager(Village village) {
        this.village = village;
    }

    // if the amount of couples is low, let them marry
    public void marry(ServerLevel world) {
        if (world.random.nextFloat() >= Config.getInstance().marriageChancePerMinute) {
            return;
        }

        //list all lonely villagers
        List<VillagerEntityMCA> allVillagers = village.getResidents(world);
        List<VillagerEntityMCA> availableVillagers = allVillagers.stream()
                .filter(v -> !v.isBaby())
                .filter(v -> !v.getRelationships().isMarried())
                .filter(v -> !v.getRelationships().isEngaged())
                .filter(v -> !v.getRelationships().isPromised())
                .collect(Collectors.toList());

        if (availableVillagers.size() <= 1 || availableVillagers.size() <= allVillagers.size() * (1.0 - village.getMarriageThreshold())) {
            return; // The village is too small.
        }

        //use the one with the least max hearts
        //this feels random yet respects relationships
        availableVillagers.sort(Comparator.comparingInt(a -> a.getVillagerBrain().getMemories().values().stream().map(Memories::getHearts).max(Integer::compare).orElse(0)));

        VillagerEntityMCA suitor = availableVillagers.remove(0);

        // Find a potential mate
        availableVillagers.stream()
                .filter(suitor::canBeAttractedTo)
                .filter(i -> !suitor.getRelationships().getFamilyEntry().isRelative(i.getUUID()))
                .findFirst().ifPresent(mate -> {
                    suitor.getRelationships().marry(mate);
                    mate.getRelationships().marry(suitor);

                    // tell everyone about it
                    if (Config.getInstance().villagerMarriageNotification) {
                        village.broadCastMessage(world, "events.marry", suitor, mate);
                    }

                    // civil entry
                    village.getCivilRegistry().ifPresent(r -> r.addText(Component.translatable("events.marry", suitor.getName(), mate.getName())));
                });
    }
}
