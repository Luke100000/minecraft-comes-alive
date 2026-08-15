package net.conczin.mca.cobalt.network;

import java.io.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

public interface Message extends Serializable {
    static Message decode(FriendlyByteBuf b) {
        byte[] data = new byte[b.readableBytes()];
        b.readBytes(data);

        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return (Message)ois.readObject();
        } catch (IOException | ClassNotFoundException e) {
            throw new RuntimeException("SneakyThrows", e);
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