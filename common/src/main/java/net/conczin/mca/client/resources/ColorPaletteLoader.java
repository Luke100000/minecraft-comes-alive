package net.conczin.mca.client.resources;

import com.mojang.blaze3d.platform.NativeImage;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class ColorPaletteLoader extends SimplePreparableReloadListener<Map<Identifier, ColorPalette.Data>> {
    public static final Identifier ID = MCA.locate("color_palettes");

    @Override
    protected Map<Identifier, ColorPalette.Data> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return ColorPalette.REGISTRY.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            return loadPalette(entry.getKey(), manager);
        }));
    }

    private ColorPalette.Data loadPalette(Identifier id, ResourceManager manager) {
        try (NativeImage img = NativeImage.read(manager.getResource(id).get().open())) {
            return new ColorPalette.Data(
                    img.getWidth(),
                    img.getHeight(),
                    img.getPixels()
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
