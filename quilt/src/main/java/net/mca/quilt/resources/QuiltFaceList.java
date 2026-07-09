package net.mca.quilt.resources;

import net.mca.resources.FaceList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltFaceList extends FaceList implements IdentifiableResourceReloader {
    @Override
    public @NotNull Identifier getQuiltId() {
        return ID;
    }
}
