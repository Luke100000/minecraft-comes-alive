package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.PathNodeGeometry;
import net.conczin.mca.server.world.data.IndoorRoomCache;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

import java.util.*;
import java.util.stream.Stream;

/** Bed POIs select shelter; floor topology supplies house admission without claiming HOME. */
public final class SeekIndoorShelterTask extends EnterBuildingTask {
    private static final int MAX_HOUSES = 10;
    private static final double MAX_ADDITIONAL_TRAVEL = 64.0D;
    private static final int MAX_EXTRA_OCCUPANTS = 5;
    private static final int FLOOR_CELLS_PER_OCCUPANT = 2;
    private static final long FAILED_SEARCH_RETRY_TICKS = 100L;
    private static final VillagerHostilesSensor HOSTILES = new VillagerHostilesSensor();
    private long nextAttemptTime;

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
        return !alreadyIndoors;
    }

    /** A selected bed identifies a house; local wandering stays in the actual current room. */
    static Optional<IndoorRoomCache.Room> currentRoom(VillagerEntityMCA villager) {
        ServerLevel level = (ServerLevel) villager.level();
        var cache = VillageManager.get(level).getIndoorRooms();
        var selected = villager.getBrain().getMemory(MemoryModuleTypeMCA.SHELTER_BED)
                .filter(bed -> bed.dimension().equals(level.dimension())).map(GlobalPos::pos);
        if (selected.isPresent()) {
            var room = cache.resolveRoom(selected.orElseThrow(), villager.blockPosition());
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
        var beds = nearbyBeds(level, villager).toList();
        var houses = manager.getIndoorRooms().resolveHouses(beds, MAX_HOUSES);
        if (houses.isEmpty()) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
            backOffFailedSearch(level);
            return Optional.empty();
        }
        boolean danger = hasDetectedDanger(villager);
        List<Villager> loaded = !danger ? manager.getLoadedVillagers() : List.of();
        Map<UUID, BlockPos> homes = !danger ? new HashMap<>() : Map.of();
        if (!danger) {
            for (var village : manager) village.forEachResidentHome(homes::put);
            for (Villager resident : loaded) {
                var home = resident.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(pos -> pos.dimension().equals(level.dimension()));
                if (home.isPresent()) homes.put(resident.getUUID(), home.orElseThrow().pos());
                else homes.remove(resident.getUUID());
            }
        }

        List<ReachableHouse> reachable = new ArrayList<>();
        ReachableHouse nearest = null;
        for (var house : houses) {
            Set<BlockPos> floor = new HashSet<>();
            for (var room : house.rooms()) floor.addAll(room.floorCells());
            int usableFloorCells = 0;
            Set<BlockPos> candidates = new HashSet<>();
            for (BlockPos pos : floor) {
                if (!isLoaded(level, pos) || !isUsableFloorGeometry(level, villager, pos)) continue;
                usableFloorCells++;
                if (isUsableFloor(level, villager, pos)) candidates.add(pos);
            }
            Occupancy occupancy = !danger && house.membershipKnown()
                    ? occupancy(level, villager, house, loaded, homes, usableFloorCells)
                    : null;
            ReachableHouse route = findReachable(level, villager, house, candidates, occupancy);
            if (route == null) continue;
            reachable.add(route);
            // Strict comparison preserves discovery order for equal-length routes.
            if (nearest == null || route.distance < nearest.distance) nearest = route;
        }
        if (nearest == null) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.SHELTER_BED);
            backOffFailedSearch(level);
            return Optional.empty();
        }
        ReachableHouse chosen = nearest;
        if (!danger) {
            ReachableHouse admitted = null;
            ReachableHouse unknown = null;
            ReachableHouse overflow = nearest;
            for (ReachableHouse route : reachable) {
                if (route.distance > nearest.distance + MAX_ADDITIONAL_TRAVEL) continue;
                if (route.occupancy == null) {
                    if (unknown == null || route.distance < unknown.distance) unknown = route;
                    continue;
                }
                if (route.hasCapacity() && (admitted == null || isBetterShelter(route, admitted))) admitted = route;
                if (overflow.occupancy == null || isBetterShelter(route, overflow)) overflow = route;
            }
            if (admitted != null) {
                chosen = admitted;
            } else if (unknown != null) {
                chosen = unknown;
            } else {
                chosen = overflow;
            }
        }
        villager.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED,
                GlobalPos.of(level.dimension(), chosen.house.anchorFor(chosen.standing)));
        return Optional.of(chosen.endpoint);
    }

    private static boolean isBetterShelter(ReachableHouse candidate, ReachableHouse current) {
        long candidateSpace = candidate.occupancy.usableFloorCells * (current.occupancy.occupants + 1L);
        long currentSpace = current.occupancy.usableFloorCells * (candidate.occupancy.occupants + 1L);
        return candidateSpace > currentSpace
                || candidateSpace == currentSpace && candidate.distance < current.distance;
    }

    private void backOffFailedSearch(ServerLevel level) {
        nextAttemptTime = Math.max(nextAttemptTime, level.getGameTime() + FAILED_SEARCH_RETRY_TICKS);
    }

    private static ReachableHouse findReachable(ServerLevel level, VillagerEntityMCA villager,
                                                IndoorRoomCache.House house, Set<BlockPos> candidates,
                                                Occupancy occupancy) {
        if (candidates.isEmpty()) {
            return null;
        }
        Path path = villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                ? navigation.createPathForPersistentIntent(candidates, 0)
                : villager.getNavigation().createPath(candidates, 0);
        if (path == null || !path.canReach()) {
            return null;
        }

        BlockPos endpoint = path.getTarget();
        if (!candidates.contains(endpoint) || !isUsableFloor(level, villager, endpoint)) {
            return null;
        }
        if (path.getNodeCount() == 0 && !villager.blockPosition().equals(endpoint)) {
            return null;
        }
        Vec3 standingPosition;
        if (path.getNodeCount() == 0) {
            standingPosition = villager.position();
        } else {
            int lastNode = path.getNodeCount() - 1;
            Vec3 nodePosition = path.getEntityPosAtNode(villager, lastNode);
            double walkingY = PathNodeGeometry.walkingPosition(level, path.getNodePos(lastNode), villager).y;
            standingPosition = new Vec3(nodePosition.x, walkingY, nodePosition.z);
        }
        BlockPos standing = BlockPos.containing(standingPosition);
        if (!isLoaded(level, standing) || !house.floorCells().contains(standing)
                || !isUsableFloor(level, villager, standing)
                || !level.noCollision(villager,
                villager.getBoundingBox().move(standingPosition.subtract(villager.position())))) {
            return null;
        }
        return new ReachableHouse(house, endpoint, standing, routeLength(villager, path),
                occupancy);
    }

    private static Occupancy occupancy(ServerLevel level, VillagerEntityMCA selecting,
                                       IndoorRoomCache.House house, List<? extends Villager> loaded,
                                       Map<UUID, BlockPos> homes, int usableFloorCells) {
        Set<BlockPos> reservedBeds = new HashSet<>();
        Map<BlockPos, UUID> ownerByBed = new HashMap<>();
        for (var entry : homes.entrySet()) {
            if (house.bedHeads().contains(entry.getValue())) {
                reservedBeds.add(entry.getValue());
                ownerByBed.putIfAbsent(entry.getValue(), entry.getKey());
            }
        }
        for (BlockPos bed : house.bedHeads()) {
            if (level.getPoiManager().existsAtPosition(PoiTypes.HOME, bed)
                    && level.getPoiManager().getFreeTickets(bed) == 0) reservedBeds.add(bed);
        }
        Set<UUID> owners = new HashSet<>(ownerByBed.values());
        int occupants = reservedBeds.size();
        int capacity = Math.min(house.bedHeads().size() + MAX_EXTRA_OCCUPANTS,
                usableFloorCells / FLOOR_CELLS_PER_OCCUPANT);
        for (Villager resident : loaded) {
            if (resident == selecting || owners.contains(resident.getUUID())) continue;
            var target = resident.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            boolean inside = house.contains(resident.blockPosition());
            if (inside || target
                    .filter(walk -> house.contains(walk.getTarget().currentBlockPosition())).isPresent()) {
                occupants++;
            }
        }
        return new Occupancy(occupants, capacity, usableFloorCells);
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
                        || isNearbyVillagerTargeter(villager, threat)
                        : HOSTILES.isMatchingEntity(villager, threat) || isNearbyVillagerTargeter(villager, threat)).isPresent();
        boolean nearbyDanger = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of()).stream()
                .anyMatch(threat -> isLiveThreat(villager, threat)
                        && (isNearbyVillagerTargeter(villager, threat)
                        || threat instanceof Creeper creeper && creeper.isIgnited()
                        && threat.distanceToSqr(villager) <= 256.0D));
        return hostile || nearbyDanger || brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).filter(threat -> isLiveThreat(villager, threat))
                .filter(threat -> threat.distanceToSqr(villager) <= 36.0D).isPresent();
    }

    private static boolean isNearbyVillagerTargeter(VillagerEntityMCA villager, LivingEntity threat) {
        return threat instanceof Mob mob && mob.getTarget() instanceof Villager
                && threat.distanceToSqr(villager) <= 256.0D;
    }

    private static boolean isLiveThreat(VillagerEntityMCA villager, LivingEntity threat) {
        return threat.isAlive() && !threat.isRemoved() && threat.level() == villager.level();
    }

    private record Occupancy(int occupants, int capacity, int usableFloorCells) {
        private boolean hasSpace() { return occupants < capacity; }
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
