package net.conczin.mca.fabric.cobalt.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.conczin.mca.MCA;
import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.cobalt.network.NetworkHandler.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    private final Map<Class<?>, ResourceLocation> cache = new ConcurrentHashMap<>();

    private ResourceLocation getMessageIdentifier(Message msg) {
        return cache.computeIfAbsent(msg.getClass(), this::getMessageIdentifier);
    }

    private <T> ResourceLocation getMessageIdentifier(Class<T> msg) {
        return MCA.locate(msg.getSimpleName().toLowerCase(Locale.ROOT));
    }

    @Override
    public <T extends Message> void registerMessage(Class<T> msg, Direction direction) {
        ResourceLocation id = getMessageIdentifier(msg);

        if (direction == Direction.SERVERBOUND) {
            ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responder) -> {
                T message = Message.decode(buffer, msg);
                server.execute(() -> message.receive(player));
            });
        } else if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.register(id, msg);
        }
    }

    @Override
    public void sendToServer(Message msg) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        msg.encode(buf);
        ClientPlayNetworking.send(getMessageIdentifier(msg), buf);
    }

    @Override
    public void sendToPlayer(Message msg, ServerPlayer e) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        msg.encode(buf);
        ServerPlayNetworking.send(e, getMessageIdentifier(msg), buf);
    }

    // Fabric's APIs are not side-agnostic.
    // We punt this to a separate class file to keep it from being eager-loaded on a server environment.
    private static final class ClientProxy {
        private ClientProxy() {
            throw new RuntimeException("new ClientProxy()");
        }

        public static <T extends Message> void register(ResourceLocation id, Class<T> type) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, ignore1, buffer, ignore2) -> {
                T m = Message.decode(buffer, type);
                client.execute(m::receive);
            });
        }
    }
}
