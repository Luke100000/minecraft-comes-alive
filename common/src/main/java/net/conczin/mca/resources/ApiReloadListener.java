package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

public class ApiReloadListener implements ResourceManagerReloadListener {
    public static final ResourceLocation ID = MCA.locate("api");

    @Override
    public void onResourceManagerReload(ResourceManager manager) {
        API.instance = new API.Data();
        API.instance.init(manager);
    }
}
