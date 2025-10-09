package net.conczin.mca.entity.ai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public class GrimReaperTargetGoal extends Goal {
    private final TargetingConditions attackTargeting = TargetingConditions.forCombat().range(64.0D);

    private final PathfinderMob mob;

    private int nextScanTick = 20;

    public GrimReaperTargetGoal(PathfinderMob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        if (this.nextScanTick > 0) {
            this.nextScanTick--;
        } else {
            this.nextScanTick = 20;
            List<Player> list = mob.level().getNearbyPlayers(this.attackTargeting, mob, mob.getBoundingBox().inflate(48.0D, 64.0D, 48.0D));
            if (!list.isEmpty()) {
                list.sort((a, b) -> Double.compare(b.getY(), a.getY()));

                for (Player player : list) {
                    if (mob.canAttack(player, TargetingConditions.DEFAULT)) {
                        mob.setTarget(player);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getTarget() != null;
    }
}
