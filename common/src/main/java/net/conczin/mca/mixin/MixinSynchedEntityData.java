package net.conczin.mca.mixin;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SynchedEntityData.class)
public class MixinSynchedEntityData {
    @Redirect(
            method = "defineId",
            at = @At(value = "INVOKE", target = "Ljava/lang/Class;equals(Ljava/lang/Object;)Z"),
            require = 0
    )
    private static boolean mca$bypassDefineIdWarning(Class<?> oclass, Object clazz) {
        if (oclass.getName().startsWith("net.conczin.mca.")) {
            return true;
        }
        return oclass.equals(clazz);
    }
}
