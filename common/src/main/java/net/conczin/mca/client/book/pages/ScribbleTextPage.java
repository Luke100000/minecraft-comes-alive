package net.conczin.mca.client.book.pages;

import com.mojang.blaze3d.systems.RenderSystem;
import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public class ScribbleTextPage extends TextPage {
    final ResourceLocation scribble;

    public ScribbleTextPage(ResourceLocation scribble, String name, int page) {
        super(name, page);
        this.scribble = scribble;
    }

    public ScribbleTextPage(ResourceLocation scribble, String text) {
        super(text);
        this.scribble = scribble;
    }

    public void render(ExtendedBookScreen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        // scribble
        int i = (screen.width - 192) / 2;
        RenderSystem.enableBlend();
        context.blit(scribble, i + 28, 32, 0, 0, 128, 128, 128, 128);
        RenderSystem.disableBlend();

        super.render(screen, context, mouseX, mouseY, delta);
    }
}
