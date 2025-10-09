package net.conczin.mca.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public class Network {
    private static Sender sender;
    private static ClientSender clientSender;

    public static void registerSender(Sender sender) {
        Network.sender = sender;
    }

    public static void registerClientSender(ClientSender clientSender) {
        Network.clientSender = clientSender;
    }

    public static void sendToServer(HandleablePayload payload) {
        clientSender.sendToServer(payload);
    }

    public static void sendToPlayer(HandleablePayload payload, ServerPlayer player) {
        sender.sendToPlayer(player, payload);
    }

    public interface Registrar {
        <T extends HandleablePayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean isServer);
    }

    public interface Sender {
        void sendToPlayer(ServerPlayer player, HandleablePayload payload);
    }

    public interface ClientSender {
        void sendToServer(HandleablePayload payload);
    }
}
