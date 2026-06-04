package net.conczin.mca.fabric.resources;

import net.conczin.mca.resources.ApiReloadListener;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;

public class ApiIdentifiableReloadListener extends ApiReloadListener implements SimpleSynchronousResourceReloadListener {
    @Override
    public Identifier getFabricId() {
        return ID;
    }
}
