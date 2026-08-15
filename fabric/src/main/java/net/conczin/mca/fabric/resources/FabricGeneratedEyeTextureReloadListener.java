package net.conczin.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.conczin.mca.MCA;
import net.conczin.mca.client.resources.GeneratedEyeTextureReloadListener;
import net.minecraft.resources.ResourceLocation;

public final class FabricGeneratedEyeTextureReloadListener
        extends GeneratedEyeTextureReloadListener
        implements IdentifiableResourceReloadListener {

    private static final ResourceLocation ID = MCA.locate("generated_eye_textures");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}