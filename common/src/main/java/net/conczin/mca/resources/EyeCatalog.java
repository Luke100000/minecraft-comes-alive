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
    private Map<ResourceLocation, EyeDefinition> definitions = Map.of();

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
        Map<ResourceLocation, EyeDefinition> loaded = new HashMap<>();
        data.forEach((id, entries) -> AppearanceCatalogLoader.addEyes(loaded, id, entries));
        definitions = selectEnabledDefinitions(loaded);
    }

    private Map<ResourceLocation, EyeDefinition> selectEnabledDefinitions(Map<ResourceLocation, EyeDefinition> loaded) {
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

        List<EyeDefinition> entries = loaded.values().stream()
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.id().toString(), b.id().toString()))
                .toList();
        List<EyeDefinition> selected = new ArrayList<>(entries);
        selected.removeIf(entry -> disabled.contains(entry.id()));
        ensureGenderFallback(selected, entries, Gender.MALE);
        ensureGenderFallback(selected, entries, Gender.FEMALE);

        Map<ResourceLocation, EyeDefinition> effective = new HashMap<>();
        selected.forEach(definition -> effective.put(definition.id(), definition));
        return Map.copyOf(effective);
    }

    public ResourceLocation resolve(ResourceLocation eye, Gender gender) {
        return EyeSelection.resolve(definitions, eye, gender);
    }

    public ResourceLocation pick(Gender gender) {
        List<EyeDefinition> candidates = candidates(gender);
        if (candidates.isEmpty()) {
            return EyeStyles.DEFAULT;
        }

        WeightedPool.Mutable<ResourceLocation> pool = new WeightedPool.Mutable<>(EyeStyles.DEFAULT);
        candidates.forEach(entry -> pool.add(entry.id(), entry.chance()));
        return pool.pickOne();
    }

    public boolean contains(ResourceLocation eye, Gender gender) {
        return EyeSelection.contains(definitions, eye, gender);
    }

    private List<EyeDefinition> candidates(Gender gender) {
        return EyeSelection.definitionsForGender(definitions, gender);
    }

    private static void ensureGenderFallback(List<EyeDefinition> selected, List<EyeDefinition> all, Gender gender) {
        if (selected.stream().anyMatch(entry -> SkinSelection.matchesGender(entry.gender(), gender))) {
            return;
        }

        EyeDefinition fallback = all.stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .findFirst()
                .orElseGet(EyeCatalog::defaultDefinition);
        selected.removeIf(entry -> entry.id().equals(fallback.id()));
        selected.add(fallback);
        MCA.LOGGER.warn("No enabled eye texture is compatible with {}; keeping {} as a fallback", gender, fallback.id());
    }

    private static EyeDefinition defaultDefinition() {
        return new EyeDefinition(EyeStyles.DEFAULT, Gender.NEUTRAL, 1.0F, Map.of());
    }

    public Map<ResourceLocation, EyeDefinition> effectiveDefinitions() {
        return definitions;
    }

    public void repair(VillagerLike<?> villager) {
        ResourceLocation stored = villager.getEyeTexture();
        ResourceLocation resolved = resolve(stored, villager.getGenetics().getGender());
        if (!stored.equals(resolved)) {
            MCA.LOGGER.info("Villager eye texture {} is not valid for {}; replacing it with {}",
                    stored, villager.getGenetics().getGender(), resolved);
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
