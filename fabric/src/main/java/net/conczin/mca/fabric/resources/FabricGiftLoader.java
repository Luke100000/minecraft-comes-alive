package net.conczin.mca.fabric.resources;

import net.conczin.mca.entity.interaction.gifts.GiftLoader;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricGiftLoader extends GiftLoader implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
