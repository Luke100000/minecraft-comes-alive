package net.conczin.mca.cobalt.network;

import io.netty.buffer.ByteBufInputStream;
import io.netty.handler.codec.DecoderException;
import net.conczin.mca.network.PacketFilter;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public interface Message extends Serializable {
    static <T extends Message> T decode(FriendlyByteBuf buffer, Class<T> expectedType) {
        if (buffer.readableBytes() > PacketFilter.MAX_PACKET_BYTES) {
            throw new DecoderException("MCA packet exceeds the size limit");
        }
        try (ObjectInputStream input = PacketFilter.openStream(new ByteBufInputStream(buffer), expectedType)) {
            Object message = input.readObject();
            if (message == null || message.getClass() != expectedType) {
                throw new DecoderException("Unexpected MCA packet type");
            }
            return expectedType.cast(message);
        } catch (IOException | ClassNotFoundException e) {
            throw new DecoderException("Invalid MCA packet " + expectedType.getSimpleName(), e);
        }
    }

    default void encode(FriendlyByteBuf b) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(this);
        } catch (IOException e) {
            throw new RuntimeException("SneakyThrows", e);
        }

        b.writeBytes(baos.toByteArray());
    }

    default void receive() {
        // N/A
    }

    default void receive(ServerPlayer player) {
        // N/A
    }
}