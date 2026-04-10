package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.BuildingTypes;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricBuildingTypes extends BuildingTypes implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
