package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
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
        Optional<BlockPos> blockPos = getNextPosition(villager);
        blockPos.ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, this.speed, 1));
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .flatMap(buildings -> buildings.getBuildings().values().stream()
                        .filter(a -> a.getType().equals(getBuilding(villager)))
                        .min(Comparator.comparingInt(a -> a.getCenter().distManhattan(villager.blockPosition()))));
    }

    protected Optional<BlockPos> getRandomPositionIn(Building b, Level world, VillagerEntityMCA villager) {
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
                && villager.getNavigation().isStableDestination(pos)
                && world.noCollision(
                        villager,
                        villager.getBoundingBox().move(Vec3.atBottomCenterOf(pos).subtract(villager.position()))
                );
    }

    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<Building> b = getNearestBuilding(villager);
        if (b.isPresent() && !b.get().containsPos(villager.blockPosition())) {
            return getRandomPositionIn(b.get(), villager.level(), villager);
        }
        return Optional.empty();
    }

    public String getBuilding(VillagerEntityMCA villager) {
        return building;
    }
}
