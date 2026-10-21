package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final int SEARCH_RADIUS = 48;
    private static final int DISCOVERY_VERTICAL_RADIUS = 14;
    private static final int COLUMNS_PER_TICK = 64;
    private static final int MAX_CANDIDATES = 16;
    private static final int GEOMETRY_CHECKS_PER_TICK = 16;
    private static final int MIN_CAST_DISTANCE = 2;
    private static final int PREFERRED_MIN_CAST_DISTANCE = 3;
    private static final int MAX_CAST_DISTANCE = 7;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    record Site(BlockPos standingPos, BlockPos waterPos, boolean likelyOpenWater) {
        Site {
            standingPos = standingPos.immutable();
            waterPos = waterPos.immutable();
        }

        private SiteKey key() { return new SiteKey(standingPos, waterPos); }
    }

    // Open-water preference ranks a site; it must not change its rejection identity.
    private record SiteKey(BlockPos standingPos, BlockPos waterPos) { }

    private final List<Site> candidates = new ArrayList<>(MAX_CANDIDATES);
    private final List<Site> remainingSurface = new ArrayList<>(MAX_CANDIDATES);
    private final Set<SiteKey> rejected = new HashSet<>();
    private BlockPos origin;
    private int radius = NEARBY_RADIUS;
    private int column;
    private boolean covered;
    private boolean validated;
    private boolean done;
    private int pathProbes;
    private int geometryBudget;

    /** Offers the next site on the server tick thread; the chore owns the selected site. */
    @Nullable
    Site tick(ServerLevel world, VillagerEntityMCA villager) {
        geometryBudget = GEOMETRY_CHECKS_PER_TICK;
        if (done) return null;
        if (origin == null) origin = villager.blockPosition().immutable();
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
                    if (Math.abs(y - origin.getY()) <= DISCOVERY_VERTICAL_RADIUS) offerWater(world, new BlockPos(x, y, z));
                } else {
                    // At most one exposed water surface per fallback column.
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
        if (!validated) {
            List<Site> usable = new ArrayList<>(MAX_CANDIDATES);
            for (Site site : candidates) {
                geometryBudget--;
                if (isUsable(world, villager, site)) {
                    usable.add(new Site(site.standingPos(), site.waterPos(), likelyOpenWater(world, site.waterPos())));
                }
            }
            candidates.clear();
            candidates.addAll(usable);
            candidates.sort(order());
            validated = true;
            return null;
        }
        if (pathProbes >= 4) {
            if (radius < SEARCH_RADIUS) {
                // Near-bank failures must not consume the distant search's
                // separate, still bounded pathfinding allowance.
                beginSearch(SEARCH_RADIUS);
                return null;
            }
            done = true;
            return null;
        }
        if (!covered && pathProbes >= 3) {
            remainingSurface.addAll(candidates);
            candidates.clear();
        }
        while (!candidates.isEmpty()) {
            if (geometryBudget == 0) return null;
            Site site = candidates.removeFirst();
            if (rejected.contains(site.key())) continue;
            geometryBudget--;
            if (!isUsable(world, villager, site)) continue;
            Vec3 delta = villager.position().subtract(Vec3.atBottomCenterOf(site.standingPos()));
            if (delta.horizontalDistanceSqr() <= 0.64 && Math.abs(delta.y) <= 0.5) return site;
            pathProbes++;
            var path = villager.getNavigation().createPath(site.standingPos(), 0);
            // Even a failed query consumes this tick's one-query allowance.
            return path != null && path.canReach() && path.getEndNode() != null
                    && path.getEndNode().asBlockPos().equals(site.standingPos()) ? site : null;
        }
        if (!covered) {
            covered = true;
            column = 0;
            validated = false;
        } else if (!remainingSurface.isEmpty() && pathProbes < 4) {
            candidates.addAll(remainingSurface);
            remainingSurface.clear();
        } else if (radius < SEARCH_RADIUS) {
            // Keep nearby fishing responsive, then look farther before entering
            // the failed-chore cooldown that permits random wandering.
            beginSearch(SEARCH_RADIUS);
        } else {
            done = true;
        }
        return null;
    }

    boolean exhausted() { return done; }

    void reset() {
        rejected.clear();
        origin = null;
        done = false;
        beginSearch(NEARBY_RADIUS);
    }

    private void beginSearch(int searchRadius) {
        candidates.clear();
        remainingSurface.clear();
        radius = searchRadius;
        column = 0;
        covered = false;
        validated = false;
        pathProbes = 0;
    }

    void reject(Site site) {
        rejected.add(site.key());
    }

    /** A bounded new aim from the existing bank; never requests another path. */
    @Nullable
    Site chooseCastSite(ServerLevel world, VillagerEntityMCA villager, Site current, @Nullable BlockPos previousWater) {
        int desiredDistance = villager.getRandom().nextIntBetweenInclusive(PREFERRED_MIN_CAST_DISTANCE, MAX_CAST_DISTANCE);
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
                    Site option = new Site(feet, water, false);
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
        // Within each range, prefer variety and then the rolled distance.
        options.sort(Comparator.comparing((Site site) -> castDistance(site) < PREFERRED_MIN_CAST_DISTANCE)
                .thenComparing(site -> site.waterPos().equals(previousWater))
                .thenComparingDouble(site -> Math.abs(castDistance(site) - desiredDistance)));
        Site choice = null;
        // Arrival permits an off-centre pose. Check the arc that will actually be
        // launched this tick, while discovery keeps its centred planning pose.
        Vec3 castPosition = villager.position();
        // Try the desired distance first, then nearby alternatives for small or
        // obstructed ponds. Reserve one full check for reusing the original aim.
        for (int i = 0; i < Math.min(GEOMETRY_CHECKS_PER_TICK - 1, options.size()); i++) {
            Site option = options.get(i);
            if (isUsable(world, villager, option, castPosition)) {
                choice = option;
                break;
            }
        }
        if (choice == null && !currentRejected && isUsable(world, villager, current, castPosition)) choice = current;
        if (choice == null) return null;
        return new Site(feet, choice.waterPos(), likelyOpenWater(world, choice.waterPos()));
    }

    private Comparator<Site> order() {
        return Comparator.comparing((Site site) -> castDistance(site) < PREFERRED_MIN_CAST_DISTANCE)
                .thenComparing(Comparator.comparing(Site::likelyOpenWater).reversed())
                .thenComparingDouble(site -> Math.abs(castDistance(site) - 5.0))
                .thenComparingDouble(site -> site.standingPos().distSqr(origin))
                .thenComparing(Site::standingPos, BlockPos::compareTo)
                .thenComparing(Site::waterPos, BlockPos::compareTo);
    }

    private void offerWater(ServerLevel world, BlockPos water) {
        if (!waterSurface(world, water)) return;
        // Cheap depth/neighbour hint; the full vanilla-shaped check follows on the shortlist.
        boolean openHint = sourceWater(world, water.below())
                && sourceWater(world, water.east(2)) && sourceWater(world, water.west(2))
                && sourceWater(world, water.north(2)) && sourceWater(world, water.south(2));
        Comparator<Site> siteOrder = order();
        for (int[] direction : DIRECTIONS) {
            double length = Math.hypot(direction[0], direction[1]);
            for (int distance = MIN_CAST_DISTANCE; distance <= MAX_CAST_DISTANCE; distance++) {
                int x = water.getX() + (int)Math.round(direction[0] * distance / length);
                int z = water.getZ() + (int)Math.round(direction[1] * distance / length);
                LevelChunk bank = chunk(world, x, z);
                if (bank == null || Math.abs(x - origin.getX()) > radius || Math.abs(z - origin.getZ()) > radius) continue;
                BlockPos standing = standingCentre(world, bank, x, z, water.getY());
                if (standing == null) continue;
                Site site = new Site(standing, water, openHint);
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
                if (candidates.size() > MAX_CANDIDATES) candidates.removeLast();
            }
        }
    }

    @Nullable
    private static BlockPos standingCentre(ServerLevel world, LevelChunk bank, int x, int z, int waterY) {
        // WORLD_SURFACE includes plants and roofs. Resolve only the centre column here;
        // the bounded shortlist phase checks the full footprint and entity clearance.
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
        if (!waterSurface(world, site.waterPos()) || castDistance(site) < MIN_CAST_DISTANCE || castDistance(site) > MAX_CAST_DISTANCE
                || Math.abs(feet.getY() - site.waterPos().getY()) > 3 || !dryFootprint(world, feet)) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos neighbour = feet.offset(dx, 0, dz);
                boolean hasSupport = supported(world, neighbour);
                if ((dx == 0 || dz == 0) && !hasSupport) return false;
                if (!hasSupport && !supported(world, neighbour.below())) return false;
            }
        }
        Vec3 standing = Vec3.atBottomCenterOf(feet);
        return world.noCollision(villager, villager.getBoundingBox().move(standing.subtract(villager.position())))
                && MCAFishingBobberEntity.isTrajectoryClear(world, villager, standing, site.waterPos())
                && (castPosition.equals(standing)
                || MCAFishingBobberEntity.isTrajectoryClear(world, villager, castPosition, site.waterPos()));
    }

    private static boolean dryFootprint(ServerLevel world, BlockPos feet) {
        if (feet.getY() < world.getMinBuildHeight() + 2 || feet.getY() + 3 >= world.getMaxBuildHeight()) return false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos pos = feet.offset(dx, 0, dz);
                if (!loaded(world, pos) || !world.getFluidState(pos).isEmpty()
                        || !world.getFluidState(pos.below()).isEmpty()) return false;
            }
        }
        return true;
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

    private static double castDistance(Site site) {
        return Math.hypot(site.standingPos().getX() - site.waterPos().getX(), site.standingPos().getZ() - site.waterPos().getZ());
    }

    private static boolean loaded(ServerLevel world, BlockPos pos) { return chunk(world, pos.getX(), pos.getZ()) != null; }

    @Nullable
    private static LevelChunk chunk(ServerLevel world, int x, int z) { return world.getChunkSource().getChunkNow(x >> 4, z >> 4); }
}
