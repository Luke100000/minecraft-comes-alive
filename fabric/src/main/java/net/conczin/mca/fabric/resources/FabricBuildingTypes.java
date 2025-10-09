package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.BuildingTypes;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

public class FabricBuildingTypes extends BuildingTypes implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
