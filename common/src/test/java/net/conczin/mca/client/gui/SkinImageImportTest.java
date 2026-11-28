package net.conczin.mca.client.gui;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SkinImageImportTest {
    @TempDir Path temporaryFiles;

    @Test
    void rejectsRemoteImageLargerThanTwoMiBWhileReading() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                out.write(new byte[2 * 1024 * 1024 + 1]);
            } catch (IOException ignored) {
                // The client may disconnect as soon as its byte budget is exhausted.
            }
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/large";
            assertThrows(IOException.class, () -> {
                try (InputStream stream = openImage(url)) {
                    stream.readAllBytes();
                }
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    void localImagePathStillOpensAsAFile() throws Exception {
        Path file = temporaryFiles.resolve("skin.png");
        Files.write(file, new byte[]{1, 2, 3});
        try (InputStream stream = openImage(file.toString())) {
            assertArrayEquals(new byte[]{1, 2, 3}, stream.readAllBytes());
        }
    }

    private static InputStream openImage(String path) throws Exception {
        Method method = SkinLibraryScreen.class.getDeclaredMethod("openImage", String.class);
        method.setAccessible(true);
        try {
            return (InputStream) method.invoke(null, path);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof IOException io) {
                throw io;
            }
            throw exception;
        }
    }
}
