package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.HairList;
import net.minecraft.resources.ResourceLocation;

public class FabricHairList extends HairList implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
