package net.conczin.mca.client.gui.widget;

import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ToggleableTextureButtonWidget extends ButtonWidget {
    private final Identifier texture;
    private final boolean toggle;

    public ToggleableTextureButtonWidget(int x, int y, int width, int height, Identifier texture, boolean toggle, Component tooltip, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, tooltip);
        this.texture = texture;
        this.toggle = toggle;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        this.extractDefaultSprite(context);
        int iconX = this.getX() + (this.width - 16) / 2;
        int iconY = this.getY() + (this.height - 16) / 2;
        context.blit(RenderPipelines.GUI_TEXTURED, texture, iconX, iconY, 0, toggle ? 0 : 16, 16, 16, 16, 32);
    }
}
