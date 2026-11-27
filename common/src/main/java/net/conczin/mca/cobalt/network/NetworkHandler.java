package net.conczin.mca.cobalt.network;

import net.minecraft.server.level.ServerPlayer;

public abstract class NetworkHandler {
    public enum Direction {
        SERVERBOUND,
        CLIENTBOUND
    }

    private static Impl INSTANCE;

    public static <T extends Message> void registerMessage(Class<T> msg, Direction direction) {
        INSTANCE.registerMessage(msg, direction);
    }

    public static void sendToServer(Message m) {
        INSTANCE.sendToServer(m);
    }

    public static void sendToPlayer(Message m, ServerPlayer e) {
        INSTANCE.sendToPlayer(m, e);
    }

    public abstract static class Impl {
        protected Impl() {
            INSTANCE = this;
        }

        public abstract <T extends Message> void registerMessage(Class<T> msg, Direction direction);

        public abstract void sendToServer(Message m);

        public abstract void sendToPlayer(Message m, ServerPlayer e);
    }
}
