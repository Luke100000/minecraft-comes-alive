package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.minecraft.core.TypedInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

@Mixin(TypedInstance.class)
public interface MixinTypedInstance {
    @SuppressWarnings({"ConstantConditions", "deprecation"})
    @Inject(method = "tags()Ljava/util/stream/Stream;", at = @At("RETURN"), cancellable = true)
    private void mca$injectTags(CallbackInfoReturnable<Stream<TagKey<?>>> cir) {
        EntityType<?> type = mca$asEntityType();
        if (type == null || !mca$isCustomVillagerType(type)) {
            return;
        }

        cir.setReturnValue(Stream.concat(cir.getReturnValue(), EntityType.VILLAGER.builtInRegistryHolder().tags()).distinct());
    }

    @SuppressWarnings({"ConstantConditions", "deprecation", "unchecked"})
    @Inject(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$injectIs(TagKey<?> tag, CallbackInfoReturnable<Boolean> cir) {
        EntityType<?> type = mca$asEntityType();
        if (type == null) {
            return;
        }

        if (!EntityType.VILLAGER.builtInRegistryHolder().is((TagKey<EntityType<?>>) tag)) {
            return;
        }

        if (mca$isCustomVillagerType(type)) {
            cir.setReturnValue(true);
        }
    }

    @Unique
    private EntityType<?> mca$asEntityType() {
        Object self = this;
        return self instanceof EntityType<?> type ? type : null;
    }

    @Unique
    private boolean mca$isCustomVillagerType(EntityType<?> type) {
        if (!Config.getInstance().villagerTagsHacks) {
            return false;
        }

        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        return id != null
                && "mca".equals(id.getNamespace())
                && ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()));
    }
}
