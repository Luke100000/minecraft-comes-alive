package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.conczin.mca.Config;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityType.class)
public class MixinEntityType {
	@SuppressWarnings({"ConstantConditions", "deprecation"})
	@ModifyReturnValue(method = "is", at = @At("RETURN"))
	private boolean mca$includeMcaVillagersInVillagerTags(boolean original, TagKey<EntityType<?>> tag) {
		if (original || !Config.getInstance().villagerTagsHacks || !RegistryHelper.isObjectInTag(BuiltInRegistries.ENTITY_TYPE, tag, EntityType.VILLAGER)) {
			return original;
		}

		ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey((EntityType<?>) (Object) this);
		return id != null
				&& "mca".equals(id.getNamespace())
				&& ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()));
	}
}
