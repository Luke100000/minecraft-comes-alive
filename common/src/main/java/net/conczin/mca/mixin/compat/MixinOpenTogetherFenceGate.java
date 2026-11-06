package net.conczin.mca.mixin.compat;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Keep linked gates from copying geometry that depends on their own wall neighbours. */
@Pseudo
@Mixin(targets = "fuzs.opentogether.common.world.level.block.DoubleFenceGateLogic", remap = false)
abstract class MixinOpenTogetherFenceGate {
    @ModifyReturnValue(method = "copyBlockState", at = @At("RETURN"), require = 0, remap = false)
    private BlockState mca$keepLocalWallConnection(BlockState result, BlockState state, BlockState neighbour) {
        // Copying IN_WALL between an end gate and a middle gate triggers corrective
        // shape updates, which can propagate an old OPEN value back into the row.
        return result.setValue(FenceGateBlock.IN_WALL, state.getValue(FenceGateBlock.IN_WALL));
    }
}
