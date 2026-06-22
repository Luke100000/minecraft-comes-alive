package net.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.Config;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.AdvancementRewards;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

@Mixin(PlayerAdvancementTracker.class)
public abstract class MixinPlayerAdvancementTracker {
    @Shadow
    @Final
    private ServerPlayerEntity owner;

    @WrapOperation(
            method = "grantCriterion",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/advancement/AdvancementRewards;apply(Lnet/minecraft/server/network/ServerPlayerEntity;)V"
            )
    )
    private void mca$onApplyRewards(AdvancementRewards rewards, ServerPlayerEntity player, Operation<Void> original, Advancement advancement, String criterionName) {
        if (!Config.getInstance().giveAdvancementBooks) {
            MixinAdvancementRewardsAccessor accessor = (MixinAdvancementRewardsAccessor) rewards;
            Identifier[] originalLoot = accessor.getLoot();
            if (originalLoot != null && Arrays.stream(originalLoot).anyMatch(id -> id.toString().startsWith("mca:books/"))) {
                Identifier[] filteredLoot = Arrays.stream(originalLoot)
                        .filter(id -> !id.toString().startsWith("mca:books/"))
                        .toArray(Identifier[]::new);
                
                accessor.setLoot(filteredLoot);
                original.call(rewards, player);
                accessor.setLoot(originalLoot);
                return;
            }
        }
        original.call(rewards, player);
    }
}
