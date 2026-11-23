package net.conczin.mca.client.tts;

import net.conczin.mca.MCA;
import net.conczin.mca.util.AtomicFileMove;
import net.conczin.mca.client.tts.sound.PCMAudioStream;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public class AudioCache {
    private static final int MIN_SIZE = 128;
    private static final int MAX_IN_MEMORY_STREAMS = 64;
    private static final Path CACHE_ROOT = Path.of("tts_cache");
    public static Map<String, PCMAudioStream> inMemory = new ConcurrentHashMap<>();
    private static final ConcurrentLinkedQueue<String> IN_MEMORY_ORDER = new ConcurrentLinkedQueue<>();
    private static final ConcurrentHashMap<Path, CompletableFuture<Boolean>> IN_FLIGHT = new ConcurrentHashMap<>();

    private static void setInMemoryAudio(String identifier, ByteBuffer buffer) {
        PCMAudioStream stream = new PCMAudioStream(buffer.asReadOnlyBuffer());
        if (inMemory.put(identifier, stream) == null) {
            IN_MEMORY_ORDER.offer(identifier);
            while (inMemory.size() > MAX_IN_MEMORY_STREAMS) {
                String oldest = IN_MEMORY_ORDER.poll();
                if (oldest == null) {
                    break;
                }
                inMemory.remove(oldest);
            }
        }
    }

    public static PCMAudioStream getPCMAudioStream(String identifier) {
        PCMAudioStream stream = inMemory.get(identifier);
        if (stream != null) {
            return stream;
        }
        return new PCMAudioStream(readFromDisk(identifier));
    }

    public static boolean get(String identifier, Consumer<OutputStream> retriever, boolean persistent) {
        if (persistent) {
            return cachedRetrieve(identifier, retriever);
        }
        ByteBuffer byteBuffer = retrieve(retriever);
        if (byteBuffer == null) {
            return false;
        }
        setInMemoryAudio(identifier, byteBuffer);
        return true;
    }

    private static ByteBuffer retrieve(Consumer<OutputStream> retriever) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            retriever.accept(baos);
            byte[] bytes = baos.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            return ByteBuffer.wrap(bytes);
        } catch (IOException | RuntimeException e) {
            MCA.LOGGER.warn("Failed to retrieve transient TTS audio", e);
            return null;
        }
    }

    static Path resolveCachePath(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        try {
            Path root = CACHE_ROOT.toAbsolutePath().normalize();
            Path resolved = root.resolve(identifier).normalize();
            return resolved.startsWith(root) ? resolved : null;
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static ByteBuffer readFromDisk(String identifier) {
        Path cachePath = resolveCachePath(identifier);
        if (cachePath == null || !isSane(cachePath)) {
            return null;
        }

        try {
            return ByteBuffer.wrap(Files.readAllBytes(cachePath));
        } catch (IOException e) {
            MCA.LOGGER.error("Failed to retrieve cached audio file: {}", identifier, e);
            return null;
        }
    }

    public static boolean cachedRetrieve(String identifier, Consumer<OutputStream> retriever) {
        Path cachePath = resolveCachePath(identifier);
        if (cachePath == null) {
            return false;
        }
        if (isSane(cachePath)) {
            return true;
        }

        CompletableFuture<Boolean> ownFuture = new CompletableFuture<>();
        CompletableFuture<Boolean> existing = IN_FLIGHT.putIfAbsent(cachePath, ownFuture);
        if (existing != null) {
            return existing.join();
        }

        try {
            if (isSane(cachePath)) {
                ownFuture.complete(true);
                return true;
            }
            boolean cached = writeCacheAtomically(cachePath, retriever);
            ownFuture.complete(cached);
            return cached;
        } catch (IOException | RuntimeException e) {
            ownFuture.complete(false);
            MCA.LOGGER.error("Failed to cache audio file: {}", identifier, e);
            return false;
        } catch (Error e) {
            ownFuture.completeExceptionally(e);
            throw e;
        } finally {
            IN_FLIGHT.remove(cachePath, ownFuture);
        }
    }

    private static boolean writeCacheAtomically(Path cachePath, Consumer<OutputStream> retriever) throws IOException {
        Path parent = cachePath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path tempFile = Files.createTempFile(parent, cachePath.getFileName().toString() + ".", ".tmp");
        try (Closeable cleanup = () -> Files.deleteIfExists(tempFile)) {
            try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(tempFile))) {
                retriever.accept(out);
            }
            if (Files.size(tempFile) <= MIN_SIZE) {
                return false;
            }
            AtomicFileMove.replace(tempFile, cachePath);
            return isSane(cachePath);
        }
    }

    private static boolean isSane(Path cacheFile) {
        try {
            return Files.isRegularFile(cacheFile) && Files.size(cacheFile) > MIN_SIZE;
        } catch (IOException e) {
            return false;
        }
    }

    public static String getHash(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public static String toHex(byte[] bytes) {
        return HexFormat.of().withUpperCase().formatHex(bytes);
    }
}
