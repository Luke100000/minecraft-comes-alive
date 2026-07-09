package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.FaceList;
import net.minecraft.util.Identifier;

public class FabricFaceList extends FaceList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
