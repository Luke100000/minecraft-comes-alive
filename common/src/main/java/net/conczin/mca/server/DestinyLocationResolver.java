package net.conczin.mca.server;

import net.conczin.mca.CommonConfig;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class DestinyLocationResolver {
    private static final String SOMEWHERE = "somewhere";

    private DestinyLocationResolver() {
    }

    public static List<DestinyDestination> resolve(MinecraftServer server, CommonConfig config) {
        Registry<Structure> structures = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        List<ServerLevel> levels = sortedLevels(server).stream()
                .filter(level -> isDimensionAllowed(level, config))
                .toList();
        List<String> locationIds = resolveLocationIds(
                config.destinySpawnLocations,
                config.autoDiscoverDestinyLocations,
                discoverVillageLocations(structures, config.autoDiscoverDestinyLocations),
                config.destinySpawnLocationBlacklist
        );

        List<DestinyDestination> destinations = new ArrayList<>();
        for (String location : locationIds) {
            if (SOMEWHERE.equals(location)) {
                destinations.add(new DestinyDestination(location, Optional.empty()));
                continue;
            }

            parseSelector(location).ifPresent(selector -> addDestinations(
                    levels,
                    structures,
                    location,
                    selector,
                    destinations
            ));
        }
        return List.copyOf(destinations);
    }

    static List<String> resolveLocationIds(
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

    public static Optional<BlockPos> findNearest(
            ServerLevel level,
            BlockPos origin,
            DestinyDestination destination,
            int radius
    ) {
        if (destination.dimension().isEmpty() || !level.dimension().equals(destination.dimension().orElseThrow())) {
            return Optional.empty();
        }

        Optional<Selector> selector = parseSelector(destination.location());
        if (selector.isEmpty()) {
            return Optional.empty();
        }
        Selector parsedSelector = selector.get();

        return switch (parsedSelector.type()) {
            case STRUCTURE -> WorldUtils.getClosestStructurePosition(level, origin, parsedSelector.id(), radius);
            case TAG -> WorldUtils.getClosestStructurePosition(
                    level,
                    origin,
                    TagKey.create(Registries.STRUCTURE, parsedSelector.id()),
                    radius
            );
        };
    }

    private static Collection<String> discoverVillageLocations(Registry<Structure> structures, boolean autoDiscover) {
        if (!autoDiscover) {
            return List.of();
        }

        TreeSet<String> discoveredLocations = new TreeSet<>();
        structures.getTagOrEmpty(StructureTags.VILLAGE).forEach(village ->
                village.unwrapKey().ifPresent(key -> discoveredLocations.add(key.location().toString()))
        );
        structures.keySet().stream()
                .filter(id -> id.getPath().contains("village"))
                .map(ResourceLocation::toString)
                .forEach(discoveredLocations::add);
        return discoveredLocations;
    }

    private static void addDestinations(
            List<ServerLevel> levels,
            Registry<Structure> structures,
            String location,
            Selector selector,
            List<DestinyDestination> destinations
    ) {
        switch (selector.type()) {
            case STRUCTURE -> addStructureDestinations(levels, structures, location, selector.id(), destinations);
            case TAG -> addTagDestinations(levels, structures, location, selector.id(), destinations);
        }
    }

    private static void addStructureDestinations(
            List<ServerLevel> levels,
            Registry<Structure> structures,
            String location,
            ResourceLocation structureId,
            List<DestinyDestination> destinations
    ) {
        Optional<Holder.Reference<Structure>> structure = structures.getHolder(structureId);
        if (structure.isEmpty()) {
            return;
        }

        for (ServerLevel level : levels) {
            if (!level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure.get()).isEmpty()) {
                destinations.add(new DestinyDestination(location, Optional.of(level.dimension())));
            }
        }
    }

    private static void addTagDestinations(
            List<ServerLevel> levels,
            Registry<Structure> structures,
            String location,
            ResourceLocation tagId,
            List<DestinyDestination> destinations
    ) {
        var taggedStructures = structures.getTag(TagKey.create(Registries.STRUCTURE, tagId));
        if (taggedStructures.isEmpty()) {
            return;
        }

        for (ServerLevel level : levels) {
            boolean hasPlacement = taggedStructures.get().stream().anyMatch(structure ->
                    !level.getChunkSource().getGeneratorState().getPlacementsForStructure(structure).isEmpty()
            );
            if (hasPlacement) {
                destinations.add(new DestinyDestination(location, Optional.of(level.dimension())));
            }
        }
    }

    private static List<ServerLevel> sortedLevels(MinecraftServer server) {
        List<ServerLevel> levels = new ArrayList<>();
        server.getAllLevels().forEach(levels::add);
        levels.sort(Comparator.comparing(level -> level.dimension().location().toString()));
        return levels;
    }

    private static boolean isDimensionAllowed(ServerLevel level, CommonConfig config) {
        if (config.destinyOverworldOnly && !Level.OVERWORLD.equals(level.dimension())) {
            return false;
        }
        return !isBlacklisted(level.dimension().location().toString(), config.destinyDimensionBlacklist);
    }

    private static Optional<Selector> parseSelector(String location) {
        if (location == null
                || location.isBlank()
                || location.length() > DestinyDestination.MAX_LOCATION_LENGTH
                || SOMEWHERE.equals(location)) {
            return Optional.empty();
        }

        boolean tag = location.startsWith("#");
        String rawId = tag ? location.substring(1) : location;
        ResourceLocation id = ResourceLocation.tryParse(rawId);
        if (id == null) {
            return Optional.empty();
        }
        return Optional.of(new Selector(tag ? SelectorType.TAG : SelectorType.STRUCTURE, id));
    }

    private static boolean isBlacklisted(String location, Collection<String> blacklistPatterns) {
        return blacklistPatterns.stream().anyMatch(pattern -> matches(location, pattern));
    }

    private static boolean matches(String location, String pattern) {
        if (location == null || pattern == null || pattern.isBlank()) {
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

    private enum SelectorType {
        STRUCTURE,
        TAG
    }

    private record Selector(SelectorType type, ResourceLocation id) {
    }
}
