package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.ClothingList;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricClothingList extends ClothingList implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
