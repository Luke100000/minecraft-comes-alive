package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/** Homeless villagers approach covered bedside floor without claiming HOME. */
public final class SeekIndoorShelterTask extends EnterBuildingTask {
    private long nextAttemptTime;

    public SeekIndoorShelterTask(float speed) {
        super("house", speed);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.isSleeping() || villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)
                || level.getGameTime() < nextAttemptTime) {
            return false;
        }
        // Preserve the vanilla homeless fallback's staggered search cadence.
        nextAttemptTime = level.getGameTime() + 20L + level.getRandom().nextInt(20);
        // Roof cover alone also accepts a doorstep under the eaves. Only stop
        // seeking shelter once the villager is at a usable bedside position.
        return level.getPoiManager().findAllClosestFirstWithType(
                poi -> poi.is(PoiTypes.HOME),
                pos -> BedPoiCompatibility.isHomePoiState(level.getBlockState(pos)),
                villager.blockPosition(), 2, PoiManager.Occupancy.ANY)
                .noneMatch(bed -> getBedsideFloorPositions(level, villager, bed.getSecond())
                        .contains(villager.blockPosition()));
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        ServerLevel level = (ServerLevel) villager.level();
        var beds = level.getPoiManager().findAllClosestFirstWithType(
                poi -> poi.is(PoiTypes.HOME),
                pos -> BedPoiCompatibility.isHomePoiState(level.getBlockState(pos)),
                villager.blockPosition(), 48, PoiManager.Occupancy.ANY).limit(5).toList();
        for (var bed : beds) {
            Optional<BlockPos> destination = findReachableFloor(level, villager,
                    getBedsideFloorPositions(level, villager, bed.getSecond()));
            if (destination.isPresent()) {
                return destination;
            }
        }
        return Optional.empty();
    }

    private Set<BlockPos> getBedsideFloorPositions(ServerLevel level, VillagerEntityMCA villager, BlockPos head) {
        var approaches = BedApproachTarget.create(level, head)
                .map(target -> target.getPathTargets(villager)).orElseGet(Set::of);
        var room = villager.getResidency().getHomeVillage()
                .flatMap(village -> village.findPhysicalRoomAt(head));
        Set<BlockPos> floorPositions = new HashSet<>();
        for (BlockPos pos : approaches) {
            if (isGoodFloorWalkTarget(level, villager, pos)
                    && room.map(building -> building.containsFloorPosition(pos)).orElse(true)) {
                floorPositions.add(pos.immutable());
            }
        }
        return floorPositions;
    }

    private Optional<BlockPos> findReachableFloor(ServerLevel level, VillagerEntityMCA villager,
                                                 Set<BlockPos> floorPositions) {
        if (floorPositions.isEmpty()) {
            return Optional.empty();
        }
        // Try the usable bedside positions together, rather than reject the bed
        // because one sampled side is blocked.
        Path path = villager.getNavigation().createPath(floorPositions, 0);
        return path != null && path.canReach() ? Optional.of(path.getTarget()) : Optional.empty();
    }

    @Override
    protected boolean isGoodFloorWalkTarget(Level level, VillagerEntityMCA villager, BlockPos pos) {
        return !level.canSeeSky(pos)
                && !BedPoiCompatibility.isCompatibleBedState(level.getBlockState(pos))
                && !BedPoiCompatibility.isCompatibleBedState(level.getBlockState(pos.below()))
                && super.isGoodFloorWalkTarget(level, villager, pos);
    }

    @Override
    protected int getCompletionRange() {
        // Reaching a neighboring bed surface is not reaching the selected shelter floor.
        return 0;
    }
}
