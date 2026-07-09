package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.LayeredHairList;
import net.minecraft.util.Identifier;

public class FabricLayeredHairList extends LayeredHairList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
