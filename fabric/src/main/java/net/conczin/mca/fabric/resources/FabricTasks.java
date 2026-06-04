package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.Tasks;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricTasks extends Tasks implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
