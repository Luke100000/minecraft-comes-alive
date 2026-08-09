package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.mca.Config;
import net.mca.util.RegistryHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityType.class)
public class MixinEntityType {
	@SuppressWarnings({"ConstantConditions", "deprecation"})
	@ModifyReturnValue(method = "isIn", at = @At("RETURN"))
	private boolean mca$includeMcaVillagersInVillagerTags(boolean original, TagKey<EntityType<?>> tag) {
		if (original || !Config.getInstance().villagerTagsHacks || !RegistryHelper.isObjectInTag(Registries.ENTITY_TYPE, tag, EntityType.VILLAGER)) {
			return original;
		}

		Identifier id = Registries.ENTITY_TYPE.getId((EntityType<?>) (Object) this);
		return id != null
				&& "mca".equals(id.getNamespace())
				&& ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()));
	}
}
