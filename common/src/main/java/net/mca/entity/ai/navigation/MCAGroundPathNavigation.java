package net.mca.entity.ai.navigation;

import net.minecraft.entity.ai.pathing.MobNavigation;
import net.minecraft.entity.ai.pathing.PathNodeNavigator;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
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

    //TODO Use classtweaker/accesstransformer to match 1.21.1
    @Override
    protected Vec3d getPos() {
        return new Vec3d(entity.getX(), getWaterAwareSurfaceY(), entity.getZ());
    }

    private int getWaterAwareSurfaceY() {
        if (entity.isTouchingWater() && canSwim()) {
            int surfaceY = entity.getBlockY();
            BlockPos.Mutable pos = new BlockPos.Mutable(entity.getX(), surfaceY, entity.getZ());
            int steps = 0;

            while (world.getFluidState(pos).isIn(FluidTags.WATER)) {
                pos.setY(++surfaceY);
                if (++steps > 16) {
                    return entity.getBlockY();
                }
            }

            return surfaceY;
        }

        return MathHelper.floor(entity.getY() + 0.5D);
    }
}
