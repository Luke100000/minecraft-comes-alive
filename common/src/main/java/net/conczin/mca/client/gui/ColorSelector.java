package net.conczin.mca.client.gui;

import net.conczin.mca.client.gui.widget.HorizontalColorPickerWidget;
import net.conczin.mca.client.resources.ClientUtils;

class ColorSelector {
    public HorizontalColorPickerWidget hueWidget;
    public HorizontalColorPickerWidget saturationWidget;
    public HorizontalColorPickerWidget brightnessWidget;
    double red, green, blue;
    double hue, saturation, brightness;

    public ColorSelector() {
        setHSV(0.5, 0.5, 0.5);
    }

    public void setRGB(double red, double green, double blue) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        updateHSV();
    }

    public void setHSV(double hue, double saturation, double brightness) {
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        updateRGB();

        if (hueWidget != null) {
            hueWidget.setValueX(hue / 360.0);
            saturationWidget.setValueX(saturation);
        }
        if (brightnessWidget != null) {
            brightnessWidget.setValueX(brightness);
        }
    }

    private void updateRGB() {
        double[] doubles = ClientUtils.HSV2RGB(hue, saturation, brightness);
        this.red = doubles[0];
        this.green = doubles[1];
        this.blue = doubles[2];
    }

    private void updateHSV() {
        double[] doubles = ClientUtils.RGB2HSV(red, green, blue);
        this.hue = doubles[0];
        this.saturation = doubles[1];
        this.brightness = doubles[2];

        if (hueWidget != null) {
            hueWidget.setValueX(hue / 360.0);
            saturationWidget.setValueX(saturation);
        }
        if (brightnessWidget != null) {
            brightnessWidget.setValueX(brightness);
        }
    }

    public int getRed() {
        return (int) (red * 255);
    }

    public int getGreen() {
        return (int) (green * 255);
    }

    public int getBlue() {
        return (int) (blue * 255);
    }

    public int getColor() {
        return 0xFF000000 | getRed() << 16 | getGreen() << 8 | getBlue();
    }
}
