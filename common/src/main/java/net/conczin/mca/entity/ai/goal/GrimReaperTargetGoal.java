package net.conczin.mca.entity.ai.goal;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
            ServerLevel level = (ServerLevel) mob.level();
            List<ServerPlayer> list = level.getPlayers(player -> player.getBoundingBox().intersects(mob.getBoundingBox().inflate(48.0D, 64.0D, 48.0D)) && this.attackTargeting.test(level, mob, player));
            if (!list.isEmpty()) {
                list.sort((a, b) -> Double.compare(b.getY(), a.getY()));

                for (Player player : list) {
                    if (mob.canAttack(player)) {
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
