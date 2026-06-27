package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.BodySkinList;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public class FabricBodySkinList extends BodySkinList implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
