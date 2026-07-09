package net.mca.client.gui.widget;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

// FIXME: 1.20 loses the DrawableHelper attachment, determine if DrawContext can replace this
public class WidgetUtils {
    public static void drawBuildingIcon(DrawContext context, Identifier texture, int x, int y, int u, int v) {
        MatrixStack matrices = context.getMatrices();
        matrices.push();
        matrices.translate(x - 6.6, y - 6.6, 0);
        matrices.scale(0.66f, 0.66f, 0.66f);
        context.drawTexture(texture, 0, 0, u, v, 20, 20);
        matrices.pop();
    }

    public static void drawRectangle(DrawContext context, int x0, int y0, int x1, int y1, int color) {
        context.fill(x0 + 1, y0, x1, y0 + 1, color);
        context.fill(x1 - 1, y0 + 1, x1, y1, color);
        context.fill(x0, y1 - 1, x1 - 1, y1, color);
        context.fill(x0, y0, x0 + 1, y1 - 1, color);
    }

    public static void drawTexturedQuad(Matrix4f matrix, float x0, float x1, float y0, float y1, float z, float u0, float u1, float v0, float v1) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        BufferBuilder bufferBuilder = Tessellator.getInstance().getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        bufferBuilder.vertex(matrix, x0, y1, z).texture(u0, v1).next();
        bufferBuilder.vertex(matrix, x1, y1, z).texture(u1, v1).next();
        bufferBuilder.vertex(matrix, x1, y0, z).texture(u1, v0).next();
        bufferBuilder.vertex(matrix, x0, y0, z).texture(u0, v0).next();
        BufferRenderer.drawWithGlobalProgram(bufferBuilder.end());
    }

    /**
     * The same as the Inventory function but with negative Z
     */
    @SuppressWarnings("deprecation")
    public static void drawBackgroundEntity(int x, int y, int size, float mouseX, float mouseY, LivingEntity entity) {
        float f = (float)Math.atan(mouseX / 40.0F);
        float g = (float)Math.atan(mouseY / 40.0F);
        MatrixStack matrixStack = RenderSystem.getModelViewStack();
        matrixStack.push();
        matrixStack.translate((float)x, (float)y, 50);
        matrixStack.scale(1.0F, 1.0F, -1.0F);
        RenderSystem.applyModelViewMatrix();
        MatrixStack matrixStack2 = new MatrixStack();
        matrixStack2.translate(0.0F, 0.0F, 1000.0F);
        matrixStack2.scale((float)size, (float)size, (float)size);
        Quaternionf quaternionf = (new Quaternionf()).rotateZ(3.1415927F);
        Quaternionf quaternionf2 = (new Quaternionf()).rotateX(g * 20.0F * 0.017453292F);
        quaternionf.mul(quaternionf2);
        matrixStack2.multiply(quaternionf);
        float h = entity.bodyYaw;
        float i = entity.getYaw();
        float j = entity.getPitch();
        float k = entity.prevHeadYaw;
        float l = entity.headYaw;
        entity.bodyYaw = 180.0F + f * 20.0F;
        entity.setYaw(180.0F + f * 40.0F);
        entity.setPitch(-g * 20.0F);
        entity.headYaw = entity.getYaw();
        entity.prevHeadYaw = entity.getYaw();
        DiffuseLighting.method_34742();
        EntityRenderDispatcher entityRenderDispatcher = MinecraftClient.getInstance().getEntityRenderDispatcher();
        quaternionf2.conjugate();
        entityRenderDispatcher.setRotation(quaternionf2);
        entityRenderDispatcher.setRenderShadows(false);
        VertexConsumerProvider.Immediate immediate = MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
        RenderSystem.runAsFancy(() -> {
            entityRenderDispatcher.render(entity, 0.0, 0.0, 0.0, 0.0F, 1.0F, matrixStack2, immediate, 15728880);
        });
        immediate.draw();
        entityRenderDispatcher.setRenderShadows(true);
        entity.bodyYaw = h;
        entity.setYaw(i);
        entity.setPitch(j);
        entity.prevHeadYaw = k;
        entity.headYaw = l;
        matrixStack.pop();
        RenderSystem.applyModelViewMatrix();
        DiffuseLighting.enableGuiDepthLighting();
    }
}
