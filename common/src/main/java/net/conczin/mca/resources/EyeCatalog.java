package net.conczin.mca.resources;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;

import java.util.*;

public class EyeCatalog extends SimplePreparableReloadListener<Map<ResourceLocation, List<SkinListJson.Entry>>> {
    public static final ResourceLocation ID = MCA.locate("skins/eyes");
    private static EyeCatalog INSTANCE;
    private final Map<ResourceLocation, EyeDefinition> definitions = new HashMap<>();
    private final Map<ResourceLocation, EyeDefinition> activeDefinitions = new HashMap<>();
    private List<EyeDefinition> active = List.of();

    public EyeCatalog() {
        INSTANCE = this;
    }

    public static EyeCatalog getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<ResourceLocation, List<SkinListJson.Entry>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return SkinListJson.textureEntryCollections(manager, ID.getPath());
    }

    @Override
    protected void apply(Map<ResourceLocation, List<SkinListJson.Entry>> data, ResourceManager manager, ProfilerFiller profiler) {
        definitions.clear();
        data.forEach((id, entries) -> AppearanceCatalogLoader.addEyes(definitions, id, entries));
        refreshDisabledEyes();
    }

    private void refreshDisabledEyes() {
        Set<ResourceLocation> disabled = new HashSet<>();
        List<String> configured = Config.getInstance().disabledEyeTextures;
        if (configured != null) {
            for (String identifier : configured) {
                try {
                    disabled.add(ResourceLocation.parse(identifier));
                } catch (ResourceLocationException exception) {
                    MCA.LOGGER.warn("Invalid disabled eye texture identifier {}", identifier, exception);
                }
            }
        }

        List<EyeDefinition> entries = definitions.values().stream()
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.id().toString(), b.id().toString()))
                .toList();
        List<EyeDefinition> enabled = entries.stream()
                .filter(entry -> !disabled.contains(entry.id()))
                .toList();
        active = enabled.isEmpty() ? entries : enabled;

        if (active.isEmpty()) {
            EyeDefinition fallback = new EyeDefinition(
                    EyeStyles.DEFAULT,
                    Gender.NEUTRAL,
                    1.0F,
                    Map.of()
            );
            active = List.of(fallback);
            MCA.LOGGER.warn("No usable eye definitions were loaded; using {}", EyeStyles.DEFAULT);
        }

        activeDefinitions.clear();
        active.forEach(definition -> activeDefinitions.put(definition.id(), definition));
    }

    public ResourceLocation resolve(ResourceLocation eye) {
        if (active.isEmpty()) {
            return EyeStyles.DEFAULT;
        }
        if (activeDefinitions.containsKey(eye)) {
            return eye;
        }
        return active.get(Math.floorMod(eye.hashCode(), active.size())).id();
    }

    public ResourceLocation pick(Gender gender) {
        if (active.isEmpty()) {
            return EyeStyles.DEFAULT;
        }

        List<EyeDefinition> candidates = active.stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .toList();
        if (candidates.isEmpty()) {
            candidates = active;
        }

        WeightedPool.Mutable<ResourceLocation> pool = new WeightedPool.Mutable<>(EyeStyles.DEFAULT);
        candidates.forEach(entry -> pool.add(entry.id(), entry.chance()));
        return pool.pickOne();
    }

    public boolean contains(ResourceLocation eye) {
        return activeDefinitions.containsKey(eye);
    }

    public Map<ResourceLocation, EyeDefinition> effectiveDefinitions() {
        return Map.copyOf(activeDefinitions);
    }

    public void repair(VillagerLike<?> villager) {
        ResourceLocation stored = villager.getEyeTexture();
        ResourceLocation resolved = resolve(stored);
        if (!stored.equals(resolved)) {
            MCA.LOGGER.info("Villager eye texture {} does not exist; replacing it with {}", stored, resolved);
            villager.setEyeTexture(resolved);
        }
    }

    public void repairLoaded(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof VillagerLike<?> villager) {
                    repair(villager);
                }
            }
        }
    }

}
