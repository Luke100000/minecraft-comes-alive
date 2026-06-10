package net.conczin.mca;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MCA {
    public static final String MOD_ID = "mca";
    public static final Logger LOGGER = LogManager.getLogger();

    public static final ExecutorService executorService = Executors.newSingleThreadExecutor();
    public static Map<String, String> storage = new HashMap<>();
    public static String language;
    public static PlatformHelper platformHelper = new PlatformHelper();
    private static MinecraftServer server;

    public static Identifier locate(String id) {
        return Identifier.fromNamespaceAndPath(MOD_ID, id);
    }

    public static boolean isBlankString(String string) {
        return string == null || string.trim().isEmpty();
    }

    private static final java.util.regex.Pattern COMBINING_MARKS = java.util.regex.Pattern.compile("\\p{M}");

    public static String normalizeString(String string) {
        if (string == null) {
            return "";
        }
        String normalized = java.text.Normalizer.normalize(string, java.text.Normalizer.Form.NFD);
        return COMBINING_MARKS.matcher(normalized).replaceAll("").toLowerCase(java.util.Locale.ROOT);
    }

    public static Optional<MinecraftServer> getServer() {
        return Optional.ofNullable(server);
    }

    public static void setServer(MinecraftServer server) {
        MCA.server = server;
    }

    public interface RegisterHelper<T> {
        void register(Identifier name, T value);
    }

    public interface AttributeRegisterHelper {
        void register(EntityType<? extends LivingEntity> entity, AttributeSupplier.Builder attributes);
    }
}
