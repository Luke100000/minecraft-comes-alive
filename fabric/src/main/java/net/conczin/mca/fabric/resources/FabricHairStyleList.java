package net.conczin.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.conczin.mca.resources.HairStyleList;
import net.minecraft.resources.ResourceLocation;

public class FabricHairStyleList extends HairStyleList implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
