package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.entity.ai.navigation.PathNodeGeometry;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomTypeResolver;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class EnterBuildingTask extends Behavior<VillagerEntityMCA> {
    private static final int FAILED_BUILDING_RETRY_TICKS = 100;
    private static final double FAILED_BUILDING_RETRY_MOVE_DISTANCE_SQR = 16.0D;
    private final String building;
    private final float speed;
    private BlockPos lastBuildingSearchOrigin;
    private String lastBuildingSearchType;
    private String selectedBuildingType;
    private long nextFailedBuildingRetryTick;

    public EnterBuildingTask(String building, float speed) {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
        this.building = building;
        this.speed = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return lastBuildingSearchOrigin == null
                || !Objects.equals(getBuilding(villager), lastBuildingSearchType)
                || villager.blockPosition().distSqr(lastBuildingSearchOrigin) >= FAILED_BUILDING_RETRY_MOVE_DISTANCE_SQR
                || level.getGameTime() >= nextFailedBuildingRetryTick;
    }

    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        selectedBuildingType = null;
        getNextPosition(villager)
                .ifPresent(pos -> {
                    // Fixed-target subclasses bypass building selection but still need
                    // the normal search retry interval after pathfinding failure.
                    rememberBuildingSearch(villager,
                            selectedBuildingType == null ? getBuilding(villager) : selectedBuildingType, l);
                    BehaviorUtils.setWalkAndLookTargetMemories(villager,
                            new PersistentPathTarget(pos, villager.getBrain().getActiveNonCoreActivity().orElse(null)),
                            this.speed, getCompletionRange());
                });
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager) {
        return getNearestBuilding(villager, getCandidateBuildings(villager));
    }

    protected Iterable<Building> getCandidateBuildings(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .<Iterable<Building>>map(village -> village)
                .orElse(List.of());
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager, Iterable<Building> candidates) {
        return getMatchingBuildings(villager, candidates, getBuilding(villager)).stream().findFirst();
    }

    protected List<Building> getMatchingBuildings(VillagerEntityMCA villager, String buildingType) {
        return getMatchingBuildings(villager, getCandidateBuildings(villager), buildingType);
    }

    private List<Building> getMatchingBuildings(VillagerEntityMCA villager, Iterable<Building> candidates,
                                                String buildingType) {
        if (buildingType == null || buildingType.isEmpty()) return List.of();
        BlockPos origin = villager.blockPosition();
        Village homeVillage = villager.getResidency().getHomeVillage().orElse(null);
        RoomTypeResolver resolver = homeVillage == null ? null : RoomTypeResolver.create(homeVillage);
        List<Building> matching = new ArrayList<>();
        for (Building candidate : candidates) {
            String effectiveType = candidate.getType();
            if (resolver != null && candidate.isFunctionalRoom()
                    && homeVillage.getBuilding(candidate.getId()).orElse(null) == candidate) {
                BuildingType resolved = resolver.effectiveType(candidate);
                effectiveType = resolved == null ? null : resolved.name();
            } else if (!candidate.isFunctionalRoom() && !candidate.isComplete()) {
                continue;
            }
            if (buildingType.equals(effectiveType)) {
                matching.add(candidate);
            }
        }
        // List.sort preserves the candidate iteration order for equal distances.
        matching.sort(Comparator.comparingInt(candidate -> candidate.getCenter().distManhattan(origin)));
        return matching;
    }

    protected Optional<BlockPos> getRandomPositionIn(Building b, Level world, VillagerEntityMCA villager) {
        Set<BlockPos> effectiveFloorCells = villager.getResidency().getHomeVillage()
                .filter(village -> village.getBuilding(b.getId()).orElse(null) == b)
                .map(village -> village.getEffectiveFloorCells(b))
                .orElseGet(b::getFloorCells);
        if (!effectiveFloorCells.isEmpty()) {
            List<BlockPos> floorTargets = effectiveFloorCells.stream()
                    .filter(pos -> isGoodFloorWalkTarget(world, villager, pos))
                    .toList();
            if (floorTargets.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(floorTargets.get(world.getRandom().nextInt(floorTargets.size())));
        }

        if (b.getBuildingType().grouped()) {
            BlockPos center = b.getCenter();
            if (center != null && isGoodFloorWalkTarget(world, villager, center)) {
                return Optional.of(center);
            }
            List<BlockPos> candidates = new ArrayList<>();
            b.getBlockPosStream().forEach(poi -> {
                for (Direction dir : Direction.Plane.HORIZONTAL) {
                    BlockPos adj = poi.relative(dir);
                    if (isGoodFloorWalkTarget(world, villager, adj)) {
                        candidates.add(adj);
                    }
                }
            });
            if (!candidates.isEmpty()) {
                return Optional.of(candidates.get(world.getRandom().nextInt(candidates.size())));
            }
            return Optional.empty();
        }

        RandomSource r = world.getRandom();
        BlockPos pos0 = b.getPos0();
        BlockPos pos1 = b.getPos1();
        BlockPos diff = pos1.subtract(pos0);
        int margin = 2;
        for (int attempt = 0; attempt < 16; attempt++) {
            BlockPos p = pos0.offset(new BlockPos(
                    r.nextInt(Math.max(1, diff.getX() - margin * 2)) + margin,
                    r.nextInt(Math.max(1, diff.getY() - margin * 2)) + margin,
                    r.nextInt(Math.max(1, diff.getZ() - margin * 2)) + margin
            ));
            if (isGoodIndoorWalkTarget(world, villager, p)) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private boolean isGoodIndoorWalkTarget(Level world, VillagerEntityMCA villager, BlockPos pos) {
        return !world.canSeeSky(pos)
                && isGoodFloorWalkTarget(world, villager, pos);
    }

    protected boolean isGoodFloorWalkTarget(Level world, VillagerEntityMCA villager, BlockPos pos) {
        return hasStandingSpace(world, villager, pos);
    }

    /** Idle standing space within a room, excluding sleep surfaces and doorways. */
    static boolean isUsableFloor(Level world, PathfinderMob mob, BlockPos pos) {
        return isIdleFloorSurface(world, pos) && hasStandingSpace(world, mob, pos);
    }

    /** Structural space does not shrink when another entity occupies the floor. */
    static boolean isUsableFloorGeometry(Level world, PathfinderMob mob, BlockPos pos) {
        return isIdleFloorSurface(world, pos)
                && mob.getNavigation().isStableDestination(pos)
                && world.noBlockCollision(mob,
                        mob.getBoundingBox().move(PathNodeGeometry.walkingPosition(world, pos, mob).subtract(mob.position())));
    }

    private static boolean isIdleFloorSurface(Level world, BlockPos pos) {
        return !world.getBlockState(pos).is(BlockTags.DOORS)
                && !BedPoiCompatibility.isCompatibleBedState(world.getBlockState(pos))
                && !BedPoiCompatibility.isCompatibleBedState(world.getBlockState(pos.below()));
    }

    static boolean hasStandingSpace(Level world, PathfinderMob mob, BlockPos pos) {
        return mob.getNavigation().isStableDestination(pos)
                && world.noCollision(
                        mob,
                        mob.getBoundingBox().move(PathNodeGeometry.walkingPosition(world, pos, mob).subtract(mob.position()))
                );
    }

    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        String buildingType = getBuilding(villager);
        return getPositionInAvailableBuilding(villager, getMatchingBuildings(villager, buildingType), buildingType);
    }

    protected Optional<BlockPos> getPositionInAvailableBuilding(VillagerEntityMCA villager, List<Building> matching,
                                                                 String buildingType) {
        selectedBuildingType = buildingType;
        for (Building candidate : matching) {
            Optional<BlockPos> position = getRandomPositionIn(candidate, villager.level(), villager);
            if (position.isPresent()) {
                if (isInsideBuilding(candidate, villager)) {
                    return Optional.empty();
                }
                return position;
            }
        }
        if (!matching.isEmpty()) {
            rememberBuildingSearch(villager, buildingType, villager.level().getGameTime());
        }
        return Optional.empty();
    }

    private void rememberBuildingSearch(VillagerEntityMCA villager, String buildingType, long gameTime) {
        lastBuildingSearchOrigin = villager.blockPosition().immutable();
        lastBuildingSearchType = buildingType;
        nextFailedBuildingRetryTick = gameTime + FAILED_BUILDING_RETRY_TICKS;
    }

    protected boolean isInsideBuilding(Building target, VillagerEntityMCA villager) {
        if (!target.isFunctionalRoom()) {
            return target.containsPos(villager.blockPosition());
        }
        Village homeVillage = villager.getResidency().getHomeVillage().orElse(null);
        if (homeVillage != null && homeVillage.getBuilding(target.getId()).orElse(null) == target) {
            return homeVillage.getEffectiveFloorCells(target).contains(villager.blockPosition());
        }
        return target.containsPos(villager.blockPosition());
    }

    protected int getCompletionRange() {
        return 1;
    }

    public String getBuilding(VillagerEntityMCA villager) {
        return building;
    }
}
