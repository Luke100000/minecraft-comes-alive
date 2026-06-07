package net.conczin.mca.client.gui;

import java.util.List;
import net.conczin.mca.MCAClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public final class PlayerRingSlotOverlay {
   public static final int SLOT_SIZE = 18;
   public static final int SLOT_X_OFFSET = 77;
   public static final int SLOT_Y_OFFSET = 38;

   private PlayerRingSlotOverlay() {
   }

   public static void render(GuiGraphicsExtractor context, int mouseX, int mouseY, int slotX, int slotY) {
      boolean hovering = isHovering(mouseX, mouseY, slotX, slotY);
      context.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, hovering ? -788529153 : -1604296608);
      context.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, hovering ? 1882206256 : 1343756312);
      Minecraft minecraft = Minecraft.getInstance();
      if (minecraft.player != null) {
         ItemStack ring = MCAClient.getEquippedRing(minecraft.player.getUUID()).orElse(ItemStack.EMPTY);
         if (ring.isEmpty()) {
            context.text(minecraft.font, "R", slotX + 6, slotY + 5, hovering ? -1 : -1329018680, false);
         } else {
            context.item(ring, slotX + 1, slotY + 1);
            context.itemDecorations(minecraft.font, ring, slotX + 1, slotY + 1);
         }
      }
   }

   public static void renderTooltip(GuiGraphicsExtractor context, int mouseX, int mouseY, int slotX, int slotY) {
      if (isHovering(mouseX, mouseY, slotX, slotY)) {
         context.setComponentTooltipForNextFrame(
            Minecraft.getInstance().font,
            List.of(Component.translatable("gui.player_ring_slot"), Component.translatable("gui.player_ring_slot.tooltip")),
            mouseX,
            mouseY
         );
      }
   }

   public static boolean isHovering(double mouseX, double mouseY, int slotX, int slotY) {
      return mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE;
   }
}
