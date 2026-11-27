package net.conczin.mca.client.gui.immersive_library;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.SamplerCache;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Exercises real DynamicTexture ownership without creating a graphics device or window. */
public final class HeadlessTextureDevice implements BeforeEachCallback, AfterEachCallback {
    private Field deviceField;
    private GpuDevice previousDevice;
    private GpuSampler[] samplers;
    private GpuSampler[] previousSamplers;

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {
        deviceField = RenderSystem.class.getDeclaredField("DEVICE");
        deviceField.setAccessible(true);
        previousDevice = (GpuDevice) deviceField.get(null);
        Field samplerField = SamplerCache.class.getDeclaredField("samplers");
        samplerField.setAccessible(true);
        samplers = (GpuSampler[]) samplerField.get(RenderSystem.getSamplerCache());
        previousSamplers = samplers.clone();
        CommandEncoder encoder = (CommandEncoder) Proxy.newProxyInstance(
                CommandEncoder.class.getClassLoader(), new Class<?>[]{CommandEncoder.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "writeToTexture" -> {
                        // Check that the upload reads live pixels; ownership remains with DynamicTexture.
                        if (((GpuTexture) args[0]).isClosed()) {
                            throw new IllegalStateException("Upload to closed texture");
                        }
                        ((NativeImage) args[1]).getPixel(0, 0);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        GpuDevice device = (GpuDevice) Proxy.newProxyInstance(
                GpuDevice.class.getClassLoader(), new Class<?>[]{GpuDevice.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "createCommandEncoder" -> encoder;
                    case "createTexture" -> new TestTexture(args);
                    case "createTextureView" -> {
                        GpuTexture texture = (GpuTexture) args[0];
                        yield args.length == 1
                                ? new TestTextureView(texture, 0, texture.getMipLevels())
                                : new TestTextureView(texture, (int) args[1], (int) args[2]);
                    }
                    case "createSampler" -> testSampler(args);
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        try {
            deviceField.set(null, device);
            RenderSystem.getSamplerCache().initialize();
        } catch (Exception | Error e) {
            restoreDevice();
            throw e;
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws Exception {
        restoreDevice();
    }

    private void restoreDevice() throws IllegalAccessException {
        if (previousSamplers != null) {
            System.arraycopy(previousSamplers, 0, samplers, 0, previousSamplers.length);
            previousSamplers = null;
        }
        if (deviceField != null) {
            deviceField.set(null, previousDevice);
            deviceField = null;
        }
    }

    private static GpuSampler testSampler(Object[] args) {
        AtomicBoolean closed = new AtomicBoolean();
        return (GpuSampler) Proxy.newProxyInstance(
                GpuSampler.class.getClassLoader(), new Class<?>[]{GpuSampler.class},
                (proxy, method, parameters) -> switch (method.getName()) {
                    case "getAddressModeU" -> args[0];
                    case "getAddressModeV" -> args[1];
                    case "getMinFilter" -> args[2];
                    case "getMagFilter" -> args[3];
                    case "getMaxAnisotropy" -> args[4];
                    case "getMaxLod" -> args[5];
                    case "isClosed" -> closed.get();
                    case "close" -> {
                        closed.set(true);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    private static final class TestTexture implements GpuTexture {
        private final int usage;
        private final String label;
        private final GpuFormat format;
        private final int width;
        private final int height;
        private final int depthOrLayers;
        private final int mipLevels;
        private boolean closed;

        private TestTexture(Object[] args) {
            usage = (int) args[1];
            label = args[0] instanceof Supplier<?> supplier ? String.valueOf(supplier.get()) : (String) args[0];
            format = (GpuFormat) args[2];
            width = (int) args[3];
            height = (int) args[4];
            depthOrLayers = (int) args[5];
            mipLevels = (int) args[6];
        }

        @Override
        public int getWidth(int mipLevel) {
            return width >> mipLevel;
        }

        @Override
        public int getHeight(int mipLevel) {
            return height >> mipLevel;
        }

        @Override
        public int getDepthOrLayers() {
            return depthOrLayers;
        }

        @Override
        public int getMipLevels() {
            return mipLevels;
        }

        @Override
        public GpuFormat getFormat() {
            return format;
        }

        @Override
        public int usage() {
            return usage;
        }

        @Override
        public String getLabel() {
            return label;
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

    private static final class TestTextureView implements GpuTextureView {
        private final GpuTexture texture;
        private final int baseMipLevel;
        private final int mipLevels;
        private boolean closed;

        private TestTextureView(GpuTexture texture, int baseMipLevel, int mipLevels) {
            this.texture = texture;
            this.baseMipLevel = baseMipLevel;
            this.mipLevels = mipLevels;
        }

        @Override
        public GpuTexture texture() {
            return texture;
        }

        @Override
        public int baseMipLevel() {
            return baseMipLevel;
        }

        @Override
        public int mipLevels() {
            return mipLevels;
        }

        @Override
        public int getWidth(int mipLevel) {
            return texture.getWidth(mipLevel + baseMipLevel);
        }

        @Override
        public int getHeight(int mipLevel) {
            return texture.getHeight(mipLevel + baseMipLevel);
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
