package net.conczin.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.conczin.mca.entity.interaction.gifts.GiftLoader;
import net.minecraft.resources.ResourceLocation;

public class FabricGiftLoader extends GiftLoader implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
