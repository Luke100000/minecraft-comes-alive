package net.conczin.mca.client.gui.widget;

import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ClothingLockButtonWidget extends ButtonWidget {
    public static final int SIZE = 20;

    private static final ResourceLocation LOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/widget/locked_button.png");
    private static final ResourceLocation LOCKED_HIGHLIGHTED_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/widget/locked_button_highlighted.png");
    private static final ResourceLocation UNLOCKED_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/widget/unlocked_button.png");
    private static final ResourceLocation UNLOCKED_HIGHLIGHTED_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/sprites/widget/unlocked_button_highlighted.png");

    private final boolean locked;

    public ClothingLockButtonWidget(int x, int y, boolean locked, Component tooltip, OnPress onPress) {
        super(x, y, SIZE, SIZE, Component.empty(), onPress, tooltip);
        this.locked = locked;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        ResourceLocation texture = locked
                ? hovered ? LOCKED_HIGHLIGHTED_TEXTURE : LOCKED_TEXTURE
                : hovered ? UNLOCKED_HIGHLIGHTED_TEXTURE : UNLOCKED_TEXTURE;
        context.blit(texture, getX(), getY(), 0, 0, width, height, SIZE, SIZE);
    }
}
