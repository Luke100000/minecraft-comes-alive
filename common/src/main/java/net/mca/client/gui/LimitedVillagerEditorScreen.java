package net.mca.client.gui;

import net.mca.entity.VillagerLike;
import net.mca.util.localization.FlowingText;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

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
        return villagerData.getInt("playerModel") != VillagerLike.PlayerModel.VILLAGER.ordinal();
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
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        int y = height / 2 + 20;
        List<Text> wrap = FlowingText.wrap(Text.translatable("gui.villager_editor.customization_hint"), DATA_WIDTH);
        for (Text text : wrap) {
            context.drawCenteredTextWithShadow(textRenderer, text, width / 2 + DATA_WIDTH / 2, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}
