package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ValidateNearbyPoi.class)
abstract class MixinValidateNearbyPoi {
    @ModifyExpressionValue(
            method = "bedIsOccupied",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;is(Lnet/minecraft/tags/TagKey;)Z"
            )
    )
    private static boolean mca$recognizeCompatibleMcaBed(
            boolean original,
            ServerLevel level,
            BlockPos pos,
            LivingEntity entity,
            @Local BlockState state
    ) {
        return original || entity instanceof VillagerEntityMCA && BedPoiCompatibility.isHomePoiState(state);
    }
}
