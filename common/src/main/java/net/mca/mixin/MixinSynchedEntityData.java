package net.mca.mixin;

import net.minecraft.entity.data.DataTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses vanilla's false-positive tracked-data warning for MCA's own entity classes.
 * MCA intentionally allocates tracked data through its CDataParameter abstraction.
 */
@Mixin(DataTracker.class)
public class MixinSynchedEntityData {
    @Redirect(
            method = "registerData",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;equals(Ljava/lang/Object;)Z"),
            require = 0
    )
    private static boolean mca$bypassDefineIdWarning(Class<?> entityClass, Object callerClass) {
        if (entityClass.getName().startsWith("net.mca.")) {
            return true;
        }
        return entityClass.equals(callerClass);
    }
}
