package net.mca.client.gui.widget;

import net.mca.MCA;
import net.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ClothingLockButtonWidget extends ButtonWidget {
    public static final int SIZE = 20;
    private static final Identifier LOCKED_TEXTURE = MCA.locate("textures/gui/widget/locked_button.png");
    private static final Identifier LOCKED_HIGHLIGHTED_TEXTURE = MCA.locate("textures/gui/widget/locked_button_highlighted.png");
    private static final Identifier UNLOCKED_TEXTURE = MCA.locate("textures/gui/widget/unlocked_button.png");
    private static final Identifier UNLOCKED_HIGHLIGHTED_TEXTURE = MCA.locate("textures/gui/widget/unlocked_button_highlighted.png");

    private final boolean locked;

    public ClothingLockButtonWidget(int x, int y, boolean locked, Text tooltip, PressAction onPress) {
        super(x, y, SIZE, SIZE, Text.empty(), onPress, tooltip);
        this.locked = locked;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean hovered = mouseX >= getX() && mouseY >= getY() && mouseX < getX() + width && mouseY < getY() + height;
        Identifier texture = locked
                ? hovered ? LOCKED_HIGHLIGHTED_TEXTURE : LOCKED_TEXTURE
                : hovered ? UNLOCKED_HIGHLIGHTED_TEXTURE : UNLOCKED_TEXTURE;
        context.drawTexture(texture, getX(), getY(), 0, 0, width, height, SIZE, SIZE);
    }
}
