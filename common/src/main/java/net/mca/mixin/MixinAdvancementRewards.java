package net.mca.mixin;

import net.mca.Config;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

@Mixin(AdvancementRewards.class)
public abstract class MixinAdvancementRewards {
    @Unique
    private static final ThreadLocal<Identifier[]> mca$savedLoot = new ThreadLocal<>();

    @Inject(method = "apply", at = @At("HEAD"))
    private void mca$filterBooksStart(ServerPlayerEntity player, CallbackInfo ci) {
        if (!Config.getInstance().giveAdvancementBooks) {
            Identifier[] loot = ((MixinAdvancementRewardsAccessor) this).getLoot();
            if (loot != null && Arrays.stream(loot).anyMatch(id -> id.toString().startsWith("mca:books/"))) {
                mca$savedLoot.set(loot);
                Identifier[] filtered = Arrays.stream(loot)
                        .filter(id -> !id.toString().startsWith("mca:books/"))
                        .toArray(Identifier[]::new);
                ((MixinAdvancementRewardsAccessor) this).setLoot(filtered);
            }
        }
    }

    @Inject(method = "apply", at = @At("RETURN"))
    private void mca$filterBooksEnd(ServerPlayerEntity player, CallbackInfo ci) {
        Identifier[] saved = mca$savedLoot.get();
        if (saved != null) {
            mca$savedLoot.remove();
            ((MixinAdvancementRewardsAccessor) this).setLoot(saved);
        }
    }
}
