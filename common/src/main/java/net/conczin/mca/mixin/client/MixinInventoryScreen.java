package net.conczin.mca.mixin.client;

import net.conczin.mca.client.gui.PlayerRingSlotOverlay;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.PlayerRingSlotRequest;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen {
   @Unique
   private int mca$lastMouseX;
   @Unique
   private int mca$lastMouseY;

   @Inject(method = "renderBg(Lnet/minecraft/client/gui/GuiGraphics;FII)V", at = @At("TAIL"))
   private void mca$renderRingSlot(GuiGraphics context, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
      this.mca$lastMouseX = mouseX;
      this.mca$lastMouseY = mouseY;
      PlayerRingSlotOverlay.render(context, mouseX, mouseY, this.mca$slotX(), this.mca$slotY());
   }

   @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"))
   private void mca$renderRingSlotTooltip(GuiGraphics context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      PlayerRingSlotOverlay.renderTooltip(context, mouseX, mouseY, this.mca$slotX(), this.mca$slotY());
   }

   @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"), cancellable = true)
   private void mca$handleRingSlotClick(MouseButtonEvent mouseButtonEvent, CallbackInfoReturnable<Boolean> cir) {
      if (mouseButtonEvent.button() == 0 && PlayerRingSlotOverlay.isHovering(this.mca$lastMouseX, this.mca$lastMouseY, this.mca$slotX(), this.mca$slotY())) {
         Network.sendToServer(new PlayerRingSlotRequest());
         cir.setReturnValue(true);
      }
   }

   @Unique
   private int mca$slotX() {
      return ((MixinAbstractContainerScreenAccessor)this).mca$getLeftPos() + 77;
   }

   @Unique
   private int mca$slotY() {
      return ((MixinAbstractContainerScreenAccessor)this).mca$getTopPos() + 44;
   }
}
