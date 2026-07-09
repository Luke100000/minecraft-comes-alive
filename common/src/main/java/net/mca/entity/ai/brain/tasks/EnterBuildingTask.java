package net.mca.entity.ai.brain.tasks;

import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Building;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

public class EnterBuildingTask extends MultiTickTask<VillagerEntityMCA> {
    private final String building;
    private final float speed;

    public EnterBuildingTask(String building, float speed) {
        super(Map.of(MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT, MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED));
        this.building = building;
        this.speed = speed;
    }

    protected void run(ServerWorld serverWorld, VillagerEntityMCA villager, long l) {
        Optional<BlockPos> blockPos = getNextPosition(villager);
        blockPos.ifPresent(pos -> LookTargetUtil.walkTowards(villager, pos, this.speed, 1));
    }

    protected Optional<Building> getNearestBuilding(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage()
                .flatMap(buildings -> buildings.getBuildings().values().stream()
                        .filter(a -> a.getType().equals(getBuilding(villager)))
                        .min(Comparator.comparingInt(a -> a.getCenter().getManhattanDistance(villager.getBlockPos()))));
    }

    protected Optional<BlockPos> getRandomPositionIn(Building b, World world, VillagerEntityMCA villager) {
        if (b.getBuildingType().grouped()) {
            //todo randomize
            return Optional.ofNullable(b.getCenter())
                    .filter(pos -> isGoodIndoorWalkTarget(world, villager, pos));
        }

        Random r = world.getRandom();
        BlockPos pos0 = b.getPos0();
        BlockPos pos1 = b.getPos1();
        BlockPos diff = pos1.subtract(pos0);
        int margin = 2;
        for (int attempt = 0; attempt < 16; attempt++) {
            //todo positions are too random and weird, they should be floor only, e.g. solid
            BlockPos p = pos0.add(new BlockPos(
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

    private boolean isGoodIndoorWalkTarget(World world, VillagerEntityMCA villager, BlockPos pos) {
        return !world.isSkyVisible(pos)
                && villager.getNavigation().isValidPosition(pos)
                && world.isSpaceEmpty(
                        villager,
                        villager.getBoundingBox().offset(Vec3d.ofBottomCenter(pos).subtract(villager.getPos()))
                );
    }

    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<Building> b = getNearestBuilding(villager);
        if (b.isPresent() && !b.get().containsPos(villager.getBlockPos())) {
            return getRandomPositionIn(b.get(), villager.getWorld(), villager);
        }
        return Optional.empty();
    }

    public String getBuilding(VillagerEntityMCA villager) {
        return building;
    }
}
