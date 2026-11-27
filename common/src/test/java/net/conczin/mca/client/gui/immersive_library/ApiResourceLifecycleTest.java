package net.conczin.mca.client.gui.immersive_library;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.conczin.mca.Config;
import net.conczin.mca.client.gui.immersive_library.responses.ErrorResponse;
import net.conczin.mca.client.gui.immersive_library.responses.IsAuthResponse;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ApiResourceLifecycleTest {
    @BeforeAll
    static void bootstrapMinecraftVersion() {
        SharedConstants.tryDetectVersion();
    }

    @Test
    void plainAndCompressedResponsesRemainReadableAcrossRepeatedRequests() throws Exception {
        withServer(server -> {
            server.createContext("/plain", exchange -> send(exchange, 200, "{\"authenticated\":true}"));
            server.createContext("/gzip", exchange -> {
                exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                byte[] body = gzip("{\"authenticated\":true}");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream output = exchange.getResponseBody()) {
                    output.write(body);
                }
            });
        }, () -> {
            for (int i = 0; i < 5; i++) {
                assertEquals(new IsAuthResponse(true), Api.request(Api.HttpMethod.GET, IsAuthResponse.class, "plain"));
                assertEquals(new IsAuthResponse(true), Api.request(Api.HttpMethod.GET, IsAuthResponse.class, "gzip"));
            }
        });
    }

    @Test
    void errorBodiesAndEmptyErrorStreamsPreserveErrorResults() throws Exception {
        withServer(server -> {
            server.createContext("/error", exchange -> send(exchange, 429, "{\"message\":\"Too many requests\"}"));
            server.createContext("/empty", exchange -> {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            });
        }, () -> {
            assertEquals(new ErrorResponse(429, "Too many requests"),
                    Api.request(Api.HttpMethod.GET, IsAuthResponse.class, "error"));
            assertEquals(new ErrorResponse(503, ""),
                    Api.request(Api.HttpMethod.GET, IsAuthResponse.class, "empty"));
        });
    }

    @Test
    void postBodyAndMalformedResponseDoNotPreventFollowingRequest() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        withServer(server -> {
            server.createContext("/post", exchange -> {
                received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                send(exchange, 200, "{\"authenticated\":true}");
            });
            server.createContext("/malformed", exchange -> send(exchange, 200, "{invalid"));
        }, () -> {
            assertInstanceOf(ErrorResponse.class,
                    Api.request(Api.HttpMethod.GET, IsAuthResponse.class, "malformed"));
            assertEquals(new IsAuthResponse(true), Api.request(Api.HttpMethod.POST,
                    IsAuthResponse.class, "post", null, Map.of("title", "Test asset")));
            assertTrue(received.get().contains("\"title\":\"Test asset\""));
        });
    }

    private static byte[] gzip(String json) throws IOException {
        try (var bytes = new java.io.ByteArrayOutputStream();
             var gzip = new GZIPOutputStream(bytes)) {
            gzip.write(json.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return bytes.toByteArray();
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void withServer(Configure configure, CheckedRunnable assertions) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String oldUrl = Config.CLIENT.immersiveLibraryUrl.get();
        try {
            configure.configure(server);
            server.start();
            Config.CLIENT.immersiveLibraryUrl.set("http://127.0.0.1:" + server.getAddress().getPort());
            assertions.run();
        } finally {
            Config.CLIENT.immersiveLibraryUrl.set(oldUrl);
            server.stop(0);
        }
    }

    @FunctionalInterface
    private interface Configure {
        void configure(HttpServer server);
    }

    @FunctionalInterface
    private interface CheckedRunnable {
        void run() throws Exception;
    }
}
