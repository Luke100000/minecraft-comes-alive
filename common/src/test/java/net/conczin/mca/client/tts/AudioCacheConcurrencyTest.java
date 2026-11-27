package net.conczin.mca.client.tts;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;
import net.conczin.mca.Config;
import net.conczin.mca.NativeConfigDefaultsExtension;
import net.conczin.mca.client.tts.sound.PCMAudioStream;
import net.conczin.mca.config.ConfigScope;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AudioCacheConcurrencyTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void overlappingVoiceRequestsMustProduceIndependentSha256Keys() throws Exception {
        int workers = 12;
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try (var pool = Executors.newFixedThreadPool(workers)) {
            for (int worker = 0; worker < workers; worker++) {
                final int id = worker;
                results.add(pool.submit(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 2_000; iteration++) {
                        String phrase = "villager voice " + id + " - " + iteration;
                        byte[] expected = MessageDigest.getInstance("SHA-256")
                                .digest(phrase.getBytes(StandardCharsets.UTF_8));
                        if (!HexFormat.of().formatHex(expected).equals(AudioCache.getHash(phrase))) {
                            return false;
                        }
                    }
                    return true;
                }));
            }
            start.countDown();
            for (Future<Boolean> result : results) {
                assertTrue(result.get(), "Concurrent voice requests must never share digest state");
            }
        }
    }

    @Test
    void concurrentCachedRetrieveDeduplicatesInFlightAndNeverExposesPartialFiles() throws Exception {
        String identifier = "concurrency_test/" + UUID.randomUUID() + ".ogg";
        Path targetPath = Path.of("tts_cache").resolve(identifier);
        byte[] fullPayload = new byte[1024];
        ThreadLocalRandom.current().nextBytes(fullPayload);

        CountDownLatch partialFlushed = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);
        AtomicInteger retrieverRuns = new AtomicInteger();

        try (var pool = Executors.newFixedThreadPool(6)) {
            CompletableFuture<Boolean> leader = CompletableFuture.supplyAsync(() ->
                    AudioCache.cachedRetrieve(identifier, out -> {
                        retrieverRuns.incrementAndGet();
                        try {
                            // Write 256 bytes (> MIN_SIZE = 128) first to reproduce partial-file race
                            out.write(fullPayload, 0, 256);
                            out.flush();
                            partialFlushed.countDown();
                            assertTrue(allowCompletion.await(5, TimeUnit.SECONDS));
                            out.write(fullPayload, 256, fullPayload.length - 256);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(e);
                        }
                    }), pool);

            assertTrue(partialFlushed.await(5, TimeUnit.SECONDS), "Leader never flushed initial >128-byte chunk");
            assertFalse(Files.exists(targetPath), "Target cache file must not exist before atomic swap");
            assertNull(AudioCache.readFromDisk(identifier), "Readers must not observe partial >128-byte audio");

            List<CompletableFuture<Boolean>> followers = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                followers.add(CompletableFuture.supplyAsync(() ->
                        AudioCache.cachedRetrieve(identifier, out -> {
                            retrieverRuns.incrementAndGet();
                            writeUnchecked(out, fullPayload);
                        }), pool));
            }

            Thread.sleep(50);
            for (CompletableFuture<Boolean> follower : followers) {
                assertFalse(follower.isDone(), "Follower returned early before leader completed atomic move");
            }

            allowCompletion.countDown();
            assertTrue(leader.get(5, TimeUnit.SECONDS));
            for (CompletableFuture<Boolean> follower : followers) {
                assertTrue(follower.get(5, TimeUnit.SECONDS));
            }

            assertEquals(1, retrieverRuns.get(), "Concurrent misses for the same key must share one download");
            ByteBuffer cached = AudioCache.readFromDisk(identifier);
            assertNotNull(cached);
            byte[] actual = new byte[cached.remaining()];
            cached.get(actual);
            assertArrayEquals(fullPayload, actual);
        } finally {
            allowCompletion.countDown();
            Files.deleteIfExists(targetPath);
        }
    }

    @Test
    void fatalCacheWriteFailureReleasesWaitersAndAllowsRetry() throws Exception {
        String identifier = "concurrency_test/" + UUID.randomUUID() + ".ogg";
        Path targetPath = Path.of("tts_cache").resolve(identifier);
        CountDownLatch writing = new CountDownLatch(1);
        CountDownLatch failWrite = new CountDownLatch(1);
        AssertionError failure = new AssertionError("Fatal retriever failure");
        try (var pool = Executors.newSingleThreadExecutor()) {
            Future<Boolean> leader = pool.submit(() -> AudioCache.cachedRetrieve(identifier, out -> {
                writing.countDown();
                try {
                    assertTrue(failWrite.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(e);
                }
                throw failure;
            }));
            assertTrue(writing.await(5, TimeUnit.SECONDS));

            FutureTask<Boolean> followerResult = new FutureTask<>(() -> AudioCache.cachedRetrieve(identifier,
                    out -> fail("Follower must join the active write")));
            Thread follower = Thread.ofVirtual().start(followerResult);
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (follower.getState() != Thread.State.WAITING && !followerResult.isDone()
                        && System.nanoTime() < deadline) {
                    Thread.sleep(1);
                }
                assertEquals(Thread.State.WAITING, follower.getState(), "Follower must be waiting for the active write");
                failWrite.countDown();

                assertSame(failure, assertThrows(ExecutionException.class, () -> leader.get(5, TimeUnit.SECONDS)).getCause());
                ExecutionException followerFailure = assertThrows(ExecutionException.class,
                        () -> followerResult.get(5, TimeUnit.SECONDS));
                assertSame(failure, assertInstanceOf(CompletionException.class, followerFailure.getCause()).getCause());
                assertFalse(Files.exists(targetPath));
                assertTrue(AudioCache.cachedRetrieve(identifier, out -> writeUnchecked(out, new byte[256])));
            } finally {
                failWrite.countDown();
                followerResult.cancel(true);
            }
        } finally {
            Files.deleteIfExists(targetPath);
        }
    }

    @Test
    void truncatedOnlineAudioCanBeRetriedWithoutPublishingPartialFile() throws Exception {
        byte[] audio = new byte[512];
        ThreadLocalRandom.current().nextBytes(audio);
        HttpServer server = startAudioServer("/v1/tts/xtts-v2", audio);
        String identifier = "concurrency_test/" + UUID.randomUUID() + ".ogg";
        Path targetPath = Path.of("tts_cache").resolve(identifier);
        var urlSetting = Config.CLIENT.onlineTTSServer;
        String previousUrl = urlSetting.get();
        ModConfigSpec.ConfigValue<?> nativeUrl = NativeConfigDefaultsExtension.spec(ConfigScope.CLIENT)
                .getValues().get(urlSetting.getPath());
        try {
            String localUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            urlSetting.set(localUrl);
            nativeUrl.clearCache();
            assertEquals(localUrl, urlSetting.get());
            OnlineSpeechManager manager = new OnlineSpeechManager();
            assertFalse(AudioCache.cachedRetrieve(identifier, out -> manager.downloadAudio(out, "en", "male_0", "Hello")));
            assertFalse(Files.exists(targetPath));
            assertTrue(AudioCache.cachedRetrieve(identifier, out -> manager.downloadAudio(out, "en", "male_0", "Hello")));
            assertArrayEquals(audio, Files.readAllBytes(targetPath));
        } finally {
            urlSetting.set(previousUrl);
            nativeUrl.clearCache();
            server.stop(0);
            Files.deleteIfExists(targetPath);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void truncatedRealtimeAudioCanBeRetriedWithoutPublishingPartialStream(boolean persistent) throws Exception {
        byte[] audio = new byte[512];
        ThreadLocalRandom.current().nextBytes(audio);
        HttpServer server = startAudioServer("/v1/tts/piper/speak", audio);
        String identifier = "concurrency_test/" + UUID.randomUUID();
        Path targetPath = Path.of("tts_cache").resolve(identifier);
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            RealtimeSpeechManager manager = new RealtimeSpeechManager(url);
            assertFalse(AudioCache.get(identifier, out -> manager.downloadAudio(out, "voice", "Hello"), persistent));
            assertFalse(Files.exists(targetPath));
            assertFalse(AudioCache.inMemory.containsKey(identifier));
            assertTrue(AudioCache.get(identifier, out -> manager.downloadAudio(out, "voice", "Hello"), persistent));
            assertArrayEquals(audio, readBytes(AudioCache.getPCMAudioStream(identifier), audio.length));
        } finally {
            server.stop(0);
            AudioCache.inMemory.remove(identifier);
            Files.deleteIfExists(targetPath);
        }
    }

    private static HttpServer startAudioServer(String path, byte[] audio) throws IOException {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(path, exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Connection", "close");
                exchange.sendResponseHeaders(200, audio.length);
                int bytesToSend = requests.getAndIncrement() == 0 ? 256 : audio.length;
                exchange.getResponseBody().write(audio, 0, bytesToSend);
                exchange.getResponseBody().flush();
            } finally {
                exchange.close();
            }
        });
        server.start();
        return server;
    }

    @Test
    void jsonPayloadsEscapeQuotesAndNewlinesAndShortLanguageCodesAreGuarded() throws Exception {
        String dialogue = "He whispered \"Run!\"\nLine two \\ tab\there.";

        JsonObject realtime = JsonParser.parseString(
                RealtimeSpeechManager.buildSpeakPayload("en_US-lessac", dialogue)).getAsJsonObject();
        assertEquals(dialogue, realtime.get("text").getAsString());
        assertEquals("en_US-lessac", realtime.get("voice").getAsString());

        JsonObject elevenlabs = JsonParser.parseString(
                ElevenlabsSpeechManager.buildSpeakPayload(dialogue, "eleven_multilingual_v2")).getAsJsonObject();
        assertEquals(dialogue, elevenlabs.get("text").getAsString());
        assertEquals("eleven_multilingual_v2", elevenlabs.get("model_id").getAsString());
        assertTrue(elevenlabs.getAsJsonObject("voice_settings").get("use_speaker_boost").getAsBoolean());

        RealtimeSpeechManager manager = new RealtimeSpeechManager("http://127.0.0.1:1/");
        manager.voiceInfoMap = Map.of(
                "en_male", new RealtimeSpeechManager.VoiceInfo("en_male", "en_US", "male"),
                "short_lang", new RealtimeSpeechManager.VoiceInfo("short_lang", "e", "male"),
                "empty_lang", new RealtimeSpeechManager.VoiceInfo("empty_lang", "", "male")
        );

        assertEquals(List.of(), manager.getVoicesAsync("e", "male").get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), manager.getVoicesAsync("", "male").get(5, TimeUnit.SECONDS));
        assertEquals(List.of(), manager.getVoicesAsync(null, "male").get(5, TimeUnit.SECONDS));
        assertEquals(List.of("en_male"), manager.getVoicesAsync("en_gb", "male").get(5, TimeUnit.SECONDS));
    }

    @Test
    void realtimeBuffersAreIsolatedPerRequestAndFailureCooldownAndStopPreviewWork() throws Exception {
        String key1 = RealtimeSpeechManager.cacheKey("voice_a", "First dynamic phrase", false, 101L);
        String key2 = RealtimeSpeechManager.cacheKey("voice_b", "Second dynamic phrase", false, 102L);
        assertEquals("realtime/101", key1);
        assertEquals("realtime/102", key2);

        byte[] pcm1 = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] pcm2 = new byte[]{9, 8, 7, 6};
        try {
            assertTrue(AudioCache.get(key1, out -> writeUnchecked(out, pcm1), false));
            PCMAudioStream stream1 = AudioCache.getPCMAudioStream(key1);

            assertTrue(AudioCache.get(key2, out -> writeUnchecked(out, pcm2), false));
            PCMAudioStream stream2 = AudioCache.getPCMAudioStream(key2);

            assertNotSame(stream1, stream2);
            assertArrayEquals(pcm1, readBytes(stream1, 16), "Later uncached realtime line must not overwrite active stream");
            assertArrayEquals(pcm2, readBytes(stream2, 16));
        } finally {
            AudioCache.inMemory.remove(key1);
            AudioCache.inMemory.remove(key2);
        }

        AtomicInteger serverHits = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/tts/piper/voices", exchange -> {
            serverHits.incrementAndGet();
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
            RealtimeSpeechManager failingManager = new RealtimeSpeechManager(baseUrl);
            assertNull(failingManager.getVoicesAsync("en_gb", "male").get(5, TimeUnit.SECONDS));
            assertNull(failingManager.getVoicesAsync("en_gb", "male").get(5, TimeUnit.SECONDS));
            assertEquals(1, serverHits.get(), "Failed voice fetches must back off for 30s instead of retrying immediately");
        } finally {
            server.stop(0);
        }

        SpeechManager speechManager = new SpeechManager();
        UUID previewVillagerId = UUID.randomUUID();
        speechManager.beginVoiceRequest(previewVillagerId);
        speechManager.stopPreview(previewVillagerId);

        Field latestField = SpeechManager.class.getDeclaredField("latestVoiceRequest");
        latestField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<UUID, Long> latestRequests = (Map<UUID, Long>) latestField.get(speechManager);
        assertFalse(latestRequests.containsKey(previewVillagerId),
                "stopPreview must clear pending latestVoiceRequest so closing VillagerEditorScreen cancels late TTS playback");
    }

    private static void writeUnchecked(OutputStream out, byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] readBytes(PCMAudioStream stream, int maxSize) throws IOException {
        ByteBuffer buffer = stream.read(maxSize);
        assertNotNull(buffer);
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
