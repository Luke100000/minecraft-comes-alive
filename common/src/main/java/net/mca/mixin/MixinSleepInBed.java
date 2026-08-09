package net.mca.mixin;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.SleepTask;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SleepTask.class)
abstract class MixinSleepInBed {
    @Inject(method = "shouldRun", at = @At("RETURN"), cancellable = true)
    private void mca$allowBehindFootApproach(
            ServerWorld world,
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue() || !(entity instanceof VillagerEntityMCA) || entity.hasVehicle()) {
            return;
        }

        Brain<?> brain = entity.getBrain();
        Optional<GlobalPos> homeMemory = brain.getOptionalMemory(MemoryModuleType.HOME);
        if (homeMemory.isEmpty()) {
            return;
        }

        GlobalPos home = homeMemory.get();
        if (world.getRegistryKey() != home.getDimension()) {
            return;
        }

        Optional<Long> lastWoken = brain.getOptionalMemory(MemoryModuleType.LAST_WOKEN);
        if (lastWoken.isPresent()) {
            long sinceWoken = world.getTime() - lastWoken.get();
            if (sinceWoken > 0L && sinceWoken < 100L) {
                return;
            }
        }

        BlockPos bedPos = home.getPos();
        BlockState bedState = world.getBlockState(bedPos);
        if (!bedState.isIn(BlockTags.BEDS)
                || !bedState.contains(BedBlock.FACING)
                || !bedState.contains(BedBlock.PART)
                || !bedState.contains(BedBlock.OCCUPIED)
                || bedState.get(BedBlock.PART) != BedPart.HEAD
                || bedState.get(BedBlock.OCCUPIED)) {
            return;
        }

        Direction facing = bedState.get(BedBlock.FACING);
        BlockPos behindFoot = bedPos.offset(facing.getOpposite(), 2);
        if (entity.getBlockPos().equals(behindFoot)) {
            cir.setReturnValue(true);
        }
    }
}
