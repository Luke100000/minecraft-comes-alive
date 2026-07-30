package net.conczin.mca.client.gui;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

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
    private static final int INITIALIZATION_TICK = 8;
    private static final float INITIALIZATION_DELTA_TICKS = 20.0F;
    private static final Map<LivingEntity, Long> START_NANOS = new WeakHashMap<>();
    private static final ThreadLocal<State> ACTIVE_STATE = new ThreadLocal<>();

    private PreviewEntityAnimation() {
    }

    public static Float getActivePartialTick() {
        State state = ACTIVE_STATE.get();
        return state == null ? null : state.partialTick;
    }

    /**
     * Gives stateful animation expressions one settled initialization step for a newly created
     * preview entity. There is no previous visible pose to interpolate from, so exposing the
     * default-zero expression state causes artificial spawn/landing transitions in GUI previews.
     */
    public static Float getActiveGameTimeDeltaTicks() {
        State state = ACTIVE_STATE.get();
        return state != null && state.initializing ? INITIALIZATION_DELTA_TICKS : null;
    }

    public static @Nullable State getActiveState(LivingEntity entity) {
        State state = ACTIVE_STATE.get();
        return state != null && state.entity == entity ? state : null;
    }

    public static <T> T withPreviewTime(LivingEntity entity, Supplier<T> render) {
        long now = System.nanoTime();
        Long existingStart = START_NANOS.get(entity);
        boolean initializing = existingStart == null;
        long start = initializing ? now : existingStart;
        if (initializing) {
            START_NANOS.put(entity, start);
        }
        long elapsed = now - start;
        long wholeTicks = elapsed / NANOS_PER_TICK;
        float partialTick = (elapsed % NANOS_PER_TICK) / (float) NANOS_PER_TICK;

        State state = new State(
                entity,
                initializing,
                partialTick,
                EntityValues.forPreview(
                        entity,
                        INITIALIZATION_TICK + (int) (wholeTicks % (TICK_WRAP - INITIALIZATION_TICK))
                )
        );
        return state.run(render);
    }

    /**
     * Immutable render-time preview values. Minecraft 26.1 queues GUI entities and renders them
     * after screen extraction has returned, while EMF deliberately keeps a live entity reference
     * in its render state. Reapplying this snapshot around the actual GUI entity draw gives EMF
     * the same entity values that were present during the synchronous 1.21.1 render.
     */
    public static final class State {
        private final LivingEntity entity;
        private final boolean initializing;
        private final float partialTick;
        private final EntityValues values;

        private State(LivingEntity entity, boolean initializing, float partialTick, EntityValues values) {
            this.entity = entity;
            this.initializing = initializing;
            this.partialTick = partialTick;
            this.values = values;
        }

        public <T> T run(Supplier<T> render) {
            EntityValues previousValues = EntityValues.capture(entity);
            State previousState = ACTIVE_STATE.get();
            values.apply(entity);
            ACTIVE_STATE.set(this);

            try {
                return render.get();
            } finally {
                previousValues.apply(entity);
                if (previousState == null) {
                    ACTIVE_STATE.remove();
                } else {
                    ACTIVE_STATE.set(previousState);
                }
            }
        }
    }

    private record EntityValues(
            int tickCount,
            double xo,
            double yo,
            double zo,
            double xOld,
            double yOld,
            double zOld,
            float bodyRot,
            float bodyRotOld,
            float yRot,
            float yRotOld,
            float xRot,
            float xRotOld,
            float headRot,
            float headRotOld
    ) {
        private static EntityValues forPreview(LivingEntity entity, int tickCount) {
            double x = entity.getX();
            double y = entity.getY();
            double z = entity.getZ();
            return new EntityValues(
                    tickCount,
                    x,
                    y,
                    z,
                    x,
                    y,
                    z,
                    entity.yBodyRot,
                    entity.yBodyRotO,
                    entity.getYRot(),
                    entity.yRotO,
                    entity.getXRot(),
                    entity.xRotO,
                    entity.yHeadRot,
                    entity.yHeadRotO
            );
        }

        private static EntityValues capture(LivingEntity entity) {
            return new EntityValues(
                    entity.tickCount,
                    entity.xo,
                    entity.yo,
                    entity.zo,
                    entity.xOld,
                    entity.yOld,
                    entity.zOld,
                    entity.yBodyRot,
                    entity.yBodyRotO,
                    entity.getYRot(),
                    entity.yRotO,
                    entity.getXRot(),
                    entity.xRotO,
                    entity.yHeadRot,
                    entity.yHeadRotO
            );
        }

        private void apply(LivingEntity entity) {
            entity.tickCount = tickCount;
            entity.xo = xo;
            entity.yo = yo;
            entity.zo = zo;
            entity.xOld = xOld;
            entity.yOld = yOld;
            entity.zOld = zOld;
            entity.yBodyRot = bodyRot;
            entity.yBodyRotO = bodyRotOld;
            entity.setYRot(yRot);
            entity.yRotO = yRotOld;
            entity.setXRot(xRot);
            entity.xRotO = xRotOld;
            entity.yHeadRot = headRot;
            entity.yHeadRotO = headRotOld;
        }
    }
}
