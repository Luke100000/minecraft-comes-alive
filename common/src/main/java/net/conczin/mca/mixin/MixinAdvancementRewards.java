package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.Config;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(AdvancementRewards.class)
public class MixinAdvancementRewards {
    @ModifyExpressionValue(
            method = "grant",
            at = @At(
                    value = "FIELD",
                    target = "Lnet/minecraft/advancements/AdvancementRewards;loot:Ljava/util/List;"
            )
    )
    private List<ResourceKey<LootTable>> mca$filterAdvancementBooks(List<ResourceKey<LootTable>> original) {
        if (Config.getInstance().giveAdvancementBooks) {
            return original;
        }

        return original.stream()
                .filter(key -> !key.location().getNamespace().equals("mca")
                        || !key.location().getPath().startsWith("books/"))
                .toList();
    }
}
