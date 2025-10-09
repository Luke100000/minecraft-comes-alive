package net.conczin.mca.client.tts.sound;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;

public class PCMAudioStream implements AudioStream {
    private static final AudioFormat FORMAT = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 22050, 16, 1, 2, 22050, false);
    private ByteBuffer buffer;

    public PCMAudioStream(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
    }

    @Override
    public ByteBuffer read(int size) {
        if (buffer == null) {
            return null;
        }
        int remaining = buffer.remaining();
        if (remaining <= 0) {
            return null;
        }
        int bytesToRead = Math.min(size, remaining);
        ByteBuffer result = buffer.slice();
        result.limit(bytesToRead);
        buffer.position(buffer.position() + bytesToRead);

        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(size);
        byteBuffer.put(result);
        byteBuffer.flip();

        return byteBuffer;
    }

    @Override
    public void close() throws IOException {
        // No-op
    }
}
