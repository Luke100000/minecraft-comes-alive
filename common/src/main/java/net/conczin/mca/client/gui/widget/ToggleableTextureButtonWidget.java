package net.conczin.mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ToggleableTextureButtonWidget extends ButtonWidget {
    private static final float SPRITE_ASPECT = 28.0F / 20.0F;

    private final ResourceLocation texture;
    private final boolean toggle;

    public ToggleableTextureButtonWidget(int x, int y, int width, int height, ResourceLocation texture, boolean toggle, Component tooltip, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, tooltip);
        this.texture = texture;
        this.toggle = toggle;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        super.renderWidget(context, mouseX, mouseY, delta);

        float availW = this.width - 4;
        float availH = this.height - 4;
        float destWidth;
        float destHeight;
        if (availW / availH > SPRITE_ASPECT) {
            destHeight = availH;
            destWidth = availH * SPRITE_ASPECT;
        } else {
            destWidth = availW;
            destHeight = availW / SPRITE_ASPECT;
        }

        int x = Math.round(this.getX() + (this.width - destWidth) / 2.0F);
        int y = Math.round(this.getY() + (this.height - destHeight) / 2.0F);
        int width = Math.round(destWidth);
        int height = Math.round(destHeight);
        int v = toggle ? 0 : 20;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.blit(texture, x, y, width, height, 0.0F, (float) v, 28, 20, 28, 40);
    }
}
