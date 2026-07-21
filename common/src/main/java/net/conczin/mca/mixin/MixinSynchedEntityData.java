package net.conczin.mca.mixin;

import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SynchedEntityData.class)
public class MixinSynchedEntityData {
    /**
     * Suppresses vanilla's false-positive tracked-data warning for MCA's
     * CParameter abstraction.
     */
    @Redirect(
            method = "defineId",
            at = @At(value = "INVOKE", target = "Ljava/lang/Object;equals(Ljava/lang/Object;)Z"),
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
