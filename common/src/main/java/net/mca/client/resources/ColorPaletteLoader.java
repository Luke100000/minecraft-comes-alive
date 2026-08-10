package net.mca.client.resources;

import net.mca.MCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ColorPaletteLoader extends SimplePreparableReloadListener<Map<ResourceLocation, ColorPalette.Data>> {
    protected static final ResourceLocation ID = new ResourceLocation(MCA.MOD_ID, "color_palettes");

    @Override
    protected Map<ResourceLocation, ColorPalette.Data> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return ColorPalette.REGISTRY.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            return loadPalette(entry.getKey(), manager);
        }));
    }

    @SuppressWarnings("deprecation")
    private ColorPalette.Data loadPalette(ResourceLocation id, ResourceManager manager) {
        try (NativeImage img = NativeImage.read(manager.getResource(id).get().open())) {
            return new ColorPalette.Data(
                    img.getWidth(),
                    img.getHeight(),
                    img.makePixelArray()
            );
        } catch (Exception e) {
            MCA.LOGGER.error("Failed to load color palette from `{}`", id, e);
        }
        return ColorPalette.EMPTY;
    }

    @Override
    protected void apply(Map<ResourceLocation, ColorPalette.Data> palettes, ResourceManager manager, ProfilerFiller profiler) {
        palettes.forEach((id, data) -> {
            if (ColorPalette.REGISTRY.containsKey(id)) {
                ColorPalette.REGISTRY.get(id).data = Objects.requireNonNull(data);
            }
        });
    }
}
