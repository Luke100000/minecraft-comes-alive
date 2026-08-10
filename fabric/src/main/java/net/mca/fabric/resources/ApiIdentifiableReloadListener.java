package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.mca.resources.ApiReloadListener;
import net.minecraft.resources.ResourceLocation;

public class ApiIdentifiableReloadListener extends ApiReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
