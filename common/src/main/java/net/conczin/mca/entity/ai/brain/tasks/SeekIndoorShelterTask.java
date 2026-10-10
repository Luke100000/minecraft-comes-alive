package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.BedDebugLog;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.server.world.data.IndoorRoomCache;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/** Bed POIs select shelter; floor topology supplies house admission without claiming HOME. */
public final class SeekIndoorShelterTask extends EnterBuildingTask {
    private static final int MAX_HOUSES = 10;
    private static final double MAX_ADDITIONAL_TRAVEL = 64.0D;
    private static final long FAILED_SEARCH_RETRY_TICKS = 100L;
    private static final VillagerHostilesSensor HOSTILES = new VillagerHostilesSensor();
    private long nextAttemptTime;
    private long nextDebugTime;

    public SeekIndoorShelterTask(float speed) {
        super("house", speed);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        if (villager.isSleeping() || villager.getBrain().hasMemoryValue(MemoryModuleType.HOME)
                || villager.getBrain().isActive(Activity.PANIC) || villager.getBrain().isActive(Activity.HIDE)
                || level.getGameTime() < nextAttemptTime) return false;
        nextAttemptTime = level.getGameTime() + 20L + level.getRandom().nextInt(20);
        boolean alreadyIndoors = currentRoom(villager).isPresent()
                && isUsableFloor(level, villager, villager.blockPosition());
        if (alreadyIndoors && tryDebug(level.getGameTime())) {
            MCA.LOGGER.info("[MCA-SHELTER][stay] name={} uuid={} gameTime={} pos={} shelterBed={} reason=ALREADY_INDOORS admission=NOT_CHECKED",
                    villager.getName().getString(), villager.getUUID(), level.getGameTime(), villager.blockPosition(),
                    villager.getBrain().getMemory(MemoryModuleTypeMCA.SHELTER_BED));
        }
        return !alreadyIndoors;
    }

    private boolean tryDebug(long gameTime) {
        if (!BedDebugLog.SHELTER_ENABLED || gameTime < nextDebugTime) return false;
        nextDebugTime = gameTime + 200L;
        return true;
    }

    /** A selected bed identifies a house; local wandering stays in the actual current room. */
    static Optional<IndoorRoomCache.Room> currentRoom(VillagerEntityMCA villager) {
        ServerLevel level = (ServerLevel) villager.level();
        var cache = VillageManager.get(level).getIndoorRooms();
        var selected = villager.getBrain().getMemory(MemoryModuleTypeMCA.SHELTER_BED)
                .filter(bed -> bed.dimension().equals(level.dimension())).map(GlobalPos::pos);
        if (selected.isPresent()) {
            var room = cache.resolve(selected.orElseThrow());
            if (room.isPresent() && room.orElseThrow().floorCells().contains(villager.blockPosition())) return room;
        }
        var beds = Stream.concat(selected.stream(), nearbyBeds(level, villager)).distinct().toList();
        for (var house : cache.resolveHouses(beds, MAX_HOUSES)) {
            for (var room : house.rooms()) {
                if (room.floorCells().contains(villager.blockPosition())) {
                    villager.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED,
                            GlobalPos.of(level.dimension(), house.anchorFor(villager.blockPosition())));
                    return Optional.of(room);
                }
            }
        }
        if (selected.isEmpty() || cache.resolve(selected.orElseThrow()).isEmpty()) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
        }
        return Optional.empty();
    }

    private static Stream<BlockPos> nearbyBeds(ServerLevel level, VillagerEntityMCA villager) {
        return level.getPoiManager().findAllClosestFirstWithType(
                poi -> poi.is(PoiTypes.HOME), pos -> isLoaded(level, pos),
                villager.blockPosition(), 48, PoiManager.Occupancy.ANY).map(bed -> bed.getSecond());
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        ServerLevel level = (ServerLevel) villager.level();
        var manager = VillageManager.get(level);
        boolean debug = tryDebug(level.getGameTime());
        var beds = nearbyBeds(level, villager).toList();
        var houses = manager.getIndoorRooms().resolveHouses(beds, MAX_HOUSES);
        if (debug) {
            MCA.LOGGER.info("[MCA-SHELTER][search] name={} uuid={} gameTime={} pos={} activity={} nearbyBedAnchors={} resolvedHouseAnchors={}",
                    villager.getName().getString(), villager.getUUID(), level.getGameTime(), villager.blockPosition(),
                    villager.getBrain().getActiveNonCoreActivity(), beds, houses.stream().map(IndoorRoomCache.House::anchor).toList());
        }
        if (houses.isEmpty()) {
            if (debug) logSelection(villager, "NO_RESOLVED_HOUSES", null, null, null);
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
            backOffFailedSearch(level);
            return Optional.empty();
        }
        List<Villager> loaded = manager.getLoadedVillagers();
        Map<UUID, BlockPos> homes = new HashMap<>();
        for (var village : manager) village.forEachResidentHome(homes::put);
        for (Villager resident : loaded) {
            var home = resident.getBrain().getMemory(MemoryModuleType.HOME)
                    .filter(pos -> pos.dimension().equals(level.dimension()));
            if (home.isPresent()) homes.put(resident.getUUID(), home.orElseThrow().pos());
            else homes.remove(resident.getUUID());
        }

        List<ReachableHouse> reachable = new ArrayList<>();
        ReachableHouse nearest = null;
        for (var house : houses) {
            Occupancy occupancy;
            if (house.membershipKnown()) {
                occupancy = occupancy(level, villager, house, loaded, homes, debug);
            } else {
                occupancy = null;
                if (debug) {
                    MCA.LOGGER.info("[MCA-SHELTER][capacity] uuid={} gameTime={} anchor={} beds={} floorCells={} membershipKnown=false admission=CAPACITY_BYPASSED",
                            villager.getUUID(), level.getGameTime(), house.anchor(), house.bedHeads(), house.floorCells().size());
                }
            }
            Set<BlockPos> candidates = house.rooms().stream().flatMap(room -> room.floorCells().stream())
                    .filter(pos -> isLoaded(level, pos) && isUsableFloor(level, villager, pos))
                    .collect(java.util.stream.Collectors.toSet());
            ReachableHouse route = findReachable(level, villager, house, candidates, occupancy, debug);
            if (route == null) continue;
            reachable.add(route);
            // Strict comparison preserves discovery order for equal-length routes.
            if (nearest == null || route.distance < nearest.distance) nearest = route;
        }
        if (nearest == null) {
            if (debug) logSelection(villager, "NO_REACHABLE_HOUSES", null, null, null);
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
            backOffFailedSearch(level);
            return Optional.empty();
        }
        ReachableHouse chosen = nearest;
        ReachableHouse admitted = null;
        boolean danger = hasDetectedDanger(villager);
        String reason = nearest.house.membershipKnown() ? "NEAREST_HAS_SPACE" : "NEAREST_CAPACITY_UNKNOWN";
        if (!danger && !nearest.hasCapacity()) {
            for (ReachableHouse route : reachable) {
                if (route.hasCapacity() && (admitted == null || route.distance < admitted.distance)) admitted = route;
            }
            if (admitted != null && admitted.distance <= nearest.distance + MAX_ADDITIONAL_TRAVEL) {
                chosen = admitted;
                reason = "ALTERNATIVE_HAS_SPACE";
            } else {
                reason = admitted == null ? "NO_REACHABLE_HOUSE_WITH_SPACE" : "EXTRA_TRAVEL_OVERFLOW";
                for (ReachableHouse route : reachable) {
                    if (route.occupancy == null || route.distance > nearest.distance + MAX_ADDITIONAL_TRAVEL) continue;
                    if (route.occupancy.excess() < chosen.occupancy.excess()
                            || route.occupancy.excess() == chosen.occupancy.excess() && route.distance < chosen.distance) {
                        chosen = route;
                    }
                }
                if (chosen != nearest) reason = "LESS_CROWDED_OVERFLOW";
            }
        } else if (danger && !nearest.hasCapacity()) {
            reason = "DANGER_OVERFLOW";
        }
        if (debug) logSelection(villager, reason, chosen, nearest, admitted);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED,
                GlobalPos.of(level.dimension(), chosen.house.anchorFor(chosen.standing)));
        return Optional.of(chosen.endpoint);
    }

    private void backOffFailedSearch(ServerLevel level) {
        nextAttemptTime = Math.max(nextAttemptTime, level.getGameTime() + FAILED_SEARCH_RETRY_TICKS);
    }

    private static ReachableHouse findReachable(ServerLevel level, VillagerEntityMCA villager,
                                                IndoorRoomCache.House house, Set<BlockPos> candidates,
                                                Occupancy occupancy, boolean debug) {
        if (candidates.isEmpty()) {
            if (debug) logRoute(villager, house, "NO_USABLE_FLOOR", null, null);
            return null;
        }
        Path path = villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                ? navigation.createPathForPersistentIntent(candidates, 0)
                : villager.getNavigation().createPath(candidates, 0);
        if (path == null || !path.canReach()) {
            if (debug) logRoute(villager, house, path == null ? "NO_PATH" : "PARTIAL_PATH", path, null);
            return null;
        }

        BlockPos endpoint = path.getTarget();
        if (!candidates.contains(endpoint) || !isUsableFloor(level, villager, endpoint)) {
            if (debug) logRoute(villager, house, "INVALID_ENDPOINT", path, null);
            return null;
        }
        if (path.getNodeCount() == 0 && !villager.blockPosition().equals(endpoint)) {
            if (debug) logRoute(villager, house, "EMPTY_PATH_AWAY_FROM_TARGET", path, null);
            return null;
        }
        Vec3 standingPosition = path.getNodeCount() == 0 ? villager.position()
                : path.getEntityPosAtNode(villager, path.getNodeCount() - 1);
        BlockPos standing = BlockPos.containing(standingPosition);
        if (!isLoaded(level, standing) || !house.floorCells().contains(standing)
                || !isUsableFloor(level, villager, standing)
                || !level.noCollision(villager,
                villager.getBoundingBox().move(standingPosition.subtract(villager.position())))) {
            if (debug) logRoute(villager, house, "INVALID_STANDING_POSITION", path, standing);
            return null;
        }
        if (debug) logRoute(villager, house, "REACHABLE", path, standing);
        return new ReachableHouse(house, endpoint, standing, routeLength(villager, path),
                occupancy);
    }

    private static Occupancy occupancy(ServerLevel level, VillagerEntityMCA selecting,
                                       IndoorRoomCache.House house, List<? extends Villager> loaded,
                                       Map<UUID, BlockPos> homes, boolean debug) {
        Set<BlockPos> reservedBeds = new HashSet<>();
        Map<BlockPos, UUID> ownerByBed = new HashMap<>();
        for (var entry : homes.entrySet()) {
            if (house.bedHeads().contains(entry.getValue())) {
                reservedBeds.add(entry.getValue());
                ownerByBed.putIfAbsent(entry.getValue(), entry.getKey());
            }
        }
        for (BlockPos bed : house.bedHeads()) {
            var poiInfo = level.getPoiManager().getDebugPoiInfo(bed);
            if (level.getPoiManager().existsAtPosition(PoiTypes.HOME, bed)
                    && poiInfo != null && poiInfo.freeTicketCount() == 0) reservedBeds.add(bed);
        }
        Set<UUID> owners = new HashSet<>(ownerByBed.values());
        int occupants = reservedBeds.size();
        int capacity = house.bedHeads().size() + 5;
        int physicalGuests = 0;
        int incomingGuests = 0;
        List<String> countedGuests = debug ? new ArrayList<>() : null;
        for (Villager resident : loaded) {
            if (resident == selecting || owners.contains(resident.getUUID())) continue;
            var target = resident.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean inside = house.contains(resident.blockPosition());
            if (inside || target
                    .filter(walk -> house.contains(walk.getTarget().currentBlockPosition())).isPresent()) {
                if (debug) {
                    if (inside) physicalGuests++; else incomingGuests++;
                    if (countedGuests.size() < 16) {
                        countedGuests.add(resident.getName().getString() + "/" + resident.getUUID()
                                + "@" + resident.blockPosition().toShortString() + (inside ? ":inside" : ":incoming"));
                    }
                }
                occupants++;
            }
        }
        if (debug) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (BlockPos cell : house.floorCells()) {
                minX = Math.min(minX, cell.getX()); minZ = Math.min(minZ, cell.getZ());
                maxX = Math.max(maxX, cell.getX()); maxZ = Math.max(maxZ, cell.getZ());
            }
            var present = level.getEntitiesOfClass(Villager.class,
                    new AABB(minX, level.getMinY(), minZ, maxX + 1, level.getMaxY() + 1, maxZ + 1),
                    resident -> resident.isAlive() && house.contains(resident.blockPosition()));
            var unindexed = present.stream().filter(resident -> !loaded.contains(resident))
                    .map(resident -> resident.getName().getString() + "/" + resident.getUUID()).toList();
            MCA.LOGGER.info("[MCA-SHELTER][capacity] uuid={} gameTime={} anchor={} membershipKnown=true beds={} capacity={} reservedBeds={} physicalGuests={} incomingGuests={} countedOccupantsExcludingSelector={} hasSpace={} indexedVillagers={} actualInside={} unindexedInside={} countedGuestsFirst16={}",
                    selecting.getUUID(), level.getGameTime(), house.anchor(), house.bedHeads(), capacity, reservedBeds,
                    physicalGuests, incomingGuests, occupants, occupants < capacity, loaded.size(), present.size(), unindexed, countedGuests);
        }
        return new Occupancy(occupants, capacity);
    }

    private static void logRoute(VillagerEntityMCA villager, IndoorRoomCache.House house,
                                 String result, Path path, BlockPos standing) {
        MCA.LOGGER.info("[MCA-SHELTER][route] uuid={} gameTime={} anchor={} result={} endpoint={} standing={} canReach={} nodes={} distance={}",
                villager.getUUID(), villager.level().getGameTime(), house.anchor(), result,
                path == null ? null : path.getTarget(), standing, path != null && path.canReach(),
                path == null ? 0 : path.getNodeCount(), path == null ? null : routeLength(villager, path));
    }

    private static void logSelection(VillagerEntityMCA villager, String reason, ReachableHouse chosen,
                                     ReachableHouse nearest, ReachableHouse alternative) {
        var brain = villager.getBrain();
        MCA.LOGGER.info("[MCA-SHELTER][selection] name={} uuid={} gameTime={} pos={} reason={} chosen={} endpoint={} chosenOccupancy={} chosenDistance={} nearest={} nearestOccupancy={} nearestDistance={} alternative={} alternativeDistance={} maxAdditionalTravel={} hostile={} attacker={}",
                villager.getName().getString(), villager.getUUID(), villager.level().getGameTime(), villager.blockPosition(), reason,
                chosen == null ? null : chosen.house.anchor(), chosen == null ? null : chosen.endpoint,
                chosen == null ? null : chosen.occupancy, chosen == null ? null : chosen.distance,
                nearest == null ? null : nearest.house.anchor(), nearest == null ? null : nearest.occupancy,
                nearest == null ? null : nearest.distance,
                alternative == null ? null : alternative.house.anchor(), alternative == null ? null : alternative.distance,
                MAX_ADDITIONAL_TRAVEL,
                brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).map(threat -> threat.getType() + "/" + threat.getUUID()
                        + "@" + threat.blockPosition().toShortString() + ":distance=" + threat.distanceTo(villager)),
                brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).map(threat -> threat.getType() + "/" + threat.getUUID()
                        + "@" + threat.blockPosition().toShortString() + ":distance=" + threat.distanceTo(villager)));
    }

    static double routeLength(VillagerEntityMCA villager, Path path) {
        double length = 0.0D;
        Vec3 previous = villager.position();
        for (int i = 0; i < path.getNodeCount(); i++) {
            Vec3 next = path.getEntityPosAtNode(villager, i);
            length += previous.distanceTo(next);
            previous = next;
        }
        return length;
    }

    private static boolean isLoaded(ServerLevel level, BlockPos position) {
        return level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) != null;
    }

    private static boolean hasDetectedDanger(VillagerEntityMCA villager) {
        var brain = villager.getBrain();
        boolean hostile = brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).filter(threat -> isLiveThreat(villager, threat))
                .filter(threat -> threat instanceof Creeper creeper ? creeper.isIgnited()
                        : HOSTILES.isMatchingEntity((ServerLevel) villager.level(), villager, threat)).isPresent();
        return hostile || brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).filter(threat -> isLiveThreat(villager, threat))
                .filter(threat -> threat.distanceToSqr(villager) <= 36.0D).isPresent();
    }

    private static boolean isLiveThreat(VillagerEntityMCA villager, LivingEntity threat) {
        return threat.isAlive() && !threat.isRemoved() && threat.level() == villager.level();
    }

    private record Occupancy(int occupants, int capacity) {
        private boolean hasSpace() { return occupants < capacity; }
        private int excess() { return Math.max(0, occupants - capacity); }
    }

    private record ReachableHouse(IndoorRoomCache.House house, BlockPos endpoint, BlockPos standing,
                                  double distance, Occupancy occupancy) {
        private boolean hasCapacity() { return occupancy == null || occupancy.hasSpace(); }
    }

    @Override
    protected int getCompletionRange() {
        return 0;
    }
}
