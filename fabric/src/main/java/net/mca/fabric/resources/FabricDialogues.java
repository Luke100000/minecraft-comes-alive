package net.mca.fabric.resources;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.mca.resources.Dialogues;
import net.minecraft.resources.ResourceLocation;

public class FabricDialogues extends Dialogues implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
