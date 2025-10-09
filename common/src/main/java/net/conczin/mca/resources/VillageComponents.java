package net.conczin.mca.resources;

import net.conczin.mca.resources.Resources.BrokenResourceException;
import net.conczin.mca.resources.data.NameSet;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;

public class VillageComponents {
    private final Map<String, NameSet> namePool = new HashMap<>();

    private final RandomSource rng;

    VillageComponents(RandomSource rng) {
        this.rng = rng;
    }

    void load() throws BrokenResourceException {
        namePool.put("village", Resources.read("api/names/village.json", NameSet.class));
    }

    //returns a random generated name for a given name set
    public String pickVillageName(String from) {
        return namePool.getOrDefault(from, NameSet.DEFAULT).toName(rng);
    }
}
