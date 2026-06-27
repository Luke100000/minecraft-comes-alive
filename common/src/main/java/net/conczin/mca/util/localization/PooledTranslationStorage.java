package net.conczin.mca.util.localization;

import net.conczin.mca.resources.PoolUtil;
import net.conczin.mca.resources.data.SerializablePair;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PooledTranslationStorage {
    private static final Pattern TRAILING_NUMBERS_PATTERN = Pattern.compile("/[0-9]+$");
    private static final Predicate<String> TRAILING_NUMBERS_PREDICATE = TRAILING_NUMBERS_PATTERN.asPredicate();

    private final Map<String, List<SerializablePair<String, String>>> multiTranslations = new HashMap<>();

    private final RandomSource rand = RandomSource.create();

    public PooledTranslationStorage(Map<String, String> translations) {
        translations.forEach(this::addTranslation);
    }

    private void addTranslation(String key, String value) {
        if (TRAILING_NUMBERS_PREDICATE.test(key)) {
            multiTranslations
                    .computeIfAbsent(TRAILING_NUMBERS_PATTERN.matcher(key).replaceAll(""), k -> new ArrayList<>())
                    .add(new SerializablePair<>(key, value));
        }
    }

    @NotNull
    private List<SerializablePair<String, String>> getOptions(String key) {
        return multiTranslations.getOrDefault(key, Collections.emptyList());
    }

    @Nullable
    public SerializablePair<String, String> get(String key) {
        List<SerializablePair<String, String>> options = getOptions(key);
        if (!options.isEmpty()) {
            SerializablePair<String, String> pair = PoolUtil.pickOne(options, new SerializablePair<>(key, key), rand);
            return new SerializablePair<>(pair.left(), TemplateSet.INSTANCE.replace(pair.right()));
        }
        return null;
    }

    public boolean contains(String key) {
        return !getOptions(key).isEmpty();
    }
}
