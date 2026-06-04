package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.resources.Names;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.level.BlockGetter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VillageInnManager {
    private final Village village;

    public VillageInnManager(Village village) {
        this.village = village;
    }

    public void updateInn(ServerLevel world) {
        village.getBuildingsOfType("inn").forEach(b -> {
            if (world.random.nextFloat() < Config.getInstance().adventurerAtInnChancePerMinute) {
                List<BlockPos> values = new ArrayList<>(b.getBlocks().values().stream().flatMap(Collection::stream).toList());
                Collections.shuffle(values);
                for (BlockPos p : values) {
                    if (trySpawnAdventurer(world, p.above())) {
                        break;
                    }
                }
            }
        });
    }

    private boolean doesNotSuffocateAt(BlockGetter world, BlockPos pos) {
        for (BlockPos blockPos : BlockPos.betweenClosed(pos, pos.above())) {
            if (world.getBlockState(blockPos).getCollisionShape(world, blockPos).isEmpty()) continue;
            return false;
        }
        return true;
    }

    private boolean trySpawnAdventurer(ServerLevel world, BlockPos blockPos) {
        if (!world.isPositionEntityTicking(blockPos)) {
            // prevent any additional retries
            return true;
        }

        String name = null;
        if (this.doesNotSuffocateAt(world, blockPos)) {
            int i = world.random.nextInt(10);
            if (i == 0 && Config.getInstance().innSpawnsWanderingTraders) {
                WanderingTrader trader = EntityType.WANDERING_TRADER.spawn(world, blockPos, EntitySpawnReason.EVENT);
                if (trader != null) {
                    name = trader.getName().getString();
                    trader.setDespawnDelay(Config.getInstance().adventurerStayTime);
                }
            } else if (i == 1 && Config.getInstance().innSpawnsCultists) {
                VillagerEntityMCA adventurer = spawnInnVillager(world, blockPos, Gender.getRandom());
                if (adventurer != null) {
                    name = adventurer.getName().getString();
                    adventurer.setProfession(ProfessionsMCA.CULTIST);
                    adventurer.setDespawnDelay(Config.getInstance().adventurerStayTime);
                }
            } else if (Config.getInstance().innSpawnsAdventurers) {
                VillagerEntityMCA adventurer = spawnInnVillager(world, blockPos, Gender.getRandom());
                if (adventurer != null) {
                    name = adventurer.getName().getString();
                    adventurer.setProfession(ProfessionsMCA.ADVENTURER);
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

    private VillagerEntityMCA spawnInnVillager(ServerLevel world, BlockPos blockPos, Gender gender) {
        VillagerEntityMCA adventurer = gender.getVillagerType().spawn(world, blockPos, EntitySpawnReason.EVENT);
        if (adventurer != null) {
            adventurer.setCustomName(Component.literal(Names.pickCitizenName(gender)));
        }
        return adventurer;
    }
}
