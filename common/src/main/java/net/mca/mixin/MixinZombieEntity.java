package net.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.passive.VillagerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(ZombieEntity.class)
public abstract class MixinZombieEntity {
    @WrapOperation(
            method = "onKilledOther(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/entity/LivingEntity;)Z",
            constant = @Constant(classValue = VillagerEntity.class, ordinal = 0)
    )
    private boolean mca$excludeMcaVillagersFromVanillaConversion(Object entity, Operation<Boolean> original) {
        return !(entity instanceof VillagerEntityMCA) && original.call(entity);
    }
}
