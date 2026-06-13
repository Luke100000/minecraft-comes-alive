package net.conczin.mca.client.gui.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

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
        float f = (float) Math.atan(mouseX / 40.0F);
        float g = (float) Math.atan(mouseY / 40.0F);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ((float) Math.PI);
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(g * 20.0F * (float) (Math.PI / 180.0));
        quaternionf.mul(quaternionf2);
        float h = entity.yBodyRot;
        float i = entity.getYRot();
        float j = entity.getXRot();
        float k = entity.yHeadRotO;
        float l = entity.yHeadRot;
        entity.yBodyRot = 180.0F + f * 20.0F;
        entity.setYRot(180.0F + f * 40.0F);
        entity.setXRot(-g * 20.0F);
        entity.yHeadRot = entity.getYRot();
        entity.yHeadRotO = entity.getYRot();

        float scale = (float) size / entity.getScale();
        Vector3f translation = new Vector3f(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
        context.pose().pushPose();
        context.pose().translate((double) x, (double) y, -50.0);
        context.pose().scale(scale, scale, -scale);
        context.pose().translate(translation.x, translation.y, translation.z);
        context.pose().mulPose(quaternionf);
        context.flush();

        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        entityRenderDispatcher.overrideCameraOrientation(quaternionf2.conjugate(new Quaternionf()).rotateY((float) Math.PI));
        entityRenderDispatcher.setRenderShadow(false);
        context.drawSpecial(bufferSource -> entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 1.0F, context.pose(), bufferSource, 15728880));
        context.flush();
        entityRenderDispatcher.setRenderShadow(true);
        context.pose().popPose();
        Lighting.setupFor3DItems();

        entity.yBodyRot = h;
        entity.setYRot(i);
        entity.setXRot(j);
        entity.yHeadRotO = k;
        entity.yHeadRot = l;
    }
}
