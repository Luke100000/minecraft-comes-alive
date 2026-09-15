package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.Config;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AdvancementRewards.class)
public class MixinAdvancementRewards {
    @ModifyExpressionValue(
            method = "grant",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;loot:Lnet/minecraft/core/HolderSet;"
            )
    )
    private HolderSet<LootTable> mca$filterAdvancementBooks(HolderSet<LootTable> original) {
        if (Config.getInstance().giveAdvancementBooks) {
            return original;
        }

        return HolderSet.direct(original.stream()
                .filter(holder -> holder.unwrapKey()
                        .map(key -> !key.identifier().getNamespace().equals("mca")
                                || !key.identifier().getPath().startsWith("books/"))
                        .orElse(true))
                .toList());
    }
}
