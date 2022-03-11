package mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.widget.PageTurnWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;

public class ExtendedPageTurnWidget extends PageTurnWidget {
    private final Identifier texture;

    private final boolean isNextPageButton;

    public ExtendedPageTurnWidget(int x, int y, boolean isNextPageButton, PressAction action, boolean playPageTurnSound, Identifier texture) {
        super(x, y, isNextPageButton, action, playPageTurnSound);
        this.isNextPageButton = isNextPageButton;
        this.texture = texture;
    }

    @Override
    public void renderButton(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.setShaderTexture(0, texture);

        int i = 0;
        int j = 192;
        if (isHovered()) {
            i += 23;
        }

        if (!isNextPageButton) {
            j += 13;
        }

        drawTexture(matrices, x, y, i, j, 23, 13);
    }
}
