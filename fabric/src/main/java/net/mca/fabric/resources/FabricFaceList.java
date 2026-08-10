package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.FaceList;
import net.minecraft.resources.ResourceLocation;

public class FabricFaceList extends FaceList implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
