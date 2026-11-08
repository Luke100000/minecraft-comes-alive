package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class DialogueSkinResourcesTest {
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
