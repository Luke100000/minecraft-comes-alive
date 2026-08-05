package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.util.ExtensibleTypeRegistry;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.AbstractMap;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Traits {
    private static final ExtensibleTypeRegistry<Trait> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "trait");

    /**
     * Read-only compatibility view of the former public bare-string registry map.
     * New code should use {@link #get(String)} or {@link #all()}.
     */
    @Deprecated(forRemoval = false)
    public static final Map<String, Trait> TRAIT_REGISTRY = new AbstractMap<>() {
        @Override
        public Set<Entry<String, Trait>> entrySet() {
            Set<Entry<String, Trait>> entries = new LinkedHashSet<>();
            for (Trait trait : all()) {
                entries.add(Map.entry(trait.legacyId(), trait));
            }
            return Collections.unmodifiableSet(entries);
        }

        @Override
        public Trait get(Object key) {
            if (!(key instanceof String id)) {
                return null;
            }
            return Traits.get(id)
                    .filter(trait -> trait.legacyId().equals(id))
                    .orElse(null);
        }

        @Override
        public boolean containsKey(Object key) {
            return get(key) != null;
        }

        @Override
        public int size() {
            return REGISTRY.size();
        }
    };

    private static final CDataParameter<CompoundTag> TRAITS = CParameter.create("Traits", new CompoundTag());

    public static final Trait LACTOSE_INTOLERANCE = register(MCA.locate("lactose_intolerance"), 1.0F, 1.0F);
    public static final Trait BISEXUAL = register(MCA.locate("bisexual"), 1.0F, 0.0F);
    public static final Trait ALBINISM = register(MCA.locate("albinism"), 1.0F, 1.0F);
    public static final Trait RAINBOW = register(MCA.locate("rainbow"), 0.05F, 0.0F);
    public static final Trait RAINBOW_EYES = register(MCA.locate("rainbow_eyes"), 0.05F, 0.0F);
    public static final Trait SIRBEN = register(MCA.locate("sirben"), 0.025F, 1.0F);
    public static final Trait DWARFISM = register(MCA.locate("dwarfism"), 1.0F, 1.0F);
    public static final Trait HOMOSEXUAL = register(MCA.locate("homosexual"), 1.0F, 0.0F);
    public static final Trait HETEROCHROMIA = register(MCA.locate("heterochromia"), 1.0F, 0.5F);
    public static final Trait ASEXUAL = register(MCA.locate("asexual"), 1.0F, 0.0F);
    public static final Trait COLOR_BLIND = register(MCA.locate("color_blind"), 1.0F, 0.5F);
    public static final Trait ATHLETIC = register(MCA.locate("athletic"), 1.0F, 0.5F, false);
    public static final Trait LEFT_HANDED = register(MCA.locate("left_handed"), 1.0F, 0.5F, false);
    public static final Trait WEAK = register(MCA.locate("weak"), 1.0F, 1.0F, false);
    public static final Trait TOUGH = register(MCA.locate("tough"), 1.0F, 1.0F, false);
    public static final Trait COELIAC_DISEASE = register(MCA.locate("coeliac_disease"), 1.0F, 1.0F, false); // TODO
    public static final Trait DIABETES = register(MCA.locate("diabetes"), 1.0F, 1.0F, false); // TODO
    public static final Trait VEGETARIAN = register(MCA.locate("vegetarian"), 1.0F, 1.0F, false); // TODO
    public static final Trait INFERTILE = register(MCA.locate("infertile"), 1.0F, 0.0F);
    public static final Trait ELECTRIFIED = register(MCA.locate("electrified"), 0.0F, 0.0F, false);
    public static final Trait NO_AGING = register(MCA.locate("no_aging"), 0.0F, 0.0F, false);
    // public static final Trait UNKNOWN = register(MCA.locate("unknown"), 0.0F, 0.0F, false);

    private final VillagerLike<?> entity;
    private RandomSource random = RandomSource.create();

    public Traits(VillagerLike<?> entity) {
        this.entity = entity;
    }

    public static Trait register(ResourceLocation id, float chance, float inherit, boolean usableOnPlayer) {
        return REGISTRY.register(id, registeredId -> new Trait(registeredId, chance, inherit, usableOnPlayer));
    }

    public static Trait register(ResourceLocation id, float chance, float inherit) {
        return register(id, chance, inherit, true);
    }

    public static Optional<Trait> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Optional<Trait> get(String id) {
        return REGISTRY.get(id);
    }

    public static List<Trait> all() {
        return REGISTRY.all();
    }

    /**
     * Compatibility wrapper for the former bare-string registration API.
     */
    @Deprecated(forRemoval = false)
    public static Trait registerTrait(String id, float chance, float inherit, boolean usableOnPlayer) {
        ResourceLocation parsed = REGISTRY.parse(id)
                .orElseThrow(() -> new IllegalArgumentException("Invalid trait id '" + id + "'"));
        return register(parsed, chance, inherit, usableOnPlayer);
    }

    /**
     * Compatibility wrapper for the former bare-string registration API.
     */
    @Deprecated(forRemoval = false)
    public static Trait registerTrait(String id, float chance, float inherit) {
        return registerTrait(id, chance, inherit, true);
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(TRAITS);
    }

    public Set<Trait> getTraits() {
        Set<Trait> traits = new LinkedHashSet<>();
        for (String id : entity.getTrackedValue(TRAITS).getAllKeys()) {
            get(id).ifPresent(traits::add);
        }
        return traits;
    }

    public Set<Trait> getInheritedTraits() {
        return getTraits().stream().filter(t -> random.nextFloat() < t.inherit * Config.getInstance().traitInheritChance).collect(Collectors.toSet());
    }

    public boolean hasTrait(VillagerLike<?> target, Trait trait) {
        if (trait == null) {
            return false;
        }

        CompoundTag traits = target.getTrackedValue(TRAITS);
        String canonicalId = trait.getId().toString();
        String legacyId = trait.legacyId();
        return traits.contains(canonicalId) || (!canonicalId.equals(legacyId) && traits.contains(legacyId));
    }

    public boolean hasTrait(Trait trait) {
        return hasTrait(entity, trait);
    }

    public boolean hasTrait(String trait) {
        return get(trait).filter(this::hasTrait).isPresent();
    }

    public boolean eitherHaveTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) || hasTrait(other, trait);
    }

    public boolean hasSameTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) && hasTrait(other, trait);
    }

    public void addTrait(Trait trait) {
        if (trait == null) {
            return;
        }

        CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
        traits.remove(trait.legacyId());
        traits.putBoolean(trait.getId().toString(), true);
        entity.setTrackedValue(TRAITS, traits);
    }

    public void removeTrait(Trait trait) {
        if (trait == null) {
            return;
        }

        CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
        traits.remove(trait.getId().toString());
        traits.remove(trait.legacyId());
        entity.setTrackedValue(TRAITS, traits);
    }

    //initializes the genes with random numbers
    public void randomize() {
        List<Trait> traits = all();
        float total = (float) traits.stream().mapToDouble(trait -> trait.chance).sum();
        for (Trait trait : traits) {
            float chance = Config.getInstance().traitChance / total * trait.chance;
            if (random.nextFloat() < chance && trait.isEnabled()) {
                addTrait(trait);
            }
        }
    }

    public void inherit(Traits from) {
        for (Trait trait : from.getInheritedTraits()) {
            addTrait(trait);
        }
    }

    public void inherit(Traits from, long seed) {
        RandomSource old = random;
        random = RandomSource.create(seed);
        inherit(from);
        random = old;
    }

    public float getVerticalScaleFactor() {
        return hasTrait(DWARFISM) ? 0.65f : 1.0f;
    }

    public float getHorizontalScaleFactor() {
        return (hasTrait(DWARFISM) ? 0.85f : 1.0f) * (hasTrait(TOUGH) ? 1.2f : 1.0f) * (hasTrait(WEAK) ? 0.85f : 1.0f);
    }

    public static final class Trait {
        private final ResourceLocation id;
        private final float chance;
        private final float inherit;
        private final boolean usableOnPlayer;

        private Trait(ResourceLocation id, float chance, float inherit, boolean usableOnPlayer) {
            this.id = id;
            this.chance = chance;
            this.inherit = inherit;
            this.usableOnPlayer = usableOnPlayer;
        }

        public ResourceLocation getId() {
            return id;
        }

        /**
         * Compatibility wrapper returning bare paths for MCA built-ins.
         */
        @Deprecated(forRemoval = false)
        public String id() {
            return legacyId();
        }

        private String legacyId() {
            return REGISTRY.legacyId(id);
        }

        /**
         * Compatibility wrapper for the former nested registry API.
         */
        @Deprecated(forRemoval = false)
        public static Collection<Trait> values() {
            return all();
        }

        /**
         * Compatibility wrapper for the former nested registry API.
         */
        @Deprecated(forRemoval = false)
        public static Trait valueOf(String id) {
            return get(id).orElse(null);
        }

        public Component getName() {
            return Component.translatable("trait." + REGISTRY.translationSuffix(id));
        }

        public Component getDescription() {
            return Component.translatable("traitDescription." + REGISTRY.translationSuffix(id));
        }

        public boolean isUsableOnPlayer() {
            return usableOnPlayer;
        }

        public boolean isEnabled() {
            Map<String, Boolean> enabledTraits = Config.getServerConfig().enabledTraits;
            Boolean canonical = enabledTraits.get(id.toString());
            return canonical != null ? canonical : enabledTraits.getOrDefault(legacyId(), true);
        }
    }
}
