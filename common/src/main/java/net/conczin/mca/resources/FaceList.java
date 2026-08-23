package net.conczin.mca.resources;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.ResourceLocationException;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.*;

public class FaceList extends SimplePreparableReloadListener<Map<ResourceLocation, List<SkinListJson.Entry>>> {
    public static final ResourceLocation ID = MCA.locate("eyes");
    private static FaceList INSTANCE;
    private final Map<String, List<EyeDefinition>> loadedFaces = new HashMap<>();
    private final Map<String, EnumMap<Gender, List<ResourceLocation>>> faces = new HashMap<>();
    private final Map<ResourceLocation, EyeDefinition> definitions = new HashMap<>();

    public FaceList() {
        INSTANCE = this;
    }

    public static FaceList getInstance() {
        return INSTANCE;
    }

    @Override
    protected Map<ResourceLocation, List<SkinListJson.Entry>> prepare(ResourceManager manager, ProfilerFiller profiler) {
        return SkinListJson.textureEntryCollections(manager, "eyes");
    }

    @Override
    protected void apply(Map<ResourceLocation, List<SkinListJson.Entry>> data, ResourceManager manager, ProfilerFiller profiler) {
        loadedFaces.clear();
        definitions.clear();
        data.forEach(this::addEntries);
        loadedFaces.values().forEach(pool -> pool.sort((a, b) -> SkinListEntry.compareIdentifiers(
                a.id().toString(),
                b.id().toString()
        )));
        refreshDisabledEyes();
    }

    private void addEntries(ResourceLocation id, List<SkinListJson.Entry> textures) {
        String variant = id.getPath().toLowerCase(Locale.ROOT);
        textures.forEach(entry -> {
            String identifier = entry.identifier();
            ResourceLocation parsed;
            try {
                parsed = ResourceLocation.parse(identifier);
            } catch (ResourceLocationException exception) {
                MCA.LOGGER.warn("Invalid face texture identifier {}", identifier, exception);
                return;
            }
            if (!parsed.getPath().startsWith("skins/face/")) {
                MCA.LOGGER.warn("Invalid face texture path {}", identifier);
                return;
            }
            Gender gender = SkinListJson.resolveGender(null, entry);
            if (gender == Gender.UNASSIGNED) {
                gender = Gender.NEUTRAL;
            }
            EyeDefinition definition;
            try {
                definition = EyeDefinition.parse(parsed, gender, entry.metadata());
            } catch (IllegalArgumentException exception) {
                MCA.LOGGER.warn("Invalid eye definition {}", parsed, exception);
                return;
            }
            definitions.put(parsed, definition);
            loadedFaces.computeIfAbsent(key(variant), ignored -> new ArrayList<>()).add(definition);
        });
    }

    public void refreshDisabledEyes() {
        Set<ResourceLocation> disabled = new HashSet<>();
        List<String> configured = Config.getServerConfig().disabledEyeTextures;
        if (configured != null) {
            for (String identifier : configured) {
                try {
                    disabled.add(ResourceLocation.parse(identifier));
                } catch (ResourceLocationException exception) {
                    MCA.LOGGER.warn("Invalid disabled eye texture identifier {}", identifier, exception);
                }
            }
        }

        faces.clear();
        loadedFaces.forEach((variant, entries) -> {
            EnumMap<Gender, List<ResourceLocation>> pools = new EnumMap<>(Gender.class);
            for (Gender gender : Gender.values()) {
                List<ResourceLocation> eligible = entries.stream()
                        .filter(entry -> SkinSelection.matchesGender(entry.gender(), gender))
                        .map(EyeDefinition::id)
                        .toList();
                pools.put(gender, FaceSelection.enabledOrFallback(eligible, disabled::contains));
            }
            faces.put(variant, pools);
        });
    }

    public ResourceLocation pick(String variant, Gender gender, float faceGene) {
        List<ResourceLocation> pool = getPool(variant, gender);
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant) + " and gender " + gender.getDataName());
        }
        return pool.get(FaceSelection.indexForGene(faceGene, pool.size()));
    }

    public int count(String variant, Gender gender) {
        return catalog(variant, gender).size();
    }

    public List<ResourceLocation> catalog(String variant, Gender gender) {
        List<ResourceLocation> pool = getPool(variant, gender);
        if (pool == null || pool.isEmpty()) {
            throw new IllegalStateException("No face textures loaded for " + key(variant) + " and gender " + gender.getDataName());
        }
        return pool;
    }

    public EyeDefinition definition(ResourceLocation id) {
        EyeDefinition definition = definitions.get(id);
        return definition != null
                ? definition
                : new EyeDefinition(id, Gender.NEUTRAL, false, Map.of());
    }

    private List<ResourceLocation> getPool(String variant, Gender gender) {
        EnumMap<Gender, List<ResourceLocation>> pools = faces.get(key(variant));
        return pools == null ? null : pools.get(gender);
    }

    private static String key(String variant) {
        return variant.toLowerCase(Locale.ROOT);
    }
}
