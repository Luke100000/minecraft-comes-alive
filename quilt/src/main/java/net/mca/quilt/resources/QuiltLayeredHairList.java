package net.mca.quilt.resources;

import net.mca.resources.LayeredHairList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltLayeredHairList extends LayeredHairList implements IdentifiableResourceReloader {
    @Override
    public @NotNull Identifier getQuiltId() {
        return ID;
    }
}
