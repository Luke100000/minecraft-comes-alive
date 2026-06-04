package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class MixinEntityType {
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$injectIs(TagKey<EntityType<?>> tag, CallbackInfoReturnable<Boolean> cir) {
        if (!EntityType.VILLAGER.builtInRegistryHolder().is(tag)) {
            return;
        }

        if (mca$isCustomVillagerType()) {
            cir.setReturnValue(true);
        }
    }

    private boolean mca$isCustomVillagerType() {
        if (!Config.getInstance().villagerTagsHacks) {
            return false;
        }

        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        return id != null
                && "mca".equals(id.getNamespace())
                && ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()));
    }
}
