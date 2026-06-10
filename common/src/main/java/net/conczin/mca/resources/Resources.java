package net.conczin.mca.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.interaction.InteractionPredicate;
import org.apache.commons.io.IOUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

public interface Resources {
    String RESOURCE_PREFIX = "assets/mca/";

    Gson GSON = new GsonBuilder()
            .registerTypeAdapter(InteractionPredicate.class, InteractionPredicateTypeAdapter.INSTANCE)
            .create();

    static String read(String path) throws IOException {
        String resourcePath = RESOURCE_PREFIX + path;
        try (InputStream stream = MCA.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new FileNotFoundException(resourcePath);
            }
            return IOUtils.toString(stream, StandardCharsets.UTF_8);
        }
    }

    static <T> T read(String path, Type type) throws BrokenResourceException {
        try {
            return GSON.fromJson(Resources.read(path), type);
        } catch (IOException | JsonParseException e) {
            throw new BrokenResourceException(path, e);
        }
    }

    static <T> T read(String path, Class<T> type) throws BrokenResourceException {
        return read(path, (Type) type);
    }

    class BrokenResourceException extends Exception {
        BrokenResourceException(String path, Throwable cause) {
            super("Unable to load resource from path " + path, cause);
        }
    }
}
