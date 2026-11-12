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
    private static final ResourceLocation SPEECH = ResourceLocation.withDefaultNamespace("toast/social_interactions");

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

        // The generated parchment contains its own warm vertical gradient;
        // the nine-slice scales it without covering the frame in an inset overlay.
    }

    private static void piece(GuiGraphics graphics, ResourceLocation texture, int x, int y,
                              int width, int height, int u, int v, int regionWidth, int regionHeight) {
        if (width > 0 && height > 0) {
            graphics.blit(texture, x, y, width, height, (float) u, (float) v,
                    regionWidth, regionHeight, FRAME_SIZE, FRAME_SIZE);
        }
    }

    static void drawTail(GuiGraphics graphics, int x, int y) {
        // The generated sprite includes the connector; no rectangular fill seam.
        graphics.blit(TAIL, x, y, 0, 0, 20, 20, 20, 20);
    }

    static void drawKeycap(GuiGraphics graphics, int x, int y, int width, int height) {
        roundedFill(graphics, x, y, width, height, 2, 0xFF140D0C);
        roundedFill(graphics, x + 1, y + 1, width - 2, height - 2, 2, 0xFF9B817A);
        roundedFill(graphics, x + 2, y + 2, width - 4, height - 4, 1, 0xFF322928);
    }

    private static void roundedFill(GuiGraphics graphics, int x, int y, int width, int height,
                                     int radius, int colour) {
        graphics.fill(x, y + radius, x + width, y + height - radius, colour);
        for (int row = 0; row < radius; row++) {
            int inset = radius - row;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, colour);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, colour);
        }
    }

    static void drawIcon(GuiGraphics graphics, int iconIndex, int x, int y, int size) {
        if (iconIndex == DialogueTopicSection.CONVERSATIONS.ordinal()) {
            graphics.blitSprite(SPEECH, x, y, size, size);
            return;
        }
        drawAtlasIcon(graphics, iconIndex, x, y, size);
    }

    static void drawFeaturedIcon(GuiGraphics graphics, int x, int y, int size) {
        drawAtlasIcon(graphics, 5, x, y, size);
    }

    /** Visible pixel bounds in each 16x16 atlas cell, excluding transparent margins. */
    static IconCrop cropFor(int iconIndex) {
        return switch (iconIndex) {
            case 2 -> new IconCrop(2, 3, 12, 11);
            case 3 -> new IconCrop(3, 3, 10, 11);
            case 4 -> new IconCrop(3, 7, 13, 3);
            case 5 -> new IconCrop(2, 2, 12, 12);
            default -> throw new IllegalArgumentException("No atlas crop for icon " + iconIndex);
        };
    }

    private static void drawAtlasIcon(GuiGraphics graphics, int iconIndex, int x, int y, int size) {
        IconCrop crop = cropFor(iconIndex);
        float scale = size / (float) Math.max(crop.width(), crop.height());
        int w = Math.round(crop.width() * scale);
        int h = Math.round(crop.height() * scale);
        graphics.blit(ICONS, x + (size - w) / 2, y + (size - h) / 2, w, h,
                (float) ((iconIndex - DialogueTopicSection.PERSONAL_STORIES.ordinal()) * 16 + crop.left()), (float) crop.top(),
                crop.width(), crop.height(), 64, 16);
    }

    record IconCrop(int left, int top, int width, int height) {
    }
}
