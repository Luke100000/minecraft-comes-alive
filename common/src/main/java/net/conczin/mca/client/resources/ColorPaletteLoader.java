package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;
import java.util.Objects;

public class ColorPaletteLoader extends SimplePreparableReloadListener<Map<Identifier, ColorPalette.Data>> {
    public static final Identifier ID = MCA.locate("color_palettes");

    @Override
    protected Map<Identifier, ColorPalette.Data> prepare(ResourceManager manager, ProfilerFiller profiler) {
        Map<Identifier, ColorPalette.Data> result = new java.util.HashMap<>();
        for (Map.Entry<Identifier, ColorPalette> entry : ColorPalette.REGISTRY.entrySet()) {
            result.put(entry.getKey(), loadPalette(entry.getKey(), manager));
        }
        return result;
    }

    private ColorPalette.Data loadPalette(Identifier id, ResourceManager manager) {
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
    protected void apply(Map<Identifier, ColorPalette.Data> palettes, ResourceManager manager, ProfilerFiller profiler) {
        palettes.forEach((id, data) -> {
            if (ColorPalette.REGISTRY.containsKey(id)) {
                ColorPalette.REGISTRY.get(id).data = Objects.requireNonNull(data);
            }
        });
    }

}
