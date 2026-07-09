package net.mca.quilt.resources;

import net.mca.resources.BodySkinList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltBodySkinList extends BodySkinList implements IdentifiableResourceReloader {
    @Override
    public @NotNull Identifier getQuiltId() {
        return ID;
    }
}
