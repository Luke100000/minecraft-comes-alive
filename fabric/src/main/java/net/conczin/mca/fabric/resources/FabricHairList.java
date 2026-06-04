package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.HairList;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricHairList extends HairList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
