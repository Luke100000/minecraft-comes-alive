package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.LayeredHairList;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public class FabricLayeredHairList extends LayeredHairList implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
