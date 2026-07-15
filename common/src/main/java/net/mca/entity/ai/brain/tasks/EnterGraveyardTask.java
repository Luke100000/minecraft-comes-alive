package net.mca.entity.ai.brain.tasks;

import dev.architectury.platform.Platform;
import net.mca.TagsMCA;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.server.world.data.Building;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Arrays;
import java.util.Comparator;
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

    public EnterGraveyardTask(float speed) {
        super("graveyard", speed);
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<MourningTarget> target = findTarget(villager);
        if (target.isEmpty() && Platform.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] no-target villager={} position={} rememberedGrave={}",
                    villager.getName().getString(),
                    villager.getBlockPos(),
                    villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null));
        }
        return target.map(result -> {
            if (Platform.isDevelopmentEnvironment()) {
                MCA.LOGGER.info("[MOURNING_TRACE_V3] target villager={} from={} grave={} stand={}",
                        villager.getName().getString(),
                        villager.getBlockPos(),
                        result.grave(),
                        result.standingPosition());
            }
            villager.getBrain().remember(MemoryModuleTypeMCA.MOURNING_SITE.get(), result.grave());
            villager.getBrain().remember(MemoryModuleTypeMCA.MOURNING_POSITION.get(), GlobalPos.create(villager.getWorld().getRegistryKey(), result.standingPosition()));
            villager.getBrain().forget(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            return result.standingPosition();
        });
    }

    @Override
    protected int getCompletionRange() {
        return 0;
    }

    private Optional<MourningTarget> findTarget(VillagerEntityMCA villager) {
        World world = villager.getWorld();
        Optional<BlockPos> rememberedSite = villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get());

        if (rememberedSite.isPresent()) {
            BlockPos grave = rememberedSite.get();
            if (!isTombstone(world, grave)) {
                return Optional.empty();
            }
            return findStandingPosition(world, villager, grave)
                    .map(standingPosition -> new MourningTarget(grave, standingPosition));
        }

        BlockPos origin = villager.getBlockPos();
        return getCompleteGraveyards(villager)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(grave -> isTombstone(world, grave))
                .flatMap(grave -> getValidStandingPositions(world, villager, grave)
                        .map(position -> new MourningTarget(grave, position)))
                .min(Comparator.comparingInt(target -> target.standingPosition().getManhattanDistance(origin)));
    }

    public static boolean isAtMourningSite(VillagerEntityMCA villager) {
        return getMourningPosition(villager)
                .filter(position -> position.equals(villager.getBlockPos()))
                .isPresent();
    }

    public static boolean hasValidMourningTarget(VillagerEntityMCA villager) {
        return getMourningPosition(villager).isPresent();
    }

    private static Optional<BlockPos> getMourningPosition(VillagerEntityMCA villager) {
        World world = villager.getWorld();
        return villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .filter(grave -> isTombstone(world, grave))
                .flatMap(grave -> villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_POSITION.get()))
                .filter(position -> position.getDimension().equals(world.getRegistryKey()))
                .map(GlobalPos::getPos);
    }

    private static Stream<Building> getCompleteGraveyards(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .stream()
                .flatMap(village -> village.getBuildingsOfType("graveyard"))
                .filter(Building::isComplete);
    }

    private static Optional<BlockPos> findStandingPosition(World world, VillagerEntityMCA villager, BlockPos grave) {
        return getValidStandingPositions(world, villager, grave)
                .min(Comparator.comparingInt(position -> position.getManhattanDistance(villager.getBlockPos())));
    }

    private static Stream<BlockPos> getValidStandingPositions(World world, VillagerEntityMCA villager, BlockPos grave) {
        return getStandingPositions(grave).filter(position -> isGoodWalkTarget(world, villager, position));
    }

    private static Stream<BlockPos> getStandingPositions(BlockPos grave) {
        return Arrays.stream(HORIZONTAL_OFFSETS)
                .flatMap(offset -> Arrays.stream(VERTICAL_OFFSETS)
                        .mapToObj(y -> grave.add(offset[0], y, offset[1])));
    }

    private static boolean isGoodWalkTarget(World world, VillagerEntityMCA villager, BlockPos position) {
        return villager.getNavigation().isValidPosition(position)
                && world.isSpaceEmpty(
                        villager,
                        villager.getBoundingBox().offset(Vec3d.ofBottomCenter(position).subtract(villager.getPos()))
                );
    }

    private static boolean isTombstone(World world, BlockPos position) {
        return world.getBlockState(position).isIn(TagsMCA.Blocks.TOMBSTONES);
    }

    private record MourningTarget(BlockPos grave, BlockPos standingPosition) {
    }
}
