package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PlayerRideableJumping;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractHorse.class)
abstract class MixinHorseBase extends Animal implements ContainerListener, PlayerRideableJumping {
    protected MixinHorseBase(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow
    @Nullable
    public abstract LivingEntity getControllingPassenger();

    @Inject(method = "isImmobile()Z", at = @At("HEAD"), cancellable = true)
    private void onIsImmobile(CallbackInfoReturnable<Boolean> info) {
        if (getControllingPassenger() instanceof VillagerEntityMCA) {
            info.setReturnValue(false); // Fixes villagers not being able to move when riding a horse
        }
    }
}
