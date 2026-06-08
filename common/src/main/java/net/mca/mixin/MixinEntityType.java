package net.mca.mixin;

import net.mca.Config;
import net.mca.util.RegistryHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public class MixinEntityType {
	@SuppressWarnings({"ConstantConditions", "deprecation"})
	@Inject(method = "isIn", at = @At("HEAD"), cancellable = true)
	private void mca$injectIsIn(TagKey<EntityType<?>> tag, CallbackInfoReturnable<Boolean> cir) {
		if (!Config.getInstance().villagerTagsHacks || !RegistryHelper.isObjectInTag(Registries.ENTITY_TYPE, tag, EntityType.VILLAGER)) {
			return;
		}

		Identifier id = Registries.ENTITY_TYPE.getId((EntityType<?>) (Object) this);
		if (id != null
				&& "mca".equals(id.getNamespace())
				&& ("male_villager".equals(id.getPath()) || "female_villager".equals(id.getPath()))) {
			cir.setReturnValue(true);
		}
	}
}
