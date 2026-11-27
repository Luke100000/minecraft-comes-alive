package net.conczin.mca.network;

import net.conczin.mca.cobalt.network.Message;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.*;

public abstract class NbtDataMessage implements Message {
    @Serial
    private static final long serialVersionUID = 3409849549326097419L;

    private final Data data;

    public NbtDataMessage(CompoundTag data) {
        this.data = new Data(data);
    }

    public CompoundTag getData() {
        return data.nbt;
    }

    static final class Data implements Serializable {
        @Serial
        private static final long serialVersionUID = 5728742776742369248L;

        transient CompoundTag nbt;

        Data(CompoundTag nbt) {
            this.nbt = nbt;
        }

        @Serial
        private void writeObject(ObjectOutputStream out) throws IOException {
            NbtIo.write(nbt, out);
        }

        @Serial
        private void readObject(ObjectInputStream in) throws IOException {
            // Match the NBT accounting limit used by Minecraft's packet buffer.
            nbt = NbtIo.read(in, new NbtAccounter(2 * 1024 * 1024));
        }
    }
}
