package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class MixinIronGolem extends LivingEntity {
    @Shadow
    public abstract boolean isPlayerCreated();

    protected MixinIronGolem(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
    }

    @Override
    public boolean canAttack(LivingEntity target) {
        if (target instanceof VillagerEntityMCA villager && !villager.isHostile()) {
            return false;
        }
        return super.canAttack(target);
    }

    @WrapOperation(
            // Both the collision check and the hostile-mob target predicate.
            // The predicate's synthetic method name differs between loaders.
            method = "*",
            constant = @Constant(classValue = Enemy.class),
            require = 2,
            allow = 2
    )
    private static boolean mca$includeOutlawEnemies(Object entity, Operation<Boolean> original) {
        return original.call(entity) || entity instanceof VillagerEntityMCA villager && villager.isHostile();
    }

    @Inject(method = "hurt", at = @At("RETURN"))
    private void mca$alertGuards(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() && !isPlayerCreated() && source.getEntity() instanceof LivingEntity attacker) {
            VillagerEntityMCA.notifyNearbyGuards(this, attacker);
        }
    }
}
