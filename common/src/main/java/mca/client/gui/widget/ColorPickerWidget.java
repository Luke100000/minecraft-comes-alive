package mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class ColorPickerWidget extends ClickableWidget {
    @FunctionalInterface
    public interface DualConsumer<A, B> {
        void apply(A a, B b);
    }

    public static final Identifier MCA_GUI_ICONS_TEXTURE = new Identifier("mca:textures/gui.png");

    private final DualConsumer<Double, Double> consumer;
    private final Identifier texture;
    private double valueX;
    private double valueY;

    public ColorPickerWidget(int x, int y, int width, int height, double valueX, double valueY, Identifier texture, DualConsumer<Double, Double> consumer) {
        super(x, y, width, height, LiteralText.EMPTY);
        this.consumer = consumer;
        this.texture = texture;
        this.valueX = valueX / width;
        this.valueY = valueY / height;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(matrices, x, y, 0, 0, width, height, width, height);

        RenderSystem.setShaderTexture(0, MCA_GUI_ICONS_TEXTURE);
        DrawableHelper.drawTexture(matrices, (int)(x + valueX * width) - 8, (int)(y + valueY * height) - 8, 240, 0, 16, 16, 256, 256);

        RectangleWidget.drawRectangle(matrices, x, y, x + width, y + height, 0xaaffffff);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        update(mouseX, mouseY);
        super.onDrag(mouseX, mouseY, deltaX, deltaY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInArea(mouseX, mouseY)) {
            update(mouseX, mouseY);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isInArea(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private void update(double mouseX, double mouseY) {
        valueX = MathHelper.clamp((mouseX - x) / width, 0.0, 1.0);
        valueY = MathHelper.clamp((mouseY - y) / height, 0.0, 1.0);
        consumer.apply(valueX, valueY);
    }

    @Override
    public void appendNarrations(NarrationMessageBuilder builder) {
        appendDefaultNarrations(builder);
    }
}
