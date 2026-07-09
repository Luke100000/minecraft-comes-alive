package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.HairStyleList;
import net.minecraft.util.Identifier;

public class FabricHairStyleList extends HairStyleList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
