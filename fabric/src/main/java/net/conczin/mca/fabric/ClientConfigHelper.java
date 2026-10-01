package net.conczin.mca.fabric;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.ConfigScreenSearch;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

public final class ClientConfigHelper {
    private ClientConfigHelper() {
    }

    public static void register() {
        NeoForgeConfigRegistry.INSTANCE.register(MCA.MOD_ID, ModConfig.Type.CLIENT, Config.CLIENT_SPEC, Config.CLIENT_FILE_NAME);
        ConfigScreenFactoryRegistry.INSTANCE.register(MCA.MOD_ID, ClientConfigHelper::createConfigScreen);
    }

    private static Screen createConfigScreen(String modId, Screen parent) {
        return ConfigScreenSearch.createRoot(parent,
                new ConfigurationScreen(modId, parent, ConfigScreenSearch::createSection), modId);
    }
}
