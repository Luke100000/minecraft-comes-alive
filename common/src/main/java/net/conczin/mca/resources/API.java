package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.RandomSource;

import java.util.*;

/**
 * Class API handles interaction with MCAs configurable options via JSON in the resources folder
 */
public class API {
    static final RandomSource rng = RandomSource.create();
    static Data instance = new Data();

    public static VillageComponents getVillagePool() {
        return instance.villageComponents;
    }

    public static String getRandomWord(String from) {
        return instance.pickWord(from);
    }

    public static String getRandomSentence(String from, int wordCount) {
        List<String> words = new LinkedList<>();
        for (int i = 0; i < wordCount; i++) {
            words.add(getRandomWord(from));
        }
        return String.join(" ", words);
    }

    public static String getRandomSentence(String from, String source) {
        int wordCount = source.split(" ").length;

        String sentence = getRandomSentence(from, wordCount);

        // add !?.
        char last = source.charAt(source.length() - 1);
        if (last == '!' || last == '?' || last == '.') {
            sentence += last;
        }

        return sentence;
    }

    public static RandomSource getRng() {
        return rng;
    }

    static class Data {
        final VillageComponents villageComponents = new VillageComponents(rng);

        private final Map<String, List<String>> words = new HashMap<>();

        void init(ResourceManager manager) {
            try {
                villageComponents.load();

                words.put("zombie", Arrays.asList(Resources.read("api/names/zombie_words.json", String[].class)));
                words.put("baby", Arrays.asList(Resources.read("api/names/baby_words.json", String[].class)));
            } catch (Resources.BrokenResourceException e) {
                MCA.LOGGER.error("Could not load MCA resources", e);
            }
        }

        public String pickWord(String from) {
            return PoolUtil.pickOne(words.get(from), "?", rng);
        }
    }
}
