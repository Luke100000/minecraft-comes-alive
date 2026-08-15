package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(SleepInBed.class)
abstract class MixinSleepInBed {
    @Inject(method = "checkExtraStartConditions", at = @At("RETURN"), cancellable = true)
    private void mca$allowBehindFootApproach(
            ServerLevel world,
            LivingEntity entity,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValue() || !(entity instanceof VillagerEntityMCA) || entity.isPassenger()) {
            return;
        }

        Brain<?> brain = entity.getBrain();
        Optional<GlobalPos> homeMemory = brain.getMemoryInternal(MemoryModuleType.HOME);
        if (homeMemory.isEmpty()) {
            return;
        }

        GlobalPos home = homeMemory.get();
        if (world.dimension() != home.dimension()) {
            return;
        }

        Optional<Long> lastWoken = brain.getMemoryInternal(MemoryModuleType.LAST_WOKEN);
        if (lastWoken.isPresent()) {
            long sinceWoken = world.getGameTime() - lastWoken.get();
            if (sinceWoken > 0L && sinceWoken < 100L) {
                return;
            }
        }

        BlockPos bedPos = home.pos();
        BlockState bedState = world.getBlockState(bedPos);
        if (!bedState.is(BlockTags.BEDS)
                || !bedState.hasProperty(BedBlock.FACING)
                || !bedState.hasProperty(BedBlock.PART)
                || !bedState.hasProperty(BedBlock.OCCUPIED)
                || bedState.getValue(BedBlock.PART) != BedPart.HEAD
                || bedState.getValue(BedBlock.OCCUPIED)) {
            return;
        }

        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos behindFoot = bedPos.relative(facing.getOpposite(), 2);
        if (entity.blockPosition().equals(behindFoot)) {
            cir.setReturnValue(true);
        }
    }
}
