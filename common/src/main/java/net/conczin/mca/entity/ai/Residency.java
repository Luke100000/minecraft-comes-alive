package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Villagers need a place to live too.
 */
public class Residency {
    private static final CDataParameter<Integer> VILLAGE = CParameter.create("HomeVillage", -1);
    private static final int WORKPLACE_SEARCH_RADIUS = 8;
    private final VillagerEntityMCA entity;

    public Residency(VillagerEntityMCA entity) {
        this.entity = entity;
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(VILLAGE);
    }

    public BlockPos getWorkplace() {
        return entity.getBrain()
                .getMemory(MemoryModuleType.JOB_SITE)
                .map(GlobalPos::pos)
                .orElse(BlockPos.ZERO);
    }

    public void setWorkplace(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        PoiManager poiManager = level.getPoiManager();
        Optional<BlockPos> freeSite = poiManager.findClosest(
                VillagerProfession.ALL_ACQUIRABLE_JOBS,
                a -> true,
                entity.blockPosition(),
                WORKPLACE_SEARCH_RADIUS,
                PoiManager.Occupancy.HAS_SPACE
        );
        Optional<BlockPos> potentialJobSite = getRememberedWorkplace(level, MemoryModuleType.POTENTIAL_JOB_SITE);
        Optional<BlockPos> currentJobSite = getRememberedWorkplace(level, MemoryModuleType.JOB_SITE);

        selectWorkplaceCandidate(entity.blockPosition(), freeSite, potentialJobSite, currentJobSite).ifPresentOrElse(blockPos -> {
            boolean alreadyOwned = potentialJobSite.filter(blockPos::equals).isPresent()
                    || currentJobSite.filter(blockPos::equals).isPresent();
            if (!alreadyOwned && poiManager.take(
                    VillagerProfession.ALL_ACQUIRABLE_JOBS,
                    (registryEntry, candidatePos) -> candidatePos.equals(blockPos),
                    blockPos,
                    1
            ).isEmpty()) {
                entity.sendChatMessage(player, "interaction.setworkplace.failed");
                return;
            }

            GlobalPos globalPos = GlobalPos.of(level.dimension(), blockPos);
            clearWorkplaceMemory(MemoryModuleType.POTENTIAL_JOB_SITE, globalPos);
            clearWorkplaceMemory(MemoryModuleType.JOB_SITE, globalPos);
            entity.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPos);
            level.broadcastEntityEvent(entity, (byte) 14);

            poiManager.getType(blockPos).flatMap(registryEntry -> BuiltInRegistries.VILLAGER_PROFESSION.stream()
                    .filter(profession -> profession.heldJobSite().test(registryEntry))
                    .findFirst()).ifPresent(profession -> {
                VillagerProfession oldProfession = entity.getVillagerData().getProfession();
                if (oldProfession == profession) {
                    return;
                }
                int villagerLevel = entity.getVillagerData().getLevel();
                entity.setVillagerData(entity.getVillagerData().setProfession(profession).setLevel(1));
                entity.setOffers(null);
                entity.getOffers();
                for (int l = 1; l < villagerLevel; l++) {
                    entity.customLevelUp();
                }
                entity.refreshBrain(level);
            });

            entity.sendChatMessage(player, "interaction.setworkplace.success");
        }, () -> entity.sendChatMessage(player, "interaction.setworkplace.failed"));
    }

    static Optional<BlockPos> selectWorkplaceCandidate(
            BlockPos origin,
            Optional<BlockPos> freeSite,
            Optional<BlockPos> potentialJobSite,
            Optional<BlockPos> currentJobSite
    ) {
        if (freeSite.isPresent() && potentialJobSite.isPresent()) {
            BlockPos freePos = freeSite.get();
            BlockPos potentialPos = potentialJobSite.get();
            return Optional.of(origin.distSqr(potentialPos) <= origin.distSqr(freePos) ? potentialPos : freePos);
        }

        return potentialJobSite.or(() -> freeSite).or(() -> currentJobSite);
    }

    private Optional<BlockPos> getRememberedWorkplace(ServerLevel level, MemoryModuleType<GlobalPos> memoryType) {
        return entity.getBrain().getMemory(memoryType)
                .filter(globalPos -> globalPos.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .filter(pos -> pos.distSqr(entity.blockPosition()) <= (double) WORKPLACE_SEARCH_RADIUS * WORKPLACE_SEARCH_RADIUS)
                .filter(pos -> level.getPoiManager().exists(pos, VillagerProfession.ALL_ACQUIRABLE_JOBS));
    }

    private void clearWorkplaceMemory(MemoryModuleType<GlobalPos> memoryType, GlobalPos selectedWorkplace) {
        if (entity.getBrain().hasMemoryValue(memoryType)
                && !entity.getBrain().isMemoryValue(memoryType, selectedWorkplace)) {
            entity.releasePoi(memoryType);
        }
        entity.getBrain().eraseMemory(memoryType);
    }

    public Optional<Village> getHomeVillage() {
        VillageManager manager = VillageManager.get((ServerLevel) entity.level());
        return manager.getOrEmpty(entity.getTrackedValue(VILLAGE));
    }

    /**
     * Joins the closest village, if in range
     */
    public void seekHome() {
        seekHome(false);
    }

    public void seekHomeAfterClaim() {
        seekHome(true);
    }

    private void seekHome(boolean authoritativeHomeClaim) {
        if (entity.requiresHome()) {
            VillageManager manager = VillageManager.get((ServerLevel) entity.level());
            Optional<Village> current = getHomeVillage();
            Optional<Village> target = getHome()
                    .filter(home -> home.dimension() == entity.level().dimension())
                    .flatMap(home -> manager.findNearestVillage(home.pos(), Village.BORDER_MARGIN))
                    .or(() -> current.filter(village -> village.isWithinBorder(entity)))
                    .or(() -> manager.findNearestVillage(entity));

            target.ifPresent(v -> {
                if (current.filter(existing -> existing.getId() == v.getId()).isEmpty()) {
                    leaveHome();
                }
                if (authoritativeHomeClaim) {
                    v.updateResidentAfterClaim(entity);
                } else if (!v.updateResident(entity)) {
                    // A duplicate memory does not own this position's POI ticket. Forget it without
                    // releasing the ticket retained for the canonical resident.
                    clearHomeMemories(entity.getBrain());
                }
                entity.setTrackedValue(VILLAGE, v.getId());
            });
        }
    }

    static void clearHomeMemories(Brain<?> brain) {
        brain.eraseMemory(MemoryModuleType.HOME);
        brain.eraseMemory(MemoryModuleTypeMCA.FORCED_HOME);
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

            if (village.filter(v -> v.isResidentHomeCurrent(entity)).isEmpty()) {
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

    public void setHome(ServerPlayer player) {
        if (!entity.requiresHome()) {
            entity.sendChatMessage(player, "interaction.sethome.temporary");
            return;
        }

        seekHome();

        ServerLevel level = (ServerLevel) player.level();
        if (trySetHome(level, player.blockPosition())) {
            entity.sendChatMessage(player, "interaction.sethome.success");
        } else {
            getHomeVillage().map(v -> v.getBuildingAt(entity.blockPosition())).filter(Optional::isPresent).map(Optional::get).filter(b -> b.getBuildingType().noBeds()).ifPresentOrElse(building -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail." + building.getBuildingType().name());
            }, () -> {
                entity.sendChatMessage(player, "interaction.sethome.bedfail");
            });
        }
    }

    boolean trySetHome(ServerLevel level, BlockPos searchOrigin) {
        PoiManager poiManager = level.getPoiManager();
        Optional<GlobalPos> previousHome = entity.getBrain().getMemoryInternal(MemoryModuleType.HOME);
        Optional<BlockPos> rememberedHome = previousHome
                .filter(home -> home.dimension().equals(level.dimension()))
                .map(GlobalPos::pos)
                .filter(home -> home.distSqr(searchOrigin) <= 64.0D)
                .filter(home -> poiManager.exists(home, type -> type.is(PoiTypes.HOME)))
                .filter(home -> BedPoiCompatibility.isAvailableHomePoiState(level.getBlockState(home)));

        Optional<BlockPos> selectedHome = Stream.concat(
                        poiManager.findAll(
                                type -> type.is(PoiTypes.HOME),
                                pos -> BedPoiCompatibility.isAvailableHomePoiState(level.getBlockState(pos)),
                                searchOrigin,
                                8,
                                PoiManager.Occupancy.HAS_SPACE
                        ),
                        rememberedHome.stream()
                )
                .distinct()
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(searchOrigin)))
                .filter(pos -> canReachBed(level, pos))
                .findFirst();
        if (selectedHome.isEmpty()) {
            return false;
        }

        BlockPos home = selectedHome.orElseThrow();
        boolean reusingPreviousHome = previousHome
                .map(previous -> previous.dimension().equals(level.dimension()) && previous.pos().equals(home))
                .orElse(false);
        if (!reusingPreviousHome) {
            Optional<BlockPos> claimedHome = poiManager.take(
                    type -> type.is(PoiTypes.HOME),
                    (type, pos) -> pos.equals(home),
                    home,
                    1
            );
            if (claimedHome.filter(home::equals).isEmpty()) {
                return false;
            }
            entity.releasePoi(MemoryModuleType.HOME);
        }

        entity.getBrain().eraseMemory(MemoryModuleType.HOME);
        entity.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), home));
        entity.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);
        seekHomeAfterClaim();
        return true;
    }

    private boolean canReachBed(ServerLevel level, BlockPos home) {
        return BedApproachTarget.create(level, home)
                .map(target -> {
                    if (target.isReached(entity, 0)) {
                        return true;
                    }
                    Path path = entity.getNavigation().createPath(target.getPathTargets(entity), 0);
                    return path != null && path.canReach();
                })
                .orElse(false);
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
