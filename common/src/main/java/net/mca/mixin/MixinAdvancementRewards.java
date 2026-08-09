package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.mca.Config;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(AdvancementRewards.class)
public abstract class MixinAdvancementRewards {
    @ModifyExpressionValue(
            method = "apply",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/advancement/AdvancementRewards;loot:[Lnet/minecraft/util/Identifier;"
            )
    )
    private Identifier[] mca$filterAdvancementBooks(Identifier[] original) {
        if (Config.getInstance().giveAdvancementBooks || original == null) {
            return original;
        }

        return Arrays.stream(original)
                .filter(id -> !id.toString().startsWith("mca:books/"))
                .toArray(Identifier[]::new);
    }
}
