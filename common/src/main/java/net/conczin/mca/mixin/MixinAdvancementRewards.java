package net.conczin.mca.mixin;

import net.conczin.mca.Config;
import net.minecraft.advancements.AdvancementRewards;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(AdvancementRewards.class)
public class MixinAdvancementRewards {
    private final ThreadLocal<List<ResourceKey<LootTable>>> mca$loot = new ThreadLocal<>();

    @Inject(method = "grant", at = @At("HEAD"))
    private void mca$removeAdvancementBooks(ServerPlayer player, CallbackInfo ci) {
        if (Config.getInstance().giveAdvancementBooks) {
            return;
        }

        MixinAdvancementRewardsAccessor accessor = (MixinAdvancementRewardsAccessor)this;
        mca$loot.set(accessor.mca$getLoot());
        accessor.mca$setLoot(accessor.mca$getLoot().stream()
                .filter(key -> !key.identifier().getNamespace().equals("mca") || !key.identifier().getPath().startsWith("books/"))
                .toList());
    }

    @Inject(method = "grant", at = @At("RETURN"))
    private void mca$restoreAdvancementBooks(ServerPlayer player, CallbackInfo ci) {
        List<ResourceKey<LootTable>> loot = mca$loot.get();
        if (loot != null) {
            ((MixinAdvancementRewardsAccessor)this).mca$setLoot(loot);
            mca$loot.remove();
        }
    }
}
