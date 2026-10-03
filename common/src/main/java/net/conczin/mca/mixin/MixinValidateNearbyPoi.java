package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Predicate;

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

    @WrapOperation(
            // Lambda names differ between Fabric's original bytecode and NeoForge's recompiled sources.
            method = "*",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/village/poi/PoiManager;release(Lnet/minecraft/core/BlockPos;)Z"
            ),
            require = 1,
            allow = 1
    )
    private static boolean mca$keepSleepingHomeClaim(
            PoiManager poiManager,
            BlockPos pos,
            Operation<Boolean> original,
            @Local(argsOnly = true) Predicate<Holder<PoiType>> poiValidator,
            @Local(argsOnly = true) ServerLevel level
    ) {
        BlockState state = level.getBlockState(pos);
        boolean validatingHome = BedPoiCompatibility.isHomePoiState(state)
                && poiManager.exists(pos, poi -> poi.is(PoiTypes.HOME) && poiValidator.test(poi));
        if (validatingHome
                && state.getValue(BedBlock.OCCUPIED)
                && !level.getEntitiesOfClass(Villager.class, new AABB(pos), LivingEntity::isSleeping).isEmpty()) {
            return false;
        }
        return original.call(poiManager, pos);
    }
}
