package net.conczin.mca.client.gui;

import java.util.UUID;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class LimitedVillagerEditorScreen extends VillagerEditorScreen {
   public LimitedVillagerEditorScreen(UUID villagerUUID, UUID playerUUID) {
      super(villagerUUID, playerUUID);
   }

   @Override
   protected boolean shouldShowPageSelection() {
      return false;
   }

   @Override
   protected boolean shouldUsePlayerModel() {
      return this.villagerData.getInt("PlayerModel").orElse(VillagerLike.PlayerModel.PLAYER.ordinal()) != VillagerLike.PlayerModel.VILLAGER.ordinal();
   }

   @Override
   protected boolean shouldPrintPlayerHint() {
      return false;
   }

   @Override
   protected void setPage(String page) {
      this.page = page;
      if (page.equals("general")) {
         int y = this.height / 2 - 40;
         this.drawName(this.width / 2, y);
         y += 24;
         if (this.villagerUUID.equals(this.playerUUID)) {
            this.addModelSelectionWidgets(this.width / 2, y);
         }
      }
   }

   @Override
   public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
      super.render(context, mouseX, mouseY, delta);
      int y = this.height / 2 + 20;

      for (Component text : FlowingText.wrap(Component.translatable("gui.villager_editor.customization_hint"), 175)) {
         context.drawCenteredString(this.font, text, this.width / 2 + 87, y, -1);
         y += 10;
      }
   }
}
