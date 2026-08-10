package net.mca.quilt.resources;

import net.mca.MCA;
import net.mca.client.resources.GeneratedEyeTextureReloadListener;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public final class QuiltGeneratedEyeTextureReloadListener extends GeneratedEyeTextureReloadListener implements IdentifiableResourceReloader {

    private static final ResourceLocation ID = MCA.locate("generated_eye_textures");

    @Override
    public @NotNull ResourceLocation getQuiltId() {
        return ID;
    }
}