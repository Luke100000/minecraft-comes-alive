package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityType.class)
public class MixinEntityType {
    @SuppressWarnings("ConstantConditions")
    @ModifyReturnValue(method = "is(Lnet/minecraft/tags/TagKey;)Z", at = @At("RETURN"))
    private boolean mca$includeMcaVillagersInVillagerTags(boolean original, TagKey<EntityType<?>> tag) {
        if (original
                || !Config.getInstance().villagerTagsHacks
                || !BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(EntityType.VILLAGER).is(tag)) {
            return original;
        }

        var id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
        return id != null
                && MCA.MOD_ID.equals(id.getNamespace())
                && ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()));
    }
}
