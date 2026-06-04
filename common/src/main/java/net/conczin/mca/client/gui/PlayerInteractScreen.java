package net.conczin.mca.client.gui;

import java.util.Objects;
import java.util.UUID;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.InteractionPlayerMessage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

public class PlayerInteractScreen extends AbstractDynamicScreen {
   private final UUID targetUUID;
   private final Component targetName;
   private int timeSinceLastClick;

   public PlayerInteractScreen(Player target) {
      super(Component.translatable("gui.player_interact.title"));
      this.targetUUID = target.getUUID();
      this.targetName = target.getDisplayName();
   }

   public boolean isPauseScreen() {
      return false;
   }

   public void init() {
      this.setLayout("player_interact");
      this.timeSinceLastClick = 3;
   }

   public void tick() {
      this.timeSinceLastClick++;
   }

   public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      context.drawString(this.font, this.title, 10, 10, -1);
      context.drawString(this.font, Component.translatable("gui.player_interact.target", new Object[]{this.targetName}), 10, 22, -3158065);
   }

   public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
      return super.mouseClicked(mouseButtonEvent, doubleClick);
   }

   public boolean keyPressed(KeyEvent keyEvent) {
      if (keyEvent.key() == 256) {
         this.onClose();
         return true;
      } else {
         return false;
      }
   }

   public void onClose() {
      Objects.requireNonNull(this.minecraft).setScreen(null);
   }

   @Override
   protected void buttonPressed(MCAButton button) {
      if (this.timeSinceLastClick > 2) {
         this.timeSinceLastClick = 0;
         String id = button.identifier();
         switch (id) {
            case "gui.button.backarrow":
               this.onClose();
               break;
            case "gui.button.propose":
               Network.sendToServer(new InteractionPlayerMessage("propose", this.targetUUID));
               break;
            case "gui.button.engagement_ring":
               Network.sendToServer(new InteractionPlayerMessage("engage", this.targetUUID));
               break;
            case "gui.button.hug":
               Network.sendToServer(new InteractionPlayerMessage("hug", this.targetUUID));
               break;
            case "gui.button.kiss":
               Network.sendToServer(new InteractionPlayerMessage("kiss", this.targetUUID));
               break;
            case "gui.button.wedding_ring":
               Network.sendToServer(new InteractionPlayerMessage("marry", this.targetUUID));
               break;
            case "gui.button.procreate":
               Network.sendToServer(new InteractionPlayerMessage("procreate", this.targetUUID));
         }
      }
   }
}
