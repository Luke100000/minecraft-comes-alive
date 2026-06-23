package net.conczin.mca.util.compat;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class ButtonWidget extends net.minecraft.client.gui.components.Button {
    /**
     * Creates a 1.19.2 and lower button implementation.
     *
     * @since MC 1.19.3
     */
    public ButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public ButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress, Component tooltip) {
        this(x, y, width, height, message, onPress);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.extractDefaultSprite(graphics);
        
        net.minecraft.client.gui.Font font = net.minecraft.client.Minecraft.getInstance().font;
        Component message = this.getMessage();
        int lineWidth = font.width(message);
        int availableWidth = this.getWidth() - 4;
        
        if (lineWidth > availableWidth && availableWidth > 0) {
            float scale = (float) availableWidth / lineWidth;
            float cx = this.getX() + this.getWidth() / 2.0f;
            float cy = this.getY() + this.getHeight() / 2.0f;
            
            org.joml.Matrix3x2fStack poseStack = graphics.pose();
            poseStack.pushMatrix();
            poseStack.translate(cx, cy);
            poseStack.scale(scale, scale);
            poseStack.translate(-cx, -cy);
            
            ActiveTextCollector textCollector = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            int textTop = this.getY() + (this.getHeight() - 9) / 2 + 1;
            int textColor = this.active ? 0xFFFFFFFF : 0xFFA0A0A0;
            Component coloredMessage = message.copy().withStyle(style -> style.withColor(textColor));
            
            textCollector.accept(net.minecraft.client.gui.TextAlignment.CENTER, this.getX() + this.getWidth() / 2, textTop, coloredMessage);
            
            poseStack.popMatrix();
        } else {
            ActiveTextCollector textCollector = graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE);
            this.extractDefaultLabel(textCollector);
        }
    }
}
