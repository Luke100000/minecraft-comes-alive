package net.mca.quilt.resources;

import net.mca.resources.HairStyleList;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltHairStyleList extends HairStyleList implements IdentifiableResourceReloader {
    @Override
    public @NotNull Identifier getQuiltId() {
        return ID;
    }
}
