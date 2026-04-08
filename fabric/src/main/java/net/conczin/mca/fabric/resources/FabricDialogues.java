package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.Dialogues;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.Identifier;

public class FabricDialogues extends Dialogues implements IdentifiableResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
