package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HuntingTask extends AbstractChoreTask {
    private static final ThreadLocal<LootCapture> ACTIVE_LOOT_CAPTURE = new ThreadLocal<>();

    private int ticks = 0;
    private int nextAction = 0;
    private Animal target = null;

    public HuntingTask() {
        super(ImmutableMap.of(MemoryModuleType.LOOK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getVillagerBrain().getCurrentJob() == Chore.HUNT && super.checkExtraStartConditions(world, villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return checkExtraStartConditions(world, villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        target = null;
        ticks = 0;
        nextAction = 0;
        releaseChorePreyMovement(villager);
        releaseChoreHand(villager, Chore.HUNT);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.start(world, villager, time);

        equipChoreTool(villager, Chore.HUNT, "chore.hunting.nosword");
    }

    @Override
    @SuppressWarnings("deprecation")
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        super.tick(world, villager, time);
        if (villager.getVillagerBrain().getCurrentJob() != Chore.HUNT) return;

        if (!equipChoreTool(villager, Chore.HUNT, "chore.hunting.nosword")) return;

        // An unloaded, removed or dying prey cannot be pursued after task interruption.
        if (target != null && (!target.isAlive() || target.isRemoved() || world.getEntity(target.getId()) != target)) {
            target = null;
            ticks = 0;
            nextAction = 0;
        }

        if (target == null) {
            ticks++;

            if (ticks >= nextAction) {
                ticks = 0;
                if (villager.getRandom().nextFloat() >= 0.0D) {
                    villager.level().getEntitiesOfClass(Animal.class, villager.getBoundingBox().inflate(15, 3, 15)).stream()
                            .filter(a -> !(a instanceof TamableAnimal))
                            .filter(a -> !a.isBaby() && a.isAlive() && !a.isRemoved())
                            .min(Comparator.comparingDouble(villager::distanceToSqr))
                            .ifPresent(animal -> {
                                target = animal;
                                moveTowardsChorePrey(target.blockPosition(), 1.0f, 1);
                            });
                }

                nextAction = 50;

                if (target == null) {
                    failedTicks = FAILED_COOLDOWN;
                }
            }
        } else {
            moveTowardsChorePrey(target.blockPosition(), 0.5F, 1);

            if (target.isDeadOrDying()) {
                target = null;
            } else if (villager.distanceToSqr(target) <= 12.25F
                    && !villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN)) {
                moveTowardsChorePrey(target.blockPosition(), 0.5F, 1);
                villager.getBrain().setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, 20L);
                villager.swingForAttack(villager.getDominantHand());
                LootCapture previous = ACTIVE_LOOT_CAPTURE.get();
                LootCapture capture = new LootCapture(target, villager);
                ACTIVE_LOOT_CAPTURE.set(capture);
                boolean hit;
                try {
                    hit = villager.doHurtTarget(world, target);
                    for (ItemEntity drop : capture.drops) {
                        if (drop.isRemoved() || world.getEntity(drop.getId()) != drop) {
                            continue;
                        }
                        ItemStack original = drop.getItem();
                        ItemStack remainder = villager.getInventory().addItem(original);
                        if (remainder.isEmpty()) {
                            drop.discard();
                        } else if (remainder.getCount() < original.getCount()) {
                            drop.setItem(remainder);
                        }
                    }
                } finally {
                    if (previous == null) {
                        ACTIVE_LOOT_CAPTURE.remove();
                    } else {
                        ACTIVE_LOOT_CAPTURE.set(previous);
                    }
                }
                if (hit) {
                    villager.getItemInHand(villager.getDominantHand())
                            .hurtAndBreak(1, villager, villager.getDominantSlot());
                }
            }
        }
    }

    public static boolean isCapturingLoot(LivingEntity prey, DamageSource source) {
        LootCapture capture = ACTIVE_LOOT_CAPTURE.get();
        return capture != null && capture.prey == prey && source != null && source.getEntity() == capture.hunter;
    }

    public static void recordCapturedLoot(ItemEntity drop) {
        LootCapture capture = ACTIVE_LOOT_CAPTURE.get();
        if (capture != null && drop != null) {
            capture.drops.add(drop);
        }
    }

    private static final class LootCapture {
        private final LivingEntity prey;
        private final VillagerEntityMCA hunter;
        private final List<ItemEntity> drops = new ArrayList<>();

        private LootCapture(LivingEntity prey, VillagerEntityMCA hunter) {
            this.prey = prey;
            this.hunter = hunter;
        }
    }
}
