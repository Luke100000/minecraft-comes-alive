package net.mca.mixin;

import net.mca.util.network.datasync.CParameter;
import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses vanilla's false-positive tracked-data warning for MCA's
 * CParameter abstraction.
 */
@Mixin(DataTracker.class)
public class MixinSynchedEntityData {
    @Redirect(
            method = "registerData",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"
            ),
            require = 0
    )
    private static boolean mca$bypassDefineIdWarning(Object callerClass, Object entityClass) {
        if (callerClass instanceof Class<?> clazz
                && CParameter.class.isAssignableFrom(clazz)) {
            return true;
        }

        return callerClass.equals(entityClass);
    }
}