package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.resources.data.skin.SkinListEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

public final class SkinSelection {
    private SkinSelection() {
    }

    public static boolean matchesGender(SkinListEntry entry, Gender gender) {
        return matchesGender(entry.getGender(), gender);
    }

    public static boolean matchesGender(Gender entryGender, Gender gender) {
        return entryGender == Gender.NEUTRAL || entryGender == Gender.UNASSIGNED || gender == Gender.NEUTRAL || gender == Gender.UNASSIGNED || entryGender == gender;
    }

    public static List<Clothing> editorClothing(Collection<Clothing> available, Gender gender) {
        return available.stream()
                .filter(clothing -> matchesGender(clothing, gender))
                .filter(clothing -> !clothing.exclude)
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    public static List<Clothing> clothingForVillager(Collection<Clothing> available, VillagerLike<?> villager, Map<String, String> professionConversions) {
        Gender gender = villager.getGenetics().getGender();
        String agePool = agePool(villager.getAgeState());
        if (agePool != null) {
            return clothingForProfession(available, gender, agePool);
        }

        String profession = BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getVillagerData().getProfession()).toString();
        List<Clothing> options = clothingForProfession(available, gender, mapProfession(profession, professionConversions));
        return options.isEmpty() ? clothingForProfession(available, gender, mapProfession("minecraft:none", professionConversions)) : options;
    }

    public static List<Clothing> clothingForProfession(Collection<Clothing> available, Gender gender, @Nullable String profession) {
        return available.stream()
                .filter(clothing -> matchesGender(clothing, gender))
                .filter(clothing -> clothing.profession == null
                        || profession == null && !clothing.exclude
                        || profession != null && (clothing.profession.equals(profession) || clothing.profession.equals(profession.replace(":", "."))))
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    @Nullable
    public static String agePool(AgeState ageState) {
        return switch (ageState) {
            case BABY -> MCA.locate("baby").toString();
            case TODDLER -> MCA.locate("toddler").toString();
            case CHILD, TEEN -> MCA.locate("child").toString();
            default -> null;
        };
    }

    public static String mapProfession(String profession, Map<String, String> professionConversions) {
        return professionConversions.getOrDefault(profession, professionConversions.getOrDefault("default", profession));
    }

    public static <T extends SkinListEntry> List<T> forGender(Collection<T> entries, Gender gender) {
        return entries.stream()
                .filter(entry -> matchesGender(entry, gender))
                .toList();
    }

    public static List<LayeredHair> layeredHair(Collection<LayeredHair> entries, LayeredHair.Category category, Gender gender) {
        return entries.stream()
                .filter(hair -> hair.getCategory() == category)
                .filter(hair -> matchesGender(hair, gender))
                .sorted((a, b) -> SkinListEntry.compareIdentifiers(a.getIdentifier(), b.getIdentifier()))
                .toList();
    }

    public static List<String> sortedIds(Collection<? extends SkinListEntry> entries, Gender gender) {
        return entries.stream()
                .filter(entry -> matchesGender(entry, gender))
                .map(SkinListEntry::getIdentifier)
                .distinct()
                .sorted(SkinListEntry::compareIdentifiers)
                .toList();
    }

    public static Optional<String> pickWeightedId(Collection<? extends SkinListEntry> entries) {
        WeightedPool<String> pool = toPool(entries, "");
        String id = pool.pickOne();
        return MCA.isBlankString(id) ? Optional.empty() : Optional.of(id);
    }

    public static <T extends SkinListEntry> Optional<String> pickDifferentWeightedId(Collection<T> entries, Predicate<T> current) {
        List<T> filtered = entries.stream().filter(current.negate()).toList();
        Optional<String> selected = pickWeightedId(filtered);
        return selected.isPresent() ? selected : pickWeightedId(entries);
    }

    public static Optional<String> pickLayeredHair(Collection<LayeredHair> entries, LayeredHair.Category category) {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        if (!category.isRequired() && API.getRng().nextFloat() > maxChance(entries)) {
            return Optional.of("");
        }
        return pickWeightedId(entries);
    }

    public static Optional<String> cycleValue(List<String> values, String selected, int offset) {
        if (values.isEmpty()) {
            return Optional.empty();
        }
        int index = values.indexOf(selected);
        int next = index < 0 ? (offset < 0 ? values.size() - 1 : 0) : Math.floorMod(index + offset, values.size());
        return Optional.of(values.get(next));
    }

    public static WeightedPool<String> toPool(Collection<? extends SkinListEntry> entries, String defaultValue) {
        return entries.stream().collect(() -> new WeightedPool.Mutable<>(defaultValue),
                (pool, entry) -> pool.add(entry.getIdentifier(), entry.getChance()),
                (a, b) -> a.entries.addAll(b.entries));
    }

    public static float maxChance(Collection<? extends SkinListEntry> entries) {
        return entries.stream()
                .map(SkinListEntry::getChance)
                .max(Float::compare)
                .orElse(0.0F);
    }
}
