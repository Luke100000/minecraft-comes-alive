package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
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
        if (!Config.getInstance().villagerTagsHacks || !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.VILLAGER).is(tag)) {
            return;
        }

        var id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        if (id != null
                && MCA.MOD_ID.equals(id.getNamespace())
                && ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()))) {
            cir.setReturnValue(true);
        }
    }
}
