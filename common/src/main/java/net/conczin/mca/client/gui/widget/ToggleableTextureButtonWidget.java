package net.conczin.mca.client.gui.widget;

import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ToggleableTextureButtonWidget extends ButtonWidget {
    private static final float SPRITE_ASPECT = 28.0F / 20.0F;

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

        float availW = this.width - 4;
        float availH = this.height - 4;
        float destWidth, destHeight;
        if (availW / availH > SPRITE_ASPECT) {
            destHeight = availH;
            destWidth = availH * SPRITE_ASPECT;
        } else {
            destWidth = availW;
            destHeight = availW / SPRITE_ASPECT;
        }

        float x0 = this.getX() + (this.width - destWidth) / 2.0f;
        float x1 = x0 + destWidth;
        float y0 = this.getY() + (this.height - destHeight) / 2.0f;
        float y1 = y0 + destHeight;

        float v0 = toggle ? 0.0f : 0.5f;
        float v1 = v0 + 0.5f;

        WidgetUtils.drawTexturedQuad(context, texture, x0, x1, y0, y1, 0.0f, 1.0f, v0, v1);
    }
}
