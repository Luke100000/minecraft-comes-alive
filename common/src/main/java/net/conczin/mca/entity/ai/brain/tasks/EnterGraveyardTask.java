package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Assigns a mourner to an occupied tombstone and an adjacent safe standing position.
 */
public class EnterGraveyardTask extends Behavior<VillagerEntityMCA> {
    private static final int[][] HORIZONTAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};
    private static final double MOURNING_GRAVE_DISTANCE = 3.0D;
    private static final double RESERVATION_FALLBACK_RADIUS = 256.0D;

    public EnterGraveyardTask() {
        super(Map.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleTypeMCA.MOURNING_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleTypeMCA.MOURNING_RETRY_AT, MemoryStatus.VALUE_ABSENT
        ));
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (Mourning.isTemporarilyBlocked(villager)) {
            Mourning.pause(villager);
            return;
        }

        GlobalPos site = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).orElse(null);
        if (site == null) {
            return;
        }
        if (!site.dimension().equals(world.dimension())) {
            Mourning.pause(villager);
            return;
        }

        BlockPos grave = site.pos();
        GlobalPos currentPosition = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                .orElse(null);
        if (!world.isLoaded(grave)) {
            if (!site.equals(currentPosition)) {
                setMourningPosition(villager, site);
            }
            return;
        }
        if (!Mourning.isMournableTombstone(world, grave)) {
            Mourning.finish(villager);
            return;
        }
        if (!Mourning.isSafeToMourn(world, grave)) {
            Mourning.deferUnsafe(villager);
            return;
        }
        if (currentPosition != null
                && currentPosition.dimension().equals(world.dimension())
                && !currentPosition.pos().equals(grave)) {
            return;
        }

        findStandingPosition(world, villager, grave)
                .ifPresentOrElse(
                        position -> setMourningPosition(villager, GlobalPos.of(world.dimension(), position)),
                        () -> Mourning.retry(villager)
                );
    }

    private static void setMourningPosition(VillagerEntityMCA villager, GlobalPos position) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION, position);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }

    public static boolean isAtMourningSite(VillagerEntityMCA villager) {
        if (!isWithinMourningArea(villager)) {
            return false;
        }

        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                .filter(position -> position.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos)
                .filter(villager.blockPosition()::equals)
                .isPresent();
    }

    public static boolean isWithinMourningArea(VillagerEntityMCA villager) {
        BlockPos villagerPosition = villager.blockPosition();
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos)
                .filter(villager.level()::isLoaded)
                .filter(grave -> Mourning.isMournableTombstone(villager.level(), grave))
                .filter(grave -> grave.closerToCenterThan(villager.position(), MOURNING_GRAVE_DISTANCE))
                .filter(grave -> grave.getX() != villagerPosition.getX() || grave.getZ() != villagerPosition.getZ())
                .isPresent();
    }

    private static Optional<BlockPos> findStandingPosition(ServerLevel level, VillagerEntityMCA villager, BlockPos grave) {
        Map<BlockPos, Integer> reservations = getMourningReservations(level, villager, grave);
        BlockPos origin = villager.blockPosition();
        int villagerHash = villager.getUUID().hashCode();
        return getStandingPositions(grave)
                .filter(position -> isGoodWalkTarget(level, villager, position))
                .min(Comparator
                        .comparingInt((BlockPos position) -> isOppositeSide(origin, grave, position) ? 1 : 0)
                        .thenComparingInt(position -> Math.abs(position.getY() - grave.getY()))
                        .thenComparingInt(position -> reservations.getOrDefault(position, 0))
                        .thenComparingInt(position -> position.distManhattan(origin))
                        .thenComparingInt(position -> position.hashCode() ^ villagerHash));
    }

    private static boolean isOppositeSide(BlockPos origin, BlockPos grave, BlockPos position) {
        long approachX = (long) origin.getX() - grave.getX();
        long approachZ = (long) origin.getZ() - grave.getZ();
        long standingX = (long) position.getX() - grave.getX();
        long standingZ = (long) position.getZ() - grave.getZ();
        return approachX * standingX + approachZ * standingZ < 0L;
    }

    private static Map<BlockPos, Integer> getMourningReservations(ServerLevel level, VillagerEntityMCA villager, BlockPos grave) {
        Map<BlockPos, Integer> reservations = new HashMap<>();
        GlobalPos site = GlobalPos.of(level.dimension(), grave);
        getReservationPeers(level, villager, grave)
                .filter(other -> other != villager)
                .filter(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(site::equals).isPresent())
                .forEach(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(position -> position.dimension().equals(level.dimension()))
                        .map(GlobalPos::pos)
                        .ifPresent(position -> reservations.merge(position, 1, Integer::sum)));
        return reservations;
    }

    private static Stream<VillagerEntityMCA> getReservationPeers(
            ServerLevel level,
            VillagerEntityMCA villager,
            BlockPos grave
    ) {
        Optional<Village> village = villager.getResidency().getHomeVillage()
                .filter(homeVillage -> homeVillage.isWithinBorder(grave, Village.BORDER_MARGIN))
                .or(() -> VillageManager.get(level).findNearestVillage(grave, Village.BORDER_MARGIN));
        return village.<Stream<VillagerEntityMCA>>map(homeVillage -> homeVillage.getResidents(level).stream())
                .orElseGet(() -> level.getEntitiesOfClass(
                        VillagerEntityMCA.class,
                        new AABB(grave).inflate(RESERVATION_FALLBACK_RADIUS)
                ).stream());
    }

    private static Stream<BlockPos> getStandingPositions(BlockPos grave) {
        return Arrays.stream(HORIZONTAL_OFFSETS)
                .flatMap(offset -> Arrays.stream(VERTICAL_OFFSETS).mapToObj(y -> grave.offset(offset[0], y, offset[1])));
    }

    private static boolean isGoodWalkTarget(ServerLevel level, VillagerEntityMCA villager, BlockPos position) {
        return villager.getNavigation().isStableDestination(position)
                && level.noCollision(villager, villager.getBoundingBox().move(Vec3.atBottomCenterOf(position).subtract(villager.position())));
    }
}
