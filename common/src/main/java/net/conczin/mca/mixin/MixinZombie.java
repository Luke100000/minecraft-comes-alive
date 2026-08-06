package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(Zombie.class)
public abstract class MixinZombie {
    @WrapOperation(
            method = "killedEntity(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;)Z",
            constant = @Constant(classValue = Villager.class, ordinal = 0)
    )
    private boolean mca$excludeMcaVillagersFromVanillaConversion(
            Object entity,
            Operation<Boolean> original
    ) {
        return original.call(entity) && !(entity instanceof VillagerEntityMCA);
    }
}
