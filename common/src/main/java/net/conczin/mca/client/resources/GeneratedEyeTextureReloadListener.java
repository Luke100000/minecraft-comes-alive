package net.conczin.mca.client.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.client.render.DynamicSkinCache;
import net.conczin.mca.client.render.layer.FaceLayer;
import net.conczin.mca.client.render.layer.VillagerLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public final class GeneratedEyeTextureReloadListener implements ResourceManagerReloadListener {
    public static final ResourceLocation ID = MCA.locate("generated_eye_textures");
    public static final GeneratedEyeTextureReloadListener INSTANCE = new GeneratedEyeTextureReloadListener();

    private GeneratedEyeTextureReloadListener() {
    }

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        VillagerLayer.clearTextureExistenceCache();
        FaceLayer.clearGeneratedEyeTextureCache();
        DynamicSkinCache.clear();
    }
}
