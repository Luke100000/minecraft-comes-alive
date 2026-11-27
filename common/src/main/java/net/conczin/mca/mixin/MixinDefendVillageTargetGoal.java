package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.ai.goal.target.DefendVillageTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(DefendVillageTargetGoal.class)
abstract class MixinDefendVillageTargetGoal {
    @Unique
    private final TargetingConditions mca$villagerTargeting = TargetingConditions.forNonCombat()
            .range(64.0)
            .selector((target, level) -> VillagerEntityMCA.isVillageFriendly(target));

    @ModifyArg(
            method = "canUse",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getNearbyEntities(Ljava/lang/Class;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"),
            index = 1
    )
    private TargetingConditions mca$includeFriendlyVillagers(TargetingConditions original) {
        // Villagers are witnesses to the player's reputation, not combat targets.
        return mca$villagerTargeting;
    }
}
