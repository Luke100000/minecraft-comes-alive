package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Messenger;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class ExtendedMeleeAttackTask extends Behavior<Mob> {
    private final float range;
    private final int interval;
    private final MemoryModuleType<? extends LivingEntity> target;

    public ExtendedMeleeAttackTask(int interval, float range) {
        this(interval, range, MemoryModuleType.ATTACK_TARGET);
    }

    public ExtendedMeleeAttackTask(int interval, float range, MemoryModuleType<? extends LivingEntity> target) {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED, target, MemoryStatus.VALUE_PRESENT, MemoryModuleType.ATTACK_COOLING_DOWN, MemoryStatus.VALUE_ABSENT));
        this.range = range;
        this.interval = interval;
        this.target = target;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverWorld, Mob attacker) {
        LivingEntity target = getTarget(attacker);
        return BehaviorUtils.canSee(attacker, target) && withinRange(attacker, target);
    }

    @Override
    protected void start(ServerLevel serverWorld, Mob mobEntity, long l) {
        LivingEntity livingEntity = getTarget(mobEntity);
        BehaviorUtils.lookAtEntity(mobEntity, livingEntity);
        if (mobEntity instanceof VillagerLike<?> villager) {
            mobEntity.swing(villager.getDominantHand());
        } else {
            mobEntity.swing(InteractionHand.MAIN_HAND);
        }
        mobEntity.doHurtTarget(livingEntity);
        mobEntity.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, interval);

        // kill phrase
        if (livingEntity.isDeadOrDying() && mobEntity instanceof Messenger messenger) {
            if (mobEntity.getRandom().nextFloat() < 0.3) {
                messenger.sendChatToAllAround("villager.kill");
            }
        }
    }

    private boolean withinRange(LivingEntity attacker, LivingEntity target) {
        double d = attacker.distanceToSqr(target.getX(), target.getY(), target.getZ());
        double r = attacker.getBbWidth() + target.getBbWidth() + range;
        return d <= r * r;
    }

    private LivingEntity getTarget(Mob mobEntity) {
        return mobEntity.getBrain().getMemoryInternal(target).get();
    }
}
