package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class MixinEntityType {
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$injectIs(TagKey<EntityType<?>> tag, CallbackInfoReturnable<Boolean> cir) {
        if (Config.getInstance().villagerTagsHacks) {
            if ((Object) this == EntitiesMCA.MALE_VILLAGER || (Object) this == EntitiesMCA.FEMALE_VILLAGER) {
                if (EntityType.VILLAGER.is(tag)) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
}
