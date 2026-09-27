package net.conczin.mca.neoforge;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.ConfigScreenSearch;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MCA.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeClientConfigHelper {
    public NeoForgeClientConfigHelper(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class, NeoForgeClientConfigHelper::createConfigScreen);
    }

    private static Screen createConfigScreen(ModContainer modContainer, Screen parent) {
        return new ConfigurationScreen(modContainer, parent, ConfigScreenSearch::createSection);
    }
}
