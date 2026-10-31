package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class FishingSiteSearch {
    private static final int NEARBY_RADIUS = 12;
    private static final int SEARCH_RADIUS = 60;
    private static final int DISCOVERY_VERTICAL_RADIUS = 14;
    private static final int COLUMNS_PER_TICK = 64;
    private static final int MAX_CANDIDATES = 16;
    private static final int MIN_SURFACE_WATER_BLOCKS = 8;
    private static final int MAX_SMALL_WATER_CANDIDATES = 4;
    private static final int GEOMETRY_CHECKS_PER_TICK = 16;
    private static final int PATH_PROBES_PER_RADIUS = 4;
    private static final int MIN_CAST_DISTANCE = 2;
    private static final int PREFERRED_MIN_CAST_DISTANCE = 3;
    private static final int PREFERRED_MAX_CAST_DISTANCE = 6;
    private static final int MAX_CAST_DISTANCE = 7;
    private static final int PREFERRED_MAX_SETBACK = 3;
    private static final int MAX_SETBACK = 4;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    record Site(BlockPos standingPos, BlockPos waterPos, boolean likelyOpenWater, boolean smallWater, int setback) {
        Site {
            standingPos = standingPos.immutable();
            waterPos = waterPos.immutable();
        }

        Site(BlockPos standingPos, BlockPos waterPos, boolean likelyOpenWater) {
            this(standingPos, waterPos, likelyOpenWater, false, 0);
        }

        private SiteKey key() { return new SiteKey(standingPos, waterPos); }
    }

    // Metadata is for ranking; a rejected cast is identified only by its block positions.
    private record SiteKey(BlockPos standingPos, BlockPos waterPos) { }

    private final List<Site> candidates = new ArrayList<>(MAX_CANDIDATES);
    private final List<Site> remainingSurface = new ArrayList<>(MAX_CANDIDATES);
    private final Set<SiteKey> rejected = new HashSet<>();
    private BlockPos origin;
    private int preferredSetback;
    private final Comparator<Site> siteOrder = Comparator
            .comparing(Site::smallWater)
            .thenComparing(site -> site.setback() > PREFERRED_MAX_SETBACK)
            .thenComparingDouble(site -> site.standingPos().distSqr(origin))
            .thenComparingInt(site -> Math.abs(site.setback() - preferredSetback))
            .thenComparing(site -> !preferredCastDistance(site))
            .thenComparingDouble(site -> Math.abs(castDistance(site) - 4.5))
            .thenComparing(Site::standingPos, BlockPos::compareTo)
            .thenComparing(Site::waterPos, BlockPos::compareTo);
    private int radius = NEARBY_RADIUS;
    private int column;
    private boolean covered;
    private boolean done;
    private int pathProbes;
    private int geometryBudget;

    /** Scan incrementally, then try one reachable candidate per tick. */
    @Nullable
    Site tick(ServerLevel world, VillagerEntityMCA villager) {
        geometryBudget = GEOMETRY_CHECKS_PER_TICK;
        if (done) return null;
        if (origin == null) {
            origin = villager.blockPosition().immutable();
            preferredSetback = villager.getRandom().nextInt(PREFERRED_MAX_SETBACK + 1);
        }
        int width = radius * 2 + 1;
        if (column < width * width) {
            int end = Math.min(column + COLUMNS_PER_TICK, width * width);
            for (; column < end; column++) {
                int x = origin.getX() + column % width - radius;
                int z = origin.getZ() + column / width - radius;
                if (radius > NEARBY_RADIUS && Math.abs(x - origin.getX()) <= NEARBY_RADIUS
                        && Math.abs(z - origin.getZ()) <= NEARBY_RADIUS) continue;
                LevelChunk chunk = chunk(world, x, z);
                if (chunk == null) continue;
                int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                if (!covered) {
                    if (Math.abs(y - origin.getY()) > DISCOVERY_VERTICAL_RADIUS) continue;
                    BlockPos water = new BlockPos(x, y, z);
                    if (waterSurface(world, water)) offerWater(world, water);
                } else {
                    // Only examine covered water when the ordinary surface search failed.
                    for (int dy = DISCOVERY_VERTICAL_RADIUS; dy >= -DISCOVERY_VERTICAL_RADIUS; dy--) {
                        BlockPos water = new BlockPos(x, origin.getY() + dy, z);
                        if (water.getY() != y && waterSurface(world, water)) {
                            offerWater(world, water);
                            break;
                        }
                    }
                }
            }
            return null;
        }

        // Reserve discovery opportunities, retaining surface banks if those alternatives are absent.
        int probeLimit = covered ? PATH_PROBES_PER_RADIUS
                : PATH_PROBES_PER_RADIUS - 1 - (radius == SEARCH_RADIUS ? 1 : 0);
        if (covered && radius == SEARCH_RADIUS && pathProbes == PATH_PROBES_PER_RADIUS - 1) {
            // Spend the final query on tiny water only when an actual candidate exists.
            candidates.sort(Comparator.comparing(Site::smallWater).reversed());
        }
        while (!candidates.isEmpty() && !candidates.getFirst().smallWater() && geometryBudget > 0) {
            Site site = candidates.removeFirst();
            if (rejected.contains(site.key())) continue;
            if (pathProbes >= probeLimit) {
                if (!covered) remainingSurface.add(site);
                continue;
            }
            geometryBudget--;
            if (!isUsable(world, villager, site)) continue;
            // A failed query also consumes this tick's one-query allowance.
            return canReachSite(villager, site) ? site : null;
        }
        if (!candidates.isEmpty() && !candidates.getFirst().smallWater()) return null;

        int surfaceLimit = PATH_PROBES_PER_RADIUS
                - (radius == SEARCH_RADIUS && candidates.stream().anyMatch(Site::smallWater) ? 1 : 0);
        while (covered && !remainingSurface.isEmpty() && pathProbes < surfaceLimit) {
            if (geometryBudget == 0) return null;
            Site site = remainingSurface.removeFirst();
            if (rejected.contains(site.key())) continue;
            geometryBudget--;
            if (isUsable(world, villager, site)) return canReachSite(villager, site) ? site : null;
        }

        if (!covered) {
            covered = true;
            column = 0;
        } else if (radius < SEARCH_RADIUS) {
            beginSearch(SEARCH_RADIUS);
        } else {
            // Tiny alternatives stay at the end of the same bounded shortlist
            // through discovery. Recheck geometry and reachability now; a blocked
            // nearest bank must not hide another tiny bank within the allowance.
            while ((!candidates.isEmpty() || !remainingSurface.isEmpty()) && pathProbes < PATH_PROBES_PER_RADIUS) {
                if (geometryBudget == 0) return null;
                Site fallback = candidates.isEmpty() ? remainingSurface.removeFirst() : candidates.removeFirst();
                if (rejected.contains(fallback.key())) continue;
                geometryBudget--;
                if (!isUsable(world, villager, fallback)) continue;
                return canReachSite(villager, fallback) ? fallback : null;
            }
            done = true;
        }
        return null;
    }

    boolean exhausted() { return done; }

    private boolean canReachSite(VillagerEntityMCA villager, Site site) {
        Vec3 delta = villager.position().subtract(Vec3.atBottomCenterOf(site.standingPos()));
        if (delta.horizontalDistanceSqr() <= 0.64 && Math.abs(delta.y) <= 0.5) return true;
        if (pathProbes >= PATH_PROBES_PER_RADIUS) return false;
        pathProbes++;
        var navigation = villager.getNavigation();
        var path = navigation instanceof MCAGroundPathNavigation mcaNavigation
                ? mcaNavigation.createPathForPersistentIntent(Set.of(site.standingPos()), 0)
                : navigation.createPath(site.standingPos(), 0);
        return path != null && path.canReach() && path.getEndNode() != null
                && path.getEndNode().asBlockPos().equals(site.standingPos());
    }

    void reset() {
        rejected.clear();
        origin = null;
        candidates.clear();
        remainingSurface.clear();
        done = false;
        beginSearch(NEARBY_RADIUS);
    }

    private void beginSearch(int searchRadius) {
        candidates.removeIf(site -> !site.smallWater());
        remainingSurface.clear();
        radius = searchRadius;
        column = 0;
        covered = false;
        pathProbes = 0;
    }

    void reject(Site site) {
        rejected.add(site.key());
    }

    /** A bounded new aim from the existing bank; never requests another path. */
    @Nullable
    Site chooseCastSite(ServerLevel world, VillagerEntityMCA villager, Site current, @Nullable BlockPos previousWater) {
        int desiredDistance = villager.getRandom().nextIntBetweenInclusive(PREFERRED_MIN_CAST_DISTANCE, PREFERRED_MAX_CAST_DISTANCE);
        List<Site> options = new ArrayList<>(48);
        Set<BlockPos> offeredWater = new HashSet<>();
        BlockPos feet = current.standingPos();
        for (int[] direction : DIRECTIONS) {
            double length = Math.hypot(direction[0], direction[1]);
            for (int distance = MIN_CAST_DISTANCE; distance <= MAX_CAST_DISTANCE; distance++) {
                int x = feet.getX() + (int)Math.round(direction[0] * distance / length);
                int z = feet.getZ() + (int)Math.round(direction[1] * distance / length);
                for (int y = feet.getY() + 3; y >= feet.getY() - 3; y--) {
                    BlockPos water = new BlockPos(x, y, z);
                    if (!waterSurface(world, water)) continue;
                    Site option = new Site(feet, water, openWaterHint(world, water));
                    if (!rejected.contains(option.key())
                            && castDistance(option) >= MIN_CAST_DISTANCE && castDistance(option) <= MAX_CAST_DISTANCE && offeredWater.add(water)) {
                        options.add(option);
                    }
                    break;
                }
            }
        }

        boolean currentRejected = rejected.contains(current.key());
        if (!currentRejected && offeredWater.add(current.waterPos())) options.add(current);
        // Shuffle before the stable distance sort to randomize equal-distance
        // directions without favouring a particular cardinal scan order.
        for (int i = options.size() - 1; i > 0; i--) {
            Collections.swap(options, i, villager.getRandom().nextInt(i + 1));
        }
        // Keep all normal-range casts ahead of short fallbacks, even a repeat.
        // Potential open water must reach the bounded geometry checks before
        // random distance preferences can fill them with shallow-water aims.
        Comparator<Site> castOrder = Comparator.comparing((Site site) -> !preferredCastDistance(site))
                .thenComparing(Comparator.comparing(Site::likelyOpenWater).reversed())
                .thenComparing(site -> site.waterPos().equals(previousWater))
                .thenComparingDouble(site -> Math.abs(castDistance(site) - desiredDistance));
        options.sort(castOrder);
        Site choice = null;
        boolean currentChecked = false;
        // Validate the actual cast trajectory, not only the planned standing centre.
        Vec3 castPosition = villager.position();
        for (int i = 0; i < Math.min(GEOMETRY_CHECKS_PER_TICK - 1, options.size()); i++) {
            Site option = options.get(i);
            currentChecked |= option.waterPos().equals(current.waterPos());
            if (!isUsable(world, villager, option, castPosition)) continue;
            Site valid = new Site(feet, option.waterPos(), likelyOpenWater(world, option.waterPos()));
            if (preferredCastDistance(valid) && valid.likelyOpenWater()) return valid;
            if (choice == null || castOrder.compare(valid, choice) < 0) choice = valid;
        }
        if (!currentRejected && !currentChecked && isUsable(world, villager, current, castPosition)) {
            Site valid = new Site(feet, current.waterPos(), likelyOpenWater(world, current.waterPos()));
            if (choice == null || castOrder.compare(valid, choice) < 0) choice = valid;
        }
        return choice;
    }

    private void offerWater(ServerLevel world, BlockPos water) {
        boolean smallWater = !hasEnoughSurfaceWater(world, water);
        boolean openHint = openWaterHint(world, water);
        for (int[] direction : DIRECTIONS) {
            double length = Math.hypot(direction[0], direction[1]);
            for (int distance = MIN_CAST_DISTANCE; distance <= MAX_CAST_DISTANCE; distance++) {
                int x = water.getX() + (int)Math.round(direction[0] * distance / length);
                int z = water.getZ() + (int)Math.round(direction[1] * distance / length);
                LevelChunk bank = chunk(world, x, z);
                if (bank == null || Math.abs(x - origin.getX()) > radius || Math.abs(z - origin.getZ()) > radius) continue;
                BlockPos standing = standingCentre(world, bank, x, z, water.getY());
                if (standing == null) continue;
                int setback = shorelineSetback(world, standing, water);
                if (setback > MAX_SETBACK) continue;
                Site site = new Site(standing, water, openHint, smallWater, setback);
                if (castDistance(site) < MIN_CAST_DISTANCE || castDistance(site) > MAX_CAST_DISTANCE) continue;
                // Keep distinct standing destinations, rather than sixteen targets for the same bank.
                int sameStanding = -1;
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).standingPos().equals(standing)) { sameStanding = i; break; }
                }
                if (sameStanding >= 0) {
                    if (siteOrder.compare(site, candidates.get(sameStanding)) >= 0) continue;
                    candidates.remove(sameStanding);
                }
                candidates.add(site);
                candidates.sort(siteOrder);
                int smallCount = 0;
                for (Site candidate : candidates) if (candidate.smallWater()) smallCount++;
                if (smallCount > MAX_SMALL_WATER_CANDIDATES) {
                    candidates.removeLast();
                } else if (candidates.size() > MAX_CANDIDATES) {
                    // Keep some tiny pools even when larger but unreachable banks fill the shortlist.
                    candidates.remove(candidates.size() - smallCount - 1);
                }
            }
        }
    }

    private static boolean hasEnoughSurfaceWater(ServerLevel world, BlockPos water) {
        List<BlockPos> connected = new ArrayList<>(MIN_SURFACE_WATER_BLOCKS);
        connected.add(water);
        for (int index = 0; index < connected.size(); index++) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos neighbour = connected.get(index).relative(direction);
                if (!connected.contains(neighbour) && waterSurface(world, neighbour)) {
                    connected.add(neighbour);
                    if (connected.size() == MIN_SURFACE_WATER_BLOCKS) return true;
                }
            }
        }
        return false;
    }

    @Nullable
    private static BlockPos standingCentre(ServerLevel world, LevelChunk bank, int x, int z, int waterY) {
        // WORLD_SURFACE includes plants and roofs. Resolve only the centre column here;
        // the bounded shortlist phase checks path type and entity clearance.
        int top = Math.min(bank.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1, waterY + 3);
        for (int y = top; y >= waterY - 3; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            if (y < world.getMinBuildHeight() + 2 || y + 3 >= world.getMaxBuildHeight()) continue;
            if (world.getFluidState(feet).isEmpty() && world.getFluidState(feet.below()).isEmpty()
                    && supported(world, feet)
                    && world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()
                    && world.getBlockState(feet.above()).getCollisionShape(world, feet.above()).isEmpty()) return feet;
        }
        return null;
    }

    static boolean isUsable(ServerLevel world, VillagerEntityMCA villager, Site site) {
        return isUsable(world, villager, site, Vec3.atBottomCenterOf(site.standingPos()));
    }

    private static boolean isUsable(ServerLevel world, VillagerEntityMCA villager, Site site, Vec3 castPosition) {
        BlockPos feet = site.standingPos();
        double distance = castDistance(site);
        if (!waterSurface(world, site.waterPos()) || distance < MIN_CAST_DISTANCE || distance > MAX_CAST_DISTANCE
                || Math.abs(feet.getY() - site.waterPos().getY()) > 3
                || feet.getY() < world.getMinBuildHeight() + 2 || feet.getY() + 3 >= world.getMaxBuildHeight()
                || !loaded(world, feet) || !world.getFluidState(feet).isEmpty()
                || !world.getFluidState(feet.below()).isEmpty() || !supported(world, feet)
                || shorelineSetback(world, feet, site.waterPos()) > MAX_SETBACK) return false;
        // Vanilla examines adjacent blocks for hazards. Check chunk availability
        // first, without requiring those neighbours to be dry or supported.
        if (!loaded(world, feet.offset(-1, 0, -1)) || !loaded(world, feet.offset(1, 0, -1))
                || !loaded(world, feet.offset(-1, 0, 1)) || !loaded(world, feet.offset(1, 0, 1))) return false;
        PathType type = WalkNodeEvaluator.getPathTypeStatic(villager, feet);
        if (type != PathType.WALKABLE && type != PathType.WATER_BORDER) return false;
        Vec3 standing = Vec3.atBottomCenterOf(feet);
        return world.noCollision(villager, villager.getBoundingBox().move(standing.subtract(villager.position())))
                && MCAFishingBobberEntity.isTrajectoryClear(world, villager, standing, site.waterPos())
                && (castPosition.equals(standing)
                || MCAFishingBobberEntity.isTrajectoryClear(world, villager, castPosition, site.waterPos()));
    }

    private static boolean supported(ServerLevel world, BlockPos feet) {
        if (!loaded(world, feet)) return false;
        var state = world.getBlockState(feet.below());
        return state.isFaceSturdy(world, feet.below(), Direction.UP)
                && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CACTUS)
                && !state.is(Blocks.CAMPFIRE) && !state.is(Blocks.SOUL_CAMPFIRE);
    }

    private static boolean waterSurface(ServerLevel world, BlockPos pos) {
        return pos.getY() >= world.getMinBuildHeight() && pos.getY() + 2 < world.getMaxBuildHeight()
                && sourceWater(world, pos) && world.getFluidState(pos.above()).isEmpty()
                && world.getBlockState(pos.above()).getCollisionShape(world, pos.above()).isEmpty();
    }

    private static boolean sourceWater(ServerLevel world, BlockPos pos) {
        if (!loaded(world, pos)) return false;
        var fluid = world.getFluidState(pos);
        return fluid.is(FluidTags.WATER) && fluid.isSource()
                && world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    private static boolean likelyOpenWater(ServerLevel world, BlockPos water) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos pos = water.offset(dx, 0, dz);
                if (!sourceWater(world, pos) || !sourceWater(world, pos.below())) return false;
                for (int y = 1; y <= 2; y++) {
                    var state = world.getBlockState(pos.above(y));
                    if (!state.isAir() && !state.is(Blocks.LILY_PAD)) return false;
                }
            }
        }
        return true;
    }

    private static boolean openWaterHint(ServerLevel world, BlockPos water) {
        // A cheap necessary condition; casting confirms the full water volume.
        return sourceWater(world, water.below())
                && sourceWater(world, water.east(2)) && sourceWater(world, water.west(2))
                && sourceWater(world, water.north(2)) && sourceWater(world, water.south(2));
    }

    private static boolean preferredCastDistance(Site site) {
        double distance = castDistance(site);
        return distance >= PREFERRED_MIN_CAST_DISTANCE && distance <= PREFERRED_MAX_CAST_DISTANCE;
    }

    private static double castDistance(Site site) {
        return Math.hypot(site.standingPos().getX() - site.waterPos().getX(), site.standingPos().getZ() - site.waterPos().getZ());
    }

    // Walk the cast ray towards the water. The first surface-water column
    // marks the shoreline; adjacent land is a setback of zero.
    private static int shorelineSetback(ServerLevel world, BlockPos feet, BlockPos water) {
        int dx = water.getX() - feet.getX();
        int dz = water.getZ() - feet.getZ();
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        for (int step = 1; step <= steps; step++) {
            int x = feet.getX() + (int) Math.round((double) dx * step / steps);
            int z = feet.getZ() + (int) Math.round((double) dz * step / steps);
            if (waterSurface(world, new BlockPos(x, water.getY(), z))) return step - 1;
        }
        return MAX_CAST_DISTANCE;
    }

    private static boolean loaded(ServerLevel world, BlockPos pos) { return chunk(world, pos.getX(), pos.getZ()) != null; }

    @Nullable
    private static LevelChunk chunk(ServerLevel world, int x, int z) { return world.getChunkSource().getChunkNow(x >> 4, z >> 4); }
}
