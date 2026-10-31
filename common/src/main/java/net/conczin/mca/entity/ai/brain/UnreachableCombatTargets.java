package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Short-lived, position-specific failures reported by the combat movement owner. */
public final class UnreachableCombatTargets {
    private static final int RETRY_TICKS = 200;
    private static final int MAX_ENTRIES = 8;

    private UnreachableCombatTargets() { }

    public record Failure(UUID target, Vec3 targetPosition, Vec3 guardPosition, long retryAt) { }

    public static boolean contains(VillagerEntityMCA guard, LivingEntity target) {
        long now = guard.level().getGameTime();
        return guard.getBrain().getMemoryInternal(MemoryModuleTypeMCA.UNREACHABLE_COMBAT_TARGETS).orElse(List.of()).stream()
                .anyMatch(failure -> failure.retryAt() > now && failure.target().equals(target.getUUID())
                        && failure.targetPosition().distanceToSqr(target.position()) < 4.0
                        && failure.guardPosition().distanceToSqr(guard.position()) < 64.0);
    }

    public static void remember(VillagerEntityMCA guard, LivingEntity target, long now) {
        List<Failure> failures = new ArrayList<>();
        failures.add(new Failure(target.getUUID(), target.position(), guard.position(), now + RETRY_TICKS));
        guard.getBrain().getMemoryInternal(MemoryModuleTypeMCA.UNREACHABLE_COMBAT_TARGETS).orElse(List.of()).stream()
                .filter(failure -> failure.retryAt() > now && !failure.target().equals(target.getUUID()))
                .limit(MAX_ENTRIES - 1).forEach(failures::add);
        guard.getBrain().setMemoryWithExpiry(MemoryModuleTypeMCA.UNREACHABLE_COMBAT_TARGETS, List.copyOf(failures), RETRY_TICKS);
    }
}
