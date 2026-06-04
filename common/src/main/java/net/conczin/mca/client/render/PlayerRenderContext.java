package net.conczin.mca.client.render;

import java.util.Optional;
import java.util.UUID;

public final class PlayerRenderContext {
    private static final ThreadLocal<UUID> CURRENT_PLAYER_UUID = new ThreadLocal<>();

    private PlayerRenderContext() {
    }

    public static void setCurrentPlayerUuid(UUID uuid) {
        CURRENT_PLAYER_UUID.set(uuid);
    }

    public static void clearCurrentPlayerUuid() {
        CURRENT_PLAYER_UUID.remove();
    }

    public static Optional<UUID> currentPlayerUuid() {
        return Optional.ofNullable(CURRENT_PLAYER_UUID.get());
    }
}