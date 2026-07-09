package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.BodySkinList;
import net.minecraft.util.Identifier;

public class FabricBodySkinList extends BodySkinList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
