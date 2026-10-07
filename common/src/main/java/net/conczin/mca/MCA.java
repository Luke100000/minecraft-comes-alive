package net.conczin.mca;

import net.conczin.mca.server.AsyncStructureLocator;
import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.resources.DialogueEvents;
import net.minecraft.resources.ResourceLocation;
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
    private static DialogueEngine dialogueEngine;

    public static ResourceLocation locate(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }

    public static boolean isBlankString(String string) {
        return string == null || string.trim().isEmpty();
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
        dialogueEngine = new DialogueEngine(DialogueEvents.INSTANCE);
    }

    public static void stopServer(MinecraftServer server) {
        if (MCA.server == server) {
            if (dialogueEngine != null) {
                dialogueEngine.clear(server);
                dialogueEngine = null;
            }
            structureLocator.close();
            MCA.server = null;
        }
    }

    public static Optional<DialogueEngine> getDialogueEngine() {
        return Optional.ofNullable(dialogueEngine);
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
        void register(ResourceLocation name, T value);
    }

    public interface AttributeRegisterHelper {
        void register(EntityType<? extends LivingEntity> entity, AttributeSupplier.Builder attributes);
    }
}
