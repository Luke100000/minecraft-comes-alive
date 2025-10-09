package net.mca.quilt.resources;

import net.mca.resources.HairList;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.quiltmc.qsl.resource.loader.api.reloader.IdentifiableResourceReloader;

public class QuiltHairList extends HairList implements IdentifiableResourceReloader {
    @Override
    public @NotNull ResourceLocation getQuiltId() {
        return ID;
    }
}
