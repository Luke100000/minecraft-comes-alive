package net.mca.mixin;

import net.mca.Config;
import net.mca.entity.EntitiesMCA;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class MixinEntityType {
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "is", at = @At("HEAD"), cancellable = true)
    private void mca$injectIs(TagKey<EntityType<?>> tag, CallbackInfoReturnable<Boolean> cir) {
        if (Config.getInstance().villagerTagsHacks) {
            if (EntitiesMCA.MALE_VILLAGER.isPresent() && EntitiesMCA.FEMALE_VILLAGER.isPresent()) {
                if ((Object) this == EntitiesMCA.MALE_VILLAGER.get() || (Object) this == EntitiesMCA.FEMALE_VILLAGER.get()) {
                    if (EntityType.VILLAGER.is(tag)) {
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }
}
