package net.mca.entity.ai.navigation;

import com.google.gson.JsonSyntaxException;
import net.mca.Config;
import net.mca.util.RegistryHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Shared compiled block matchers for villager teleport safety and exact path-node collision checks.
 */
public final class PathfindingBlacklist {
    private static int cachedSize = -1;
    private static int cachedHash;
    private static List<Predicate<BlockState>> cachedMatchers = List.of();
    private static int cachedCollisionSize = -1;
    private static int cachedCollisionHash;
    private static List<Predicate<BlockState>> cachedCollisionMatchers = List.of();

    private PathfindingBlacklist() {
    }

    public static boolean isBlocked(BlockState state) {
        refreshCacheIfNeeded();
        return matches(state, cachedMatchers);
    }

    public static boolean overlapsSpecialCollisionBlock(BlockGetter world, AABB box) {
        refreshCollisionCacheIfNeeded();
        int minX = floorMin(box.minX);
        int maxX = floorMax(box.maxX);
        int minY = floorMin(box.minY);
        int maxY = floorMax(box.maxY);
        int minZ = floorMin(box.minZ);
        int maxZ = floorMax(box.maxZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (matches(world.getBlockState(pos.set(x, y, z)), cachedCollisionMatchers)) {
                        return true;
                    }
                }
            }
        }

        return false;
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
        int size = configured.size();
        int hash = configured.hashCode();
        if (size == cachedSize && hash == cachedHash) {
            return;
        }

        cachedMatchers = buildMatchers(configured, "unSafeBlocksToTeleportOn");
        cachedSize = size;
        cachedHash = hash;
    }

    private static void refreshCollisionCacheIfNeeded() {
        List<String> configured = Config.getInstance().villagerPathfindingCollisionCheckBlocks;
        int size = configured.size();
        int hash = configured.hashCode();
        if (size == cachedCollisionSize && hash == cachedCollisionHash) {
            return;
        }

        cachedCollisionMatchers = buildMatchers(configured, "villagerPathfindingCollisionCheckBlocks");
        cachedCollisionSize = size;
        cachedCollisionHash = hash;
    }

    private static List<Predicate<BlockState>> buildMatchers(List<String> configured, String configName) {
        List<Predicate<BlockState>> matchers = new ArrayList<>();
        for (String entry : configured) {
            if (entry == null || entry.isBlank()) {
                continue;
            }

            if (entry.charAt(0) == '#') {
                ResourceLocation identifier = new ResourceLocation(entry.substring(1));
                TagKey<Block> tag = TagKey.create(Registries.BLOCK, identifier);
                if (RegistryHelper.isTagEmpty(tag)) {
                    throw new JsonSyntaxException("Unknown block tag in " + configName + " '" + identifier + "'");
                }
                matchers.add(state -> state.is(tag));
            } else {
                ResourceLocation identifier = new ResourceLocation(entry);
                matchers.add(state -> BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(identifier));
            }
        }
        return List.copyOf(matchers);
    }

    private static int floorMin(double value) {
        return (int) Math.floor(value);
    }

    private static int floorMax(double value) {
        return (int) Math.floor(value - 1.0E-7D);
    }
}
