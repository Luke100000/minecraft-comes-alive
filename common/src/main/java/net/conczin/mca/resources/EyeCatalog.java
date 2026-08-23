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
    private final Map<String, List<EyeDefinition>> activeByVariant = new HashMap<>();

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

        Map<String, List<EyeDefinition>> grouped = new HashMap<>();
        definitions.values().forEach(definition -> grouped
                .computeIfAbsent(key(definition.variant()), ignored -> new ArrayList<>())
                .add(definition));
        grouped.values().forEach(entries -> entries.sort((a, b) -> SkinListEntry.compareIdentifiers(
                a.id().toString(),
                b.id().toString()
        )));

        activeDefinitions.clear();
        activeByVariant.clear();
        grouped.forEach((variant, entries) -> {
            List<EyeDefinition> enabled = entries.stream()
                    .filter(entry -> !disabled.contains(entry.id()))
                    .toList();
            List<EyeDefinition> active = enabled.isEmpty() ? List.copyOf(entries) : enabled;
            activeByVariant.put(variant, active);
            active.forEach(definition -> activeDefinitions.put(definition.id(), definition));
        });

        if (activeByVariant.getOrDefault(EyeStyles.DEFAULT_VARIANT, List.of()).isEmpty()) {
            EyeDefinition fallback = new EyeDefinition(
                    EyeStyles.DEFAULT,
                    EyeStyles.DEFAULT_VARIANT,
                    Gender.NEUTRAL,
                    1.0F,
                    false,
                    Map.of()
            );
            activeByVariant.put(EyeStyles.DEFAULT_VARIANT, List.of(fallback));
            activeDefinitions.put(fallback.id(), fallback);
            MCA.LOGGER.warn("No usable {} eye definitions were loaded; using {}", EyeStyles.DEFAULT_VARIANT, EyeStyles.DEFAULT);
        }
    }

    public ResourceLocation resolve(String variant, ResourceLocation eye) {
        String variantKey = key(variant);
        List<EyeDefinition> entries = activeByVariant.get(variantKey);
        if (entries == null || entries.isEmpty()) {
            return EyeStyles.DEFAULT;
        }

        EyeDefinition current = activeDefinitions.get(eye);
        if (current != null && current.variant().equals(variantKey)) {
            return eye;
        }
        return entries.get(Math.floorMod(eye.hashCode(), entries.size())).id();
    }

    public ResourceLocation pick(String variant, Gender gender) {
        List<EyeDefinition> entries = activeByVariant.get(key(variant));
        if (entries == null || entries.isEmpty()) {
            return EyeStyles.DEFAULT;
        }

        List<EyeDefinition> candidates = entries.stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .toList();
        if (candidates.isEmpty()) {
            candidates = entries;
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
        ResourceLocation resolved = resolve(EyeStyles.DEFAULT_VARIANT, stored);
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

    private static String key(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }
}
