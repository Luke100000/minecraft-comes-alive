package net.conczin.mca.fabric.client.gui;

import net.conczin.mca.client.gui.MCAScreens;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricMCAScreens extends MCAScreens implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
