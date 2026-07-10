package net.conczin.mca.mixin;

import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
public abstract class MixinItemStack {
    @Unique
    private boolean mca$wasFlintAndSteel;

    @Inject(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("HEAD")
    )
    private void mca$captureUsedItem(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        mca$wasFlintAndSteel = context.getItemInHand().getItem() instanceof FlintAndSteelItem;
    }

    @Inject(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("RETURN")
    )
    private void mca$trySpawnReaper(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        if (mca$wasFlintAndSteel
                && cir.getReturnValue().consumesAction()
                && context.getLevel() instanceof ServerLevel level) {
            VillageManager.get(level)
                    .getReaperSpawner()
                    .trySpawnReaper(level, context.getClickedPos());
        }

        mca$wasFlintAndSteel = false;
    }
}
