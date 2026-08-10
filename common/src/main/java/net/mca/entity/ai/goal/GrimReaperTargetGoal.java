package net.mca.entity.ai.goal;

import java.util.Comparator;
import java.util.List;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

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
                list.sort(Comparator.comparingDouble((Player player) -> player.getY()).reversed());

                for (Player playerentity : list) {
                    if (mob.canAttack(playerentity, TargetingConditions.DEFAULT)) {
                        mob.setTarget(playerentity);
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
