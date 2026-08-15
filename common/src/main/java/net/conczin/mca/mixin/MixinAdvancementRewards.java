package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.Config;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(AdvancementRewards.class)
public abstract class MixinAdvancementRewards {
    @ModifyExpressionValue(
            method = "grant",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;loot:[Lnet/minecraft/resources/ResourceLocation;"
            )
    )
    private ResourceLocation[] mca$filterAdvancementBooks(ResourceLocation[] original) {
        if (Config.getInstance().giveAdvancementBooks || original == null) {
            return original;
        }

        return Arrays.stream(original)
                .filter(id -> !id.toString().startsWith("mca:books/"))
                .toArray(ResourceLocation[]::new);
    }
}
