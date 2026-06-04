package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.Names;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricNames extends Names implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
