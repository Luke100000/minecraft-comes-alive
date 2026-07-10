package net.mca.entity.ai.navigation;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.World;

/** 1.20.1 equivalent of the newer MCA ground navigation implementation. */
public class MCAGroundPathNavigation extends MobNavigation {
    public MCAGroundPathNavigation(MobEntity mobEntity, World world) {
        super(mobEntity, world);
    }

    @Override
    protected PathNodeNavigator createPathNodeNavigator(int range) {
        nodeMaker = new MCAWalkNodeEvaluator();
        nodeMaker.setCanEnterOpenDoors(true);
        nodeMaker.setCanOpenDoors(true);
        return new PathNodeNavigator(nodeMaker, range);
    }
}
