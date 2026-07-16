package net.conczin.mca.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Supplies a small, real-time animation clock while MCA renders entities inside GUI previews.
 *
 * <p>GUI screens may pause the world, which also freezes Minecraft's normal partial tick. EMF
 * reads that partial tick for CEM animation interpolation. MCA preview entities are also often
 * detached dummies which are not ticked by the world at all. This helper gives preview renders a
 * precision-safe real-time tick and exposes the matching fractional tick to the timer mixin, while
 * restoring the entity's real tick count immediately afterwards.</p>
 */
public final class PreviewEntityAnimation {
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final int TICK_WRAP = 27_720;
    private static final ThreadLocal<Float> ACTIVE_PARTIAL_TICK = new ThreadLocal<>();

    private PreviewEntityAnimation() {
    }

    public static Float getActivePartialTick() {
        return ACTIVE_PARTIAL_TICK.get();
    }

    public static void renderEntityInInventory(
            GuiGraphics graphics,
            float x,
            float y,
            float scale,
            Vector3f translate,
            Quaternionf pose,
            Quaternionf cameraOrientation,
            LivingEntity entity
    ) {
        withPreviewTime(entity, () -> InventoryScreen.renderEntityInInventory(
                graphics, x, y, scale, translate, pose, cameraOrientation, entity
        ));
    }

    public static void renderEntityInInventoryFollowsMouse(
            GuiGraphics graphics,
            int x0,
            int y0,
            int x1,
            int y1,
            int size,
            float verticalOffset,
            float mouseX,
            float mouseY,
            LivingEntity entity
    ) {
        withPreviewTime(entity, () -> InventoryScreen.renderEntityInInventoryFollowsMouse(
                graphics, x0, y0, x1, y1, size, verticalOffset, mouseX, mouseY, entity
        ));
    }

    private static void withPreviewTime(LivingEntity entity, Runnable render) {
        long now = System.nanoTime();
        long wholeTicks = now / NANOS_PER_TICK;
        float partialTick = (now % NANOS_PER_TICK) / (float) NANOS_PER_TICK;

        int previousTickCount = entity.tickCount;
        Float previousPartialTick = ACTIVE_PARTIAL_TICK.get();
        entity.tickCount = (int) (wholeTicks % TICK_WRAP);
        ACTIVE_PARTIAL_TICK.set(partialTick);

        try {
            render.run();
        } finally {
            entity.tickCount = previousTickCount;
            if (previousPartialTick == null) {
                ACTIVE_PARTIAL_TICK.remove();
            } else {
                ACTIVE_PARTIAL_TICK.set(previousPartialTick);
            }
        }
    }
}
