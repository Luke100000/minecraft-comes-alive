package net.conczin.mca.client.resources;

import java.util.function.BiConsumer;

public class ClientUtils {
    public static void bethlehemLine(int x0, int y0, int x1, int y1, BiConsumer<Integer, Integer> callback) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;

        while (true) {
            callback.accept(x, y);
            if (x == x1 && y == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    public static double[] RGB2HSV(double red, double green, double blue) {
        double cmax = Math.max(red, Math.max(green, blue));
        double cmin = Math.min(red, Math.min(green, blue));
        double delta = cmax - cmin;
        double h, s, v;

        if (delta == 0) {
            h = 0;
        } else if (cmax == red) {
            h = (green - blue) / delta % 6;
        } else if (cmax == green) {
            h = (blue - red) / delta + 2;
        } else {
            h = (red - green) / delta + 4;
        }

        h *= 60;

        if (h < 0) {
            h += 360;
        }

        if (cmax == 0) {
            s = 0;
        } else {
            s = delta / cmax;
        }

        v = cmax;

        return new double[] {h, s, v};
    }

    public static double[] HSV2RGB(double hue, double saturation, double brightness) {
        double c = brightness * saturation;
        double x = c * (1 - Math.abs(hue / 60 % 2 - 1));
        double m = brightness - c;
        double r, g, b;

        if (hue >= 0 && hue < 60) {
            r = c;
            g = x;
            b = 0;
        } else if (hue >= 60 && hue < 120) {
            r = x;
            g = c;
            b = 0;
        } else if (hue >= 120 && hue < 180) {
            r = 0;
            g = c;
            b = x;
        } else if (hue >= 180 && hue < 240) {
            r = 0;
            g = x;
            b = c;
        } else if (hue >= 240 && hue < 300) {
            r = x;
            g = 0;
            b = c;
        } else {
            r = c;
            g = 0;
            b = x;
        }

        return new double[] {
                r + m,
                g + m,
                b + m
        };
    }
}
