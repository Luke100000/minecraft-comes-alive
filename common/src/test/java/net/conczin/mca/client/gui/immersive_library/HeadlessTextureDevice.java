package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.GpuDeviceBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.function.Supplier;

/** Exercises real DynamicTexture ownership without creating an OpenGL window. */
public final class HeadlessTextureDevice implements BeforeEachCallback, AfterEachCallback {
    private Field deviceField;
    private GpuDevice previousDevice;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        deviceField = RenderSystem.class.getDeclaredField("DEVICE");
        deviceField.setAccessible(true);
        previousDevice = (GpuDevice) deviceField.get(null);
        CommandEncoderBackend encoder = (CommandEncoderBackend) Proxy.newProxyInstance(
                CommandEncoderBackend.class.getClassLoader(), new Class<?>[]{CommandEncoderBackend.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "writeToTexture" -> {
                        // Check that the upload reads live pixels; ownership remains with DynamicTexture.
                        ((ByteBuffer) args[1]).get(0);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        GpuDeviceBackend backend = (GpuDeviceBackend) Proxy.newProxyInstance(
                GpuDeviceBackend.class.getClassLoader(), new Class<?>[]{GpuDeviceBackend.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createCommandEncoder" -> encoder;
                    case "createTexture" -> new TestTexture(args);
                    case "createTextureView" -> new TestTextureView((GpuTexture) args[0], (int) args[1], (int) args[2]);
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        deviceField.set(null, new GpuDevice(backend, () -> {}));
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        if (deviceField != null) {
            deviceField.set(null, previousDevice);
        }
    }

    private static final class TestTexture extends GpuTexture {
        private boolean closed;

        private TestTexture(Object[] args) {
            super((int) args[1], args[0] instanceof Supplier<?> label ? String.valueOf(label.get()) : (String) args[0],
                    (GpuFormat) args[2], (int) args[3], (int) args[4], (int) args[5], (int) args[6]);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }

    private static final class TestTextureView extends GpuTextureView {
        private boolean closed;

        private TestTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
            super(texture, baseMipLevel, mipLevels);
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public boolean isClosed() {
            return closed;
        }
    }
}
