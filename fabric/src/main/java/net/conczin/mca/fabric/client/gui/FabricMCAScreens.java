package net.conczin.mca.fabric.client.gui;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.conczin.mca.client.gui.MCAScreens;
import net.minecraft.resources.ResourceLocation;

public class FabricMCAScreens extends MCAScreens implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
