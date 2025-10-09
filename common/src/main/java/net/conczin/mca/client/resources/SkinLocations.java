package net.conczin.mca.client.resources;

import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class SkinLocations {
    public static final Part[][] LOOKUP = new Part[64][64];
    public static final boolean[][] SKIN_LOOKUP = new boolean[64][64];
    public static Set<Part> SKIN_PARTS = new HashSet<>();

    static {
        SKIN_PARTS.add(Part.HEAD);
        SKIN_PARTS.add(Part.RIGHT_LEG);
        SKIN_PARTS.add(Part.BODY);
        SKIN_PARTS.add(Part.RIGHT_ARM);
        SKIN_PARTS.add(Part.LEFT_LEG);
        SKIN_PARTS.add(Part.LEFT_ARM);
    }

    static {
        add(Part.HEAD, 8, 0, 16, 8);
        add(Part.HEAD, 8, 0, 16, 8);
        add(Part.HEAD, 16, 0, 24, 8);
        add(Part.HEAD, 0, 8, 8, 16);
        add(Part.HEAD, 8, 8, 16, 16);
        add(Part.HEAD, 16, 8, 24, 16);
        add(Part.HEAD, 24, 8, 32, 16);
        add(Part.HAT, 40, 0, 48, 8);
        add(Part.HAT, 48, 0, 56, 8);
        add(Part.HAT, 32, 8, 40, 16);
        add(Part.HAT, 40, 8, 48, 16);
        add(Part.HAT, 48, 8, 56, 16);
        add(Part.HAT, 56, 8, 64, 16);
        add(Part.RIGHT_LEG, 4, 16, 8, 20);
        add(Part.RIGHT_LEG, 8, 16, 12, 20);
        add(Part.RIGHT_LEG, 0, 20, 4, 32);
        add(Part.RIGHT_LEG, 4, 20, 8, 32);
        add(Part.RIGHT_LEG, 8, 20, 12, 32);
        add(Part.RIGHT_LEG, 12, 20, 16, 32);
        add(Part.BODY, 20, 16, 28, 20);
        add(Part.BODY, 28, 16, 36, 20);
        add(Part.BODY, 16, 20, 20, 32);
        add(Part.BODY, 20, 20, 28, 32);
        add(Part.BODY, 28, 20, 32, 32);
        add(Part.BODY, 32, 20, 40, 32);
        add(Part.RIGHT_ARM, 44, 16, 48, 20);
        add(Part.RIGHT_ARM, 48, 16, 52, 20);
        add(Part.RIGHT_ARM, 40, 20, 44, 32);
        add(Part.RIGHT_ARM, 44, 20, 48, 32);
        add(Part.RIGHT_ARM, 48, 20, 52, 32);
        add(Part.RIGHT_ARM, 52, 20, 56, 32);
        add(Part.LEFT_LEG, 20, 48, 24, 52);
        add(Part.LEFT_LEG, 24, 48, 28, 52);
        add(Part.LEFT_LEG, 16, 52, 20, 64);
        add(Part.LEFT_LEG, 20, 52, 24, 64);
        add(Part.LEFT_LEG, 24, 52, 28, 64);
        add(Part.LEFT_LEG, 28, 52, 32, 64);
        add(Part.LEFT_ARM, 36, 48, 40, 52);
        add(Part.LEFT_ARM, 40, 48, 44, 52);
        add(Part.LEFT_ARM, 32, 52, 36, 64);
        add(Part.LEFT_ARM, 36, 52, 40, 64);
        add(Part.LEFT_ARM, 40, 52, 44, 64);
        add(Part.LEFT_ARM, 44, 52, 48, 64);
        add(Part.RIGHT_LEG_2, 4, 48, 8, 36);
        add(Part.RIGHT_LEG_2, 8, 48, 12, 36);
        add(Part.RIGHT_LEG_2, 0, 36, 4, 48);
        add(Part.RIGHT_LEG_2, 4, 36, 8, 48);
        add(Part.RIGHT_LEG_2, 8, 36, 12, 48);
        add(Part.RIGHT_LEG_2, 12, 36, 16, 48);
        add(Part.JACKET, 20, 48, 28, 36);
        add(Part.JACKET, 28, 48, 36, 36);
        add(Part.JACKET, 16, 36, 20, 48);
        add(Part.JACKET, 20, 36, 28, 48);
        add(Part.JACKET, 28, 36, 32, 48);
        add(Part.JACKET, 32, 36, 40, 48);
        add(Part.RIGHT_ARM_2, 44, 48, 48, 36);
        add(Part.RIGHT_ARM_2, 48, 48, 52, 36);
        add(Part.RIGHT_ARM_2, 40, 36, 44, 48);
        add(Part.RIGHT_ARM_2, 44, 36, 48, 48);
        add(Part.RIGHT_ARM_2, 48, 36, 52, 48);
        add(Part.RIGHT_ARM_2, 52, 36, 64, 48);
        add(Part.LEFT_LEG_2, 4, 48, 8, 52);
        add(Part.LEFT_LEG_2, 8, 48, 12, 52);
        add(Part.LEFT_LEG_2, 0, 52, 4, 64);
        add(Part.LEFT_LEG_2, 4, 52, 8, 64);
        add(Part.LEFT_LEG_2, 8, 52, 12, 64);
        add(Part.LEFT_LEG_2, 12, 52, 16, 64);
        add(Part.LEFT_ARM_2, 52, 48, 56, 52);
        add(Part.LEFT_ARM_2, 56, 48, 60, 52);
        add(Part.LEFT_ARM_2, 48, 52, 52, 64);
        add(Part.LEFT_ARM_2, 52, 52, 56, 64);
        add(Part.LEFT_ARM_2, 56, 52, 60, 64);
        add(Part.LEFT_ARM_2, 60, 52, 64, 64);
    }

    private static void add(Part part, int x0, int y0, int x1, int y1) {
        for (int x = x0; x < x1; x++) {
            for (int y = y0; y < y1; y++) {
                LOOKUP[x][y] = part;

                if (SKIN_PARTS.contains(part)) {
                    SKIN_LOOKUP[x][y] = true;
                }
            }
        }
    }

    public enum Part {
        HEAD, HAT, RIGHT_LEG, BODY, RIGHT_ARM, LEFT_LEG, LEFT_ARM, JACKET, RIGHT_LEG_2, RIGHT_ARM_2, LEFT_LEG_2, LEFT_ARM_2;

        public Component getTranslation() {
            return Component.translatable("gui.skin_library.element." + name().toLowerCase(Locale.ROOT));
        }
    }
}
