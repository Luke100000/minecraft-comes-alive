package net.conczin.mca.client.tts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AudioCacheConcurrencyTest {
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
}
