package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.MCA;
import net.mca.client.resources.GeneratedEyeTextureReloadListener;
import net.minecraft.util.Identifier;

public final class FabricGeneratedEyeTextureReloadListener
        extends GeneratedEyeTextureReloadListener
        implements IdentifiableResourceReloadListener {

    private static final Identifier ID = MCA.locate("generated_eye_textures");

    @Override
    public Identifier getFabricId() {
        return ID;
    }
}