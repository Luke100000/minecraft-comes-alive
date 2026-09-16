package net.conczin.mca.server;

import net.conczin.mca.CommonConfig;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Collection;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DestinyLocationResolver {
    private DestinyLocationResolver() {
    }

    public static List<String> resolve(CommonConfig config, Registry<Structure> structures) {
        TreeSet<String> discoveredLocations = new TreeSet<>();
        if (config.autoDiscoverDestinyLocations) {
            structures.getTagOrEmpty(StructureTags.VILLAGE).forEach(village ->
                    village.unwrapKey().ifPresent(key -> discoveredLocations.add(key.location().toString()))
            );

            // Some structure mods use village-named structures without adding them to #minecraft:village.
            structures.keySet().stream()
                    .filter(id -> id.getPath().contains("village"))
                    .map(ResourceLocation::toString)
                    .forEach(discoveredLocations::add);
        }

        return resolve(
                config.destinySpawnLocations,
                config.autoDiscoverDestinyLocations,
                discoveredLocations,
                config.destinySpawnLocationBlacklist
        );
    }

    public static List<String> resolve(
            Collection<String> configuredLocations,
            boolean autoDiscover,
            Collection<String> discoveredLocations,
            Collection<String> blacklistPatterns
    ) {
        LinkedHashSet<String> locations = new LinkedHashSet<>(configuredLocations);
        if (autoDiscover) {
            new TreeSet<>(discoveredLocations).forEach(locations::add);
        }

        locations.removeIf(location -> isBlacklisted(location, blacklistPatterns));
        return List.copyOf(locations);
    }

    private static boolean isBlacklisted(String location, Collection<String> blacklistPatterns) {
        return blacklistPatterns.stream().anyMatch(pattern -> matches(location, pattern));
    }

    private static boolean matches(String location, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return false;
        }
        if (!pattern.contains("*")) {
            return location.equals(pattern);
        }

        String regex = Arrays.stream(pattern.split("\\*", -1))
                .map(Pattern::quote)
                .collect(Collectors.joining(".*"));
        return location.matches(regex);
    }
}
