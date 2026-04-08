package net.conczin.mca.client.render;

public final class LivingEntityRenderContext {
    private static final ThreadLocal<Boolean> VILLAGER_RENDERER_ACTIVE = ThreadLocal.withInitial(() -> false);
    private static final ThreadLocal<Boolean> GENETICS_RENDERER_ACTIVE = ThreadLocal.withInitial(() -> false);

    private LivingEntityRenderContext() {
    }

    public static boolean isVillagerRendererActive() {
        return VILLAGER_RENDERER_ACTIVE.get();
    }

    public static void setVillagerRendererActive(boolean active) {
        VILLAGER_RENDERER_ACTIVE.set(active);
    }

    public static boolean isGeneticsRendererActive() {
        return GENETICS_RENDERER_ACTIVE.get();
    }

    public static void setGeneticsRendererActive(boolean active) {
        GENETICS_RENDERER_ACTIVE.set(active);
    }

    public static void clear() {
        VILLAGER_RENDERER_ACTIVE.remove();
        GENETICS_RENDERER_ACTIVE.remove();
    }
}
