package net.conczin.mca.client.tts;

import net.conczin.mca.MCA;
import net.conczin.mca.client.tts.sound.PCMAudioStream;

import java.io.*;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AudioCache {
    private static final int MIN_SIZE = 128;
    private static final String CACHE_DIR = "tts_cache/";
    private static final MessageDigest MESSAGEDIGEST;
    public static Map<String, PCMAudioStream> inMemory = new ConcurrentHashMap<>();

    static {
        try {
            MESSAGEDIGEST = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private static void setInMemoryAudio(String identifier, ByteBuffer buffer) {
        if (inMemory.containsKey(identifier)) {
            inMemory.get(identifier).setBuffer(buffer);
        } else {
            inMemory.put(identifier, new PCMAudioStream(buffer));
        }
    }

    public static PCMAudioStream getPCMAudioStream(String identifier) {
        if (inMemory.containsKey(identifier)) {
            return inMemory.get(identifier);
        } else {
            return new PCMAudioStream(readFromDisk(identifier));
        }
    }

    public static boolean get(String identifier, Consumer<OutputStream> retriever, boolean persistent) {
        if (persistent) {
            return cachedRetrieve(identifier, retriever);
        } else {
            ByteBuffer byteBuffer = retrieve(retriever);
            if (byteBuffer == null) {
                return false;
            } else {
                AudioCache.setInMemoryAudio(identifier, byteBuffer);
                return true;
            }
        }
    }

    private static ByteBuffer retrieve(Consumer<OutputStream> retriever) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            retriever.accept(baos);
            return ByteBuffer.wrap(baos.toByteArray());
        } catch (IOException e) {
            return null;
        }
    }

    public static ByteBuffer readFromDisk(String identifier) {
        File cacheFile = new File(CACHE_DIR, identifier);
        if (!isSane(cacheFile)) return null;

        try (FileInputStream fis = new FileInputStream(cacheFile)) {
            return ByteBuffer.wrap(fis.readAllBytes());
        } catch (IOException e) {
            MCA.LOGGER.error("Failed to retrieve cached audio file: {}", identifier, e);
            return null;
        }
    }

    public static boolean cachedRetrieve(String identifier, Consumer<OutputStream> retriever) {
        try {
            File cacheFile = new File(CACHE_DIR, identifier);
            if (isSane(cacheFile)) {
                return true;
            } else {
                //noinspection ResultOfMethodCallIgnored
                cacheFile.getParentFile().mkdirs();
                try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
                    retriever.accept(fos);
                }
                return isSane(cacheFile);
            }
        } catch (IOException e) {
            MCA.LOGGER.error("Failed to cache audio file: {}", identifier, e);
            return false;
        }
    }

    private static boolean isSane(File cacheFile) {
        return cacheFile.exists() && cacheFile.length() > MIN_SIZE;
    }

    public static String getHash(String text) {
        MESSAGEDIGEST.update(text.getBytes());
        return toHex(MESSAGEDIGEST.digest()).toLowerCase(Locale.ROOT);
    }

    public static String toHex(byte[] bytes) {
        BigInteger bi = new BigInteger(1, bytes);
        return String.format(Locale.ROOT, "%0" + (bytes.length << 1) + "X", bi);
    }
}
