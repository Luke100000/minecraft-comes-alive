package net.conczin.mca.entity.interaction.gifts;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import net.conczin.mca.MCA;
import net.conczin.mca.resources.Resources;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import java.util.Map;

public class GiftLoader extends SimpleJsonResourceReloadListener {
    protected static final ResourceLocation ID = new ResourceLocation(MCA.MOD_ID, "gifts");

    public GiftLoader() {
        super(Resources.GSON, "gifts");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        GiftType.REGISTRY.clear();
        data.forEach((id, json) -> {
            try {
                GiftType.REGISTRY.add(GiftType.fromJson(id, GsonHelper.convertToJsonObject(json, "root")));
            } catch (JsonParseException e) {
                MCA.LOGGER.error("Could not load gift type for id {}", id, e);
            }
        });

        //extend from mca entries to avoid copy pasta commonly used stuff
        for (GiftType type : GiftType.REGISTRY) {
            if (!type.getId().getNamespace().equals(MCA.MOD_ID) && type.getConditions().isEmpty()) {
                for (GiftType extendingType : GiftType.REGISTRY) {
                    if (extendingType.getId().getNamespace().equals(MCA.MOD_ID) && extendingType.getId().getPath().equals(type.getId().getPath())) {
                        type.extendFrom(extendingType);
                        break;
                    }
                }
            }
        }
    }
}
