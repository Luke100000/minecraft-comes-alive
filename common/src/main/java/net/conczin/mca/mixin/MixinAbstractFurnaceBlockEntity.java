package net.conczin.mca.mixin;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.conczin.mca.MCA;
import net.conczin.mca.registry.CriterionMCA;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractFurnaceBlockEntity.class)
public class MixinAbstractFurnaceBlockEntity {
    @Final
    @Shadow
    private Reference2IntOpenHashMap<ResourceKey<Recipe<?>>> recipesUsed;

    @Inject(method = "awardUsedRecipesAndPopExperience", at = @At("HEAD"))
    public void mca$injectAwardUsedRecipesAndPopExperience(ServerPlayer player, CallbackInfo ci) {
        recipesUsed.forEach((recipeKey, count) -> {
            Identifier identifier = recipeKey.identifier();
            if (identifier.getNamespace().equals(MCA.MOD_ID)) {
                boolean isBaby = identifier.equals(MCA.locate("baby_boy_from_smelting"));
                boolean isSirbenBaby = identifier.equals(MCA.locate("baby_sirben_boy_from_smelting"));
                if (isBaby || isSirbenBaby) {
                    CriterionMCA.BABY_SMELTED.trigger(player, count);
                    if (isSirbenBaby) {
                        CriterionMCA.BABY_SIRBEN_SMELTED.trigger(player, count);
                    }
                }
            }
        });
    }
}
