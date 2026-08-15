package net.conczin.mca.util.localization;

import net.conczin.mca.resources.PoolUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public class PooledTranslationStorage {
    private static final Pattern TRAILING_NUMBERS_PATTERN = Pattern.compile("/[0-9]+$");
    private static final Predicate<String> TRAILING_NUMBERS_PREDICATE = TRAILING_NUMBERS_PATTERN.asPredicate();

    private final Map<String, List<Tuple<String, String>>> multiTranslations = new HashMap<>();

    private final RandomSource rand = RandomSource.create();

    public PooledTranslationStorage(Map<String, String> translations) {
        translations.forEach(this::addTranslation);
    }

    private void addTranslation(String key, String value) {
        if (TRAILING_NUMBERS_PREDICATE.test(key)) {
            multiTranslations
                .computeIfAbsent(TRAILING_NUMBERS_PATTERN.matcher(key).replaceAll(""), k -> new ArrayList<>())
                .add(new Tuple<>(key, value));
        }
    }

    @NotNull
    private List<Tuple<String, String>> getOptions(String key) {
        return multiTranslations.getOrDefault(key, Collections.emptyList());
    }

    @Nullable
    public Tuple<String, String> get(String key) {
        List<Tuple<String, String>> options = getOptions(key);
        if (!options.isEmpty()) {
            Tuple<String, String> pair = PoolUtil.pickOne(options, new Tuple<>(key, key), rand);
            pair.setB(TemplateSet.INSTANCE.replace(pair.getB()));
            return pair;
        }
        return null;
    }

    public boolean contains(String key) {
        return !getOptions(key).isEmpty();
    }
}
