package net.mca.server.world.data.villageComponents;

import net.mca.Config;
import net.mca.ProfessionsMCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.Gender;
import net.mca.server.world.data.Village;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VillageInnManager {
    private final Village village;

    public VillageInnManager(Village village) {
        this.village = village;
    }

    public void updateInn(ServerWorld world) {
        village.getBuildingsOfType("inn").forEach(b -> {
            if (world.random.nextFloat() < Config.getInstance().adventurerAtInnChancePerMinute) {
                List<BlockPos> values = new ArrayList<>(b.getBlocks().values().stream().flatMap(Collection::stream).toList());
                Collections.shuffle(values);
                for (BlockPos p : values) {
                    if (trySpawnAdventurer(world, p.up())) {
                        break;
                    }
                }
            }
        });
    }

    private boolean doesNotSuffocateAt(BlockView world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.iterate(pos, pos.up())) {
            if (world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) continue;
            return false;
        }
        return true;
    }

    private boolean trySpawnAdventurer(ServerWorld world, BlockPos blockPos) {
        if (!world.shouldTickEntity(blockPos)) {
            // prevent any additional retries
            return true;
        }

        String name = null;
        if (this.doesNotSuffocateAt(world, blockPos)) {
            int i = world.random.nextInt(10);
            if (i == 0 && Config.getInstance().innSpawnsWanderingTraders) {
                WanderingTraderEntity trader = EntityType.WANDERING_TRADER.spawn(world, blockPos, SpawnReason.EVENT);
                if (trader != null) {
                    name = trader.getName().getString();
                    trader.setDespawnDelay(Config.getInstance().adventurerStayTime);
                }
            } else if (i == 1 && Config.getInstance().innSpawnsCultists) {
                VillagerEntityMCA adventurer = Gender.getRandom().getVillagerType().spawn(world, blockPos, SpawnReason.EVENT);
                if (adventurer != null) {
                    name = adventurer.getName().getString();
                    adventurer.setProfession(ProfessionsMCA.CULTIST.get());
                    adventurer.setDespawnDelay(Config.getInstance().adventurerStayTime);
                }
            } else if (Config.getInstance().innSpawnsAdventurers) {
                VillagerEntityMCA adventurer = Gender.getRandom().getVillagerType().spawn(world, blockPos, SpawnReason.EVENT);
                if (adventurer != null) {
                    name = adventurer.getName().getString();
                    adventurer.setProfession(ProfessionsMCA.ADVENTURER.get());
                    adventurer.setDespawnDelay(Config.getInstance().adventurerStayTime);
                }
            }

            if (name != null) {
                if (Config.getInstance().innArrivalNotification) {
                    village.broadCastMessage(world, "events.arrival.inn", name);
                }
                return true;
            }
        }
        return false;
    }
}
