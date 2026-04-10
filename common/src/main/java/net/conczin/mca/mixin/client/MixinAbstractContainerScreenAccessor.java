package net.conczin.mca.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface MixinAbstractContainerScreenAccessor {
    @Accessor("leftPos")
    int mca$getLeftPos();

    @Accessor("topPos")
    int mca$getTopPos();
}
