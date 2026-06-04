package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Traits {
    public static final Map<String, Trait> TRAIT_REGISTRY = new HashMap<>();
    private static final CDataParameter<CompoundTag> TRAITS = CParameter.create("Traits", new CompoundTag());

    public static Trait ATHLETIC = registerTrait("athletic", 1.0F, 0.5F, false);
    public static Trait LEFT_HANDED = registerTrait("left_handed", 1.0F, 0.5F, false);
    public static Trait WEAK = registerTrait("weak", 1.0F, 1.0F, false);
    public static Trait TOUGH = registerTrait("tough", 1.0F, 1.0F, false);
    public static Trait COLOR_BLIND = registerTrait("color_blind", 1.0F, 0.5F);
    public static Trait HETEROCHROMIA = registerTrait("heterochromia", 1.0F, 0.5F);
    public static Trait LACTOSE_INTOLERANCE = registerTrait("lactose_intolerance", 1.0F, 1.0F);
    public static Trait COELIAC_DISEASE = registerTrait("coeliac_disease", 1.0F, 1.0F, false); // TODO
    public static Trait DIABETES = registerTrait("diabetes", 1.0F, 1.0F, false); // TODO
    public static Trait DWARFISM = registerTrait("dwarfism", 1.0F, 1.0F);
    public static Trait ALBINISM = registerTrait("albinism", 1.0F, 1.0F);
    public static Trait VEGETARIAN = registerTrait("vegetarian", 1.0F, 1.0F, false); // TODO
    public static Trait BISEXUAL = registerTrait("bisexual", 1.0F, 0.0F);
    public static Trait HOMOSEXUAL = registerTrait("homosexual", 1.0F, 0.0F);
    public static Trait ASEXUAL = registerTrait("asexual", 1.0F, 0.0F);
    public static Trait ELECTRIFIED = registerTrait("electrified", 0.0F, 0.0F, false);
    public static Trait SIRBEN = registerTrait("sirben", 0.025F, 1.0F);
    public static Trait RAINBOW = registerTrait("rainbow", 0.05F, 0.0F);
    public static Trait UNKNOWN = registerTrait("unknown", 0.0F, 0.0F, false);

    private final VillagerLike<?> entity;
    private RandomSource random = RandomSource.create();

    public Traits(VillagerLike<?> entity) {
        this.entity = entity;
    }

    public static Trait registerTrait(String id, float chance, float inherit, boolean usableOnPlayer) {
        Trait trait = new Trait(id, chance, inherit, usableOnPlayer);
        TRAIT_REGISTRY.put(id, trait);
        return trait;
    }

    public static Trait registerTrait(String id, float chance, float inherit) {
        return registerTrait(id, chance, inherit, true);
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(TRAITS);
    }

    public Set<Trait> getTraits() {
        return entity.getTrackedValue(TRAITS).keySet().stream().map(Trait::valueOf).collect(Collectors.toSet());
    }

    public Set<Trait> getInheritedTraits() {
        return getTraits().stream().filter(t -> random.nextFloat() < t.inherit * Config.getInstance().traitInheritChance).collect(Collectors.toSet());
    }

    public boolean hasTrait(VillagerLike<?> target, Trait trait) {
        return target.getTrackedValue(TRAITS).contains(trait.id());
    }

    public boolean hasTrait(Trait trait) {
        return hasTrait(entity, trait);
    }

    public boolean hasTrait(String trait) {
        if (Trait.valueOf(trait) != null) {
            return hasTrait(entity, Trait.valueOf(trait));
        }
        return false;
    }

    public boolean eitherHaveTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) || hasTrait(other, trait);
    }

    public boolean hasSameTrait(Trait trait, VillagerLike<?> other) {
        return hasTrait(entity, trait) && hasTrait(other, trait);
    }

    public void addTrait(Trait trait) {
        CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
        traits.putBoolean(trait.id(), true);
        entity.setTrackedValue(TRAITS, traits);
    }

    public void removeTrait(Trait trait) {
        CompoundTag traits = entity.getTrackedValue(TRAITS).copy();
        traits.remove(trait.id());
        entity.setTrackedValue(TRAITS, traits);
    }

    //initializes the genes with random numbers
    public void randomize() {
        float total = (float) Trait.values().stream().mapToDouble(tr -> tr.chance).sum();
        for (Trait t : Trait.values()) {
            float chance = Config.getInstance().traitChance / total * t.chance;
            if (random.nextFloat() < chance && t.isEnabled()) {
                addTrait(t);
            }
        }
    }

    public void inherit(Traits from) {
        for (Trait t : from.getInheritedTraits()) {
            addTrait(t);
        }
    }

    public void inherit(Traits from, long seed) {
        RandomSource old = random;
        random = RandomSource.create(seed);
        inherit(from);
        random = old;
    }

    public float getVerticalScaleFactor() {
        return hasTrait(Traits.DWARFISM) ? 0.65f : 1.0f;
    }

    public float getHorizontalScaleFactor() {
        return (hasTrait(Traits.DWARFISM) ? 0.85f : 1.0f) * (hasTrait(Traits.TOUGH) ? 1.2f : 1.0f) * (hasTrait(Traits.WEAK) ? 0.85f : 1.0f);
    }

    public static class Trait {
        private final String id;
        private final float chance;
        private final float inherit;
        private final boolean usableOnPlayer;

        Trait(String id, float chance, float inherit, boolean usableOnPlayer) {
            this.id = id;
            this.chance = chance;
            this.inherit = inherit;
            this.usableOnPlayer = usableOnPlayer;
        }

        public static Collection<Trait> values() {
            return TRAIT_REGISTRY.values();
        }

        public static Trait valueOf(String id) {
            return TRAIT_REGISTRY.getOrDefault(id, UNKNOWN);
        }

        public String id() {
            return this.id;
        }

        public Component getName() {
            return Component.translatable("trait." + id());
        }

        public Component getDescription() {
            return Component.translatable("traitDescription." + id());
        }

        public boolean isUsableOnPlayer() {
            return usableOnPlayer;
        }

        public boolean isEnabled() {
            return Config.getServerConfig().enabledTraits.getOrDefault(id(), false);
        }
    }
}
