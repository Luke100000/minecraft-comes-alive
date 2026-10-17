package net.conczin.mca.entity.ai.navigation;

import com.google.gson.JsonSyntaxException;
import net.conczin.mca.Config;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

public final class TeleportBlockBlacklist {
    private static MatcherSnapshot snapshot;

    private TeleportBlockBlacklist() {
    }

    public static boolean isBlocked(BlockState state) {
        refreshCacheIfNeeded();
        return matches(state, snapshot.matchers());
    }

    private static boolean matches(BlockState state, List<Predicate<BlockState>> matchers) {
        for (Predicate<BlockState> matcher : matchers) {
            if (matcher.test(state)) {
                return true;
            }
        }

        return false;
    }

    private static void refreshCacheIfNeeded() {
        List<String> configured = Config.getInstance().unSafeBlocksToTeleportOn;
        if (snapshot != null && snapshot.configured().equals(configured)) {
            return;
        }

        // Equality avoids stale matchers for different lists with the same hash.
        // Preserve null entries, which the matcher parser deliberately ignores.
        snapshot = new MatcherSnapshot(Collections.unmodifiableList(new ArrayList<>(configured)),
                buildMatchers(configured, "unSafeBlocksToTeleportOn"));
    }

    private record MatcherSnapshot(List<String> configured, List<Predicate<BlockState>> matchers) {
    }

    private static List<Predicate<BlockState>> buildMatchers(List<String> configured, String configName) {
        List<Predicate<BlockState>> matchers = new ArrayList<>();

        for (String entry : configured) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            if (entry.charAt(0) == '#') {
                ResourceLocation identifier = ResourceLocation.parse(entry.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (RegistryHelper.isTagEmpty(tag)) {
                    throw new JsonSyntaxException("Unknown block tag in " + configName + " '" + identifier + "'");
                }

                matchers.add(state -> state.is(tag));
            } else {
                ResourceLocation identifier = ResourceLocation.parse(entry);
                matchers.add(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(identifier));
            }
        }

        return List.copyOf(matchers);
    }
}
