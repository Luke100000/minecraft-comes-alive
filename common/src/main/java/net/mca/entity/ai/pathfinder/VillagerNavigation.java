package net.mca.entity.ai.pathfinder;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathFinder;

public class VillagerNavigation extends GroundPathNavigation {
    public VillagerNavigation(Mob mobEntity, Level world) {
        super(mobEntity, world);
    }

    @Override
    protected PathFinder createPathFinder(int range) {
        nodeEvaluator = new VillagerLandPathNodeMaker();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, range);
    }
}
