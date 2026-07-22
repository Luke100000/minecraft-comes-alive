package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Stream;

/**
 * Assigns a mourner to an occupied tombstone and an adjacent safe standing position.
 */
public class EnterGraveyardTask extends EnterBuildingTask {
    private static final int[][] HORIZONTAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};
    private static final double MOURNING_GRAVE_DISTANCE = 3.0D;
    private static final double RESERVATION_SCAN_RANGE = 256.0D;

    public EnterGraveyardTask(float speed) {
        super("graveyard", speed);
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        return findTarget(villager).map(target -> {
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, target.grave());
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION, GlobalPos.of(villager.level().dimension(), target.standingPosition()));
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return target.standingPosition();
        });
    }

    @Override
    protected int getCompletionRange() {
        return 0;
    }

    private Optional<MourningTarget> findTarget(VillagerEntityMCA villager) {
        Level level = villager.level();
        Optional<BlockPos> rememberedSite = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
        if (rememberedSite.isPresent()) {
            BlockPos grave = rememberedSite.get();
            return isMournableTombstone(level, grave)
                    ? findStandingPosition(level, villager, grave).map(position -> new MourningTarget(grave, position))
                    : Optional.empty();
        }

        BlockPos origin = villager.blockPosition();
        return getCompleteGraveyards(villager)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(grave -> isMournableTombstone(level, grave))
                .sorted(Comparator.comparingInt(grave -> grave.distManhattan(origin)))
                .map(grave -> findStandingPosition(level, villager, grave).map(position -> new MourningTarget(grave, position)))
                .flatMap(Optional::stream)
                .findFirst();
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
                .filter(grave -> isMournableTombstone(villager.level(), grave))
                .filter(grave -> grave.closerToCenterThan(villager.position(), MOURNING_GRAVE_DISTANCE))
                .filter(grave -> grave.getX() != villagerPosition.getX() || grave.getZ() != villagerPosition.getZ())
                .isPresent();
    }

    public static boolean hasValidMourningTarget(VillagerEntityMCA villager) {
        return getMourningPosition(villager).isPresent();
    }

    public static boolean hasMournableSite(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(grave -> isMournableTombstone(villager.level(), grave))
                .isPresent();
    }

    public static boolean hasPeriodicMourningCandidate(VillagerEntityMCA villager) {
        return getCompleteGraveyards(villager)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .anyMatch(grave -> isMournableTombstone(villager.level(), grave));
    }

    private static Optional<BlockPos> getMourningPosition(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(grave -> isMournableTombstone(villager.level(), grave))
                .flatMap(grave -> villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION))
                .filter(position -> position.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos);
    }

    private static Stream<Building> getCompleteGraveyards(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .stream()
                .flatMap(village -> village.getBuildingsOfType("graveyard"))
                .filter(Building::isComplete);
    }

    private static Optional<BlockPos> findStandingPosition(Level level, VillagerEntityMCA villager, BlockPos grave) {
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

    private static Map<BlockPos, Integer> getMourningReservations(Level level, VillagerEntityMCA villager, BlockPos grave) {
        Map<BlockPos, Integer> reservations = new HashMap<>();
        WorldUtils.getCloseEntities(level, Vec3.atCenterOf(grave), RESERVATION_SCAN_RANGE, VillagerEntityMCA.class).stream()
                .filter(other -> other != villager)
                .filter(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(grave::equals).isPresent())
                .forEach(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION)
                        .filter(position -> position.dimension().equals(level.dimension()))
                        .map(GlobalPos::pos)
                        .ifPresent(position -> reservations.merge(position, 1, Integer::sum)));
        return reservations;
    }

    private static Stream<BlockPos> getStandingPositions(BlockPos grave) {
        return Arrays.stream(HORIZONTAL_OFFSETS)
                .flatMap(offset -> Arrays.stream(VERTICAL_OFFSETS).mapToObj(y -> grave.offset(offset[0], y, offset[1])));
    }

    private static boolean isGoodWalkTarget(Level level, VillagerEntityMCA villager, BlockPos position) {
        return villager.getNavigation().isStableDestination(position)
                && level.noCollision(villager, villager.getBoundingBox().move(Vec3.atBottomCenterOf(position).subtract(villager.position())));
    }

    private static boolean isMournableTombstone(Level level, BlockPos position) {
        return level.getBlockState(position).is(TagsMCA.Blocks.TOMBSTONES)
                && TombstoneBlock.Data.of(level.getBlockEntity(position))
                .filter(TombstoneBlock.Data::hasEntity)
                .filter(data -> !data.isResurrecting())
                .isPresent();
    }

    private record MourningTarget(BlockPos grave, BlockPos standingPosition) {
    }
}
