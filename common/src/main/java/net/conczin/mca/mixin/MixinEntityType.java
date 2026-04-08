package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(EntityType.class)
public class MixinEntityType {
    @SuppressWarnings("ConstantConditions")
    @Inject(method = "getTags()Ljava/util/stream/Stream;", at = @At("RETURN"), cancellable = true, require = 0)
    private void mca$injectGetTags(CallbackInfoReturnable<Stream<TagKey<EntityType<?>>>> cir) {
        if (mca$isCustomVillagerType()) {
            cir.setReturnValue(Stream.concat(cir.getReturnValue(), EntityType.VILLAGER.builtInRegistryHolder().tags()).distinct());
        }
    }

    @SuppressWarnings("ConstantConditions")
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void mca$injectIs(TagKey<EntityType<?>> tag, CallbackInfoReturnable<Boolean> cir) {
        if (mca$isCustomVillagerType() && EntityType.VILLAGER.builtInRegistryHolder().is(tag)) {
            cir.setReturnValue(true);
        }
    }

    private boolean mca$isCustomVillagerType() {
        if (!Config.getInstance().villagerTagsHacks) {
            return false;
        }
        return (Object) this == EntitiesMCA.MALE_VILLAGER || (Object) this == EntitiesMCA.FEMALE_VILLAGER;
    }
}
