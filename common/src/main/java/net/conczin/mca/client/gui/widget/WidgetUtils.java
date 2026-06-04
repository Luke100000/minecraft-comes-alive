package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

// FIXME: 1.20 loses the DrawableHelper attachment, determine if DrawContext can replace this
public class WidgetUtils {
    public static void drawRectangle(GuiGraphics context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(GuiGraphics context, Identifier texture, float x0, float x1, float y0,
            float y1, float u0, float u1, float v0, float v1) {
        int minX = Mth.floor(Math.min(x0, x1));
        int maxX = Mth.ceil(Math.max(x0, x1));
        int minY = Mth.floor(Math.min(y0, y1));
        int maxY = Mth.ceil(Math.max(y0, y1));

        int width = maxX - minX;
        int height = maxY - minY;
        if (width <= 0 || height <= 0) {
            return;
        }

        float texU = Math.min(u0, u1) * 64.0F;
        float texV = Math.min(v0, v1) * 64.0F;
        context.blit(RenderPipelines.GUI_TEXTURED, texture, minX, minY, texU, texV, width, height, 64, 64);
    }

    @Deprecated
    public static void drawTexturedQuad(Matrix4f matrix, float x0, float x1, float y0, float y1, float z, float u0,
            float u1, float v0, float v1) {
    }

    /**
     * The same as the Inventory function but with negative Z
     */
    public static void drawBackgroundEntity(GuiGraphics context, int x, int y, int size, float mouseX, float mouseY,
            LivingEntity entity) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(context, x - size / 2, y - size, x + size / 2, y + size,
                size, 0.0F, mouseX, mouseY, entity);
    }
}
