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
        double previousXo = entity.xo;
        double previousYo = entity.yo;
        double previousZo = entity.zo;
        double previousXOld = entity.xOld;
        double previousYOld = entity.yOld;
        double previousZOld = entity.zOld;
        entity.tickCount = (int) (wholeTicks % TICK_WRAP);
        ACTIVE_PARTIAL_TICK.set(partialTick);
        // Preview dummies are never ticked, so their position interpolation history can hold a
        // stale position (e.g. the spawn point from before NBT data teleported them). Mods that
        // lerp the entity position from previous to current with the preview partial tick (EMF
        // does, for CEM variables like pos_y) then see the position sweep that gap every frame,
        // and animation packs read entities as perpetual falling/landing - the editor
        // preview twitches or replays landing animations in a loop. Pin the history to the
        // current position for the duration of the preview render.

        entity.xo = entity.getX();
        entity.yo = entity.getY();
        entity.zo = entity.getZ();
        entity.xOld = entity.getX();
        entity.yOld = entity.getY();
        entity.zOld = entity.getZ();

        try {
            render.run();
        } finally {
            entity.tickCount = previousTickCount;
            entity.xo = previousXo;
            entity.yo = previousYo;
            entity.zo = previousZo;
            entity.xOld = previousXOld;
            entity.yOld = previousYOld;
            entity.zOld = previousZOld;
            if (previousPartialTick == null) {
                ACTIVE_PARTIAL_TICK.remove();
            } else {
                ACTIVE_PARTIAL_TICK.set(previousPartialTick);
            }
        }
    }
}
