package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class MixinEntity {

    @Redirect(
            method = "startRiding(Lnet/minecraft/world/entity/Entity;ZZ)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/EntityType;canSerialize()Z")
    )
    private boolean mca$allowCarriedVillagersToRidePlayers(EntityType<?> type, Entity entityToRide, boolean force, boolean sendEventAndTriggers) {
        // Players are non-serializable entities, but MCA uses them as temporary carry vehicles for children.
        if (force && entityToRide instanceof Player && (Object) this instanceof VillagerEntityMCA villager) {
            AgeState ageState = villager.getAgeState();
            if (ageState == AgeState.BABY || ageState == AgeState.TODDLER || ageState == AgeState.CHILD) {
                return true;
            }
        }

        return type.canSerialize();
    }
}
