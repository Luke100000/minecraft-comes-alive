package net.conczin.mca.client.resources;

import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

/** Releases client-generated textures before resource reloads replace their source assets. */
public class GeneratedEyeTextureReloadListener extends SimplePreparableReloadListener<Void> {
    @Override
    protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void ignored, ResourceManager manager, ProfilerFiller profiler) {
        FaceLayer.clearGeneratedEyeTextureCache();
        DynamicSkinCache.clear();
    }
}