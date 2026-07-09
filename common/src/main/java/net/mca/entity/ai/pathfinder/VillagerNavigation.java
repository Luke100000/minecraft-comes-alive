package net.mca.entity.ai.pathfinder;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

public class VillagerNavigation extends MobNavigation {
    public VillagerNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new VillagerLandPathNodeMaker();
        nodeMaker.setCanEnterOpenDoors(true);
        nodeMaker.setCanOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, range);
    }
}
