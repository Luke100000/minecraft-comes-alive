package net.conczin.mca.client.gui.widget;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;

public class WidgetUtils {
    public static void drawRectangle(GuiGraphics context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(Matrix4f matrix, float x0, float x1, float y0, float y1, float z, float u0, float u1, float v0, float v1) {
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(matrix, x0, y1, z).setUv(u0, v1);
        builder.addVertex(matrix, x1, y1, z).setUv(u1, v1);
        builder.addVertex(matrix, x1, y0, z).setUv(u1, v0);
        builder.addVertex(matrix, x0, y0, z).setUv(u0, v0);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    public static void drawBackgroundEntity(GuiGraphics context, int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        InventoryScreen.renderEntityInInventoryFollowsMouse(context, x - size, y - size, x + size, y + size, size, 0.0F, mouseX, mouseY, entity);
    }
}
