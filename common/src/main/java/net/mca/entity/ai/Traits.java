package net.mca.entity.ai;

import net.mca.Config;
import net.mca.MCA;
import net.mca.entity.VillagerLike;
import net.mca.util.ExtensibleTypeRegistry;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Traits {
    private static final ExtensibleTypeRegistry<Trait> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "trait");
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
    public static final Trait COELIAC_DISEASE = register(MCA.locate("coeliac_disease"), 1.0F, 1.0F, false);
    public static final Trait DIABETES = register(MCA.locate("diabetes"), 1.0F, 1.0F, false);
    public static final Trait VEGETARIAN = register(MCA.locate("vegetarian"), 1.0F, 1.0F, false);
    public static final Trait INFERTILE = register(MCA.locate("infertile"), 1.0F, 0.0F);
    public static final Trait ELECTRIFIED = register(MCA.locate("electrified"), 0.0F, 0.0F, false);
    public static final Trait NO_AGING = register(MCA.locate("no_aging"), 0.0F, 0.0F, false);

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
        return trait != null && target.getTrackedValue(TRAITS).contains(trait.getId().toString());
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
        traits.putBoolean(trait.getId().toString(), true);
        entity.setTrackedValue(TRAITS, traits);
        updateAttributes(trait);
    }

    public void removeTrait(Trait trait) {
        if (trait == null) {
            return;
        }
        CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
        traits.remove(trait.getId().toString());
        entity.setTrackedValue(TRAITS, traits);
        updateAttributes(trait);
    }

    private void updateAttributes(Trait trait) {
        if (trait == ATHLETIC || trait == WEAK || trait == TOUGH) {
            entity.updateAttributes();
        }
    }

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
            return enabledTraits.getOrDefault(id.toString(), true);
        }
    }
}
