package net.mca.quilt.resources;

import net.mca.resources.BuildingTypes;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltBuildingTypes extends BuildingTypes implements IdentifiableResourceReloader {
    @Override
    public @NotNull ResourceLocation getQuiltId() {
        return ID;
    }
}
