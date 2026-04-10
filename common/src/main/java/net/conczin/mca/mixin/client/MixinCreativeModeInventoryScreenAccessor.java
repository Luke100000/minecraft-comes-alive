package net.conczin.mca.mixin.client;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CreativeModeInventoryScreen.class)
public interface MixinCreativeModeInventoryScreenAccessor {
    @Invoker("isInventoryOpen")
    boolean mca$invokeIsInventoryOpen();
}
