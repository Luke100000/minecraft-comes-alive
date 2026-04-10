package net.conczin.mca.fabric.resources;

import net.conczin.mca.client.resources.ColorPaletteLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricColorPaletteLoader extends ColorPaletteLoader implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
