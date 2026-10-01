package net.conczin.mca.mixin;

import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.ExpirableValue;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Brain.class)
abstract class MixinBrain {
    @Inject(method = "setMemoryInternal", at = @At("HEAD"))
    private <U> void mca$rememberWalkTargetProducer(
            MemoryModuleType<U> memoryType,
            Optional<? extends ExpirableValue<?>> value,
            CallbackInfo ci
    ) {
        if (memoryType == MemoryModuleType.WALK_TARGET
                && value.isPresent()
                && value.get().getValue() instanceof WalkTarget walkTarget) {
            PathRequestDiagnostics.recordWalkTargetPublication(walkTarget);
        }
    }
}
