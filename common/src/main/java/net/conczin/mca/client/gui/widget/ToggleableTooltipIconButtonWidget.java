package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import static net.conczin.mca.client.gui.InteractScreen.ICON_TEXTURES;

public class ToggleableTooltipIconButtonWidget extends ToggleableTooltipButtonWidget {
    private final int u;
    private final int v;

    public ToggleableTooltipIconButtonWidget(int x, int y, int u, int v, boolean toggle, MutableComponent tooltip, OnPress onPress) {
        super(x, y, 16, 16, toggle, Component.literal(""), tooltip, onPress);

        this.u = u;
        this.v = v;
    }

    @Override
    protected void renderContents(GuiGraphics context, int mouseX, int mouseY, float delta) {
        drawIcon(context);
    }

    private void drawIcon(GuiGraphics context) {
        int offset = this.toggle ? 0 : 16;
        context.blit(RenderPipelines.GUI_TEXTURED, ICON_TEXTURES, this.getX(), this.getY(), u, v + offset, this.width, this.height, 256, 256);
    }
}
