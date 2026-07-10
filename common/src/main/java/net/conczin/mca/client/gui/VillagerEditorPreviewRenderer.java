package net.conczin.mca.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class VillagerEditorPreviewRenderer {
    private VillagerEditorPreviewRenderer() {
    }

    static void render(
            GuiGraphics context,
            int x0,
            int y0,
            int x1,
            int y1,
            int size,
            float mouseX,
            float mouseY,
            LivingEntity entity,
            float rotationOffset,
            float previewRotation,
            float previewZoom,
            boolean followsMouse
    ) {
        float centerX = (x0 + x1) / 2.0F;
        float centerY = (y0 + y1) / 2.0F;
        float baseXAngle = followsMouse ? (float)Math.atan((centerX - mouseX) / 40.0F) : 0.0F;
        float yAngle = followsMouse ? (float)Math.atan((centerY - mouseY) / 40.0F) : 0.0F;
        float displayRotation = previewRotation + rotationOffset;
        float followXAngle = baseXAngle * Mth.cos(displayRotation * ((float)Math.PI / 180.0F));
        Quaternionf pose = new Quaternionf().rotateZ((float)Math.PI);
        Quaternionf cameraOrientation = new Quaternionf().rotateX(yAngle * 20.0F * (float)(Math.PI / 180.0));
        pose.mul(cameraOrientation);

        float previousBodyRot = entity.yBodyRot;
        float previousBodyRotO = entity.yBodyRotO;
        float previousYRot = entity.getYRot();
        float previousYRotO = entity.yRotO;
        float previousXRot = entity.getXRot();
        float previousXRotO = entity.xRotO;
        float previousHeadRotO = entity.yHeadRotO;
        float previousHeadRot = entity.yHeadRot;
        boolean scissorEnabled = false;

        try {
            entity.yBodyRot = 180.0F + displayRotation + followXAngle * 20.0F;
            entity.yBodyRotO = entity.yBodyRot;
            entity.setYRot(180.0F + displayRotation + followXAngle * 40.0F);
            entity.yRotO = entity.getYRot();
            entity.setXRot(-yAngle * 20.0F);
            entity.xRotO = entity.getXRot();
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();

            float scale = Math.max(1.0F, size * previewZoom) / entity.getScale();
            Vector3f translate = new Vector3f(0.0F, entity.getBbHeight() / 2.0F, 0.0F);
            context.enableScissor(x0, y0, x1, y1);
            scissorEnabled = true;
            InventoryScreen.renderEntityInInventory(context, centerX, centerY, scale, translate, pose, cameraOrientation, entity);
            context.flush();
        } finally {
            if (scissorEnabled) {
                context.disableScissor();
            }
            entity.yBodyRot = previousBodyRot;
            entity.yBodyRotO = previousBodyRotO;
            entity.setYRot(previousYRot);
            entity.yRotO = previousYRotO;
            entity.setXRot(previousXRot);
            entity.xRotO = previousXRotO;
            entity.yHeadRotO = previousHeadRotO;
            entity.yHeadRot = previousHeadRot;
        }
    }
}
