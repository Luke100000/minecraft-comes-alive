package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSkinResourcesTest {
    @Test
    void footerHighlightsMeetVisibleBottomStrokeWithoutCoveringIt() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/menu_frame.png")) {
            assertNotNull(in);
            BufferedImage texture = ImageIO.read(in);
            DialogueOverlayLayout.Rect menu = new DialogueOverlayLayout.Rect(100, 150, 300, 140);
            DialogueOverlayLayout.FooterLayout footer = DialogueOverlayLayout.footer(menu, 27);
            int centreX = texture.getWidth() / 2;
            int borderColour = texture.getRGB(centreX, 2);
            for (DialogueOverlayLayout.Rect action : new DialogueOverlayLayout.Rect[]{footer.back(), footer.close()}) {
                int firstUncoveredTextureRow = texture.getHeight() - (menu.bottom() - action.bottom());
                assertEquals(borderColour, texture.getRGB(centreX, firstUncoveredTextureRow),
                        "Footer must meet the visible brown stroke, leaving no inner shadow strip");
                assertNotEquals(borderColour, texture.getRGB(centreX, firstUncoveredTextureRow - 1),
                        "Footer must stop at the stroke without painting over it");
            }
        }
    }

    @Test
    void frameCornerSilhouettesMatchOnEverySide() throws Exception {
        for (String name : new String[]{"paper_frame", "menu_frame", "featured_frame"}) {
            try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/" + name + ".png")) {
                assertNotNull(in);
                BufferedImage texture = ImageIO.read(in);
                for (int y = 0; y < 24; y++) {
                    for (int x = 0; x < 24; x++) {
                        int alpha = texture.getRGB(x, y) >>> 24;
                        assertEquals(alpha, texture.getRGB(23 - x, y) >>> 24, name + " horizontal silhouette");
                        assertEquals(alpha, texture.getRGB(x, 23 - y) >>> 24, name + " vertical silhouette");
                    }
                }
            }
        }
    }

    @Test
    void dialogueTexturesHaveExpectedPixelDimensionsAndAlpha() throws Exception {
        verify("paper_frame", 24, 24);
        verify("paper_tail", 16, 20);
        verify("menu_frame", 24, 24);
        verify("featured_frame", 24, 24);
        verify("icons", 96, 16);
    }

    @Test
    void categoryAndFeaturedIconCropsMatchActualOpaquePixelBounds() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/icons.png")) {
            assertNotNull(in);
            BufferedImage atlas = ImageIO.read(in);
            for (int index = 2; index <= 5; index++) {
                DialogueSkin.IconCrop crop = DialogueSkin.cropFor(index);
                int minX = 16;
                int minY = 16;
                int maxX = -1;
                int maxY = -1;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        if ((atlas.getRGB(index * 16 + x, y) >>> 24) != 0) {
                            minX = Math.min(minX, x);
                            minY = Math.min(minY, y);
                            maxX = Math.max(maxX, x);
                            maxY = Math.max(maxY, y);
                        }
                    }
                }
                assertTrue(maxX >= 0, "Icon cell " + index + " is empty");
                assertEquals(minX, crop.left());
                assertEquals(minY, crop.top());
                assertEquals(maxX + 1, crop.left() + crop.width());
                assertEquals(maxY + 1, crop.top() + crop.height());
            }
        }
    }

    @Test
    void parchmentArtHasNoBrightScuffsAndTailHasAnOpenAttachment() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/paper_frame.png")) {
            assertNotNull(in);
            BufferedImage frame = ImageIO.read(in);
            for (int y = 0; y < frame.getHeight(); y++) {
                for (int x = 0; x < frame.getWidth(); x++) {
                    assertNotEquals(0xFFFFF4D9, frame.getRGB(x, y), "Isolated bright scuff at " + x + "," + y);
                }
            }
        }
        try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/paper_tail.png")) {
            assertNotNull(in);
            BufferedImage tail = ImageIO.read(in);
            assertEquals(0, tail.getRGB(0, 0) >>> 24);
            assertEquals(0xFFFAE8C9, tail.getRGB(15, 10));
        }
    }

    @Test
    void parchmentInteriorHasAContinuousWarmVerticalGradient() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/assets/mca/textures/gui/dialogue/paper_frame.png")) {
            assertNotNull(in);
            BufferedImage paper = ImageIO.read(in);
            int top = paper.getRGB(12, 7);
            int bottom = paper.getRGB(12, 16);
            assertTrue((top & 0xFF) > (bottom & 0xFF), "Paper should grow warmer towards the bottom");
            assertTrue(((top >> 8) & 0xFF) > ((bottom >> 8) & 0xFF));
            for (int y = 7; y <= 16; y++) {
                assertEquals(paper.getRGB(7, y), paper.getRGB(16, y),
                        "Nine-slice interior should keep the same tone across the row");
            }
        }
    }

    private static void verify(String name, int width, int height) throws Exception {
        String path = "/assets/mca/textures/gui/dialogue/" + name + ".png";
        try (InputStream in = DialogueSkinResourcesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "Missing " + path);
            BufferedImage texture = ImageIO.read(in);
            assertNotNull(texture, "Unreadable PNG " + path);
            assertEquals(width, texture.getWidth(), path);
            assertEquals(height, texture.getHeight(), path);
            assertTrue(texture.getColorModel().hasAlpha(), path);
        }
    }
}
