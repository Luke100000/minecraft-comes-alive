package net.mca.client.gui.widget;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// FIXME: 1.20 loses the DrawableHelper attachment, determine if DrawContext can replace this
public class WidgetUtils {
    public static void drawBuildingIcon(GuiGraphics context, ResourceLocation texture, int x, int y, int u, int v) {
        PoseStack matrices = context.pose();
        matrices.pushPose();
        matrices.translate(x - 6.6, y - 6.6, 0);
        matrices.scale(0.66f, 0.66f, 0.66f);
        context.blit(texture, 0, 0, u, v, 20, 20);
        matrices.popPose();
    }

    public static void drawRectangle(GuiGraphics context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(Matrix4f matrix, float x0, float x1, float y0, float y1, float z, float u0, float u1, float v0, float v1) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        BufferBuilder bufferBuilder = Tesselator.getInstance().getBuilder();
        bufferBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        bufferBuilder.vertex(matrix, x0, y1, z).uv(u0, v1).endVertex();
        bufferBuilder.vertex(matrix, x1, y1, z).uv(u1, v1).endVertex();
        bufferBuilder.vertex(matrix, x1, y0, z).uv(u1, v0).endVertex();
        bufferBuilder.vertex(matrix, x0, y0, z).uv(u0, v0).endVertex();
        BufferUploader.drawWithShader(bufferBuilder.end());
    }

    /**
     * The same as the Inventory function but with negative Z
     */
    @SuppressWarnings("deprecation")
    public static void drawBackgroundEntity(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float)Math.atan(mouseX / 40.0F);
        float g = (float)Math.atan(mouseY / 40.0F);
        PoseStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.pushPose();
        matrixStack.translate((float)x, (float)y, 50);
        matrixStack.scale(1.0F, 1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();
        PoseStack matrixStack2 = new PoseStack();
        matrixStack2.translate(0.0F, 0.0F, 1000.0F);
        matrixStack2.scale((float)size, (float)size, (float)size);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ(3.1415927F);
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(g * 20.0F * 0.017453292F);
        quaternionf.mul(quaternionf2);
        matrixStack2.mulPose(quaternionf);
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
        Lighting.setupForEntityInInventory();
        EntityRenderDispatcher entityRenderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        quaternionf2.conjugate();
        entityRenderDispatcher.overrideCameraOrientation(quaternionf2);
        entityRenderDispatcher.setRenderShadow(false);
        MultiBufferSource.BufferSource immediate = Minecraft.getInstance().renderBuffers().bufferSource();
        RenderSystem.runAsFancy(() -> {
            entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, matrixStack2, immediate, 15728880);
        });
        immediate.endBatch();
        entityRenderDispatcher.setRenderShadow(true);
        entity.yBodyRot = h;
        entity.setYRot(i);
        entity.setXRot(j);
        entity.yHeadRotO = k;
        entity.yHeadRot = l;
        matrixStack.popPose();
        RenderSystem.applyModelViewMatrix();
        Lighting.setupFor3DItems();
    }
}
