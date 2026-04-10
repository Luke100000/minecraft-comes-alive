package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.client.gui.SkinLibraryScreen;
import net.conczin.mca.client.gui.immersive_library.types.LiteContent;
import net.conczin.mca.client.resources.SkinMeta;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;

import java.util.LinkedList;
import java.util.Queue;

public final class Workspace {
    private static final int MAX_HISTORY = 50;
    public final NativeImage currentImage;
    public final DynamicTexture backendTexture;
    public SkinLibraryScreen.SkinType skinType;
    public int contentid = -1;
    public int temperature;
    public double chance = 1.0;
    public String title = "Unnamed Asset";
    public String profession;
    public Gender gender = Gender.NEUTRAL;
    public int fillToolThreshold = 32;
    public LinkedList<NativeImage> history = new LinkedList<>();

    private boolean dirty;
    private boolean dirtySinceSnapshot;

    public Workspace(NativeImage image) {
        this.currentImage = image;
        this.backendTexture = new DynamicTexture(() -> "mca/workspace", currentImage);
        this.dirty = true;
    }

    public Workspace(NativeImage image, SkinMeta meta, LiteContent content) {
        this(image);

        this.contentid = content.contentid();
        this.title = content.title();

        this.skinType = content.hasTag("clothing") ? SkinLibraryScreen.SkinType.CLOTHING : SkinLibraryScreen.SkinType.HAIR;

        this.chance = meta.getChance();
        this.gender = meta.getGender();
        this.profession = meta.getProfession();
        this.temperature = meta.getTemperature();
    }

    public SkinListEntry toListEntry() {
        if (skinType == SkinLibraryScreen.SkinType.CLOTHING) {
            return new Clothing("immersive_library:" + contentid, profession, temperature, false, gender);
        } else {
            return new Hair("immersive_library:" + contentid);
        }
    }

    private void fillDeleteFunc(FillTodo entry, Queue<FillTodo> todo, int x, int y) {
        if (x < 0 || y < 0 || x >= 64 || y >= 64) return;

        FillTodo nextEntry = new FillTodo(x, y, redAt(x, y), greenAt(x, y), blueAt(x, y), alphaAt(x, y));

        if (Math.abs(nextEntry.red - entry.red) > fillToolThreshold) return;
        if (Math.abs(nextEntry.green - entry.green) > fillToolThreshold) return;
        if (Math.abs(nextEntry.blue - entry.blue) > fillToolThreshold) return;
        if (Math.abs(nextEntry.alpha - entry.alpha) > fillToolThreshold) return;

        todo.add(nextEntry);
    }

    public void removeSaturation() {
        saveSnapshot(true);

        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int r = redAt(x, y);
                int g = greenAt(x, y);
                int b = blueAt(x, y);
                int a = alphaAt(x, y);
                int l = Mth.clamp((int) (0.2126 * r + 0.7152 * g + 0.0722 * b), 0, 255);
                currentImage.setPixel(x, y, ARGB.color(a, l, l, l));
            }
        }

        dirty = true;
    }

    public void addBrightness(int i) {
        saveSnapshot(true);

        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int r = Mth.clamp(redAt(x, y) + i, 0, 255);
                int g = Mth.clamp(greenAt(x, y) + i, 0, 255);
                int b = Mth.clamp(blueAt(x, y) + i, 0, 255);
                int a = alphaAt(x, y);
                currentImage.setPixel(x, y, ARGB.color(a, r, g, b));
            }
        }

        dirty = true;
    }

    public void addContrast(float c) {
        saveSnapshot(true);

        int average = 0;
        int samples = 0;
        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int a = alphaAt(x, y);
                if (a > 0) {
                    average += redAt(x, y);
                    average += blueAt(x, y);
                    average += greenAt(x, y);
                    samples += 3;
                }
            }
        }
        average /= samples;

        for (int x = 0; x < 64; x++) {
            for (int y = 0; y < 64; y++) {
                int r = Mth.clamp((int) ((redAt(x, y) - average) * (1.0f + c) + average), 0, 255);
                int g = Mth.clamp((int) ((greenAt(x, y) - average) * (1.0f + c) + average), 0, 255);
                int b = Mth.clamp((int) ((blueAt(x, y) - average) * (1.0f + c) + average), 0, 255);
                int a = alphaAt(x, y);
                currentImage.setPixel(x, y, ARGB.color(a, r, g, b));
            }
        }

        dirty = true;
    }

    public void fillDelete(int x, int y) {
        if (x < 0 || y < 0 || x >= 64 || y >= 64) return;

        saveSnapshot(true);

        Queue<FillTodo> todo = new LinkedList<>();
        todo.add(new FillTodo(x, y, redAt(x, y), greenAt(x, y), blueAt(x, y), alphaAt(x, y)));

        while (!todo.isEmpty()) {
            FillTodo entry = todo.poll();

            if (alphaAt(entry.x, entry.y) == 0) {
                continue;
            }

            currentImage.setPixel(entry.x, entry.y, 0);
            dirty = true;

            for (int ox = -1; ox <= 1; ox++) {
                for (int oy = -1; oy <= 1; oy++) {
                    if (ox != 0 || oy != 0) {
                        fillDeleteFunc(entry, todo, entry.x + ox, entry.y + oy);
                    }
                }
            }
        }
    }

    public boolean validPixel(int x, int y) {
        return x >= 0 && x < 64 && y >= 0 && y < 64;
    }

    public void saveSnapshot(boolean always) {
        if (always || dirtySinceSnapshot) {
            dirtySinceSnapshot = false;
            while (history.size() > MAX_HISTORY) {
                history.removeFirst().close();
            }
            NativeImage image = new NativeImage(64, 64, false);
            image.copyFrom(currentImage);
            history.add(image);
        }
    }

    public void undo() {
        if (history.size() > 0) {
            NativeImage image = history.removeLast();
            currentImage.copyFrom(image);
            image.close();
            dirty = true;
            dirtySinceSnapshot = false;
        }
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (dirty) {
            dirtySinceSnapshot = true;
        }
    }

    private int redAt(int x, int y) {
        return ARGB.red(currentImage.getPixel(x, y));
    }

    private int greenAt(int x, int y) {
        return ARGB.green(currentImage.getPixel(x, y));
    }

    private int blueAt(int x, int y) {
        return ARGB.blue(currentImage.getPixel(x, y));
    }

    private int alphaAt(int x, int y) {
        return ARGB.alpha(currentImage.getPixel(x, y));
    }

    private record FillTodo(int x, int y, int red, int green, int blue, int alpha) {

    }
}
