package net.conczin.mca.client.gui;

/** Keeps animated viewport geometry still during native scrollbar gestures. */
final class DialogueMenuAnimation {
    private static final long DURATION_MILLIS = 120;
    private static final long WHEEL_HOLD_MILLIS = 180;
    private float startHeight;
    private int targetHeight;
    private int displayedHeight;
    private long startedAt;
    private long holdUntil;
    private boolean initialized;

    int update(int desiredHeight, long now, boolean dragging) {
        if (!initialized) {
            startHeight = targetHeight = displayedHeight = desiredHeight;
            startedAt = now;
            initialized = true;
        }
        if (dragging || now < holdUntil) {
            startHeight = targetHeight = displayedHeight;
            startedAt = now;
            return displayedHeight;
        }
        if (desiredHeight != targetHeight) {
            startHeight = displayedHeight;
            targetHeight = desiredHeight;
            startedAt = now;
        }
        float remaining = 1 - Math.clamp((now - startedAt) / (float) DURATION_MILLIS, 0, 1);
        displayedHeight = Math.round(startHeight + (targetHeight - startHeight)
                * (1 - remaining * remaining * remaining));
        return displayedHeight;
    }

    void hold(long now) {
        holdUntil = now + WHEEL_HOLD_MILLIS;
    }

    void reset() {
        initialized = false;
        holdUntil = 0;
    }
}
