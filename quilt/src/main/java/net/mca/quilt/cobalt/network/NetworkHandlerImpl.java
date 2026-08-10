package net.mca.quilt.cobalt.network;

import io.netty.buffer.Unpooled;
import net.fabricmc.api.EnvType;
import net.mca.MCA;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.quiltmc.loader.api.minecraft.MinecraftQuiltLoader;
import org.quiltmc.qsl.networking.api.ServerPlayNetworking;
import org.quiltmc.qsl.networking.api.client.ClientPlayNetworking;

import java.util.Locale;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    @Override
    public <T extends Message> void registerMessage(Class<T> msg) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, msg.getName().toLowerCase(Locale.ENGLISH));

        ServerPlayNetworking.registerGlobalReceiver(id, (server, player, handler, buffer, responder) -> {
            Message m = Message.decode(buffer);
            server.execute(() -> m.receive(player));
        });

        if (MinecraftQuiltLoader.getEnvironmentType() == EnvType.CLIENT) {
            ClientProxy.register(id, msg);
        }
    }

    @Override
    public void sendToServer(Message m) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        m.encode(buf);
        ClientPlayNetworking.send(new ResourceLocation(MCA.MOD_ID, m.getClass().getName().toLowerCase(Locale.ENGLISH)), buf);
    }

    @Override
    public void sendToPlayer(Message m, ServerPlayer e) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        m.encode(buf);
        ServerPlayNetworking.send(e, new ResourceLocation(MCA.MOD_ID, m.getClass().getName().toLowerCase(Locale.ENGLISH)), buf);
    }

    // Fabric's APIs are not side-agnostic.
    // We punt this to a separate class file to keep it from being eager-loaded on a server environment.
    private static final class ClientProxy {
        private ClientProxy() {throw new RuntimeException("new ClientProxy()");}
        public static <T extends Message> void register(ResourceLocation id, Class<T> msg) {
            ClientPlayNetworking.registerGlobalReceiver(id, (client, ignore1, buffer, ignore2) -> {
                Message m = Message.decode(buffer);
                client.execute(m::receive);
            });
        }
    }
}
