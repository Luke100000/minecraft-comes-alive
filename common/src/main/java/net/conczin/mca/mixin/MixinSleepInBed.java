package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SleepInBed.class)
abstract class MixinSleepInBed {
    @ModifyExpressionValue(
            method = "checkExtraStartConditions",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerToCenterThan(Lnet/minecraft/core/Position;D)Z"
            )
    )
    private boolean mca$allowBehindFootApproach(
            boolean original,
            ServerLevel world,
            LivingEntity entity
    ) {
        if (original || !(entity instanceof VillagerEntityMCA villager)) {
            return original;
        }

        BlockPos bedPos = villager.getBrain().getMemory(MemoryModuleType.HOME).orElseThrow().pos();
        return BedApproachTarget.create(world, bedPos)
                .map(target -> target.isAtBehindFootApproach(villager))
                .orElse(false);
    }
}
