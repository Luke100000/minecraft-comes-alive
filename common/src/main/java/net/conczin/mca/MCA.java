package net.conczin.mca;

import net.conczin.mca.server.AsyncStructureLocator;
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

public final class MCA {
    public static final String MOD_ID = "mca";
    public static final Logger LOGGER = LogManager.getLogger();

    public static Map<String, String> storage = new HashMap<>();
    public static String language;
    public static PlatformHelper platformHelper = new PlatformHelper();
    private static MinecraftServer server;
    private static AsyncStructureLocator structureLocator;

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

    public static void startServer(MinecraftServer server) {
        if (structureLocator != null && !structureLocator.isTerminated()) {
            LOGGER.warn("Previous structure locator has not terminated; starting the server with "
                    + "structure lookups unavailable until the old worker exits");
        } else {
            structureLocator = new AsyncStructureLocator(server);
        }
        MCA.server = server;
    }

    public static void stopServer(MinecraftServer server) {
        if (MCA.server == server) {
            if (structureLocator != null) {
                structureLocator.close();
            }
            MCA.server = null;
        }
    }

    public static void finishServerStop(MinecraftServer server) {
        // A failed startup/crashed run can reach STOPPED without the normal STOPPING hook.
        stopServer(server);
        if (MCA.server == null && structureLocator != null && structureLocator.finishStopping(server)) {
            structureLocator = null;
        }
    }

    public static AsyncStructureLocator getStructureLocator() {
        // Recover on the next lookup once the previous server's worker has actually exited.
        // Until then its closed locator rejects requests without starting an overlapping worker.
        if (server != null && structureLocator != null && structureLocator.isTerminated()) {
            structureLocator = new AsyncStructureLocator(server);
        }
        return structureLocator;
    }

    public interface RegisterHelper<T> {
        void register(Identifier name, T value);
    }

    public interface AttributeRegisterHelper {
        void register(EntityType<? extends LivingEntity> entity, AttributeSupplier.Builder attributes);
    }
}
