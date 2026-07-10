package net.mca.client.resources;

import net.mca.client.render.DynamicSkinCache;
import net.mca.client.render.layer.FaceLayer;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.SinglePreparationResourceReloader;
import net.minecraft.util.profiler.Profiler;

/** Releases client-generated textures before resource reloads replace their source assets. */
public class GeneratedEyeTextureReloadListener extends SinglePreparationResourceReloader<Void> {
    @Override
    protected Void prepare(ResourceManager manager, Profiler profiler) {
        return null;
    }

    @Override
    protected void apply(Void ignored, ResourceManager manager, Profiler profiler) {
        FaceLayer.clearGeneratedEyeTextureCache();
        DynamicSkinCache.clear();
    }
}