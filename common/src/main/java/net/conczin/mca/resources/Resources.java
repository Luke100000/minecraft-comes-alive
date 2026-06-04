package net.conczin.mca.resources;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.interaction.InteractionPredicate;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;

public interface Resources {
    String RESOURCE_PREFIX = "assets/mca/";

    Gson GSON = new GsonBuilder()
            .registerTypeAdapter(InteractionPredicate.class, InteractionPredicateTypeAdapter.INSTANCE)
            .create();

    Codec<JsonElement> JSON_ELEMENT_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(JsonOps.INSTANCE).getValue(),
            json -> new Dynamic<>(JsonOps.INSTANCE, json)
    );

    static String read(String path) throws IOException {
        return IOUtils.toString(new InputStreamReader(MCA.class.getClassLoader().getResourceAsStream(RESOURCE_PREFIX + path)));
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
