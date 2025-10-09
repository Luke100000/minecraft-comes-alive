package net.mca.mixin;

import net.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlintAndSteelItem.class)
public class MixinFlintAndSteelItem {
    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void mca$injectUseOn(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (cir.getReturnValue().consumesAction() && context.getLevel() instanceof ServerLevel serverWorld) {
            serverWorld.getServer().execute(() ->
                    VillageManager.get(serverWorld).getReaperSpawner().trySpawnReaper(serverWorld, context.getClickedPos())
            );
        }
    }
}
