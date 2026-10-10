package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class EnterBuildingTask extends Behavior<VillagerEntityMCA> {
    private final String building;
    private final float speed;

    public EnterBuildingTask(String building, float speed) {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
        this.building = building;
        this.speed = speed;
    }

    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        getNextPosition(villager)
                .ifPresent(pos -> villager.moveTowardsPersistent(pos, this.speed, getCompletionRange()));
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .flatMap(village -> getNearestBuilding(villager, village.getBuildings().values()));
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager, Iterable<Building> candidates) {
        String buildingType = getBuilding(villager);
        BlockPos origin = villager.blockPosition();
        Building nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (Building candidate : candidates) {
            if (!candidate.getType().equals(buildingType)) {
                continue;
            }
            int distance = candidate.getCenter().distManhattan(origin);
            if (nearest == null || distance < nearestDistance) {
                nearest = candidate;
                nearestDistance = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    protected Optional<BlockPos> getRandomPositionIn(Building b, Level world, VillagerEntityMCA villager) {
        if (!b.getFloorCells().isEmpty()) {
            List<BlockPos> floorTargets = b.getFloorCells().stream()
                    .filter(pos -> isGoodFloorWalkTarget(world, villager, pos))
                    .toList();
            if (floorTargets.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(floorTargets.get(world.getRandom().nextInt(floorTargets.size())));
        }

        if (b.getBuildingType().grouped()) {
            //todo randomize
            return Optional.ofNullable(b.getCenter())
                    .filter(pos -> isGoodIndoorWalkTarget(world, villager, pos));
        }

        RandomSource r = world.getRandom();
        BlockPos pos0 = b.getPos0();
        BlockPos pos1 = b.getPos1();
        BlockPos diff = pos1.subtract(pos0);
        int margin = 2;
        for (int attempt = 0; attempt < 16; attempt++) {
            //todo positions are too random and weird, they should be floor only, e.g. solid
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
        return !world.getBlockState(pos).is(BlockTags.DOORS)
                && !BedPoiCompatibility.isCompatibleBedState(world.getBlockState(pos))
                && !BedPoiCompatibility.isCompatibleBedState(world.getBlockState(pos.below()))
                && hasStandingSpace(world, mob, pos);
    }

    static boolean hasStandingSpace(Level world, PathfinderMob mob, BlockPos pos) {
        return mob.getNavigation().isStableDestination(pos)
                && world.noCollision(
                        mob,
                        mob.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(mob.position()))
                );
    }

    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<Building> b = getNearestBuilding(villager);
        if (b.isPresent() && !isInsideBuilding(b.get(), villager)) {
            return getRandomPositionIn(b.get(), villager.level(), villager);
        }
        return Optional.empty();
    }

    protected boolean isInsideBuilding(Building target, VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .flatMap(village -> village.findPhysicalRoomAt(villager.blockPosition()))
                .map(current -> current.getId() == target.getId())
                .orElse(false);
    }

    protected int getCompletionRange() {
        return 1;
    }

    public String getBuilding(VillagerEntityMCA villager) {
        return building;
    }
}
