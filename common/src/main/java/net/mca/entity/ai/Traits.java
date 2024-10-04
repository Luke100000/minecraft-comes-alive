package net.mca.entity.ai;

import net.mca.Config;
import net.mca.entity.VillagerLike;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.random.Random;

import java.util.*;
import java.util.stream.Collectors;

public class Traits {
    private static final CDataParameter<NbtCompound> TRAITS = CParameter.create("traits", new NbtCompound());

    public static final Map<String, Trait> TRAIT_REGISTRY = new HashMap<>();

    public static Trait LEFT_HANDED = registerTrait("LEFT_HANDED", 1.0F, 0.5F, false);
    public static Trait WEAK = registerTrait("WEAK", 1.0F, 1.0F, false);
    public static Trait TOUGH = registerTrait("TOUGH", 1.0F, 1.0F, false);
    public static Trait COLOR_BLIND = registerTrait("COLOR_BLIND", 1.0F, 0.5F);
    public static Trait HETEROCHROMIA = registerTrait("HETEROCHROMIA", 1.0F, 0.5F);
    public static Trait LACTOSE_INTOLERANCE = registerTrait("LACTOSE_INTOLERANCE", 1.0F, 1.0F);
    public static Trait COELIAC_DISEASE = registerTrait("COELIAC_DISEASE", 1.0F, 1.0F, false); // TODO: Implement for 7.4
    public static Trait DIABETES = registerTrait("DIABETES", 1.0F, 1.0F, false); // TODO: Implement for 7.4
    public static Trait DWARFISM = registerTrait("DWARFISM", 1.0F, 1.0F);
    public static Trait ALBINISM = registerTrait("ALBINISM", 1.0F, 1.0F);
    public static Trait VEGETARIAN = registerTrait("VEGETARIAN", 1.0F, 1.0F, false); // TODO: Implement for 7.4
    public static Trait BISEXUAL = registerTrait("BISEXUAL", 1.0F, 0.0F);
    public static Trait HOMOSEXUAL = registerTrait("HOMOSEXUAL", 1.0F, 0.0F);
    public static Trait ELECTRIFIED = registerTrait("ELECTRIFIED", 0.0F, 0.0F, false);
    public static Trait SIRBEN = registerTrait("SIRBEN", 0.025F, 1.0F);
    public static Trait RAINBOW = registerTrait("RAINBOW", 0.05F, 0.0F);
    public static Trait UNKNOWN = registerTrait("UNKNOWN", 0.0F, 0.0F, false);

    public static Trait registerTrait(String id, float chance, float inherit, boolean usableOnPlayer) {
        Trait trait = new Trait(id, chance, inherit, usableOnPlayer);
        TRAIT_REGISTRY.put(id, trait);
        return trait;
    }

    public static Trait registerTrait(String id, float chance, float inherit) {
        return registerTrait(id, chance, inherit, true);
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

        public String id() {
            return this.id;
        }

        public static Collection<Trait> values() {
            return TRAIT_REGISTRY.values();
        }

        public static Trait valueOf(String id) {
            return TRAIT_REGISTRY.getOrDefault(id.toUpperCase(Locale.ROOT), UNKNOWN);
        }

        public Text getName() {
            return Text.translatable("trait." + id().toLowerCase(Locale.ROOT));
        }

        public Text getDescription() {
            return Text.translatable("traitDescription." + id().toLowerCase(Locale.ROOT));
        }

        public boolean isUsableOnPlayer() {
            return usableOnPlayer;
        }

        public boolean isEnabled() {
            return Config.getServerConfig().enabledTraits.getOrDefault(id(), false);
        }
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        return builder.addAll(TRAITS);
    }

    private Random random = Random.create();

    private final VillagerLike<?> entity;

    public Traits(VillagerLike<?> entity) {
        this.entity = entity;
    }

    public Set<Trait> getTraits() {
        return entity.getTrackedValue(TRAITS).getKeys().stream().map(Trait::valueOf).collect(Collectors.toSet());
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
        NbtCompound traits = entity.getTrackedValue(TRAITS).copy();
        traits.putBoolean(trait.id(), true);
        entity.setTrackedValue(TRAITS, traits);
    }

    public void removeTrait(Trait trait) {
        NbtCompound traits = entity.getTrackedValue(TRAITS).copy();
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
        Random old = random;
        random = Random.create(seed);
        inherit(from);
        random = old;
    }

    public float getVerticalScaleFactor() {
        return hasTrait(Traits.DWARFISM) ? 0.65f : 1.0f;
    }

    public float getHorizontalScaleFactor() {
        return (hasTrait(Traits.DWARFISM) ? 0.85f : 1.0f) * (hasTrait(Traits.TOUGH) ? 1.2f : 1.0f) * (hasTrait(Traits.WEAK) ? 0.85f : 1.0f);
    }
}
