package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.BuildingTypes;
import net.minecraft.resources.ResourceLocation;

public class FabricBuildingTypes extends BuildingTypes implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
