package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Original pixel-art frames with fixed-size corners and stretchable middles. */
final class DialogueSkin {
    static final ResourceLocation PAPER = MCA.locate("textures/gui/dialogue/paper_frame.png");
    static final ResourceLocation TAIL = MCA.locate("textures/gui/dialogue/paper_tail.png");
    static final ResourceLocation MENU = MCA.locate("textures/gui/dialogue/menu_frame.png");
    static final ResourceLocation FEATURED = MCA.locate("textures/gui/dialogue/featured_frame.png");
    private static final ResourceLocation ICONS = MCA.locate("textures/gui/dialogue/icons.png");

    private static final int FRAME_SIZE = 24;
    private static final int CORNER = 7;

    private DialogueSkin() {
    }

    static void drawFrame(GuiGraphics graphics, ResourceLocation texture, int x, int y, int width, int height) {
        if (width < CORNER * 2 || height < CORNER * 2) {
            return;
        }
        int inner = FRAME_SIZE - CORNER * 2;
        int centerWidth = width - CORNER * 2;
        int centerHeight = height - CORNER * 2;

        piece(graphics, texture, x, y, CORNER, CORNER, 0, 0, CORNER, CORNER);
        piece(graphics, texture, x + CORNER, y, centerWidth, CORNER, CORNER, 0, inner, CORNER);
        piece(graphics, texture, x + width - CORNER, y, CORNER, CORNER, FRAME_SIZE - CORNER, 0, CORNER, CORNER);

        piece(graphics, texture, x, y + CORNER, CORNER, centerHeight, 0, CORNER, CORNER, inner);
        piece(graphics, texture, x + CORNER, y + CORNER, centerWidth, centerHeight, CORNER, CORNER, inner, inner);
        piece(graphics, texture, x + width - CORNER, y + CORNER, CORNER, centerHeight,
                FRAME_SIZE - CORNER, CORNER, CORNER, inner);

        piece(graphics, texture, x, y + height - CORNER, CORNER, CORNER, 0,
                FRAME_SIZE - CORNER, CORNER, CORNER);
        piece(graphics, texture, x + CORNER, y + height - CORNER, centerWidth, CORNER,
                CORNER, FRAME_SIZE - CORNER, inner, CORNER);
        piece(graphics, texture, x + width - CORNER, y + height - CORNER, CORNER, CORNER,
                FRAME_SIZE - CORNER, FRAME_SIZE - CORNER, CORNER, CORNER);
    }

    private static void piece(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                              int width, int height, int u, int v, int regionWidth, int regionHeight) {
        if (width > 0 && height > 0) {
            graphics.blit(texture, x, y, width, height, (float) u, (float) v,
                    regionWidth, regionHeight, FRAME_SIZE, FRAME_SIZE);
        }
    }

    static void drawTail(GuiGraphics graphics, int x, int y) {
        graphics.blit(TAIL, x, y, 0, 0, 16, 20, 16, 20);
    }

    static void drawIcon(GuiGraphics graphics, int iconIndex, int x, int y) {
        graphics.blit(ICONS, x, y, 0, iconIndex * 16, 0, 16, 16, 96, 16);
    }
}
