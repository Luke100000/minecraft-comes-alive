package net.mca.mixin;

import net.mca.server.world.data.VillageManager;
import net.minecraft.item.FlintAndSteelItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Inject(
            method = "useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;",
            at = @At("RETURN")
    )
    private void mca$trySpawnReaper(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
        if (context.getStack().getItem() instanceof FlintAndSteelItem
                && cir.getReturnValue().isAccepted()
                && context.getWorld() instanceof ServerWorld serverWorld) {
            VillageManager.get(serverWorld)
                    .getReaperSpawner()
                    .trySpawnReaper(serverWorld, context.getBlockPos());
        }
    }
}
