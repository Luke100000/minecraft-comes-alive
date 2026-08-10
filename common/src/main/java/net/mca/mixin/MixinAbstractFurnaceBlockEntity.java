package net.mca.mixin;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.mca.MCA;
import net.mca.advancement.criterion.CriterionMCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
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
    private Object2IntOpenHashMap<ResourceLocation> recipesUsed;

    @Inject(method = "awardUsedRecipesAndPopExperience", at = @At("HEAD"))
    public void onDropExperience(ServerPlayer player, CallbackInfo ci) {
        recipesUsed.forEach((identifier, count) -> {
            if (identifier.getNamespace().equals(MCA.MOD_ID)) {
                boolean isBaby = identifier.equals(MCA.locate("baby_boy_from_smelting"));
                boolean isSirbenBaby = identifier.equals(MCA.locate("baby_sirben_boy_from_smelting"));
                if (isBaby || isSirbenBaby) {
                    CriterionMCA.BABY_SMELTED_CRITERION.trigger(player, count);
                    if (isSirbenBaby) {
                        CriterionMCA.BABY_SIRBEN_SMELTED_CRITERION.trigger(player, count);
                    }
                }
            }
        });
    }
}
