package net.mca.quilt.resources;

import net.mca.resources.ApiReloadListener;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.SimpleSynchronousResourceReloader;

public class ApiIdentifiableReloadListener extends ApiReloadListener implements SimpleSynchronousResourceReloader {
    @Override
    public @NotNull ResourceLocation getQuiltId() {
        return ID;
    }
}
