package net.conczin.mca;

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

    public static ResourceLocation locate(String id) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, id);
    }

    public static boolean isBlankString(String string) {
        return string == null || string.trim().isEmpty();
    }

    public static Optional<MinecraftServer> getServer() {
        return Optional.ofNullable(server);
    }

    public static void setServer(MinecraftServer server) {
        MCA.server = server;
    }

    public interface RegisterHelper<T> {
        void register(ResourceLocation name, T value);
    }

    public interface AttributeRegisterHelper {
        void register(EntityType<? extends LivingEntity> entity, AttributeSupplier.Builder attributes);
    }
}
