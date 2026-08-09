package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Villagers need a place to live too.
 */
public class Residency {
    private static final CDataParameter<Integer> VILLAGE = CParameter.create("HomeVillage", -1);
    private final VillagerEntityMCA entity;

    public Residency(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(VILLAGE);
    }

    public BlockPos getWorkplace() {
        return entity.getBrain()
                .getMemoryInternal(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos)
                .orElse(BlockPos.ZERO);
    }

    public void setWorkplace(ServerPlayer player) {
        PoiManager pointOfInterestStorage = ((ServerLevel) player.level()).getPoiManager();
        pointOfInterestStorage.findClosest(VillagerProfession.NONE.acquirableJobSite(), a -> true, entity.blockPosition(), 8, PoiManager.Occupancy.HAS_SPACE).ifPresentOrElse(blockPos -> {
                    pointOfInterestStorage.getType(blockPos).ifPresent(pointOfInterestType -> {
                        pointOfInterestStorage.take(VillagerProfession.NONE.acquirableJobSite(), (registryEntry, blockPos2) -> {
                            return blockPos2.equals(blockPos);
                        }, blockPos, 1);

                        // Forget current site
                        entity.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
                        entity.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
                        entity.releasePoi(MemoryModuleType.JOB_SITE);
                        entity.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);

                        // Set
                        GlobalPos globalPos = GlobalPos.of(player.level().dimension(), blockPos);
                        entity.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
                        player.level().broadcastEntityEvent(entity, (byte) 14);
                        MinecraftServer minecraftServer = player.level().getServer();
                        Optional.ofNullable(minecraftServer.getLevel(globalPos.dimension())).flatMap(world -> {
                            return world.getPoiManager().getType(globalPos.pos());
                        }).flatMap(registryEntry -> {
                            return BuiltInRegistries.VILLAGER_PROFESSION.stream().filter(profession -> {
                                return profession.heldJobSite().test(registryEntry);
                            }).findFirst();
                        }).ifPresent(profession -> {
                            VillagerProfession oldProfession = entity.getVillagerData().getProfession();
                            if (oldProfession == profession) {
                                return;
                            }
                            int level = entity.getVillagerData().getLevel();
                            entity.setVillagerData(entity.getVillagerData().setProfession(profession).setLevel(1));
                            entity.setOffers(null);
                            entity.getOffers();
                            for (int l = 1; l < level; l++) {
                                entity.customLevelUp();
                            }
                            entity.refreshBrain((ServerLevel) player.level());
                        });

                        // Success
                        entity.sendChatMessage(player, "interaction.setworkplace.success");
                    });
                },
                () -> {
                    entity.sendChatMessage(player, "interaction.setworkplace.failed");
                });
    }

    public Optional<Village> getHomeVillage() {
        VillageManager manager = VillageManager.get((ServerLevel) entity.level());
        return manager.getOrEmpty(entity.getTrackedValue(VILLAGE));
    }

    /**
     * Joins the closest village, if in range
     */
    public void seekHome() {
        if (entity.requiresHome()) {
            VillageManager manager = VillageManager.get((ServerLevel) entity.level());
            Optional<Village> current = getHomeVillage();
            Optional<Village> target = getHome()
                    .filter(home -> home.dimension() == entity.level().dimension())
                    .flatMap(home -> manager.findNearestVillage(home.pos(), Village.BORDER_MARGIN))
                    .or(() -> current)
                    .or(() -> manager.findNearestVillage(entity));

            target.ifPresent(v -> {
                if (current.filter(existing -> existing.getId() == v.getId()).isEmpty()) {
                    leaveHome();
                }
                v.updateResident(entity);
                entity.setTrackedValue(VILLAGE, v.getId());
            });
        }
    }

    public void leaveHome() {
        Optional<Village> village = getHomeVillage();
        village.ifPresent(v -> {
            v.removeResident(entity);
        });
        entity.setTrackedValue(VILLAGE, -1);
    }

    public void tick() {
        //report buildings close by
        if (entity.tickCount % 600 == 0 && entity.requiresHome()) {
            Optional<Village> village = getHomeVillage();
            if (village.isEmpty() && Config.getInstance().enableAutoScanByDefault || village.filter(Village::isAutoScan).isPresent()) {
                reportBuildings();
            }

            //seek a home
            if (village.isEmpty()) {
                seekHome();
            }
        }

        //slowly inject village boni
        if (entity.tickCount % 1200 == 0) {
            getHomeVillage().ifPresentOrElse(village -> {
                //update the reputation
                entity.level().players().forEach(player -> {
                    //currently, only hearts are considered, maybe additional factors can affect that too
                    int hearts = entity.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
                    village.setReputation(player, entity, hearts);
                });
            }, this::leaveHome);
        }
    }

    //report potential buildings within this villagers reach
    private void reportBuildings() {
        VillageManager manager = VillageManager.get((ServerLevel) entity.level());

        //fetch all near POIs
        Stream<BlockPos> stream = ((ServerLevel) entity.level()).getPoiManager().findAll(
                type -> true,
                p -> !manager.cache.contains(p),
                entity.blockPosition(),
                48,
                PoiManager.Occupancy.ANY);

        //check if it is a building
        stream.forEach(manager::reportBuilding);

        // also add tombstones
        GraveyardManager.get((ServerLevel) entity.level()).reportToVillageManager(entity);
    }

    public Optional<GlobalPos> getHome() {
        return entity.getMCABrain().getMemoryInternal(MemoryModuleType.HOME);
    }

    private static boolean validateBedPoi(ServerLevel level, BlockPos blockPos) {
        BlockState blockState = level.getBlockState(blockPos);
        return blockState.is(BlockTags.BEDS)
                && blockState.hasProperty(BedBlock.OCCUPIED)
                && !blockState.getValue(BedBlock.OCCUPIED);
    }

    public void setHome(ServerPlayer player) {
        if (!entity.requiresHome()) {
            entity.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }

        // also trigger a building refresh, because why not
        VillageManager manager = VillageManager.get((ServerLevel) player.level());
        manager.processBuilding(player.blockPosition(), true, false);

        seekHome();

        ServerLevel level = (ServerLevel) player.level();
        PoiManager poiManager = level.getPoiManager();
        Optional<GlobalPos> previousHome = entity.getBrain().getMemoryInternal(MemoryModuleType.HOME);
        poiManager.take(
                registryEntry -> registryEntry.is(PoiTypes.HOME),
                (registryEntry, blockPos) -> validateBedPoi(level, blockPos),
                player.blockPosition(),
                8
        ).ifPresentOrElse(claimedHome -> {
            entity.sendChatMessage(player, "interaction.sethome.success");

            boolean reclaimedSameHome = previousHome
                    .map(home -> home.dimension().equals(level.dimension()) && home.pos().equals(claimedHome))
                    .orElse(false);
            if (!reclaimedSameHome) {
                entity.releasePoi(MemoryModuleType.HOME);
            }
            entity.getBrain().eraseMemory(MemoryModuleType.HOME);

            entity.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), claimedHome));
            entity.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);

            seekHome();
        }, () -> {
            entity.getBrain().eraseMemory(MemoryModuleTypeMCA.FORCED_HOME);

            getHomeVillage().map(v -> v.getBuildingAt(entity.blockPosition())).filter(Optional::isPresent).map(Optional::get).filter(b -> b.getBuildingType().noBeds()).ifPresentOrElse(building -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail." + building.getBuildingType().name());
            }, () -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail");
            });
        });
    }

    public void goHome(Player player) {
        entity.getVillagerBrain().setMoveState(MoveState.MOVE, player);
        entity.getInteractions().stopInteracting();
        getHome().filter(p -> p.dimension() == entity.level().dimension()).ifPresentOrElse(home -> {
            entity.moveTowards(home.pos());
            entity.sendChatMessage(player, "interaction.gohome.success");
        }, () -> entity.sendChatMessage(player, "interaction.gohome.fail.nohome"));
    }
}
