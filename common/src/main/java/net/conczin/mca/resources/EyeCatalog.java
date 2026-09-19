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
        List<EyeDefinition> selected = new ArrayList<>(enabled.isEmpty() ? entries : enabled);
        ensureGenderFallback(selected, entries, Gender.MALE);
        ensureGenderFallback(selected, entries, Gender.FEMALE);
        selected.sort((a, b) -> SkinListEntry.compareIdentifiers(a.id().toString(), b.id().toString()));
        active = List.copyOf(selected);

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
        return resolve(eye, Gender.NEUTRAL);
    }

    public ResourceLocation resolve(ResourceLocation eye, Gender gender) {
        EyeDefinition current = activeDefinitions.get(eye);
        if (current != null && SkinSelection.matchesGender(current.gender(), gender)) {
            return eye;
        }

        ResourceLocation counterpart = EyeStyles.forGender(eye, gender);
        EyeDefinition counterpartDefinition = activeDefinitions.get(counterpart);
        if (counterpartDefinition != null && SkinSelection.matchesGender(counterpartDefinition.gender(), gender)) {
            return counterpart;
        }

        List<EyeDefinition> candidates = candidates(gender);
        return candidates.isEmpty()
                ? EyeStyles.DEFAULT
                : candidates.get(Math.floorMod(eye.hashCode(), candidates.size())).id();
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

    public boolean contains(ResourceLocation eye) {
        return activeDefinitions.containsKey(eye);
    }

    public boolean contains(ResourceLocation eye, Gender gender) {
        EyeDefinition definition = activeDefinitions.get(eye);
        return definition != null && SkinSelection.matchesGender(definition.gender(), gender);
    }

    private List<EyeDefinition> candidates(Gender gender) {
        return active.stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .toList();
    }

    private static void ensureGenderFallback(List<EyeDefinition> selected, List<EyeDefinition> all, Gender gender) {
        if (selected.stream().anyMatch(entry -> SkinSelection.matchesGender(entry.gender(), gender))) {
            return;
        }

        all.stream()
                .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                .findFirst()
                .ifPresent(entry -> {
                    selected.add(entry);
                    MCA.LOGGER.warn("All eye textures compatible with {} were disabled; keeping {} as a fallback", gender, entry.id());
                });
    }

    public Map<ResourceLocation, EyeDefinition> effectiveDefinitions() {
        return Map.copyOf(activeDefinitions);
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
