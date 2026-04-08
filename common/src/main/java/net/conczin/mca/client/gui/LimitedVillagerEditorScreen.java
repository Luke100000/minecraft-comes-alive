package net.conczin.mca.client.gui;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.util.localization.FlowingText;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.UUID;

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
        return villagerData.getInt("PlayerModel").orElse(VillagerLike.PlayerModel.VILLAGER.ordinal()) != VillagerLike.PlayerModel.VILLAGER.ordinal();
    }

    @Override
    protected boolean shouldPrintPlayerHint() {
        return false;
    }

    @Override
    protected void setPage(String page) {
        this.page = page;

        if (page.equals("general")) {
            int y = height / 2 - 40;

            //name
            drawName(width / 2, y);
            y += 24;

            //which model to use
            if (villagerUUID.equals(playerUUID)) {
                addModelSelectionWidgets(width / 2, y);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);

        int y = height / 2 + 20;
        List<Component> wrap = FlowingText.wrap(Component.translatable("gui.villager_editor.customization_hint"), DATA_WIDTH);
        for (Component text : wrap) {
            context.centeredText(font, text, width / 2 + DATA_WIDTH / 2, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
