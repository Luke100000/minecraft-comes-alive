package net.mca.entity.ai.brain.tasks;

import net.mca.MCA;
import net.mca.TagsMCA;
import net.mca.MCA;
import net.mca.block.TombstoneBlock;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.server.world.data.Building;
import net.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Selects a valid standing position beside the exact tombstone that should be mourned.
 * Death-triggered mourning reuses the remembered burial site; periodic mourning selects
 * a tombstone from a complete graveyard and remembers it for the duration of the attempt.
 */
public class EnterGraveyardTask extends EnterBuildingTask {
    private static final int[][] HORIZONTAL_OFFSETS = {
            {1, 0},
            {-1, 0},
            {0, 1},
            {0, -1},
            {1, 1},
            {1, -1},
            {-1, 1},
            {-1, -1}
    };
    private static final int[] VERTICAL_OFFSETS = {0, 1, -1};
    private static final double MOURNING_GRAVE_DISTANCE = 3.0D;
    private static final double RESERVATION_SCAN_RANGE = 256.0D;

    public EnterGraveyardTask(float speed) {
        super("graveyard", speed);
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<MourningTarget> target = findTarget(villager);
        if (target.isEmpty() && MCA.platformHelper.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] no-target villager={} position={} rememberedGrave={}",
                    villager.getName().getString(),
                    villager.blockPosition(),
                    villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null));
        }
        return target.map(result -> {
            if (MCA.platformHelper.isDevelopmentEnvironment()) {
                MCA.LOGGER.info("[MOURNING_TRACE_V3] target villager={} from={} grave={} stand={}",
                        villager.getName().getString(),
                        villager.blockPosition(),
                        result.grave(),
                        result.standingPosition());
            }
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE.get(), result.grave());
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_POSITION.get(), GlobalPos.of(villager.level().dimension(), result.standingPosition()));
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return result.standingPosition();
        });
    }

    @Override
    protected int getCompletionRange() {
        return 0;
    }

    private Optional<MourningTarget> findTarget(VillagerEntityMCA villager) {
        Level world = villager.level();
        Optional<BlockPos> rememberedSite = villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get());

        if (rememberedSite.isPresent()) {
            BlockPos grave = rememberedSite.get();
            if (!isMournableTombstone(world, grave)) {
                return Optional.empty();
            }
            return findStandingPosition(world, villager, grave)
                    .map(standingPosition -> new MourningTarget(grave, standingPosition));
        }

        BlockPos origin = villager.blockPosition();
        return getCompleteGraveyards(villager)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(grave -> isMournableTombstone(world, grave))
                .sorted(Comparator.comparingInt(grave -> grave.distManhattan(origin)))
                .map(grave -> findStandingPosition(world, villager, grave)
                        .map(position -> new MourningTarget(grave, position)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    public static boolean isAtMourningSite(VillagerEntityMCA villager) {
        if (!isWithinMourningArea(villager)) {
            return false;
        }

        Level world = villager.level();
        BlockPos villagerPosition = villager.blockPosition();
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION.get())
                .filter(position -> position.dimension().equals(world.dimension()))
                .map(GlobalPos::pos)
                .filter(position -> position.equals(villagerPosition))
                .isPresent();
    }

    public static boolean isWithinMourningArea(VillagerEntityMCA villager) {
        Level world = villager.level();
        BlockPos villagerPosition = villager.blockPosition();
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .filter(grave -> isMournableTombstone(world, grave))
                .filter(grave -> grave.closerToCenterThan(villager.position(), MOURNING_GRAVE_DISTANCE))
                .filter(grave -> grave.getX() != villagerPosition.getX() || grave.getZ() != villagerPosition.getZ())
                .isPresent();
    }

    public static boolean hasValidMourningTarget(VillagerEntityMCA villager) {
        return getMourningPosition(villager).isPresent();
    }

    private static Optional<BlockPos> getMourningPosition(VillagerEntityMCA villager) {
        Level world = villager.level();
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .filter(grave -> isMournableTombstone(world, grave))
                .flatMap(grave -> villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION.get()))
                .filter(position -> position.dimension().equals(world.dimension()))
                .map(GlobalPos::pos);
    }

    public static boolean hasMournableSite(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .filter(grave -> isMournableTombstone(villager.level(), grave))
                .isPresent();
    }

    public static boolean hasPeriodicMourningCandidate(VillagerEntityMCA villager) {
        Level world = villager.level();
        return getCompleteGraveyards(villager)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .anyMatch(grave -> isMournableTombstone(world, grave));
    }

    private static Stream<Building> getCompleteGraveyards(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .stream()
                .flatMap(village -> village.getBuildingsOfType("graveyard"))
                .filter(Building::isComplete);
    }

    private static Optional<BlockPos> findStandingPosition(Level world, VillagerEntityMCA villager, BlockPos grave) {
        Map<BlockPos, Integer> reservations = getMourningReservations(world, villager, grave);
        BlockPos origin = villager.blockPosition();
        int villagerHash = villager.getUUID().hashCode();
        return getValidStandingPositions(world, villager, grave)
                .min(Comparator
                        .comparingInt((BlockPos position) -> isOppositeSide(origin, grave, position) ? 1 : 0)
                        .thenComparingInt(position -> Math.abs(position.getY() - grave.getY()))
                        .thenComparingInt(position -> reservations.getOrDefault(position, 0))
                        .thenComparingInt(position -> position.distManhattan(origin))
                        .thenComparingInt(position -> position.hashCode() ^ villagerHash));
    }

    private static boolean isOppositeSide(BlockPos origin, BlockPos grave, BlockPos standingPosition) {
        long approachX = (long) origin.getX() - grave.getX();
        long approachZ = (long) origin.getZ() - grave.getZ();
        long standingX = (long) standingPosition.getX() - grave.getX();
        long standingZ = (long) standingPosition.getZ() - grave.getZ();
        return approachX * standingX + approachZ * standingZ < 0L;
    }

    private static Map<BlockPos, Integer> getMourningReservations(Level world, VillagerEntityMCA villager, BlockPos grave) {
        Map<BlockPos, Integer> reservations = new HashMap<>();

                WorldUtils.getCloseEntities(world, Vec3.atCenterOf(grave), RESERVATION_SCAN_RANGE, VillagerEntityMCA.class)
                .stream()
                .filter(other -> other != villager)
                .filter(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get())
                        .filter(grave::equals)
                        .isPresent())
                .forEach(other -> other.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION.get())
                        .filter(position -> position.dimension().equals(world.dimension()))
                        .map(GlobalPos::pos)
                        .ifPresent(position -> reservations.merge(position, 1, Integer::sum)));
        return reservations;
    }

    private static Stream<BlockPos> getValidStandingPositions(Level world, VillagerEntityMCA villager, BlockPos grave) {
        return getStandingPositions(grave).filter(position -> isGoodWalkTarget(world, villager, position));
    }

    private static Stream<BlockPos> getStandingPositions(BlockPos grave) {
        return Arrays.stream(HORIZONTAL_OFFSETS)
                .flatMap(offset -> Arrays.stream(VERTICAL_OFFSETS)
                        .mapToObj(y -> grave.offset(offset[0], y, offset[1])));
    }

    private static boolean isGoodWalkTarget(Level world, VillagerEntityMCA villager, BlockPos position) {
        return villager.getNavigation().isStableDestination(position)
                && world.noCollision(
                        villager,
                        villager.getBoundingBox().move(Vec3.atBottomCenterOf(position).subtract(villager.position()))
                );
    }

    private static boolean isMournableTombstone(Level world, BlockPos position) {
        if (!world.getBlockState(position).is(TagsMCA.Blocks.TOMBSTONES)) {
            return false;
        }
        return TombstoneBlock.Data.of(world.getBlockEntity(position))
                .filter(TombstoneBlock.Data::hasEntity)
                .filter(data -> !data.isResurrecting())
                .isPresent();
    }

    private record MourningTarget(BlockPos grave, BlockPos standingPosition) {
    }
}
