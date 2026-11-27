package net.conczin.mca.forge.cobalt.network;

import net.conczin.mca.MCA;
import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.cobalt.network.NetworkHandler.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

public class NetworkHandlerImpl extends NetworkHandler.Impl {
    private final String PROTOCOL_VERSION = "1";
    private final SimpleChannel channel = NetworkRegistry.newSimpleChannel(
            MCA.locate("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );
    private int id = 0;

    @Override
    public <T extends Message> void registerMessage(Class<T> msg, Direction direction) {
        NetworkDirection networkDirection =
                direction == Direction.SERVERBOUND ? NetworkDirection.PLAY_TO_SERVER : NetworkDirection.PLAY_TO_CLIENT;
        channel.registerMessage(id++, msg,
                Message::encode,
                buffer -> Message.decode(buffer, msg),
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> {
                        ServerPlayer sender = ctx.get().getSender();
                        if (sender == null) {
                            m.receive();
                        } else {
                            m.receive(sender);
                        }
                    });
                    ctx.get().setPacketHandled(true);
                }, Optional.of(networkDirection));
    }

    @Override
    public void sendToServer(Message m) {
        channel.sendToServer(m);
    }

    @Override
    public void sendToPlayer(Message m, ServerPlayer e) {
        channel.send(PacketDistributor.PLAYER.with(() -> e), m);
    }
}
